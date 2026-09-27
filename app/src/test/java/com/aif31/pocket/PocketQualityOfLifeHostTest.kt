package com.aif31.pocket

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.RoomPocketLedger
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
class PocketQualityOfLifeHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var database: FinanceDatabase
    private val zone = ZoneId.of("Asia/Riyadh")

    @Before fun setUp() {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    /** Previous period (25 Jan – 24 Feb) with SAR 31.00 spent; current period starts 25 Feb. */
    private fun ledgerWithPreviousPeriod(): RoomPocketLedger {
        val previous = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-10T09:00:00Z"), zone), zone)
        runBlocking {
            previous.execute(LedgerCommand.Initialize(20_000))
            val state = previous.state.first { !it.needsOnboarding }
            previous.execute(
                LedgerCommand.AddMovement(
                    id = "previous-spend",
                    pocketId = state.pockets.first { it.pocket.name == "Supermercado" }.pocket.id,
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = 3_100,
                    occurredAtUtcMillis = Instant.parse("2026-02-10T09:00:00Z").toEpochMilli(),
                    localDate = LocalDate.of(2026, 2, 10),
                    merchant = "Mercado anterior",
                ),
            )
            previous.execute(LedgerCommand.CreateNextPeriod())
        }
        return RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
    }

    @Test
    fun hero_details_show_spend_pace_and_open_the_period_comparison() {
        val ledger = ledgerWithPreviousPeriod()
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasContentDescription("Mostrar métricas del periodo"), 10_000)
        compose.onNodeWithContentDescription("Mostrar métricas del periodo").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Puedes gastar al día"))
        compose.onNodeWithText("Puedes gastar al día").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Promedio diario del periodo anterior"))
        compose.onNodeWithText("SAR 1.00").assertIsDisplayed()

        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Comparar periodos"))
        // Semantic click: at this test's screen size the button can sit under the extended FAB.
        compose.onNodeWithText("Comparar periodos").performSemanticsAction(SemanticsActions.OnClick)

        compose.waitUntilExactlyOneExists(hasTestTag("comparison_list"), 5_000)
        compose.onNodeWithTag("comparison_baseline").assertTextContains("25 ene – 24 feb", substring = true)
        compose.onNodeWithTag("comparison_list").performScrollToNode(hasText("Resumen"))
        compose.onNodeWithTag("comparison_list").performScrollToNode(hasText("Gasto diario promedio por Pocket"))
        compose.onNodeWithContentDescription("Atrás").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)
    }

    @Test
    fun pocket_quick_add_preselects_that_pocket() {
        val ledger = ledgerWithPreviousPeriod()
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasContentDescription("Registrar gasto en Supermercado"))
        compose.onNodeWithContentDescription("Registrar gasto en Supermercado").performClick()

        compose.waitUntilExactlyOneExists(hasTestTag("movement_form"), 5_000)
        compose.onNodeWithTag("movement_form").performScrollToNode(hasTestTag("movement_pocket_Supermercado"))
        compose.onNodeWithTag("movement_pocket_Supermercado").assertIsSelected()
    }

    @Test
    fun back_from_another_root_returns_to_inicio_and_tab_state_survives() {
        val ledger = ledgerWithPreviousPeriod()
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithTag("history_search").performTextInput("fruta")
        compose.onNodeWithText("Inicio").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithTag("history_search").assertTextContains("fruta")

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)
    }

    @Test
    fun swiping_a_movement_deletes_it_with_undo() {
        val ledger = ledgerWithPreviousPeriod()
        runBlocking {
            val state = ledger.state.first { it.currentPeriod?.start == LocalDate.of(2026, 2, 25) }
            ledger.execute(
                LedgerCommand.AddMovement(
                    id = "swipe", pocketId = state.pockets.first().pocket.id, type = MovementType.EXPENSE,
                    accountingAmountMinor = 1_234, occurredAtUtcMillis = Instant.parse("2026-02-26T08:00:00Z").toEpochMilli(),
                    localDate = LocalDate.of(2026, 2, 26), merchant = "Deslizable",
                ),
            )
        }
        compose.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasText("Movimientos"), 10_000)
        compose.onNodeWithText("Movimientos").performClick()
        compose.waitUntilExactlyOneExists(hasText("Deslizable"), 5_000)

        // Start mid-card: on the small Robolectric screen the FAB overlaps the row's end edge.
        compose.onNodeWithText("Deslizable").performTouchInput { swipeLeft(startX = centerX, endX = left) }

        compose.waitUntilExactlyOneExists(hasText("Deshacer"), 5_000)
        compose.onAllNodesWithText("Deslizable").fetchSemanticsNodes().let { assertEquals(0, it.size) }
        compose.onNodeWithText("Deshacer").performClick()
        compose.waitUntilExactlyOneExists(hasText("Deslizable"), 5_000)
    }

    @Test
    fun selecting_a_previous_period_shows_its_spending_metrics() {
        val ledger = ledgerWithPreviousPeriod()
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithText("Pockets").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("pockets_list"), 5_000)
        val previousId = runBlocking { ledger.state.first().periods.minBy { it.start }.id }
        compose.onNodeWithTag("period_$previousId").performClick()

        compose.waitUntilExactlyOneExists(hasText("Vista histórica · Solo lectura"), 5_000)
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasText("Mayor gasto"))
        compose.onNodeWithText("Mercado anterior").assertIsDisplayed()
        // Net spend and the largest expense are both SAR 31.00 in this fixture.
        assertEquals(2, compose.onAllNodesWithText("SAR 31.00").fetchSemanticsNodes().size)
        compose.onNodeWithText("SAR 1.00").assertIsDisplayed()
        compose.waitUntilDoesNotExist(hasText("Puedes gastar al día"), 1_000)
    }

    @Test
    fun a_handled_new_expense_launch_does_not_reopen_after_state_restoration_but_a_new_one_does() {
        val ledger = ledgerWithPreviousPeriod()
        var request by mutableIntStateOf(1)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PocketApp(ledger, newExpenseRequest = request) }

        compose.waitUntilExactlyOneExists(hasTestTag("movement_form"), 10_000)
        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)

        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)
        assertEquals(0, compose.onAllNodes(hasTestTag("movement_form")).fetchSemanticsNodes().size)

        request = 2
        compose.waitUntilExactlyOneExists(hasTestTag("movement_form"), 5_000)
    }

    @Test
    fun earliest_period_explains_that_there_is_nothing_before_it_to_compare() {
        val ledger = ledgerWithPreviousPeriod()
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithText("Pockets").performClick()
        val earliestId = runBlocking { ledger.state.first().periods.minBy { it.start }.id }
        compose.onNodeWithTag("period_$earliestId").performClick()

        compose.onNodeWithTag("pockets_list").performScrollToNode(hasText("No hay un periodo anterior para comparar."))
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasText("Comparar periodos"))
    }
}
