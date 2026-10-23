package com.aif31.pocket

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.RoomPocketLedger
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(ExperimentalTestApi::class)
class RecurringChargesHostFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: FinanceDatabase
    private val zone = ZoneId.of("Asia/Riyadh")

    @Before fun setUp() {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After fun tearDown() = database.close()

    private fun ledgerAt(instant: String) = RoomPocketLedger(database, Clock.fixed(Instant.parse(instant), zone), zone)

    private fun seededLedger(today: String = "2026-03-16T09:00:00Z"): RoomPocketLedger = runBlocking {
        val old = ledgerAt("2025-12-15T09:00:00Z")
        assertEquals(LedgerResult.Success, old.execute(LedgerCommand.Initialize(200_000)))
        val ledger = ledgerAt(today)
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.CatchUpPeriods(preferredStartDay = 25)))
        val pocket = ledger.state.first().pocketCatalog.first { it.name == "Ocio" }
        listOf("2025-12-15", "2026-01-14", "2026-02-13").forEach { day ->
            val date = LocalDate.parse(day)
            assertEquals(
                LedgerResult.Success,
                ledger.execute(LedgerCommand.AddMovement(
                    pocketId = pocket.id, type = MovementType.EXPENSE, accountingAmountMinor = 1_099,
                    occurredAtUtcMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(), localDate = date,
                    merchant = "Stream",
                )),
            )
        }
        ledger
    }

    @Test
    fun dashboard_pending_total_opens_analysis_and_a_recurring_charge_opens_its_past_charges() {
        val ledger = seededLedger()
        val before = runBlocking { ledger.state.first() }
        compose.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Ver detalles"))
        compose.onNodeWithContentDescription("Mostrar métricas del periodo").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Ocultar detalles").assertExists()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Cargos recurrentes pendientes: SAR 10.99 (1)"))
        compose.onNodeWithText("Cargos recurrentes pendientes: SAR 10.99 (1)").performSemanticsAction(SemanticsActions.OnClick)

        compose.waitUntilExactlyOneExists(hasTestTag("analysis_list"), 5_000)
        compose.onNodeWithTag("analysis_list").performScrollToNode(hasText("Cargos recurrentes"))
        compose.onNodeWithText("Faltan por cobrarse: SAR\u00A010.99").assertExists()
        compose.onNodeWithText("Esperado el 15 mar · SAR\u00A010.99").assertExists()
        compose.onNode(hasText("Stream") and hasText("Ocio")).assertExists()
        compose.onNodeWithText("Vencido").assertExists()
        compose.onNodeWithText("Stream").performSemanticsAction(SemanticsActions.OnClick)

        compose.waitUntilExactlyOneExists(hasText("3 movimientos · Neto SAR 32.97"), 5_000)
        assertEquals(before.movements, runBlocking { ledger.state.first().movements })
        assertEquals(before.movementSuggestions, runBlocking { ledger.state.first().movementSuggestions })
    }

    @Test
    fun historical_analysis_does_not_show_upcoming_charges() {
        val state = runBlocking { seededLedger().state.first() }
        var periodId by mutableStateOf(state.currentPeriod!!.id)
        compose.setContent { AnalysisScreen(state, periodId, onPeriodChange = { periodId = it }, onBack = {}) }
        compose.onNodeWithTag("analysis_list").performScrollToNode(hasText("Cargos recurrentes"))
        compose.onNodeWithText("Faltan por cobrarse: SAR\u00A010.99").assertExists()
        compose.runOnIdle { periodId = state.periods.first { it.id != state.currentPeriod.id }.id }
        compose.onNodeWithText("Cargos recurrentes").assertDoesNotExist()
    }
}
