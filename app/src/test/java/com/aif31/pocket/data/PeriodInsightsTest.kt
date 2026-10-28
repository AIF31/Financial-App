package com.aif31.pocket.data

import com.aif31.pocket.domain.PocketMath
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
    fun budget_used_stays_exact_for_amounts_whose_hundredfold_exceeds_a_long() {
        // 5 × 10¹⁷ of 10¹⁸ minor units: valid amounts, but 5 × 10¹⁷ × 100 does not fit in a Long.
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(march.id to listOf(summary(market, budget = 1_000_000_000_000_000_000, expense = 500_000_000_000_000_000))),
        )

        assertEquals(50, PeriodInsights.of(state, march.id)!!.budgetUsedPercent)
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
    fun rollover_counts_as_funds_and_overspending_wins_over_missing_new_funds() {
        val rolloverOnly = periodsWithoutNewFunds()
        val onPlan = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            periods = rolloverOnly,
            summaries = mapOf(march.id to listOf(summary(market, budget = 0, rollover = 50_000, expense = 1_000))),
        )
        val fastPace = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            periods = rolloverOnly,
            summaries = mapOf(march.id to listOf(summary(market, budget = 0, rollover = 10_000, expense = 9_000))),
        )
        val overspent = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            periods = rolloverOnly,
            summaries = mapOf(march.id to listOf(summary(market, budget = 0, rollover = 10_000, expense = 15_000))),
        )
        val spentWithoutFunds = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            periods = rolloverOnly,
            summaries = mapOf(march.id to listOf(summary(market, budget = 0, expense = 500))),
        )

        assertEquals(SpendPaceStatus.ON_PLAN, PeriodInsights.of(onPlan, march.id)!!.paceStatus)
        assertEquals(SpendPaceStatus.OVER_PACE, PeriodInsights.of(fastPace, march.id)!!.paceStatus)
        assertEquals(SpendPaceStatus.OVERSPENT, PeriodInsights.of(overspent, march.id)!!.paceStatus)
        assertEquals(SpendPaceStatus.OVERSPENT, PeriodInsights.of(spentWithoutFunds, march.id)!!.paceStatus)
    }

    @Test
    fun movements_dated_after_today_are_reported_separately_from_the_curve() {
        val state = state(
            today = LocalDate.of(2026, 2, 27),
            elapsedDays = 3,
            summaries = mapOf(march.id to listOf(summary(market, budget = 30_000, expense = 1_700, refund = 100))),
            movements = listOf(
                movement("today", march, LocalDate.of(2026, 2, 27), 1_000),
                movement("later", march, LocalDate.of(2026, 3, 10), 700),
                movement("later-refund", march, LocalDate.of(2026, 3, 11), 100, MovementType.REFUND),
            ),
        )

        val insights = PeriodInsights.of(state, march.id)!!

        assertEquals(listOf(0L, 0L, 1_000L), insights.cumulativeNetSpendByDayMinor)
        assertEquals(600L, insights.netSpendAfterTodayMinor)
        assertEquals(insights.netSpendMinor, insights.cumulativeNetSpendByDayMinor.last() + insights.netSpendAfterTodayMinor)
        assertEquals(0L, PeriodInsights.of(state, february.id)!!.netSpendAfterTodayMinor)
    }

    @Test
    fun a_future_dated_expense_counts_once_in_the_projection_but_not_in_todays_pace() {
        // Day 9 of March's 28 days: SAR 9.00 spent so far, plus SAR 30.00 of rent recorded for 20 March.
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(march.id to listOf(summary(market, budget = 30_000, expense = 3_900))),
            movements = listOf(
                movement("groceries", march, LocalDate.of(2026, 3, 1), 900),
                movement("rent", march, LocalDate.of(2026, 3, 20), 3_000),
            ),
        )

        val insights = PeriodInsights.of(state, march.id)!!

        assertEquals(100L, insights.averageDailySpendMinor) // 900 / 9, the pace so far
        assertEquals(5_800L, insights.projectedSpendMinor) // 900 * 28 / 9 = 2,800, plus the rent once
        assertEquals(3_900L, insights.netSpendMinor) // Period totals still include the rent.
        assertEquals(26_100L, insights.availabilityMinor)
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
    fun pocket_comparison_averages_only_spending_dated_through_today() {
        // Day 9 of March: SAR 9.00 spent so far, plus SAR 30.00 of rent recorded for 20 March.
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(
                march.id to listOf(summary(market, budget = 30_000, expense = 3_900)),
                february.id to listOf(summary(market, budget = 30_000, expense = 6_200)),
            ),
            movements = listOf(
                movement("groceries", march, LocalDate.of(2026, 3, 1), 900),
                movement("rent", march, LocalDate.of(2026, 3, 20), 3_000),
            ),
        )

        val row = PeriodComparison.of(state, march.id, february.id)!!.pockets.single()

        assertEquals(3_900L, row.currentNetSpendMinor) // The period total still includes the rent.
        assertEquals(100L, row.currentAverageDailyMinor) // 900 / 9, like the headline pace
        assertEquals(200L, row.baselineAverageDailyMinor) // 6,200 / 31
        assertEquals(-50, row.averageDailyDeltaPercent)
    }

    @Test
    fun an_in_progress_baseline_averages_only_spending_dated_through_today() {
        // February compared against March, which is on day 9 with SAR 30.00 of rent recorded for 20 March.
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(
                march.id to listOf(summary(market, budget = 30_000, expense = 3_900)),
                february.id to listOf(summary(market, budget = 30_000, expense = 6_200)),
            ),
            movements = listOf(
                movement("groceries", march, LocalDate.of(2026, 3, 1), 900),
                movement("rent", march, LocalDate.of(2026, 3, 20), 3_000),
            ),
        )

        val comparison = PeriodComparison.of(state, february.id, march.id)!!
        val baseline = comparison.convertedBaseline!!

        assertEquals(3_900L, baseline.netSpendMinor) // The period total still includes the rent.
        assertEquals(100L, baseline.averageDailySpendMinor) // 900 / 9
        assertEquals(100, baseline.averageDailyDeltaPercent) // February's 200 a day against 100
        assertEquals(100L, comparison.pockets.single().baselineAverageDailyMinor)
    }

    @Test
    fun comparison_percentages_stay_exact_for_amounts_whose_hundredfold_exceeds_a_long() {
        // 2 × 10¹⁷ a day over 9 days against 10¹⁶ a day over February's 31: a 1,900% rise.
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(
                march.id to listOf(summary(market, budget = 2_000_000_000_000_000_000, expense = 1_800_000_000_000_000_000)),
                february.id to listOf(summary(market, budget = 2_000_000_000_000_000_000, expense = 310_000_000_000_000_000)),
            ),
        )

        val comparison = PeriodComparison.of(state, march.id, february.id)!!

        assertEquals(1_900, comparison.convertedBaseline!!.averageDailyDeltaPercent)
        assertEquals(1_900, comparison.pockets.single().averageDailyDeltaPercent)
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
    fun without_insights_only_the_exhausted_flag_and_the_budget_count() {
        assertEquals(PocketBudgetStatus.EXHAUSTED, summary(market, budget = 1_000, expense = 1_000).budgetStatusAt(null))
        assertEquals(PocketBudgetStatus.ON_TRACK, summary(market, budget = 1_000, expense = 850).budgetStatusAt(null))
        assertEquals(PocketBudgetStatus.UNBUDGETED, summary(market, budget = 0, rollover = 500, expense = 0).budgetStatusAt(null))
        assertEquals(PocketBudgetStatus.ON_TRACK, summary(market, budget = 1_000, expense = 100).budgetStatusAt(null))
    }

    @Test
    fun a_pocket_is_at_risk_only_when_it_has_consumed_80_percent_and_its_pace_runs_out() {
        // Day 9 of March. Supermercado: SAR 850.00 of SAR 1,000.00 spent on two days, about SAR 94.44 a day; SAR 150.00
        // lasts one more day. Viajes runs out on 12 March at its pace too, but 40% of its budget is left.
        val atRisk = summary(market, budget = 100_000, expense = 85_000)
        val fast = summary(travel, budget = 100_000, expense = 60_000)
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(march.id to listOf(atRisk, fast)),
            movements = listOf(
                movement("a", march, LocalDate.of(2026, 2, 26), 42_500),
                movement("b", march, LocalDate.of(2026, 3, 3), 42_500),
                movement("c", march, LocalDate.of(2026, 2, 27), 30_000, pocket = travel),
                movement("d", march, LocalDate.of(2026, 3, 4), 30_000, pocket = travel),
            ),
        )
        val insights = PeriodInsights.of(state, march.id)!!

        assertEquals(mapOf(market.id to LocalDate.of(2026, 3, 7)), insights.runOutDateByPocket)
        assertEquals(setOf(travel.id), insights.unconfirmedRunOutPocketIds)
        assertEquals(PocketBudgetStatus.AT_RISK, atRisk.budgetStatusAt(insights))
        assertEquals(PocketBudgetStatus.UNCONFIRMED_PACE, fast.budgetStatusAt(insights))
    }

    @Test
    fun a_pocket_past_80_percent_whose_pace_lasts_the_period_is_on_track() {
        // Day 25 of March: SAR 850.00 of SAR 1,000.00 spent, SAR 34.00 a day; SAR 150.00 lasts past 25 March.
        val steady = summary(market, budget = 100_000, expense = 85_000)
        val state = state(
            today = LocalDate.of(2026, 3, 21),
            elapsedDays = 25,
            summaries = mapOf(march.id to listOf(steady)),
            movements = listOf(movement("a", march, LocalDate.of(2026, 2, 26), 42_500), movement("b", march, LocalDate.of(2026, 3, 15), 42_500)),
        )
        val insights = PeriodInsights.of(state, march.id)!!

        assertTrue(insights.runOutDateByPocket.isEmpty())
        assertEquals(PocketBudgetStatus.ON_TRACK, steady.budgetStatusAt(insights))
    }

    @Test
    fun a_small_budget_shows_no_status_until_it_is_at_risk_after_the_middle_of_the_period() {
        // Supermercado: SAR 127.20 of SAR 159.00 spent on two days. On day 9 it would run out on 8 March, but the
        // middle of the period is 11 March. On day 15 it runs out on 15 March. Viajes is fast but at 50%.
        val small = summary(market, budget = 15_900, expense = 12_720)
        val fast = summary(travel, budget = 15_900, expense = 7_950)
        val movements = listOf(
            movement("a", march, LocalDate.of(2026, 2, 26), 6_000),
            movement("b", march, LocalDate.of(2026, 3, 3), 6_720),
            movement("c", march, LocalDate.of(2026, 2, 27), 3_975, pocket = travel),
            movement("d", march, LocalDate.of(2026, 3, 4), 3_975, pocket = travel),
        )
        val summaries = mapOf(march.id to listOf(small, fast))
        val early = PeriodInsights.of(state(LocalDate.of(2026, 3, 5), 9, summaries = summaries, movements = movements), march.id)!!
        val late = PeriodInsights.of(state(LocalDate.of(2026, 3, 11), 15, summaries = summaries, movements = movements), march.id)!!

        assertTrue(early.runOutDateByPocket.isEmpty())
        assertEquals(PocketBudgetStatus.SMALL_BUDGET, small.budgetStatusAt(early))
        assertEquals(PocketBudgetStatus.SMALL_BUDGET, fast.budgetStatusAt(early))
        assertEquals(mapOf(market.id to LocalDate.of(2026, 3, 15)), late.runOutDateByPocket)
        assertEquals(PocketBudgetStatus.AT_RISK, small.budgetStatusAt(late))
    }

    @Test
    fun the_small_budget_limit_is_about_sar_200_in_each_currency() {
        fun small(currency: SupportedCurrency, budget: Long, rollover: Long = 0): Boolean {
            val period = march.copy(accountingCurrency = currency)
            val state = state(LocalDate.of(2026, 3, 5), 9, periods = listOf(february, period, april))
            return PeriodInsights.of(state, march.id)!!.hasSmallBudget(summary(market, budget = budget, rollover = rollover, expense = 0))
        }

        assertTrue(small(SupportedCurrency.SAR, 19_999))
        assertFalse(small(SupportedCurrency.SAR, 20_000))
        assertFalse(small(SupportedCurrency.SAR, 15_000, rollover = 5_000))
        // USD 53.33 is SAR 199.99 at the 3.75 peg; USD 53.34 is SAR 200.03.
        assertTrue(small(SupportedCurrency.USD, 5_333))
        assertFalse(small(SupportedCurrency.USD, 5_334))
        assertTrue(small(SupportedCurrency.MXN, 99_999))
        assertFalse(small(SupportedCurrency.MXN, 100_000))
        assertFalse(small(SupportedCurrency.SAR, 0))
    }

    @Test
    fun a_shortfall_of_a_fraction_of_a_minor_unit_a_day_still_runs_out() {
        // Day 25 of 28: SAR 400.24 spent on two days, SAR 16.0096 a day. The 3 days after today need SAR 48.0288,
        // and SAR 48.00 is left. A pace truncated to SAR 16.00 would need exactly SAR 48.00 and never warn.
        val tight = summary(market, budget = 44_824, expense = 40_024)
        val state = state(
            today = LocalDate.of(2026, 3, 21),
            elapsedDays = 25,
            summaries = mapOf(march.id to listOf(tight)),
            movements = listOf(movement("a", march, LocalDate.of(2026, 2, 26), 20_000), movement("b", march, LocalDate.of(2026, 3, 15), 20_024)),
        )
        val insights = PeriodInsights.of(state, march.id)!!

        assertEquals(mapOf(market.id to LocalDate.of(2026, 3, 24)), insights.runOutDateByPocket)
        assertEquals(PocketBudgetStatus.AT_RISK, tight.budgetStatusAt(insights))
    }

    @Test
    fun a_single_large_expense_or_a_closed_period_has_no_run_out_date() {
        val rentOnly = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(march.id to listOf(summary(market, budget = 100_000, expense = 90_000))),
            movements = listOf(movement("rent", march, LocalDate.of(2026, 2, 25), 90_000)),
        )
        val closed = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(february.id to listOf(summary(market, budget = 10_000, expense = 6_000))),
            movements = listOf(
                movement("a", february, LocalDate.of(2026, 1, 26), 3_000),
                movement("b", february, LocalDate.of(2026, 1, 27), 3_000),
            ),
        )

        assertTrue(PeriodInsights.of(rentOnly, march.id)!!.runOutDateByPocket.isEmpty())
        assertEquals(PocketBudgetStatus.ON_TRACK, rentOnly.pocketSummariesByPeriod.getValue(march.id).single().budgetStatusAt(PeriodInsights.of(rentOnly, march.id)))
        assertTrue(PeriodInsights.of(closed, february.id)!!.runOutDateByPocket.isEmpty())
    }

    @Test
    fun spending_dated_after_today_reduces_what_is_left_but_not_the_pace() {
        // SAR 200.00 over two days through day 9, plus SAR 700.00 booked for 20 March: SAR 100.00 left at SAR 22.22 a day.
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(march.id to listOf(summary(market, budget = 100_000, expense = 90_000))),
            movements = listOf(
                movement("a", march, LocalDate.of(2026, 2, 26), 10_000),
                movement("b", march, LocalDate.of(2026, 3, 3), 10_000),
                movement("later", march, LocalDate.of(2026, 3, 20), 70_000),
            ),
        )

        assertEquals(LocalDate.of(2026, 3, 10), PeriodInsights.of(state, march.id)!!.runOutDateByPocket[market.id])
    }

    @Test
    fun a_huge_budget_spent_slowly_has_no_run_out_date_instead_of_overflowing_the_calendar() {
        // 10^14 minor units at SAR 0.22 a day would run out after more days than a date can hold.
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(march.id to listOf(summary(market, budget = 100_000_000_000_000, expense = 200))),
            movements = listOf(
                movement("a", march, LocalDate.of(2026, 2, 26), 100),
                movement("b", march, LocalDate.of(2026, 3, 3), 100),
            ),
        )

        assertTrue(PeriodInsights.of(state, march.id)!!.runOutDateByPocket.isEmpty())
    }

    @Test
    fun a_pocket_funded_only_by_rollover_can_be_at_risk() {
        val rolloverOnly = summary(market, budget = 0, rollover = 100_000, expense = 85_000)
        val state = state(
            today = LocalDate.of(2026, 3, 5),
            elapsedDays = 9,
            summaries = mapOf(march.id to listOf(rolloverOnly)),
            movements = listOf(movement("a", march, LocalDate.of(2026, 2, 26), 42_500), movement("b", march, LocalDate.of(2026, 3, 3), 42_500)),
        )

        assertEquals(PocketBudgetStatus.AT_RISK, rolloverOnly.budgetStatusAt(PeriodInsights.of(state, march.id)))
        assertEquals(PocketBudgetStatus.UNBUDGETED, rolloverOnly.budgetStatusAt(null))
    }

    @Test
    fun a_few_days_of_ordinary_spending_are_not_stretched_into_an_early_warning() {
        // Day 3 of March: SAR 155.25 of SAR 1,252.00 spent on two days. At that pace SAR 1,096.75 runs out on 21 March
        // and the period would need SAR 1,449.00, but three days are too few to warn that far ahead.
        val cafe = summary(market, budget = 125_200, expense = 15_525)
        val state = state(
            today = LocalDate.of(2026, 2, 27),
            elapsedDays = 3,
            periods = listOf(february, march.copy(newFundsMinor = 125_200), april),
            summaries = mapOf(march.id to listOf(cafe)),
            movements = listOf(movement("a", march, LocalDate.of(2026, 2, 25), 7_000), movement("b", march, LocalDate.of(2026, 2, 27), 8_525)),
        )
        val insights = PeriodInsights.of(state, march.id)!!

        assertTrue(insights.runOutDateByPocket.isEmpty())
        assertEquals(setOf(market.id), insights.unconfirmedRunOutPocketIds)
        assertEquals(PocketBudgetStatus.UNCONFIRMED_PACE, cafe.budgetStatusAt(insights))
        // The whole-period projection is above the funds, so the period is not called on plan either.
        assertEquals(SpendPaceStatus.EARLY_OVER_PACE, insights.paceStatus)
        assertEquals(144_900L, insights.projectedSpendMinor)
    }

    @Test
    fun early_in_the_period_spending_that_runs_out_before_mid_period_still_warns() {
        // Day 3 of March: SAR 850.00 of SAR 1,000.00 spent, SAR 283.33 a day; SAR 150.00 runs out tomorrow, before
        // the middle of the period on 11 March. The period's funds run out by then too.
        val fast = summary(market, budget = 100_000, expense = 85_000)
        val state = state(
            today = LocalDate.of(2026, 2, 27),
            elapsedDays = 3,
            periods = listOf(february, march.copy(newFundsMinor = 100_000), april),
            summaries = mapOf(march.id to listOf(fast)),
            movements = listOf(movement("a", march, LocalDate.of(2026, 2, 25), 42_500), movement("b", march, LocalDate.of(2026, 2, 27), 42_500)),
        )
        val insights = PeriodInsights.of(state, march.id)!!

        assertEquals(mapOf(market.id to LocalDate.of(2026, 2, 28)), insights.runOutDateByPocket)
        assertEquals(PocketBudgetStatus.AT_RISK, fast.budgetStatusAt(insights))
        assertEquals(SpendPaceStatus.OVER_PACE, insights.paceStatus)
    }

    @Test
    fun from_the_seventh_day_pace_warns_about_the_whole_period() {
        // SAR 140.00 spent over two days plus SAR 660.00 booked for 20 March: SAR 200.00 of SAR 1,000.00 is left at
        // SAR 20.00 a day, so it runs out on 14 March, after the middle of the period but before it ends.
        val booked = summary(market, budget = 100_000, expense = 80_000)
        val movements = listOf(
            movement("a", march, LocalDate.of(2026, 2, 25), 7_000),
            movement("b", march, LocalDate.of(2026, 3, 2), 7_000),
            movement("later", march, LocalDate.of(2026, 3, 20), 66_000),
        )
        fun insights(today: LocalDate, elapsedDays: Int) = PeriodInsights.of(
            state(today, elapsedDays, listOf(february, march.copy(newFundsMinor = 100_000), april), mapOf(march.id to listOf(booked)), movements),
            march.id,
        )!!
        val warmingUp = insights(LocalDate.of(2026, 3, 2), 6)
        val warmedUp = insights(LocalDate.of(2026, 3, 3), 7)

        assertTrue(warmingUp.runOutDateByPocket.isEmpty())
        assertEquals(PocketBudgetStatus.UNCONFIRMED_PACE, booked.budgetStatusAt(warmingUp))
        assertEquals(mapOf(market.id to LocalDate.of(2026, 3, 14)), warmedUp.runOutDateByPocket)
        assertEquals(SpendPaceStatus.OVER_PACE, warmedUp.paceStatus)
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

    private fun periodsWithoutNewFunds() = listOf(february, march.copy(newFundsMinor = 0), april)

    private fun pocket(id: String, name: String) = Pocket(id, name, PocketIconKey.forName(name), 0, false, false)

    private fun summary(pocket: Pocket, budget: Long, expense: Long, rollover: Long = 0, refund: Long = 0): PocketPeriodSummary {
        val math = PocketMath.summary(budget, rollover, expense, refund)
        return PocketPeriodSummary(
            pocket = pocket,
            budgetMinor = budget,
            rolloverMinor = rollover,
            expenseMinor = expense,
            refundMinor = refund,
            netSpendMinor = math.netSpendMinor,
            availabilityMinor = math.availabilityMinor,
            consumedPercent = math.consumedPercent,
            atRisk = math.atRisk,
            exhausted = math.exhausted,
        )
    }

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
