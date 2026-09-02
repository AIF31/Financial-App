package com.aif31.pocket

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.RoomPocketLedger
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.PocketIconKey
import com.aif31.pocket.settings.AppPreferences
import com.aif31.pocket.settings.PreferencesStore
import com.aif31.pocket.settings.ReminderScheduler
import com.aif31.pocket.ui.SettingsSection
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(ExperimentalTestApi::class)
class PocketAppHostFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: FinanceDatabase

    @Before fun setUp() {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun user_completes_the_core_spending_flow_through_the_public_UI() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        val preferences = FakePreferences()
        compose.setContent { PocketApp(ledger, preferences = preferences) }

        compose.waitUntilExactlyOneExists(hasText("Configura tu primer periodo"), 5_000)
        compose.onNodeWithTag("new_funds").performTextInput("1000.00")
        compose.onNodeWithTag("start_day").performTextReplacement("10")
        compose.onNodeWithText("Comenzar").performClick()
        compose.waitUntilDoesNotExist(hasText("Configura tu primer periodo"), 10_000)
        compose.waitUntil(5_000) { preferences.current.futurePeriodStartDay == 10 }
        compose.waitUntilAtLeastOneExists(hasText("SAR 1,000.00"), 5_000)

        compose.onNodeWithText("Pockets").performClick()
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("pocket_Supermercado"))
        compose.onNodeWithTag("pocket_Supermercado").performClick()
        compose.onNodeWithTag("allocation_amount").performTextReplacement("300.00")
        compose.onNodeWithText("Guardar presupuesto").performClick()
        compose.onNodeWithText("Inicio").performClick()
        compose.onNodeWithTag("contextual_add").performClick()
        compose.onNodeWithTag("movement_amount").performTextInput("100.00")
        compose.onNodeWithTag("movement_pocket_Supermercado").performClick()
        compose.onNodeWithText("Guardar gasto", substring = true).performClick()

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.waitUntil(5_000) {
            runBlocking {
                ledger.state.first().pockets.any {
                    it.pocket.name == "Supermercado" && it.availabilityMinor == 20_000L
                }
            }
        }

        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("SAR 200.00 disponibles"))
        compose.onNodeWithText("SAR 200.00 disponibles").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("rollover_Supermercado"))
        compose.onNodeWithTag("rollover_Supermercado").assertIsDisplayed()
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithText("- SAR 100.00").assertIsDisplayed()
    }

    @Test
    fun new_expense_uses_default_payment_and_template_method_overrides_it() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking {
            ledger.execute(LedgerCommand.Initialize(100_000))
            val state = ledger.state.first { !it.needsOnboarding }
            val cashId = state.paymentMethods.single { it.name == "Efectivo" }.id
            ledger.execute(
                LedgerCommand.UpsertTemplate(
                    id = "cash-template",
                    name = "Plantilla efectivo",
                    amountMinor = 2_500,
                    pocketId = state.pockets.first().pocket.id,
                    paymentMethodId = cashId,
                    inputCurrency = com.aif31.pocket.domain.SupportedCurrency.USD,
                )
            )
        }
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithTag("contextual_add").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("movement_form"), 5_000)
        compose.onNodeWithTag("movement_form").performScrollToNode(hasText("✓ Tarjeta"))
        compose.onNodeWithText("✓ Tarjeta").assertIsDisplayed()

        compose.onNodeWithTag("movement_form").performScrollToNode(hasText("Plantilla efectivo"))
        compose.onNodeWithText("Plantilla efectivo").performClick()
        compose.onNodeWithTag("movement_form").performScrollToNode(hasText("✓ Efectivo"))
        compose.onNodeWithText("✓ Efectivo").assertIsDisplayed()
        compose.onNodeWithTag("movement_form").performScrollToNode(hasText("Más detalles"))
        compose.onNodeWithText("Más detalles").performClick()
        compose.onNodeWithTag("movement_form").performScrollToNode(hasText("✓ USD"))
        compose.onNodeWithText("✓ USD").assertIsDisplayed()
        compose.onNodeWithTag("movement_amount").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
        )
        compose.onNodeWithTag("movement_original_amount").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("25.00"))
        )
    }

    @Test
    fun settings_selects_an_active_default_payment_or_none() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking { ledger.execute(LedgerCommand.Initialize(100_000)) }
        compose.setContent {
            val state = ledger.state.collectAsState(initial = null).value
            state?.let {
                SettingsScreen(
                    state = it,
                    ledger = ledger,
                    preferences = AppPreferences(),
                    preferencesStore = null,
                    reminderScheduler = null,
                    onCreateBackup = {},
                    onCreateCsv = {},
                    onPickBackup = {},
                    onRequestNotificationPermission = {},
                    padding = PaddingValues(),
                    section = SettingsSection.PAYMENT_METHODS,
                    onSectionChange = {},
                )
            }
        }

        compose.waitUntilExactlyOneExists(hasTestTag("settings_list"), 5_000)
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("default_payment_Tarjeta"))
        compose.onNodeWithTag("default_payment_Tarjeta").assertTextContains("✓ Tarjeta")

        compose.onNodeWithTag("default_payment_none").performClick()
        compose.waitUntil(5_000) { runBlocking { ledger.state.first().defaultPaymentMethodId == null } }
        compose.onNodeWithTag("default_payment_none").assertTextContains("✓ Ninguno")

        compose.onNodeWithTag("default_payment_Efectivo").performClick()
        val cashId = runBlocking { ledger.state.first().paymentMethods.single { it.name == "Efectivo" }.id }
        compose.waitUntil(5_000) { runBlocking { ledger.state.first().defaultPaymentMethodId == cashId } }
        compose.onNodeWithTag("default_payment_Efectivo").assertTextContains("✓ Efectivo")
    }

    @Test
    fun dashboard_expense_action_opens_a_full_screen_task_and_returns_to_inicio() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(
            database,
            Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone),
            zone,
        )
        runBlocking { ledger.execute(LedgerCommand.Initialize(100_000)) }

        compose.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasTestTag("contextual_add"), 5_000)
        compose.onNodeWithTag("contextual_add").assertIsDisplayed()
        compose.onNodeWithTag("contextual_add").performClick()

        compose.waitUntilExactlyOneExists(hasText("Nuevo gasto"), 5_000)
        compose.onAllNodesWithText("Inicio").assertCountEquals(0)
        compose.onNodeWithTag("movement_amount").performTextInput("12.34")
        compose.onNodeWithTag("movement_pocket_Supermercado").performClick()
        compose.onNodeWithText("Guardar gasto", substring = true).performClick()

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithText("Inicio").assertExists()
        val saved = runBlocking {
            ledger.state.first { it.movements.size == 1 }.movements.single()
        }
        assertEquals(1_234L, saved.accountingAmountMinor)
    }

    @Test
    fun quick_expense_keeps_advanced_fields_behind_more_details() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(
            database,
            Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone),
            zone,
        )
        runBlocking { ledger.execute(LedgerCommand.Initialize(100_000)) }

        compose.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasTestTag("contextual_add"), 5_000)
        compose.onNodeWithTag("contextual_add").performClick()

        compose.onNodeWithText("Fecha y hora").assertDoesNotExist()
        compose.onNodeWithTag("movement_form").performScrollToNode(hasText("Más detalles"))
        compose.onNodeWithText("Más detalles").performClick()
        compose.onNodeWithTag("movement_form").performScrollToNode(hasText("Fecha y hora"))
        compose.onNodeWithText("Fecha y hora").assertIsDisplayed()
        compose.onNodeWithTag("movement_form").performScrollToNode(hasText("Ocultar detalles"))
        compose.onNodeWithText("Ocultar detalles").assertIsDisplayed()
    }

    @Test
    fun movement_draft_survives_saved_state_restoration() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking { ledger.execute(LedgerCommand.Initialize(100_000)) }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasTestTag("contextual_add"), 5_000)
        compose.onNodeWithTag("contextual_add").performClick()
        compose.onNodeWithTag("movement_amount").performTextInput("12.34")
        compose.onNodeWithTag("movement_pocket_Supermercado").performClick()

        restoration.emulateSavedInstanceStateRestore()

        compose.waitUntilExactlyOneExists(hasTestTag("movement_amount"), 5_000)
        compose.onNodeWithTag("movement_amount").assertTextContains("12.34")
        compose.onNodeWithTag("movement_pocket_Supermercado").assertIsDisplayed()
    }

    @Test
    fun daily_reminder_can_be_enabled_and_disabled_through_settings() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking { ledger.execute(com.aif31.pocket.data.LedgerCommand.Initialize(100_000)) }
        val preferences = FakePreferences()
        val scheduler = FakeReminderScheduler()
        compose.setContent { PocketApp(ledger, preferences = preferences, reminderScheduler = scheduler) }

        compose.waitUntilExactlyOneExists(hasText("Inicio"), 5_000)
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("Recordatorio diario").performClick()
        compose.onAllNodesWithText("Inicio").assertCountEquals(0)
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("reminder_switch"))
        compose.onNodeWithTag("reminder_time").performTextReplacement("08:30")
        compose.onNodeWithTag("reminder_switch").performClick()
        compose.waitUntil(5_000) {
            preferences.current.reminderEnabled && scheduler.enabled == true && scheduler.time == LocalTime.of(8, 30)
        }
        compose.onNodeWithTag("reminder_switch").performClick()
        compose.waitUntil(5_000) { !preferences.current.reminderEnabled && scheduler.enabled == false }
    }

    @Test
    fun history_search_and_each_filter_are_observable() {
        val zone = ZoneId.of("Asia/Riyadh")
        val clock = Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone)
        val ledger = RoomPocketLedger(database, clock, zone)
        runBlocking {
            ledger.execute(LedgerCommand.Initialize(100_000))
            val state = ledger.state.first { !it.needsOnboarding }
            val supermarket = state.pockets.first { it.pocket.name == "Supermercado" }
            val travel = state.pockets.first { it.pocket.name == "Viajes" }
            val cash = state.paymentMethods.first { it.name == "Efectivo" }
            val card = state.paymentMethods.first { it.name == "Tarjeta" }
            ledger.execute(LedgerCommand.AddMovement(id = "market", pocketId = supermarket.pocket.id, type = MovementType.EXPENSE,
                accountingAmountMinor = 1_000, occurredAtUtcMillis = clock.millis(), localDate = java.time.LocalDate.of(2026, 2, 26),
                merchant = "Mercado", note = "fruta fresca", paymentMethodId = cash.id))
            ledger.execute(LedgerCommand.AddMovement(id = "hotel", pocketId = travel.pocket.id, type = MovementType.EXPENSE,
                accountingAmountMinor = 2_000, occurredAtUtcMillis = clock.millis() + 1, localDate = java.time.LocalDate.of(2026, 2, 26),
                merchant = "Hotel", paymentMethodId = card.id, originalAmountMinor = 500, originalCurrencyCode = "USD"))
            ledger.execute(LedgerCommand.CreateNextPeriod())
            val nextPeriod = ledger.state.first { it.periods.size == 2 }.periods.last()
            ledger.execute(LedgerCommand.AddMovement(id = "taxi", pocketId = travel.pocket.id, type = MovementType.EXPENSE,
                accountingAmountMinor = 3_000, occurredAtUtcMillis = clock.millis() + 2, localDate = nextPeriod.start,
                merchant = "Taxi", paymentMethodId = card.id))
        }
        runBlocking { ledger.state.first { it.movements.size == 3 } }
        compose.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasText("Inicio"), 5_000)
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithTag("history_search").performTextInput("fruta")
        compose.onNodeWithText("Mercado").assertExists()
        compose.onAllNodesWithText("Hotel").assertCountEquals(0)
        compose.onAllNodesWithText("Taxi").assertCountEquals(0)
        compose.onNodeWithTag("history_search").performTextClearance()

        compose.onNodeWithTag("filter_period").performClick()
        compose.onNodeWithTag("filter_period_option_1").performClick()
        compose.onNodeWithText("Hotel").assertExists()
        compose.onAllNodesWithText("Taxi").assertCountEquals(0)
        compose.onNodeWithTag("clear_filters").performClick()

        compose.onNodeWithTag("filter_pocket").performClick()
        compose.onNodeWithTag("filter_pocket_option_1").performClick()
        compose.onNodeWithText("Mercado").assertExists()
        compose.onAllNodesWithText("Hotel").assertCountEquals(0)
        compose.onAllNodesWithText("Taxi").assertCountEquals(0)
        compose.onNodeWithTag("clear_filters").performClick()

        compose.onNodeWithTag("filter_currency").performClick()
        compose.onNodeWithTag("filter_currency_option_1").performClick()
        compose.onNodeWithText("Taxi").assertExists()
        compose.onAllNodesWithText("Hotel").assertCountEquals(0)
        compose.onNodeWithTag("clear_filters").performClick()

        compose.onNodeWithTag("filter_method").performClick()
        compose.onNodeWithTag("filter_method_option_1").performClick()
        compose.onNodeWithText("Mercado").assertExists()
        compose.onAllNodesWithText("Hotel").assertCountEquals(0)
        compose.onAllNodesWithText("Taxi").assertCountEquals(0)
    }

    @Test
    fun movement_search_survives_saved_state_restoration() {
        val zone = ZoneId.of("Asia/Riyadh")
        val clock = Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone)
        val ledger = RoomPocketLedger(database, clock, zone)
        runBlocking {
            ledger.execute(LedgerCommand.Initialize(100_000))
            val pocket = ledger.state.first { !it.needsOnboarding }.pockets.first()
            ledger.execute(
                LedgerCommand.AddMovement(
                    id = "market",
                    pocketId = pocket.pocket.id,
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = 1_000,
                    occurredAtUtcMillis = clock.millis(),
                    localDate = LocalDate.of(2026, 2, 26),
                    merchant = "Mercado",
                    note = "fruta fresca",
                )
            )
        }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasText("Movimientos"), 5_000)
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithTag("history_search").performTextInput("fruta")

        restoration.emulateSavedInstanceStateRestore()

        compose.waitUntilExactlyOneExists(hasTestTag("history_search"), 5_000)
        compose.onNodeWithTag("history_search").assertTextContains("fruta")
        compose.onNodeWithText("Mercado").assertIsDisplayed()
    }

    @Test
    fun delete_supports_undo_and_permanent_expiry_through_the_UI() {
        val zone = ZoneId.of("Asia/Riyadh")
        val clock = Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone)
        val ledger = RoomPocketLedger(database, clock, zone)
        runBlocking {
            ledger.execute(LedgerCommand.Initialize(100_000))
            val pocket = ledger.state.first { !it.needsOnboarding }.pockets.first()
            ledger.execute(LedgerCommand.AddMovement(id = "undo", pocketId = pocket.pocket.id, type = MovementType.EXPENSE,
                accountingAmountMinor = 1_000, occurredAtUtcMillis = clock.millis(), localDate = java.time.LocalDate.of(2026, 2, 26)))
        }
        compose.setContent { PocketApp(ledger, undoWindowMillis = 1_000) }
        compose.waitUntilExactlyOneExists(hasText("Inicio"), 5_000)
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithText("- SAR 10.00").performClick()
        compose.onNodeWithText("Eliminar").performClick()
        compose.waitUntilExactlyOneExists(hasText("Deshacer"), 5_000)
        compose.onNodeWithText("Deshacer").performClick()
        compose.waitUntilExactlyOneExists(hasText("- SAR 10.00"), 5_000)

        compose.onNodeWithText("- SAR 10.00").performClick()
        compose.onNodeWithText("Eliminar").performClick()
        compose.waitUntilExactlyOneExists(hasText("Deshacer"), 5_000)
        compose.waitUntilDoesNotExist(hasText("Deshacer"), 5_000)
        compose.onAllNodesWithText("- SAR 10.00").assertCountEquals(0)
    }

    @Test
    fun launcher_shortcut_action_opens_the_expense_form_and_saves_a_movement() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking { ledger.execute(LedgerCommand.Initialize(100_000)) }
        compose.setContent { PocketApp(ledger, openNewExpense = true) }
        compose.waitUntilExactlyOneExists(hasText("Nuevo gasto"), 5_000)
        compose.onNodeWithTag("movement_amount").performTextInput("12.34")
        compose.onNodeWithTag("movement_pocket_Supermercado").performClick()
        compose.onNodeWithText("Guardar gasto", substring = true).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        val saved = runBlocking { ledger.state.first { it.movements.size == 1 }.movements.single() }
        assertEquals(1_234L, saved.accountingAmountMinor)
        assertEquals("Supermercado", saved.pocketName)
    }

    @Test
    fun catch_up_review_banner_routes_to_pockets_and_can_be_cleared() {
        val zone = ZoneId.of("Asia/Riyadh")
        val initialLedger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking { initialLedger.execute(LedgerCommand.Initialize(100_000)) }
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-05-01T09:00:00Z"), zone), zone)
        runBlocking { ledger.execute(LedgerCommand.CatchUpPeriods(25)) }

        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasText("Revisa el presupuesto de este periodo"), 5_000)
        compose.onNodeWithText("Revisar Pockets").performClick()
        compose.waitUntilExactlyOneExists(hasText("Marcar periodo como revisado"), 5_000)
        compose.onNodeWithText("Marcar periodo como revisado").performClick()
        compose.waitUntilDoesNotExist(hasText("Marcar periodo como revisado"), 5_000)
        assertEquals(false, runBlocking { ledger.state.first { it.currentPeriod?.needsReview == false }.currentPeriod!!.needsReview })
    }

    @Test
    fun launcher_shortcut_after_catch_up_records_against_the_new_current_period() {
        val zone = ZoneId.of("Asia/Riyadh")
        val initialLedger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking { initialLedger.execute(LedgerCommand.Initialize(100_000)) }
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-05-01T09:00:00Z"), zone), zone)
        runBlocking { ledger.execute(LedgerCommand.CatchUpPeriods(25)) }
        val currentPeriodId = runBlocking { ledger.state.first { it.currentPeriod != null }.currentPeriod!!.id }

        compose.setContent { PocketApp(ledger, openNewExpense = true) }
        compose.waitUntilExactlyOneExists(hasText("Nuevo gasto"), 5_000)
        compose.onNodeWithTag("movement_amount").performTextInput("12.34")
        compose.onNodeWithTag("movement_pocket_Supermercado").performClick()
        compose.onNodeWithText("Guardar gasto", substring = true).performClick()

        val saved = runBlocking { ledger.state.first { it.movements.size == 1 }.movements.single() }
        assertEquals(currentPeriodId, saved.periodId)
    }

    @Test
    fun restore_confirmation_warns_before_replacing_initialized_data() {
        val zone = ZoneId.of("Asia/Riyadh")
        val clock = Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone)
        val sourceDatabase = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        val backup = try {
            val source = RoomPocketLedger(sourceDatabase, clock, zone)
            runBlocking {
                source.execute(LedgerCommand.Initialize(75_000))
                source.exportBackup()
            }
        } finally {
            sourceDatabase.close()
        }
        val target = RoomPocketLedger(database, clock, zone)
        runBlocking { target.execute(LedgerCommand.Initialize(10_000)) }

        compose.setContent {
            PocketApp(
                ledger = target,
                restoreCandidate = backup,
                onRestoreCandidateHandled = {},
            )
        }

        compose.waitUntilExactlyOneExists(
            hasText("Esta acción reemplazará los datos actuales y puede eliminar información anterior. No se puede deshacer."),
            5_000,
        )
        compose.onNodeWithText("Restaurar y reemplazar").performClick()
        compose.waitUntil(5_000) { runBlocking { target.state.first().newFundsMinor == 75_000L } }
    }

    @Test
    fun restoring_an_expired_backup_catches_up_before_returning_to_the_app() {
        val zone = ZoneId.of("Asia/Riyadh")
        val sourceDatabase = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        val backup = try {
            val source = RoomPocketLedger(
                sourceDatabase,
                Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone),
                zone,
            )
            runBlocking {
                source.execute(LedgerCommand.Initialize(75_000))
                source.exportBackup()
            }
        } finally {
            sourceDatabase.close()
        }
        val target = RoomPocketLedger(
            database,
            Clock.fixed(Instant.parse("2026-05-01T09:00:00Z"), zone),
            zone,
        )
        runBlocking { target.execute(LedgerCommand.Initialize(10_000)) }
        val preferences = FakePreferences()
        var restoreHandled = false
        compose.setContent {
            PocketApp(
                ledger = target,
                preferences = preferences,
                restoreCandidate = backup,
                onRestoreCandidateHandled = { restoreHandled = true },
            )
        }

        compose.waitUntilExactlyOneExists(hasText("Restaurar y reemplazar"), 5_000)
        compose.onNodeWithText("Restaurar y reemplazar").performClick()

        compose.waitUntil(10_000) { restoreHandled }
        compose.waitUntil(10_000) {
            runBlocking {
                target.state.first().let { state -> state.currentPeriod != null && state.periods.size == 3 }
            }
        }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        val restored = runBlocking { target.state.first() }
        assertEquals(LocalDate.of(2026, 4, 25), restored.currentPeriod!!.start)
        assertEquals(listOf(false, false, true), restored.periods.map { it.needsReview })
    }

    @Test
    fun document_failures_are_reported_through_the_public_UI() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking { ledger.execute(LedgerCommand.Initialize(100_000)) }
        var handled = false

        compose.setContent {
            PocketApp(
                ledger = ledger,
                operationMessage = "No se pudo crear el backup.",
                onOperationMessageHandled = { handled = true },
            )
        }

        compose.waitUntilExactlyOneExists(hasText("No se pudo crear el backup."), 5_000)
        compose.waitUntil(5_000) { handled }
    }

    @Test
    fun new_pocket_offers_nine_generated_icons_and_persists_the_selection() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking { ledger.execute(LedgerCommand.Initialize(100_000)) }
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasText("Pockets"), 5_000)
        compose.onNodeWithText("Pockets").performClick()
        compose.onNodeWithText("Crear Pocket").performClick()
        listOf(
            "supermarket", "restaurant", "transport", "university", "health",
            "travel", "leisure", "gifts", "emergency",
        ).forEach { key -> compose.onNodeWithTag("pocket_icon_$key").assertExists() }
        compose.onNodeWithTag("pocket_name").performTextInput("Vacaciones")
        compose.onNodeWithTag("pocket_icon_travel").performClick()
        compose.onNodeWithText("Guardar Pocket").performClick()

        val created = runBlocking {
            ledger.state.first { state -> state.pockets.any { it.pocket.name == "Vacaciones" } }
                .pockets.first { it.pocket.name == "Vacaciones" }
        }
        assertEquals(PocketIconKey.TRAVEL, created.pocket.iconKey)
    }

    @Test
    fun retired_current_period_pocket_is_visible_without_edit_actions() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking {
            ledger.execute(LedgerCommand.Initialize(100_000))
            val pocket = ledger.state.first { !it.needsOnboarding }.pockets.first { it.pocket.name == "Viajes" }.pocket
            ledger.execute(LedgerCommand.ArchivePocket(pocket.id))
        }
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasText("Pockets"), 5_000)
        compose.onNodeWithText("Pockets").performClick()
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasText("Retirado este periodo"))
        compose.onNodeWithText("Retirado este periodo").assertIsDisplayed()
        compose.onNodeWithTag("retired_Viajes").performClick()
        compose.onNodeWithText("Pocket retirado").assertIsDisplayed()
        compose.onAllNodesWithText("Editar Pocket").assertCountEquals(0)
        compose.onAllNodesWithText("Guardar presupuesto").assertCountEquals(0)
    }

    @Test
    fun a_retired_Pocket_remains_visible_with_spending_and_refunds_in_history() {
        val zone = ZoneId.of("Asia/Riyadh")
        val firstLedger = RoomPocketLedger(
            database,
            Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone),
            zone,
        )
        runBlocking {
            firstLedger.execute(LedgerCommand.Initialize(100_000))
            val state = firstLedger.state.first { !it.needsOnboarding }
            val pocket = state.pockets.first { it.pocket.name == "Viajes" }.pocket
            firstLedger.execute(
                LedgerCommand.AddMovement(
                    "retired-expense",
                    pocket.id,
                    MovementType.EXPENSE,
                    2_000,
                    Instant.parse("2026-02-26T09:00:00Z").toEpochMilli(),
                    LocalDate.of(2026, 2, 26),
                )
            )
            firstLedger.execute(
                LedgerCommand.AddMovement(
                    "retired-refund",
                    pocket.id,
                    MovementType.REFUND,
                    500,
                    Instant.parse("2026-02-26T09:01:00Z").toEpochMilli(),
                    LocalDate.of(2026, 2, 26),
                )
            )
            firstLedger.execute(LedgerCommand.ArchivePocket(pocket.id))
            firstLedger.execute(LedgerCommand.CreateNextPeriod())
        }
        val currentLedger = RoomPocketLedger(
            database,
            Clock.fixed(Instant.parse("2026-03-26T09:00:00Z"), zone),
            zone,
        )
        compose.setContent { PocketApp(currentLedger) }

        compose.waitUntilExactlyOneExists(hasText("Pockets"), 5_000)
        compose.onNodeWithText("Pockets").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("pockets_list"), 5_000)
        compose.onNodeWithText("25 feb – 24 mar").performClick()
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasText("Retirado este periodo"))

        compose.onNodeWithText("Retirado este periodo").assertIsDisplayed()
        compose.onNodeWithText("Gastos SAR 20.00").assertIsDisplayed()
        compose.onNodeWithText("Reembolsos SAR 5.00").assertIsDisplayed()
        compose.onAllNodesWithText("Restaurar").assertCountEquals(0)
    }

    @Test
    fun historical_period_uses_its_snapshots_and_exposes_only_read_only_details() {
        val zone = ZoneId.of("Asia/Riyadh")
        val firstPeriodLedger = RoomPocketLedger(
            database,
            Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone),
            zone,
        )
        runBlocking {
            firstPeriodLedger.execute(LedgerCommand.Initialize(100_000))
            val firstState = firstPeriodLedger.state.first { !it.needsOnboarding }
            val pocket = firstState.pockets.first { it.pocket.name == "Viajes" }.pocket
            val releasedPocket = firstState.pockets.first { it.pocket.name == "Ocio" }.pocket
            firstPeriodLedger.execute(
                LedgerCommand.UpsertPocket(
                    id = pocket.id,
                    name = pocket.name,
                    rolloverEnabled = true,
                    iconKey = pocket.iconKey,
                )
            )
            firstPeriodLedger.execute(
                LedgerCommand.UpsertPocket(
                    id = releasedPocket.id,
                    name = releasedPocket.name,
                    rolloverEnabled = true,
                    iconKey = releasedPocket.iconKey,
                )
            )
            firstPeriodLedger.execute(LedgerCommand.SetAllocation(firstState.currentPeriod!!.id, pocket.id, 10_000))
            firstPeriodLedger.execute(LedgerCommand.SetAllocation(firstState.currentPeriod.id, releasedPocket.id, 5_000))
            firstPeriodLedger.execute(LedgerCommand.CreateNextPeriod())
        }

        val middlePeriodLedger = RoomPocketLedger(
            database,
            Clock.fixed(Instant.parse("2026-03-26T09:00:00Z"), zone),
            zone,
        )
        runBlocking {
            val middleState = middlePeriodLedger.state.first { it.currentPeriod?.start == LocalDate.of(2026, 3, 25) }
            val pocket = middleState.pockets.first { it.pocket.name == "Viajes" }.pocket
            middlePeriodLedger.execute(
                LedgerCommand.AddMovement(
                    id = "historical-expense",
                    pocketId = pocket.id,
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = 2_000,
                    occurredAtUtcMillis = Instant.parse("2026-03-26T09:00:00Z").toEpochMilli(),
                    localDate = LocalDate.of(2026, 3, 26),
                )
            )
            middlePeriodLedger.execute(
                LedgerCommand.AddMovement(
                    id = "historical-refund",
                    pocketId = pocket.id,
                    type = MovementType.REFUND,
                    accountingAmountMinor = 500,
                    occurredAtUtcMillis = Instant.parse("2026-03-26T09:01:00Z").toEpochMilli(),
                    localDate = LocalDate.of(2026, 3, 26),
                )
            )
            val releasedPocket = middleState.pockets.first { it.pocket.name == "Ocio" }.pocket
            middlePeriodLedger.execute(LedgerCommand.ArchivePocket(releasedPocket.id))
            middlePeriodLedger.execute(LedgerCommand.CreateNextPeriod())
        }

        val currentLedger = RoomPocketLedger(
            database,
            Clock.fixed(Instant.parse("2026-04-26T09:00:00Z"), zone),
            zone,
        )
        runBlocking {
            val pocket = currentLedger.state.first { it.currentPeriod?.start == LocalDate.of(2026, 4, 25) }
                .pockets.first { it.pocket.name == "Viajes" }.pocket
            currentLedger.execute(LedgerCommand.ArchivePocket(pocket.id))
        }
        compose.setContent { PocketApp(currentLedger) }

        compose.waitUntilExactlyOneExists(hasText("Pockets"), 5_000)
        compose.onNodeWithText("Pockets").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("pockets_list"), 5_000)
        compose.onNodeWithText("25 mar – 24 abr").performClick()
        compose.onNodeWithText("Vista histórica · Solo lectura").assertIsDisplayed()
        compose.onNodeWithText("Moneda del periodo · SAR").assertIsDisplayed()
        compose.onNodeWithText("Asignado").assertIsDisplayed()
        compose.onNodeWithText("SAR 900.00 sin asignar").assertIsDisplayed()
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("pocket_Viajes"))
        compose.onNodeWithTag("pocket_Viajes").assertIsDisplayed()
        compose.onAllNodesWithText("Crear Pocket").assertCountEquals(0)
        compose.onAllNodesWithText("Restaurar").assertCountEquals(0)

        compose.onNodeWithTag("pocket_Viajes").performClick()
        compose.onNodeWithText("Detalle histórico").assertIsDisplayed()
        compose.onNodeWithText("Presupuesto: SAR 100.00").assertIsDisplayed()
        compose.onNodeWithText("Rollover recibido: SAR 100.00").assertIsDisplayed()
        compose.onNodeWithText("Gastos: SAR 20.00").assertIsDisplayed()
        compose.onNodeWithText("Reembolsos: SAR 5.00").assertIsDisplayed()
        compose.onNodeWithText("Rollover liberado: SAR 0.00").assertIsDisplayed()
        compose.onNodeWithText("Disponibilidad final: SAR 185.00").assertIsDisplayed()
        compose.onAllNodesWithText("Guardar presupuesto").assertCountEquals(0)
        compose.onAllNodesWithText("Editar Pocket").assertCountEquals(0)
        compose.onAllNodesWithText("Archivar Pocket").assertCountEquals(0)
        compose.onAllNodesWithText("Subir").assertCountEquals(0)
        compose.onAllNodesWithText("Bajar").assertCountEquals(0)
        compose.onAllNodesWithText("Aplicar rollover").assertCountEquals(0)
        compose.onNodeWithText("Cerrar").performClick()

        compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("retired_Ocio"))
        compose.onNodeWithTag("retired_Ocio").performClick()
        compose.onNodeWithText("Rollover liberado: SAR 50.00").assertIsDisplayed()
        compose.onNodeWithText("Disponibilidad final: SAR 0.00").assertIsDisplayed()
    }

    @Test
    fun period_owned_amounts_use_their_accounting_currency_without_exposing_currency_controls() {
        val zone = ZoneId.of("Asia/Riyadh")
        val firstLedger = RoomPocketLedger(
            database,
            Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone),
            zone,
        )
        runBlocking {
            firstLedger.execute(LedgerCommand.Initialize(100_000))
            val firstState = firstLedger.state.first { !it.needsOnboarding }
            val first = firstState.currentPeriod!!
            val pocket = firstState.pockets.first { it.pocket.name == "Viajes" }.pocket
            firstLedger.execute(LedgerCommand.SetAllocation(first.id, pocket.id, 10_000))
            firstLedger.execute(
                LedgerCommand.ScheduleCurrencyChange(
                    com.aif31.pocket.domain.SupportedCurrency.MXN,
                    "2",
                    first.endExclusive,
                    "TEST",
                )
            )
            firstLedger.execute(LedgerCommand.CreateNextPeriod())
            firstLedger.execute(
                LedgerCommand.AddMovement(
                    id = "mxn-movement",
                    pocketId = pocket.id,
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = 2_000,
                    occurredAtUtcMillis = Instant.parse("2026-03-26T09:00:00Z").toEpochMilli(),
                    localDate = LocalDate.of(2026, 3, 26),
                    originalCurrencyCode = "MXN",
                )
            )
        }
        val currentLedger = RoomPocketLedger(
            database,
            Clock.fixed(Instant.parse("2026-03-26T09:00:00Z"), zone),
            zone,
        )
        compose.setContent { PocketApp(currentLedger) }

        compose.waitUntilExactlyOneExists(hasText("MXN 180.00"), 5_000)
        compose.onNodeWithText("MXN 180.00").assertIsDisplayed()
        compose.onNodeWithTag("contextual_add").performClick()
        compose.waitUntilExactlyOneExists(hasText("Guardar gasto · MXN 0.00"), 5_000)
        compose.onNodeWithTag("movement_form").performScrollToNode(hasText("Más detalles"))
        compose.onNodeWithText("Más detalles").performClick()
        compose.onNodeWithTag("movement_form").performScrollToNode(hasText("Fecha (AAAA-MM-DD)"))
        compose.onNodeWithText("Fecha (AAAA-MM-DD)").performTextReplacement("2026-02-26")
        compose.waitUntilExactlyOneExists(hasText("Guardar gasto · SAR 0.00"), 5_000)
        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.waitUntilExactlyOneExists(hasText("Pockets"), 5_000)
        compose.onNodeWithText("Pockets").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("pockets_list"), 5_000)
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasText("Presupuesto MXN 200.00"))
        compose.onNodeWithText("Presupuesto MXN 200.00").assertIsDisplayed()
        compose.onAllNodesWithText("Cambiar moneda", substring = true).assertCountEquals(0)

        compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("period_selector"))
        compose.onNodeWithTag("period_selector").performScrollToNode(hasText("25 feb – 24 mar"))
        compose.onNodeWithText("25 feb – 24 mar").performClick()
        compose.onNodeWithText("Moneda del periodo · SAR").assertIsDisplayed()
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasText("Presupuesto SAR 100.00"))
        compose.onNodeWithText("Presupuesto SAR 100.00").assertIsDisplayed()

        compose.onNodeWithText("Movimientos").performClick()
        compose.waitUntilExactlyOneExists(hasText("- MXN 20.00"), 5_000)
        compose.onNodeWithText("- MXN 20.00").assertIsDisplayed()
    }

    @Test
    fun zero_allocation_is_an_empty_input_with_a_zero_placeholder() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking { ledger.execute(LedgerCommand.Initialize(100_000)) }
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasText("Pockets"), 5_000)
        compose.onNodeWithText("Pockets").performClick()
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("pocket_Viajes"))
        compose.onNodeWithTag("pocket_Viajes").performClick()
        compose.onNodeWithTag("allocation_amount").assertTextContains("0.00")
        compose.onNodeWithTag("allocation_amount").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
        )
        compose.onNodeWithTag("allocation_amount").performClick()
        compose.onNodeWithTag("allocation_amount").performTextInput("25.00")
        compose.onNodeWithTag("allocation_amount").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("25.00"))
        )
        compose.onNodeWithText("Guardar presupuesto").performClick()

        val savedBudget = runBlocking {
            ledger.state.first { state -> state.pockets.any { it.pocket.name == "Viajes" && it.budgetMinor == 2_500L } }
                .pockets.first { it.pocket.name == "Viajes" }.budgetMinor
        }
        assertEquals(2_500L, savedBudget)
    }

    @Test
    fun untouched_empty_allocation_saves_as_zero() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking { ledger.execute(LedgerCommand.Initialize(100_000)) }
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasText("Pockets"), 5_000)
        compose.onNodeWithText("Pockets").performClick()
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("pocket_Viajes"))
        compose.onNodeWithTag("pocket_Viajes").performClick()
        compose.onNodeWithTag("allocation_amount").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
        )
        compose.onNodeWithText("Guardar presupuesto").performClick()

        compose.waitUntilDoesNotExist(hasTestTag("allocation_amount"), 5_000)
        val savedBudget = runBlocking {
            ledger.state.first().pockets.first { it.pocket.name == "Viajes" }.budgetMinor
        }
        assertEquals(0L, savedBudget)
    }

    @Test
    fun first_focus_selects_an_existing_allocation_so_typing_replaces_it() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking {
            ledger.execute(LedgerCommand.Initialize(100_000))
            val state = ledger.state.first { !it.needsOnboarding }
            val pocket = state.pockets.first { it.pocket.name == "Viajes" }.pocket
            ledger.execute(LedgerCommand.SetAllocation(state.currentPeriod!!.id, pocket.id, 10_000))
        }
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasText("Pockets"), 5_000)
        compose.onNodeWithText("Pockets").performClick()
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("pocket_Viajes"))
        compose.onNodeWithTag("pocket_Viajes").performClick()
        compose.onNodeWithTag("allocation_amount").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("100.00"))
        )
        compose.onNodeWithTag("allocation_amount").assertIsFocused()
        compose.onNodeWithTag("allocation_amount").performTextInput("250.00")
        compose.onNodeWithTag("allocation_amount").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("250.00"))
        )
        compose.onNodeWithText("Guardar presupuesto").performClick()

        val savedBudget = runBlocking {
            ledger.state.first { state -> state.pockets.any { it.pocket.name == "Viajes" && it.budgetMinor == 25_000L } }
                .pockets.first { it.pocket.name == "Viajes" }.budgetMinor
        }
        assertEquals(25_000L, savedBudget)
    }

    @Test
    fun transition_period_identifies_its_daily_pace_comparison() {
        val zone = ZoneId.of("Asia/Riyadh")
        val firstPeriodLedger = RoomPocketLedger(
            database,
            Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone),
            zone,
        )
        runBlocking {
            firstPeriodLedger.execute(LedgerCommand.Initialize(20_000))
            val firstState = firstPeriodLedger.state.first { !it.needsOnboarding }
            firstPeriodLedger.execute(
                LedgerCommand.AddMovement(
                    id = "transition-ui-previous-spend",
                    pocketId = firstState.pockets.first().pocket.id,
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = 1_000,
                    occurredAtUtcMillis = Instant.parse("2026-02-26T09:00:00Z").toEpochMilli(),
                    localDate = LocalDate.of(2026, 2, 26),
                ),
            )
            firstPeriodLedger.execute(LedgerCommand.CreateNextPeriod(startDay = 10))
        }
        val transitionLedger = RoomPocketLedger(
            database,
            Clock.fixed(Instant.parse("2026-03-26T09:00:00Z"), zone),
            zone,
        )
        compose.setContent { PocketApp(transitionLedger) }

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Periodo de transición"))
        compose.onNodeWithText("Periodo de transición").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasContentDescription("Mostrar métricas del periodo"))
        compose.onNodeWithContentDescription("Mostrar métricas del periodo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mostrar métricas del periodo").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasText("Ocultar más información"), 5_000)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Ritmo diario del periodo anterior"))
        compose.onNodeWithText("Ritmo diario del periodo anterior").assertIsDisplayed()
        compose.onNodeWithText("Pockets").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("pockets_list"), 10_000)
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasText("Periodo de transición"))
        compose.onNodeWithText("Periodo de transición").assertIsDisplayed()
    }

    private class FakePreferences : PreferencesStore {
        private val values = MutableStateFlow(AppPreferences())
        override val state = values
        val current: AppPreferences get() = values.value
        override suspend fun setFuturePeriodStartDay(day: Int) { values.value = values.value.copy(futurePeriodStartDay = day) }
        override suspend fun setReminder(enabled: Boolean, time: LocalTime) { values.value = values.value.copy(reminderEnabled = enabled, reminderTime = time) }
    }

    private class FakeReminderScheduler : ReminderScheduler {
        var enabled: Boolean? = null
        var time: LocalTime? = null
        override fun apply(enabled: Boolean, time: LocalTime) { this.enabled = enabled; this.time = time }
    }
}
