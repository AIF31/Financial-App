package com.aif31.pocket

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
class AnalysisActionsHostFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: FinanceDatabase
    private val zone = ZoneId.of("Asia/Riyadh")

    @Before fun setUp() {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After fun tearDown() = database.close()

    private fun ledgerAt(instant: String) = RoomPocketLedger(database, Clock.fixed(Instant.parse(instant), zone), zone)

    private suspend fun RoomPocketLedger.spend(pocketName: String, amount: Long, date: LocalDate, merchant: String) {
        val pocket = state.first().pocketCatalog.single { it.name == pocketName }
        assertEquals(
            LedgerResult.Success,
            execute(
                LedgerCommand.AddMovement(
                    pocketId = pocket.id,
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = amount,
                    occurredAtUtcMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                    localDate = date,
                    merchant = merchant,
                ),
            ),
        )
    }

    @Test
    fun a_merchant_in_the_analysis_opens_its_movements_and_an_edit_returns_to_that_list() {
        val ledger = ledgerAt("2026-03-05T09:00:00Z")
        runBlocking {
            ledger.execute(LedgerCommand.Initialize(200_000))
            ledger.spend("Supermercado", 2_000, LocalDate.of(2026, 2, 26), "Tamimi")
            ledger.spend("Supermercado", 3_000, LocalDate.of(2026, 3, 1), "TAMIMI")
            ledger.spend("Supermercado", 9_000, LocalDate.of(2026, 3, 2), "Jarir")
        }
        compose.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)

        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("open_analysis"))
        compose.onNodeWithTag("open_analysis").performClick()
        compose.onNodeWithTag("analysis_list").performScrollToNode(hasText("TAMIMI"))
        compose.onNodeWithText("TAMIMI").performClick()

        compose.waitUntilExactlyOneExists(hasText("2 movimientos · Neto SAR 50.00"), 5_000)
        compose.onNodeWithText("Jarir").assertDoesNotExist()
        compose.onNodeWithText("Tamimi").performClick()
        compose.onNodeWithText("Editar").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("movement_save"), 5_000)
        compose.onNodeWithTag("movement_save").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("drill_summary"), 5_000)
    }

    @Test
    fun suggested_budgets_use_the_final_total_when_increases_precede_reductions() {
        val old = ledgerAt("2026-03-05T09:00:00Z")
        runBlocking {
            old.execute(LedgerCommand.Initialize(200_000))
            old.spend("Supermercado", 100, LocalDate.of(2026, 3, 1), "Tamimi")
            old.spend("Restaurantes/café", 100, LocalDate.of(2026, 3, 1), "Cafe")
        }
        val ledger = ledgerAt("2026-03-26T09:00:00Z")
        runBlocking { ledger.execute(LedgerCommand.CatchUpPeriods(preferredStartDay = 25)) }
        val before = runBlocking { ledger.state.first() }
        val period = before.currentPeriod!!.copy(newFundsMinor = Long.MAX_VALUE)
        val pockets = before.pockets.map { summary ->
            val budget = when (summary.pocket.name) {
                "Supermercado" -> 100L
                "Restaurantes/café" -> Long.MAX_VALUE - 100
                else -> 0L
            }
            summary.copy(budgetMinor = budget, availabilityMinor = budget)
        }
        val state = before.copy(
            periods = before.periods.map { if (it.id == period.id) period else it },
            currentPeriod = period,
            pockets = pockets,
            pocketSummariesByPeriod = before.pocketSummariesByPeriod + (period.id to pockets),
        )
        compose.setContent { PocketsScreen(state, ledger, PaddingValues()) }
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("review_suggested_budgets"))
        compose.onNodeWithTag("review_suggested_budgets").performClick()
        compose.onNodeWithTag("apply_suggested_budgets").assertIsEnabled()
        compose.onNodeWithText("Total asignado: SAR 4.00 de SAR 92,233,720,368,547,758.07").assertExists()
    }

    @Test
    fun a_new_period_offers_a_recap_and_applies_suggested_budgets_in_one_step() {
        // February (25 Feb – 24 Mar, 28 days): Supermercado spends SAR 280.00. March has 31 days.
        val february = ledgerAt("2026-03-05T09:00:00Z")
        runBlocking {
            february.execute(LedgerCommand.Initialize(200_000))
            february.spend("Supermercado", 28_000, LocalDate.of(2026, 3, 1), "Tamimi")
        }
        val march = ledgerAt("2026-03-26T09:00:00Z")
        runBlocking { march.execute(LedgerCommand.CatchUpPeriods(preferredStartDay = 25)) }
        compose.setContent { PocketApp(march) }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)

        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("period_recap"))
        compose.onNodeWithText("Donde más gastaste: Tamimi, SAR 280.00").assertExists()
        compose.onNodeWithTag("dismiss_recap").performClick()
        compose.onNodeWithTag("period_recap").assertDoesNotExist()

        compose.onNode(hasText("Pockets") and hasClickAction()).performClick()
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("review_suggested_budgets"))
        compose.onNodeWithTag("review_suggested_budgets").performClick()
        compose.waitUntilExactlyOneExists(hasText("SAR 310.00"), 5_000)
        compose.onNodeWithTag("apply_suggested_budgets").performClick()
        compose.waitUntilDoesNotExist(hasTestTag("apply_suggested_budgets"), 5_000)

        assertEquals(31_000L, runBlocking { march.state.first().pockets.single { it.pocket.name == "Supermercado" }.budgetMinor })
    }
}
