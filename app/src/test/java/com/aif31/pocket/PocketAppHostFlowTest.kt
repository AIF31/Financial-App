package com.aif31.pocket

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
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
        assertEquals(1_234L, saved.sarAmountMinor)
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
                sarAmountMinor = 1_000, occurredAtUtcMillis = clock.millis(), localDate = java.time.LocalDate.of(2026, 2, 26),
                merchant = "Mercado", note = "fruta fresca", paymentMethodId = cash.id))
            ledger.execute(LedgerCommand.AddMovement(id = "hotel", pocketId = travel.pocket.id, type = MovementType.EXPENSE,
                sarAmountMinor = 2_000, occurredAtUtcMillis = clock.millis() + 1, localDate = java.time.LocalDate.of(2026, 2, 26),
                merchant = "Hotel", paymentMethodId = card.id, originalAmountMinor = 500, originalCurrencyCode = "USD"))
            ledger.execute(LedgerCommand.CreateNextPeriod())
            val nextPeriod = ledger.state.first { it.periods.size == 2 }.periods.last()
            ledger.execute(LedgerCommand.AddMovement(id = "taxi", pocketId = travel.pocket.id, type = MovementType.EXPENSE,
                sarAmountMinor = 3_000, occurredAtUtcMillis = clock.millis() + 2, localDate = nextPeriod.start,
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
                    sarAmountMinor = 1_000,
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
                sarAmountMinor = 1_000, occurredAtUtcMillis = clock.millis(), localDate = java.time.LocalDate.of(2026, 2, 26)))
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
        assertEquals(1_234L, saved.sarAmountMinor)
        assertEquals("Supermercado", saved.pocketName)
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
