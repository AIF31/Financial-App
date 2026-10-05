package com.aif31.pocket

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.PeriodInsights
import com.aif31.pocket.data.RoomPocketLedger
import com.aif31.pocket.domain.FrozenRate
import com.aif31.pocket.domain.SupportedCurrency
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Drives the ledger with seeded random command sequences and checks, after every command, the accounting rules from
 * CONTEXT.md against values recomputed from the ledger's own Movements and periods.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LedgerInvariantsHostTest {
    private lateinit var database: FinanceDatabase
    private val zone = ZoneId.of("Asia/Riyadh")

    @Before
    fun setUp() {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun random_command_sequences_keep_every_period_balanced() {
        for (seed in 1..6) {
            database.close()
            database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
            runSequence(seed, steps = 120)
        }
    }

    @Test
    fun archiving_a_pocket_moves_its_whole_budget_and_rollover_to_unassigned_funds() = runBlocking {
        val clock = MutableClock(Instant.parse("2026-02-26T09:00:00Z"))
        val ledger = RoomPocketLedger(database, clock, zone)
        ledger.execute(LedgerCommand.Initialize(30_000))
        val first = ledger.state.first()
        val pocket = first.pockets.first { it.pocket.name == "Viajes" }.pocket
        ledger.execute(LedgerCommand.UpsertPocket(pocket.id, pocket.name, rolloverEnabled = true))
        ledger.execute(LedgerCommand.SetAllocation(first.currentPeriod!!.id, pocket.id, 10_000))
        clock.current = Instant.parse("2026-03-26T09:00:00Z")
        ledger.execute(LedgerCommand.CatchUpPeriods(25))
        val current = ledger.state.first { it.currentPeriod?.id != first.currentPeriod.id }.currentPeriod!!
        ledger.execute(LedgerCommand.SetAllocation(current.id, pocket.id, 2_000))
        // Spends past its own budget but within budget plus the 10,000 it received as rollover.
        ledger.execute(LedgerCommand.AddMovement(pocketId = pocket.id, type = MovementType.EXPENSE, accountingAmountMinor = 5_000, occurredAtUtcMillis = clock.millis(), localDate = LocalDate.of(2026, 3, 26)))
        val before = ledger.state.first()

        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.ArchivePocket(pocket.id)))

        val after = ledger.state.first()
        val retired = after.pockets.single { it.pocket.id == pocket.id }
        assertEquals(10_000L, retired.rolloverReleasedMinor)
        assertEquals(-5_000L, retired.availabilityMinor)
        // Archiving reclassifies availability; it neither creates nor destroys funds.
        assertEquals(before.unallocatedMinor + 12_000, after.unallocatedMinor)
        assertEquals(spendable(before), spendable(after))
    }

    private fun spendable(state: LedgerState) = state.unallocatedMinor + state.pockets.sumOf { it.availabilityMinor }

    private fun runSequence(seed: Int, steps: Int) = runBlocking {
        val random = Random(seed)
        val clock = MutableClock(Instant.parse("2026-02-26T09:00:00Z"))
        val ledger = RoomPocketLedger(database, clock, zone)
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.Initialize(random.nextLong(50_000, 500_000))))
        var startDay = 25
        var movementSerial = 0
        repeat(steps) { step ->
            val state = ledger.state.first()
            val current = requireNotNull(state.currentPeriod)
            val activePockets = state.pockets.filter { !it.retiredThisPeriod && !it.pocket.archived }
            val command: LedgerCommand = when (random.nextInt(100)) {
                in 0..29 -> {
                    val period = state.periods.random(random)
                    val pocket = state.pocketSummariesByPeriod.getValue(period.id).filterNot { it.retiredThisPeriod }
                        .randomOrNull(random) ?: return@repeat
                    val date = period.start.plusDays(random.nextLong((period.endExclusive.toEpochDay() - period.start.toEpochDay())))
                    LedgerCommand.AddMovement(
                        id = "m${movementSerial++}",
                        pocketId = pocket.pocket.id,
                        type = if (random.nextInt(6) == 0) MovementType.REFUND else MovementType.EXPENSE,
                        accountingAmountMinor = random.nextLong(1, 40_000),
                        occurredAtUtcMillis = clock.millis() + step,
                        localDate = date,
                    )
                }
                in 30..37 -> {
                    val movement = state.movements.randomOrNull(random) ?: return@repeat
                    val period = state.periods.random(random)
                    val pocket = state.pocketSummariesByPeriod.getValue(period.id).randomOrNull(random) ?: return@repeat
                    LedgerCommand.AddMovement(
                        id = movement.id,
                        pocketId = pocket.pocket.id,
                        type = movement.type,
                        accountingAmountMinor = random.nextLong(1, 40_000),
                        occurredAtUtcMillis = movement.occurredAtUtcMillis,
                        localDate = period.start,
                    )
                }
                in 38..43 -> {
                    val movement = state.movements.randomOrNull(random) ?: return@repeat
                    val deleted = ledger.execute(LedgerCommand.DeleteMovement(movement.id))
                    checkInvariants(ledger.state.first(), "seed $seed step $step delete")
                    if (deleted is LedgerResult.Deleted && random.nextBoolean()) {
                        LedgerCommand.RestoreMovement(deleted.movement)
                    } else {
                        return@repeat
                    }
                }
                in 44..61 -> {
                    val period = state.periods.random(random)
                    val pocket = state.pocketSummariesByPeriod.getValue(period.id).randomOrNull(random) ?: return@repeat
                    LedgerCommand.SetAllocation(period.id, pocket.pocket.id, random.nextLong(0, period.newFundsMinor / 3 + 2))
                }
                in 62..66 -> {
                    val period = state.periods.random(random)
                    LedgerCommand.UpdatePeriodFunds(period.id, random.nextLong(0, 600_000))
                }
                in 67..74 -> {
                    val pocket = state.pocketCatalog.filterNot { it.archived }.randomOrNull(random) ?: return@repeat
                    LedgerCommand.UpsertPocket(pocket.id, pocket.name, rolloverEnabled = !pocket.rolloverEnabled)
                }
                in 75..77 -> LedgerCommand.UpsertPocket(name = "Extra ${random.nextInt(1_000)}", rolloverEnabled = random.nextBoolean())
                in 78..81 -> {
                    val pocket = activePockets.randomOrNull(random) ?: return@repeat
                    val result = ledger.execute(LedgerCommand.ArchivePocket(pocket.pocket.id))
                    val archived = ledger.state.first()
                    if (result == LedgerResult.Success) {
                        assertEquals("seed $seed step $step archive conserves funds", spendable(state), spendable(archived))
                    }
                    checkInvariants(archived, "seed $seed step $step archive")
                    return@repeat
                }
                in 82..84 -> {
                    val pocket = state.pocketCatalog.filter { it.archived }.randomOrNull(random) ?: return@repeat
                    LedgerCommand.ArchivePocket(pocket.id, archived = false)
                }
                in 85..86 -> {
                    val latest = state.periods.maxBy { it.start }
                    val target = SupportedCurrency.entries.filter { it != latest.accountingCurrency }.random(random)
                    LedgerCommand.ScheduleCurrencyChange(
                        targetCurrency = target,
                        rate = listOf("3.75", "0.2666", "18.4", "0.054", "4.9")[random.nextInt(5)],
                        effectiveDate = latest.endExclusive,
                        source = "Prueba",
                    )
                }
                in 87..88 -> LedgerCommand.CreateNextPeriod()
                else -> {
                    if (random.nextInt(8) == 0) startDay = listOf(1, 10, 25, 31)[random.nextInt(4)]
                    clock.current = clock.current.plusSeconds(86_400L * random.nextInt(1, 20))
                    LedgerCommand.CatchUpPeriods(startDay)
                }
            }
            val result = ledger.execute(command)
            val after = ledger.state.first()
            if (result is LedgerResult.Rejected && command !is LedgerCommand.CatchUpPeriods) {
                assertEquals("seed $seed step $step rejected ${command::class.simpleName} changed state", state, after)
            }
            checkInvariants(after, "seed $seed step $step ${command::class.simpleName}")
        }
        val finalState = ledger.state.first()
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.CatchUpPeriods(startDay)))
        assertEquals("seed $seed catch-up is idempotent", finalState, ledger.state.first())
        val backup = ledger.exportBackup()
        assertEquals(LedgerResult.Success, ledger.restoreBackup(backup))
        val restored = ledger.state.first()
        assertEquals("seed $seed backup restores summaries", finalState.pocketSummariesByPeriod.mapValues { it.value.toSet() }, restored.pocketSummariesByPeriod.mapValues { it.value.toSet() })
        assertEquals("seed $seed backup restores movements", finalState.movements.toSet(), restored.movements.toSet())
        assertEquals("seed $seed backup restores unassigned funds", finalState.unallocatedMinorByPeriod, restored.unallocatedMinorByPeriod)
    }

    private fun checkInvariants(state: LedgerState, context: String) {
        val ordered = state.periods.sortedBy { it.start }
        state.movements.forEach { movement ->
            assertTrue(
                "$context: ${movement.id} is not counted in its period",
                state.pocketSummariesByPeriod[movement.periodId].orEmpty().any { it.pocket.id == movement.pocketId },
            )
        }
        ordered.forEach { period ->
            val summaries = state.pocketSummariesByPeriod.getValue(period.id)
            summaries.forEach { summary ->
                val movements = state.movements.filter { it.periodId == period.id && it.pocketId == summary.pocket.id }
                val expenses = movements.filter { it.type == MovementType.EXPENSE }.sumOf { it.accountingAmountMinor }
                val refunds = movements.filter { it.type == MovementType.REFUND }.sumOf { it.accountingAmountMinor }
                assertEquals("$context: expenses of ${summary.pocket.name}", expenses, summary.expenseMinor)
                assertEquals("$context: refunds of ${summary.pocket.name}", refunds, summary.refundMinor)
                assertEquals("$context: net spend of ${summary.pocket.name}", expenses - refunds, summary.netSpendMinor)
                assertEquals(
                    "$context: availability of ${summary.pocket.name}",
                    summary.budgetMinor + summary.rolloverMinor - summary.netSpendMinor,
                    summary.availabilityMinor,
                )
                if (summary.retiredThisPeriod) {
                    assertEquals("$context: retired budget", 0L, summary.budgetMinor)
                    assertEquals("$context: retired rollover", 0L, summary.rolloverMinor)
                }
            }
            val budgeted = summaries.sumOf { it.budgetMinor }
            assertTrue("$context: budgets $budgeted exceed new funds ${period.newFundsMinor}", budgeted <= period.newFundsMinor)
            assertEquals(
                "$context: unassigned funds",
                period.newFundsMinor - budgeted + summaries.sumOf { it.rolloverReleasedMinor },
                state.unallocatedMinorByPeriod.getValue(period.id),
            )
            // A period that has not started has no daily curve yet.
            PeriodInsights.of(state, period.id)?.takeIf { it.elapsedDays > 0 }?.let { insights ->
                assertEquals("$context: insights net spend", summaries.sumOf { it.netSpendMinor }, insights.netSpendMinor)
                assertEquals(
                    "$context: daily curve plus later-dated spend is the period's net spend",
                    insights.netSpendMinor,
                    (insights.cumulativeNetSpendByDayMinor.lastOrNull() ?: 0L) + insights.netSpendAfterTodayMinor,
                )
            }
        }
        ordered.zipWithNext().forEach { (source, target) ->
            val sourceSummaries = state.pocketSummariesByPeriod.getValue(source.id).associateBy { it.pocket.id }
            val rate = target.priorCurrencyBoundary
                ?.takeIf { source.accountingCurrency != target.accountingCurrency }
                ?.let { FrozenRate(it.from, it.to, it.rate) }
            state.pocketSummariesByPeriod.getValue(target.id).forEach { summary ->
                val sourceSummary = sourceSummaries[summary.pocket.id]
                val carried = if (sourceSummary != null && sourceSummary.rolloverEligible && !sourceSummary.retiredThisPeriod) {
                    sourceSummary.availabilityMinor.coerceAtLeast(0)
                } else {
                    0L
                }
                val expected = rate?.convertMinor(carried) ?: carried
                if (summary.retiredThisPeriod) {
                    assertEquals("$context: released rollover of ${summary.pocket.name}", expected, summary.rolloverReleasedMinor)
                } else {
                    assertEquals("$context: rollover of ${summary.pocket.name} into ${target.start}", expected, summary.rolloverMinor)
                }
            }
        }
    }

    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
    }
}
