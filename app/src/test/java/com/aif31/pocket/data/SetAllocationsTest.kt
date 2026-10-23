package com.aif31.pocket.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SetAllocationsTest {
    private lateinit var database: FinanceDatabase
    private lateinit var ledger: RoomPocketLedger
    private val zone = ZoneId.of("Asia/Riyadh")

    @Before fun setUp() = runTest {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.Initialize(100_000)))
    }

    @After fun tearDown() = database.close()

    private suspend fun budgets(): Map<String, Long> = ledger.state.first().pockets.associate { it.pocket.id to it.budgetMinor }

    private suspend fun setUpBudgets(first: Long, second: Long): Pair<String, String> {
        val state = ledger.state.first()
        val (a, b) = state.pockets.take(2).map { it.pocket.id }
        val periodId = state.currentPeriod!!.id
        state.pockets.drop(2).forEach { assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.SetAllocation(periodId, it.pocket.id, 0))) }
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.SetAllocation(periodId, a, 0)))
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.SetAllocation(periodId, b, 0)))
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.SetAllocation(periodId, a, first)))
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.SetAllocation(periodId, b, second)))
        return a to b
    }

    @Test
    fun covering_an_overspent_pocket_keeps_the_fully_assigned_total_and_applies_both_budgets() = runTest {
        val (target, donor) = setUpBudgets(10_000, 90_000)
        val before = ledger.state.first()
        val periodId = before.currentPeriod!!.id
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.AddMovement(
            pocketId = target, type = MovementType.EXPENSE, accountingAmountMinor = 12_500,
            occurredAtUtcMillis = Instant.parse("2026-02-26T08:00:00Z").toEpochMilli(), localDate = before.currentLocalDate,
        )))
        val overspent = ledger.state.first()
        assertEquals(-2_500L, overspent.pockets.single { it.pocket.id == target }.availabilityMinor)
        val needed = BudgetReassignment.of(overspent, periodId, target)!!.neededMinor!!

        val command = BudgetReassignment.command(overspent, periodId, target, donor, needed)
        assertEquals(LedgerResult.Success, ledger.execute(command))

        val covered = ledger.state.first()
        assertEquals(12_500L, covered.pockets.single { it.pocket.id == target }.budgetMinor)
        assertEquals(87_500L, covered.pockets.single { it.pocket.id == donor }.budgetMinor)
        assertEquals(0L, covered.pockets.single { it.pocket.id == target }.availabilityMinor)
        assertEquals(100_000L, covered.pockets.sumOf { it.budgetMinor })
        assertTrue(covered.pockets.sumOf { it.budgetMinor } <= covered.currentPeriod!!.newFundsMinor)
        assertEquals(0L, covered.unallocatedMinor)
    }

    @Test
    fun budgets_change_together_even_when_one_at_a_time_would_exceed_new_funds() = runTest {
        val (a, b) = setUpBudgets(60_000, 30_000)
        val periodId = ledger.state.first().currentPeriod!!.id

        // Raising b first would total 130,000 against 100,000 of new funds.
        val result = ledger.execute(LedgerCommand.SetAllocations(periodId, mapOf(b to 70_000L, a to 20_000L)))

        assertEquals(LedgerResult.Success, result)
        assertEquals(20_000L, budgets().getValue(a))
        assertEquals(70_000L, budgets().getValue(b))
    }

    @Test
    fun a_batch_over_new_funds_changes_nothing() = runTest {
        val (a, b) = setUpBudgets(60_000, 30_000)
        val periodId = ledger.state.first().currentPeriod!!.id

        val result = ledger.execute(LedgerCommand.SetAllocations(periodId, mapOf(a to 60_000L, b to 50_000L)))

        assertTrue(result is LedgerResult.Rejected)
        assertEquals(60_000L, budgets().getValue(a))
        assertEquals(30_000L, budgets().getValue(b))
    }

    @Test
    fun a_batch_with_an_archived_pocket_changes_nothing() = runTest {
        val (a, b) = setUpBudgets(60_000, 0)
        val periodId = ledger.state.first().currentPeriod!!.id
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.ArchivePocket(b)))

        val result = ledger.execute(LedgerCommand.SetAllocations(periodId, mapOf(a to 10_000L, b to 10_000L)))

        assertTrue(result is LedgerResult.Rejected)
        assertEquals(60_000L, budgets().getValue(a))
    }
}
