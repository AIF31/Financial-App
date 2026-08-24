package com.aif31.pocket

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
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
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.RoomPocketLedger
import com.aif31.pocket.settings.AppPreferences
import com.aif31.pocket.settings.PreferencesStore
import com.aif31.pocket.settings.ReminderScheduler
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
@OptIn(ExperimentalTestApi::class)
class PocketAppFlowTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var database: FinanceDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = FinanceDatabase.inMemory(context)
        compose.enableAccessibilityChecks()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun onboarding_allocation_expense_dashboard_and_history_are_consistent() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone),
            zoneId = zone,
        )

        val preferences = FakePreferences()
        compose.setContent { PocketApp(ledger = ledger, preferences = preferences) }

        compose.waitUntilExactlyOneExists(hasText("Configura tu primer periodo"), 5_000)
        compose.onNodeWithTag("new_funds").performTextInput("1000.00")
        compose.onNodeWithTag("start_day").performTextReplacement("10")
        compose.onNodeWithText("Comenzar").performClick()

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
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Más información"))
        compose.onNodeWithText("Más información").performClick()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Gasto diario promedio"))
        compose.onNodeWithText("Gasto diario promedio").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("SAR 200.00 disponibles"))
        compose.onNodeWithText("SAR 200.00 disponibles").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("rollover_Supermercado"))
        compose.onNodeWithTag("rollover_Supermercado").assertIsDisplayed()
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithText("- SAR 100.00").assertIsDisplayed()
        compose.onNodeWithText("Supermercado").assertIsDisplayed()
    }

    @Test
    fun daily_reminder_can_be_enabled_and_disabled_through_settings() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking { ledger.execute(LedgerCommand.Initialize(100_000)) }
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
        compose.waitUntilExactlyOneExists(hasText("Mercado"), 5_000)
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
        compose.waitUntilExactlyOneExists(hasText("Mercado"), 5_000)
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
        compose.waitUntilExactlyOneExists(hasText("Mercado"), 5_000)
        compose.onAllNodesWithText("Hotel").assertCountEquals(0)
        compose.onAllNodesWithText("Taxi").assertCountEquals(0)
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
    fun launcher_shortcut_intent_opens_the_expense_form_and_saves_a_movement() {
        val application = ApplicationProvider.getApplicationContext<PocketApplication>()
        runBlocking {
            application.database.clearAllTables()
            application.ledger.execute(LedgerCommand.Initialize(100_000))
            application.ledger.state.first { !it.needsOnboarding }
        }
        val intent = Intent(application, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_EXPENSE)

        try {
            ActivityScenario.launch<MainActivity>(intent).use {
                compose.waitUntilExactlyOneExists(hasText("Nuevo gasto"), 5_000)
                compose.onNodeWithTag("movement_amount").performTextInput("12.34")
                compose.onNodeWithTag("movement_pocket_Supermercado").performClick()
                compose.onNodeWithText("Guardar gasto", substring = true).performClick()
                compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
                val saved = runBlocking {
                    application.ledger.state.first { it.movements.size == 1 }.movements.single()
                }
                assertEquals(1_234L, saved.sarAmountMinor)
                assertEquals("Supermercado", saved.pocketName)
            }
        } finally {
            application.database.clearAllTables()
        }
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
