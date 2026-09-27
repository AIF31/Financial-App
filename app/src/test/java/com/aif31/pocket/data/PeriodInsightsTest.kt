package com.aif31.pocket.data

import com.aif31.pocket.domain.SupportedCurrency
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodInsightsTest {
    private val february = Period("p1", LocalDate.of(2026, 1, 25), LocalDate.of(2026, 2, 25), 30_000, 25)
    private val march = Period("p2", LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 25), 30_000, 25)
    private val april = Period("p3", LocalDate.of(2026, 3, 25), LocalDate.of(2026, 4, 25), 30_000, 25)
    private val market = pocket("market", "Supermercado")
    private val travel = pocket("travel", "Viajes")

    @Test
    fun current_period_reports_pace_safe_daily_spend_and_projection() {
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(march.id to listOf(summary(market, budget = 30_000, expense = 9_000))),
        )

        val insights = PeriodInsights.of(state, march.id)!!

        assertTrue(insights.inProgress)
        assertEquals(28, insights.totalDays)
        assertEquals(9, insights.elapsedDays)
        assertEquals(19, insights.remainingDays)
        assertEquals(9_000L, insights.netSpendMinor)
        assertEquals(21_000L, insights.availabilityMinor)
        assertEquals(1_000L, insights.averageDailySpendMinor)
        assertEquals(1_050L, insights.safeDailySpendMinor)
        assertEquals(28_000L, insights.projectedSpendMinor)
        assertEquals(30, insights.budgetUsedPercent)
        assertEquals(32, insights.periodElapsedPercent)
        assertEquals(SpendPaceStatus.ON_PLAN, insights.paceStatus)
    }

    @Test
    fun budget_used_counts_rollover_like_availability_does() {
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(march.id to listOf(summary(market, budget = 10_000, rollover = 10_000, expense = 15_000))),
        )

        val insights = PeriodInsights.of(state, march.id)!!

        assertEquals(20_000L, insights.availableBudgetMinor)
        assertEquals(5_000L, insights.availabilityMinor)
        assertEquals(75, insights.budgetUsedPercent)
    }

    @Test
    fun pace_status_reports_overspending_and_projection_above_new_funds() {
        val overspent = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(march.id to listOf(summary(market, budget = 1_000, expense = 1_500))),
        )
        val fastPace = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(march.id to listOf(summary(market, budget = 30_000, expense = 12_000))),
        )
        val noFunds = state(today = LocalDate.of(2026, 3, 5), elapsedDays = 9, periods = listOf(february, march.copy(newFundsMinor = 0), april))

        assertEquals(SpendPaceStatus.OVERSPENT, PeriodInsights.of(overspent, march.id)!!.paceStatus)
        assertEquals(SpendPaceStatus.OVER_PACE, PeriodInsights.of(fastPace, march.id)!!.paceStatus)
        assertEquals(SpendPaceStatus.NO_FUNDS, PeriodInsights.of(noFunds, march.id)!!.paceStatus)
    }

    @Test
    fun closed_period_uses_every_day_and_has_no_forward_looking_metrics() {
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(february.id to listOf(summary(market, budget = 31_000, expense = 6_200))),
        )

        val insights = PeriodInsights.of(state, february.id)!!

        assertFalse(insights.inProgress)
        assertEquals(31, insights.elapsedDays)
        assertEquals(0, insights.remainingDays)
        assertEquals(200L, insights.averageDailySpendMinor)
        assertNull(insights.safeDailySpendMinor)
        assertNull(insights.projectedSpendMinor)
        assertEquals(100, insights.periodElapsedPercent)
    }

    @Test
    fun future_period_has_no_elapsed_days_or_average() {
        val state = state(today = LocalDate.of(2026, 3, 5), elapsedDays = 9)

        val insights = PeriodInsights.of(state, april.id)!!

        assertEquals(0, insights.elapsedDays)
        assertNull(insights.averageDailySpendMinor)
        assertEquals(emptyList<Long>(), insights.cumulativeNetSpendByDayMinor)
    }

    @Test
    fun no_safe_daily_spend_when_nothing_is_available() {
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(march.id to listOf(summary(market, budget = 1_000, expense = 1_500))),
        )

        assertNull(PeriodInsights.of(state, march.id)!!.safeDailySpendMinor)
    }

    @Test
    fun cumulative_curve_nets_refunds_by_day_until_today() {
        val state = state(
            today = LocalDate.of(2026, 2, 27),
            elapsedDays = 3,
            summaries = mapOf(march.id to listOf(summary(market, budget = 30_000, expense = 1_500, refund = 300))),
            movements = listOf(
                movement("a", march, LocalDate.of(2026, 2, 25), 1_000),
                movement("b", march, LocalDate.of(2026, 2, 27), 500),
                movement("c", march, LocalDate.of(2026, 2, 27), 300, MovementType.REFUND),
                movement("other-period", february, LocalDate.of(2026, 2, 1), 9_999),
            ),
        )

        val insights = PeriodInsights.of(state, march.id)!!

        assertEquals(listOf(1_000L, 1_000L, 1_200L), insights.cumulativeNetSpendByDayMinor)
        assertEquals(2, insights.expenseCount)
        assertEquals("a", insights.largestExpense?.id)
    }

    @Test
    fun curve_and_movement_stats_only_count_pockets_in_the_period_snapshot_like_net_spend() {
        val state = state(
            today = LocalDate.of(2026, 2, 27),
            elapsedDays = 3,
            summaries = mapOf(march.id to listOf(summary(market, budget = 30_000, expense = 1_000))),
            movements = listOf(
                movement("in-snapshot", march, LocalDate.of(2026, 2, 25), 1_000),
                movement("outside-snapshot", march, LocalDate.of(2026, 2, 26), 7_000, pocket = travel),
            ),
        )

        val insights = PeriodInsights.of(state, march.id)!!

        assertEquals(insights.netSpendMinor, insights.cumulativeNetSpendByDayMinor.last())
        assertEquals(1, insights.expenseCount)
        assertEquals("in-snapshot", insights.largestExpense?.id)
    }

    @Test
    fun movement_net_spend_subtracts_refunds() {
        val movements = listOf(
            movement("a", march, LocalDate.of(2026, 2, 25), 1_000),
            movement("b", march, LocalDate.of(2026, 2, 25), 4_300, MovementType.REFUND),
        )

        assertEquals(-3_300L, movements.netSpendMinor())
    }

    @Test
    fun comparison_uses_daily_average_and_matches_pockets_by_identity() {
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(
                march.id to listOf(summary(market, budget = 30_000, expense = 9_000)),
                february.id to listOf(
                    summary(market, budget = 20_000, expense = 6_200),
                    summary(travel, budget = 10_000, expense = 3_100),
                ),
            ),
        )

        val comparison = PeriodComparison.of(state, march.id, february.id)!!
        val baseline = comparison.convertedBaseline!!

        assertEquals(1_000L, comparison.current.averageDailySpendMinor)
        assertEquals(300L, baseline.averageDailySpendMinor)
        assertEquals(700L, baseline.averageDailyDeltaMinor)
        assertEquals(233, baseline.averageDailyDeltaPercent)
        val rows = comparison.pockets.associateBy { it.pocket.id }
        assertEquals(1_000L, rows.getValue("market").currentAverageDailyMinor)
        assertEquals(200L, rows.getValue("market").baselineAverageDailyMinor)
        assertEquals(800L, rows.getValue("market").averageDailyDeltaMinor)
        assertEquals(400, rows.getValue("market").averageDailyDeltaPercent)
        assertEquals(0L, rows.getValue("travel").currentAverageDailyMinor)
        assertEquals(100L, rows.getValue("travel").baselineAverageDailyMinor)
        assertEquals(-100, rows.getValue("travel").averageDailyDeltaPercent)
        assertEquals(listOf("market", "travel"), comparison.pockets.map { it.pocket.id })
    }

    @Test
    fun default_baseline_is_the_immediately_previous_period() {
        val state = state(today = LocalDate.of(2026, 3, 5), elapsedDays = 9)

        assertEquals(february.id, PeriodComparison.previousPeriodId(state, march.id))
        assertNull(PeriodComparison.previousPeriodId(state, february.id))
    }

    @Test
    fun adjacent_currency_change_converts_the_baseline_with_the_frozen_boundary_rate() {
        val converted = march.copy(
            accountingCurrency = SupportedCurrency.MXN,
            priorCurrencyBoundary = CurrencyBoundary(
                SupportedCurrency.SAR, SupportedCurrency.MXN, "4.5", march.start, "TEST",
            ),
        )
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            periods = listOf(february, converted, april),
            summaries = mapOf(february.id to listOf(summary(market, budget = 20_000, expense = 6_200))),
        )

        val comparison = PeriodComparison.of(state, converted.id, february.id)!!

        assertEquals(900L, comparison.convertedBaseline?.averageDailySpendMinor)
    }

    @Test
    fun unrelated_currencies_are_not_compared_numerically() {
        val mexican = april.copy(accountingCurrency = SupportedCurrency.MXN)
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            periods = listOf(february, march, mexican),
        )

        val comparison = PeriodComparison.of(state, mexican.id, february.id)!!

        assertNull(comparison.convertedBaseline)
        assertTrue(comparison.pockets.all { it.averageDailyDeltaMinor == null })
    }

    @Test
    fun pocket_budget_status_follows_the_ledger_flags_and_budget() {
        assertEquals(PocketBudgetStatus.EXHAUSTED, summary(market, budget = 1_000, expense = 1_000).copy(exhausted = true).budgetStatus)
        assertEquals(PocketBudgetStatus.AT_RISK, summary(market, budget = 1_000, expense = 850).copy(atRisk = true).budgetStatus)
        assertEquals(PocketBudgetStatus.UNBUDGETED, summary(market, budget = 0, rollover = 500, expense = 0).budgetStatus)
        assertEquals(PocketBudgetStatus.ON_TRACK, summary(market, budget = 1_000, expense = 100).budgetStatus)
    }

    private fun state(
        today: LocalDate,
        elapsedDays: Int,
        periods: List<Period> = listOf(february, march, april),
        summaries: Map<String, List<PocketPeriodSummary>> = emptyMap(),
        movements: List<Movement> = emptyList(),
    ): LedgerState {
        val current = periods.first { today >= it.start && today < it.endExclusive }
        return LedgerState(
            periods = periods,
            currentPeriod = current,
            pocketSummariesByPeriod = periods.associate { it.id to summaries[it.id].orEmpty() },
            unallocatedMinorByPeriod = periods.associate { it.id to 0L },
            movements = movements,
            elapsedDays = elapsedDays,
            totalDays = (current.endExclusive.toEpochDay() - current.start.toEpochDay()).toInt(),
            currentLocalDate = today,
        )
    }

    private fun pocket(id: String, name: String) = Pocket(id, name, PocketIconKey.forName(name), 0, false, false)

    private fun summary(pocket: Pocket, budget: Long, expense: Long, rollover: Long = 0, refund: Long = 0) = PocketPeriodSummary(
        pocket = pocket,
        budgetMinor = budget,
        rolloverMinor = rollover,
        expenseMinor = expense,
        refundMinor = refund,
        netSpendMinor = expense - refund,
        availabilityMinor = budget + rollover - (expense - refund),
        consumedPercent = if (budget + rollover == 0L) 0 else ((expense - refund) * 100 / (budget + rollover)).toInt(),
        atRisk = false,
        exhausted = false,
    )

    private fun movement(
        id: String,
        period: Period,
        date: LocalDate,
        amount: Long,
        type: MovementType = MovementType.EXPENSE,
        pocket: Pocket = market,
    ) = Movement(
        id = id, pocketId = pocket.id, pocketName = pocket.name, periodId = period.id, type = type,
        accountingAmountMinor = amount, occurredAtUtcMillis = 0, localDate = date, zoneId = "Asia/Riyadh",
        merchant = null, note = null, paymentMethodId = null, paymentMethodName = null,
        originalAmountMinor = null, originalCurrencyCode = period.accountingCurrency.name,
        conversionStatus = ConversionStatus.CONFIRMED, rate = null, conversionEffectiveDate = null,
        conversionSource = null,
    )
}
