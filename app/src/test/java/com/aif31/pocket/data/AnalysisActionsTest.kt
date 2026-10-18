package com.aif31.pocket.data

import com.aif31.pocket.domain.SupportedCurrency
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drill-through, budget suggestions, and the period recap: the actions built on the period analysis. */
class AnalysisActionsTest {
    private val december = Period("p0", LocalDate.of(2025, 11, 25), LocalDate.of(2025, 12, 25), 30_000, 25, accountingCurrency = SupportedCurrency.MXN)
    private val january = Period("p1", LocalDate.of(2025, 12, 25), LocalDate.of(2026, 1, 25), 30_000, 25)
    private val february = Period("p2", LocalDate.of(2026, 1, 25), LocalDate.of(2026, 2, 25), 30_000, 25)
    private val march = Period("p3", LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 25), 30_000, 25)
    private val market = pocket("market", "Supermercado")
    private val food = pocket("food", "Restaurantes")
    private val gifts = pocket("gifts", "Regalos")

    // Drill-through

    @Test
    fun a_merchant_drill_lists_the_period_movements_with_that_normalized_merchant() {
        val state = state(
            movements = listOf(
                movement("a", march, LocalDate.of(2026, 2, 26), 1_000, merchant = "Tamimi"),
                movement("b", march, LocalDate.of(2026, 3, 1), 2_000, merchant = "TAMIMI."),
                movement("c", march, LocalDate.of(2026, 3, 1), 3_000, merchant = "Jarir"),
                movement("d", february, LocalDate.of(2026, 2, 1), 4_000, merchant = "Tamimi"),
            ),
        )

        val drill = MovementDrill.merchant(march.id, merchantKey("Tamimi")!!)

        assertEquals(listOf("a", "b"), drill.movements(state).map { it.id }.sorted())
    }

    @Test
    fun a_weekday_drill_counts_only_days_through_today_like_the_weekday_average() {
        // 25 Feb and 4 Mar 2026 are Wednesdays; 18 Mar is a Wednesday after today (5 Mar).
        val state = state(
            movements = listOf(
                movement("a", march, LocalDate.of(2026, 2, 25), 1_000),
                movement("b", march, LocalDate.of(2026, 3, 4), 1_000),
                movement("later", march, LocalDate.of(2026, 3, 18), 1_000),
                movement("thursday", march, LocalDate.of(2026, 2, 26), 1_000),
            ),
        )

        assertEquals(listOf("a", "b"), MovementDrill.weekday(march.id, DayOfWeek.WEDNESDAY).movements(state).map { it.id }.sorted())
    }

    @Test
    fun day_and_payment_method_drills_match_exactly_including_movements_without_a_method() {
        val state = state(
            movements = listOf(
                movement("card", march, LocalDate.of(2026, 3, 1), 1_000, method = "card"),
                movement("none", march, LocalDate.of(2026, 3, 1), 1_000),
                movement("other-day", march, LocalDate.of(2026, 3, 2), 1_000, method = "card"),
            ),
        )

        assertEquals(listOf("card", "none"), MovementDrill.day(march.id, LocalDate.of(2026, 3, 1)).movements(state).map { it.id }.sorted())
        assertEquals(listOf("card", "other-day"), MovementDrill.paymentMethod(march.id, "card").movements(state).map { it.id }.sorted())
        assertEquals(listOf("none"), MovementDrill.paymentMethod(march.id, null).movements(state).map { it.id })
    }

    // Budget suggestions

    @Test
    fun a_suggestion_is_the_median_of_recent_same_currency_periods_scaled_to_the_target_length_and_rounded_up() {
        // January and February are 31 days, March is 28. Supermercado spent 310.00, 620.00 and (in MXN) 9,999.00.
        val state = state(
            summaries = mapOf(
                december.id to listOf(summary(market, budget = 50_000, expense = 999_900)),
                january.id to listOf(summary(market, budget = 50_000, expense = 31_000)),
                february.id to listOf(summary(market, budget = 50_000, expense = 62_000)),
                march.id to listOf(summary(market, budget = 50_000, expense = 0)),
            ),
        )

        val suggestion = BudgetSuggestions.of(state, march.id).getValue(market.id)

        // Median of 280.00 and 560.00 (each scaled to 28 days) is 420.00.
        assertEquals(42_000L, suggestion.suggestedMinor)
        assertEquals(2, suggestion.basedOnPeriods)
    }

    @Test
    fun suggestions_round_up_to_a_whole_unit_and_skip_pockets_without_spending_to_go_on() {
        val state = state(
            periods = listOf(february, march),
            summaries = mapOf(
                february.id to listOf(summary(market, budget = 50_000, expense = 31_031), summary(food, budget = 20_000, expense = 0)),
                march.id to listOf(summary(market, budget = 0, expense = 0), summary(food, budget = 20_000, expense = 0), summary(gifts, budget = 0, expense = 0)),
            ),
        )

        val suggestions = BudgetSuggestions.of(state, march.id)

        // 310.31 over 31 days is 280.28 over 28 days, rounded up to 281.00.
        assertEquals(28_100L, suggestions.getValue(market.id).suggestedMinor)
        assertNull(suggestions[food.id])
        assertNull(suggestions[gifts.id])
    }

    @Test
    fun the_fit_flags_pockets_consistently_over_or_well_under_their_budget() {
        val state = state(
            summaries = mapOf(
                january.id to listOf(summary(market, budget = 10_000, expense = 12_000), summary(food, budget = 10_000, expense = 3_000), summary(gifts, budget = 10_000, expense = 12_000)),
                february.id to listOf(summary(market, budget = 10_000, expense = 15_000), summary(food, budget = 10_000, expense = 4_000), summary(gifts, budget = 10_000, expense = 8_000)),
                march.id to listOf(summary(market, budget = 10_000, expense = 0), summary(food, budget = 10_000, expense = 0), summary(gifts, budget = 10_000, expense = 0)),
            ),
        )

        val suggestions = BudgetSuggestions.of(state, march.id)

        assertEquals(BudgetFit.OVER, suggestions.getValue(market.id).fit)
        assertEquals(BudgetFit.UNDER, suggestions.getValue(food.id).fit)
        assertNull(suggestions.getValue(gifts.id).fit)
    }

    // Period recap

    @Test
    fun the_recap_summarizes_the_period_before_the_current_one() {
        val state = state(
            summaries = mapOf(
                february.id to listOf(
                    summary(market, budget = 20_000, expense = 25_000),
                    summary(food, budget = 10_000, expense = 4_000),
                    summary(gifts, budget = 10_000, expense = 9_000),
                ),
                march.id to listOf(summary(food, budget = 10_000, expense = 0, rollover = 6_000)),
            ),
            movements = listOf(
                movement("a", february, LocalDate.of(2026, 2, 1), 25_000, merchant = "Tamimi"),
                movement("b", february, LocalDate.of(2026, 2, 2), 4_000, merchant = "Jarir", pocket = food),
            ),
        )

        val recap = PeriodRecap.forCurrentPeriod(state)!!

        assertEquals(february.id, recap.insights.period.id)
        assertEquals(38_000L, recap.insights.netSpendMinor)
        assertEquals(listOf(market.id), recap.overspentPockets.map { it.pocket.id })
        assertEquals(food.id, recap.bestPocket?.pocket?.id)
        assertEquals("Tamimi", recap.topMerchant?.name)
        assertEquals(6_000L, recap.rolloverCarriedMinor)
        assertEquals(SupportedCurrency.SAR, recap.carriedCurrency)
    }

    @Test
    fun there_is_no_recap_without_a_previous_period() {
        val state = state(periods = listOf(march))

        assertNull(PeriodRecap.forCurrentPeriod(state))
        assertTrue(BudgetSuggestions.of(state, march.id).isEmpty())
    }

    private fun state(
        periods: List<Period> = listOf(december, january, february, march),
        summaries: Map<String, List<PocketPeriodSummary>> = emptyMap(),
        movements: List<Movement> = emptyList(),
    ): LedgerState {
        val withPockets = periods.associate { period ->
            period.id to (summaries[period.id] ?: listOf(summary(market, budget = 0, expense = 0), summary(food, budget = 0, expense = 0)))
        }
        return LedgerState(
            periods = periods,
            currentPeriod = march,
            pocketCatalog = listOf(market, food, gifts),
            pocketSummariesByPeriod = withPockets,
            unallocatedMinorByPeriod = periods.associate { it.id to 0L },
            movements = movements,
            elapsedDays = 9,
            totalDays = 28,
            currentLocalDate = LocalDate.of(2026, 3, 5),
        )
    }

    private fun pocket(id: String, name: String) = Pocket(id, name, PocketIconKey.forName(name), 0, false, false)

    private fun summary(pocket: Pocket, budget: Long, expense: Long, rollover: Long = 0) = PocketPeriodSummary(
        pocket = pocket,
        budgetMinor = budget,
        rolloverMinor = rollover,
        expenseMinor = expense,
        netSpendMinor = expense,
        availabilityMinor = budget + rollover - expense,
        consumedPercent = if (budget + rollover == 0L) 0 else (expense * 100 / (budget + rollover)).toInt(),
        atRisk = false,
        exhausted = false,
    )

    private fun movement(
        id: String,
        period: Period,
        date: LocalDate,
        amount: Long,
        merchant: String? = null,
        method: String? = null,
        pocket: Pocket = market,
    ) = Movement(
        id = id, pocketId = pocket.id, pocketName = pocket.name, periodId = period.id, type = MovementType.EXPENSE,
        accountingAmountMinor = amount, occurredAtUtcMillis = 0, localDate = date, zoneId = "Asia/Riyadh",
        merchant = merchant, note = null, paymentMethodId = method, paymentMethodName = method,
        originalAmountMinor = null, originalCurrencyCode = period.accountingCurrency.name,
        conversionStatus = ConversionStatus.CONFIRMED, rate = null, conversionEffectiveDate = null, conversionSource = null,
    )
}
