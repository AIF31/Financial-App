package com.aif31.pocket.data

import java.time.LocalDate
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetReassignmentTest {
    private val period = Period("p", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1), 100_000, 1)
    private fun summary(id: String, budget: Long, available: Long) = PocketPeriodSummary(
        pocket = Pocket(id, id, PocketIconKey.OTHER, 0, false, false),
        budgetMinor = budget, rolloverMinor = 0, netSpendMinor = budget - available,
        availabilityMinor = available, consumedPercent = 0, atRisk = false, exhausted = available <= 0,
    )
    private fun state(vararg pockets: PocketPeriodSummary) = LedgerState(
        periods = listOf(period), currentPeriod = period, pockets = pockets.toList(),
        pocketSummariesByPeriod = mapOf(period.id to pockets.toList()),
        elapsedDays = 10, totalDays = 31, currentLocalDate = LocalDate.of(2026, 3, 10),
    )

    private fun movement(id: String, pocketId: String, date: LocalDate, amount: Long) = Movement(
        id = id, pocketId = pocketId, pocketName = pocketId, periodId = period.id, type = MovementType.EXPENSE,
        accountingAmountMinor = amount, occurredAtUtcMillis = 0, localDate = date, zoneId = "Asia/Riyadh",
        merchant = null, note = null, paymentMethodId = null, paymentMethodName = null,
        originalAmountMinor = null, originalCurrencyCode = "SAR", conversionStatus = ConversionStatus.CONFIRMED,
        rate = null, conversionEffectiveDate = null, conversionSource = null,
    )

    @Test fun forecast_uses_pace_through_today_and_rounds_the_remaining_shortfall_up() {
        // 1,030 spent over 10 days gives 103/day; 21 remaining days need 2,163.
        // A later-dated expense is already subtracted from availability, so it is not extrapolated again.
        val target = summary("target", 3_000, 1_520)
        val state = state(target).copy(movements = listOf(
            movement("a", "target", LocalDate.of(2026, 3, 2), 500),
            movement("b", "target", LocalDate.of(2026, 3, 3), 530),
            movement("later", "target", LocalDate.of(2026, 3, 20), 450),
        ))
        assertEquals(PocketBudgetStatus.FAST_PACE, target.budgetStatusAt(PeriodInsights.of(state, period.id)))
        assertEquals(700L, BudgetReassignment.of(state, period.id, "target")!!.neededMinor)
    }

    @Test fun only_current_active_targets_that_need_covering_are_offered() {
        val target = summary("target", 1_000, -235)
        assertNull(BudgetReassignment.of(state(target).copy(currentPeriod = null), period.id, "target"))
        assertNull(BudgetReassignment.of(state(target.copy(retiredThisPeriod = true)), period.id, "target"))
        assertNull(BudgetReassignment.of(state(target.copy(pocket = target.pocket.copy(archived = true))), period.id, "target"))
        assertNull(BudgetReassignment.of(state(summary("target", 1_000, 500)), period.id, "target"))
    }

    @Test fun an_unrepresentable_needed_amount_is_left_for_manual_entry() {
        assertNull(BudgetReassignment.of(state(summary("target", 0, Long.MIN_VALUE)), period.id, "target")!!.neededMinor)
    }

    @Test fun sources_put_unassigned_first_then_eligible_pockets_with_the_most_room() {
        val target = summary("target", 1_000, -235)
        val archived = summary("archived", 8_000, 8_000).let { it.copy(pocket = it.pocket.copy(archived = true)) }
        val retired = summary("retired", 9_000, 9_000).copy(retiredThisPeriod = true)
        val fast = summary("fast", 3_000, 1_970)
        val state = state(
            target, summary("small", 500, 300), summary("large", 4_000, 4_000),
            archived, retired, summary("zero", 1_000, 0), summary("negative", 1_000, -1),
            summary("risk", 1_000, 100).copy(atRisk = true), fast,
        ).copy(
            unallocatedMinorByPeriod = mapOf(period.id to 250L, "other" to 9_000L),
            movements = listOf(
                movement("a", "fast", LocalDate.of(2026, 3, 2), 500),
                movement("b", "fast", LocalDate.of(2026, 3, 3), 530),
            ),
        )
        val sources = BudgetReassignment.of(state, period.id, "target")!!.sources
        assertEquals(listOf(null, "large", "small"), sources.map { it.pocketId })
        assertEquals(listOf(250L, 4_000L, 300L), sources.map { it.maximumMinor })
    }

    @Test fun rollover_cannot_be_reassigned_and_nonpositive_unassigned_funds_are_omitted() {
        val target = summary("target", 1_000, -235)
        val donor = summary("donor", 500, 1_500).copy(rolloverMinor = 1_000, netSpendMinor = 0)
        val rolloverOnly = summary("rolloverOnly", 0, 2_000).copy(rolloverMinor = 2_000, netSpendMinor = 0)
        for (unassigned in listOf(0L, -1L)) {
            val sources = BudgetReassignment.of(
                state(target, donor, rolloverOnly).copy(unallocatedMinorByPeriod = mapOf(period.id to unassigned)), period.id, "target",
            )!!.sources
            assertEquals(listOf("donor"), sources.map { it.pocketId })
            assertEquals(500L, sources.single().maximumMinor)
        }
    }

    @Test fun covering_builds_one_map_with_both_new_budgets_or_only_the_target_for_unassigned() {
        val state = state(summary("target", 1_000, -235), summary("donor", 2_000, 1_500))
            .copy(unallocatedMinorByPeriod = mapOf(period.id to 400L))
        assertEquals(
            LedgerCommand.SetAllocations(period.id, mapOf("target" to 1_235L, "donor" to 1_765L)),
            BudgetReassignment.command(state, period.id, "target", "donor", 235),
        )
        assertEquals(
            LedgerCommand.SetAllocations(period.id, mapOf("target" to 1_235L)),
            BudgetReassignment.command(state, period.id, "target", null, 235),
        )
    }

    @Test fun invalid_amounts_or_sources_are_rejected_without_building_a_command() {
        val state = state(summary("target", 1_000, -235), summary("donor", 500, 1_500))
            .copy(unallocatedMinorByPeriod = mapOf(period.id to 200L))
        for (amount in listOf(0L, -1L, 501L)) {
            assertThrows(IllegalArgumentException::class.java) { BudgetReassignment.command(state, period.id, "target", "donor", amount) }
        }
        for (source in listOf(null, "target", "missing")) {
            assertThrows(IllegalArgumentException::class.java) { BudgetReassignment.command(state, period.id, "target", source, 235) }
        }
        assertThrows(IllegalArgumentException::class.java) { BudgetReassignment.command(state, period.id, "missing", "donor", 1) }
    }

    @Test fun adding_to_the_target_uses_exact_arithmetic() {
        val state = state(summary("target", Long.MAX_VALUE, -1), summary("donor", 1, 1))
        assertThrows(ArithmeticException::class.java) { BudgetReassignment.command(state, period.id, "target", "donor", 1) }
    }

    @Test fun overspending_needs_exactly_the_negative_availability() {
        val target = summary("target", 1_000, -235)
        assertEquals(235L, BudgetReassignment.of(state(target), period.id, target.pocket.id)!!.neededMinor)
    }
}
