package com.aif31.pocket

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.RoomPocketLedger
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@MediumTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(ExperimentalTestApi::class)
class BudgetReassignmentHostFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: FinanceDatabase
    private lateinit var ledger: RoomPocketLedger
    private lateinit var targetId: String
    private lateinit var donorId: String
    private val zone = ZoneId.of("Asia/Riyadh")

    @Before fun setUp() = runBlocking {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-03-05T09:00:00Z"), zone), zone)
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.Initialize(100_000)))
        val state = ledger.state.first()
        targetId = state.pockets.single { it.pocket.name == "Supermercado" }.pocket.id
        donorId = state.pockets.single { it.pocket.name == "Restaurantes/café" }.pocket.id
        val budgets = state.pockets.associate { it.pocket.id to when (it.pocket.id) { targetId -> 10_000L; donorId -> 90_000L; else -> 0L } }
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.SetAllocations(state.currentPeriod!!.id, budgets)))
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.AddMovement(
            pocketId = targetId, type = MovementType.EXPENSE, accountingAmountMinor = 12_500,
            occurredAtUtcMillis = Instant.parse("2026-03-04T09:00:00Z").toEpochMilli(),
            localDate = state.currentLocalDate.minusDays(1),
        )))
    }

    @After fun tearDown() = database.close()

    @Test fun an_overspent_pocket_can_be_covered_from_its_management_dialog() {
        assertEquals(-2_500L, runBlocking { ledger.state.first().pockets.single { it.pocket.id == targetId }.availabilityMinor })
        compose.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)
        compose.onNode(hasText("Pockets") and hasClickAction()).performClick()
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasContentDescription("Gestionar Supermercado"))
        compose.onNodeWithContentDescription("Gestionar Supermercado").performClick()
        compose.onNodeWithTag("cover_pocket").performClick()
        compose.onNodeWithText("Restaurantes", substring = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.onNodeWithTag("confirm_budget_reassignment").performClick()
        compose.waitUntilDoesNotExist(hasTestTag("confirm_budget_reassignment"), 5_000)

        val state = runBlocking { ledger.state.first() }
        assertEquals(0L, state.pockets.single { it.pocket.id == targetId }.availabilityMinor)
        assertEquals(87_500L, state.pockets.single { it.pocket.id == donorId }.budgetMinor)
        assertTrue(state.pockets.sumOf { it.budgetMinor } <= state.currentPeriod!!.newFundsMinor)
    }

    @Test fun invalid_amount_is_announced_on_the_field_and_clears_when_edited() {
        val state = runBlocking { ledger.state.first() }
        compose.setContent {
            BudgetReassignmentDialog(state, state.currentPeriod!!.id, targetId, ledger, state.currentPeriod.accountingCurrency, {}, {})
        }
        compose.onNodeWithTag("reassignment_amount").performTextReplacement("abc")
        compose.onNodeWithTag("confirm_budget_reassignment").performClick()
        compose.onNodeWithTag("reassignment_amount")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        compose.onNodeWithText("Escribe un importe válido").assertExists()

        compose.onNodeWithTag("reassignment_amount").performTextReplacement("25")
        compose.onNodeWithTag("reassignment_amount")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }
}
