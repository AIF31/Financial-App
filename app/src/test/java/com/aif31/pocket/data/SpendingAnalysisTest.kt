package com.aif31.pocket.data

import com.aif31.pocket.domain.SupportedCurrency
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendingAnalysisTest {
    private val january = Period("p0", LocalDate.of(2025, 12, 25), LocalDate.of(2026, 1, 25), 30_000, 25, accountingCurrency = SupportedCurrency.MXN)
    private val february = Period("p1", LocalDate.of(2026, 1, 25), LocalDate.of(2026, 2, 25), 30_000, 25)
    private val march = Period("p2", LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 25), 30_000, 25)
    private val market = pocket("market", "Supermercado")
    private val travel = pocket("travel", "Viajes", rollover = true)

    @Test
    fun merchants_are_grouped_by_normalized_name_and_compared_with_the_previous_period() {
        val state = state(
            movements = listOf(
                movement("a", march, LocalDate.of(2026, 2, 26), 2_000, merchant = "Tamimi", occurredAt = 1),
                movement("b", march, LocalDate.of(2026, 3, 1), 4_000, merchant = "TAMIMI.", occurredAt = 2),
                movement("c", march, LocalDate.of(2026, 3, 2), 500, MovementType.REFUND, merchant = "tamimi", occurredAt = 3),
                movement("d", march, LocalDate.of(2026, 3, 3), 9_000, merchant = "Jarir"),
                movement("e", march, LocalDate.of(2026, 3, 3), 700),
                movement("f", february, LocalDate.of(2026, 2, 1), 4_000, merchant = "Tamimi"),
            ),
        )

        val analysis = SpendingAnalysis.of(state, march.id)!!

        assertEquals(listOf("Jarir", "tamimi"), analysis.merchants.map { it.name })
        val tamimi = analysis.merchants[1]
        assertEquals(5_500L, tamimi.netSpendMinor)
        assertEquals(2, tamimi.expenseCount)
        assertEquals(3_000L, tamimi.averageExpenseMinor)
        assertEquals(4_000L, tamimi.previousNetSpendMinor)
        assertEquals(0L, analysis.merchants[0].previousNetSpendMinor)
        assertEquals(700L, analysis.unnamedNetSpendMinor)
    }

    @Test
    fun merchants_have_no_previous_figure_when_the_previous_period_uses_another_currency() {
        val state = state(
            movements = listOf(
                movement("a", february, LocalDate.of(2026, 2, 1), 1_000, merchant = "Tamimi"),
                movement("b", january, LocalDate.of(2026, 1, 1), 1_000, merchant = "Tamimi"),
            ),
        )

        assertNull(SpendingAnalysis.of(state, february.id)!!.merchants.single().previousNetSpendMinor)
    }

    @Test
    fun weekdays_average_spending_dated_through_today_over_the_days_that_have_passed() {
        // 25 Feb 2026 is a Wednesday; through 5 March is nine days, so two Wednesdays and two Thursdays.
        val state = state(
            movements = listOf(
                movement("a", march, LocalDate.of(2026, 2, 25), 1_000),
                movement("b", march, LocalDate.of(2026, 3, 4), 3_000),
                movement("c", march, LocalDate.of(2026, 3, 1), 600),
                movement("later", march, LocalDate.of(2026, 3, 18), 9_000),
            ),
        )

        val analysis = SpendingAnalysis.of(state, march.id)!!

        assertEquals(DayOfWeek.entries, analysis.weekdays.map { it.dayOfWeek })
        val wednesday = analysis.weekdays.single { it.dayOfWeek == DayOfWeek.WEDNESDAY }
        assertEquals(4_000L, wednesday.netSpendMinor)
        assertEquals(2, wednesday.dayCount)
        assertEquals(2_000L, wednesday.averagePerDayMinor)
        val sunday = analysis.weekdays.single { it.dayOfWeek == DayOfWeek.SUNDAY }
        assertEquals(1, sunday.dayCount)
        assertEquals(600L, sunday.averagePerDayMinor)
        assertEquals(listOf(1_000L, 0L, 0L, 0L, 600L, 0L, 0L, 3_000L, 0L), analysis.dailyNetSpendMinor)
    }

    @Test
    fun payment_methods_split_net_spending_with_their_share() {
        val state = state(
            movements = listOf(
                movement("a", march, LocalDate.of(2026, 2, 26), 3_000, method = "card" to "Tarjeta"),
                movement("b", march, LocalDate.of(2026, 2, 27), 1_000, MovementType.REFUND, method = "card" to "Tarjeta"),
                movement("c", march, LocalDate.of(2026, 2, 28), 6_000),
                movement("d", march, LocalDate.of(2026, 3, 1), 2_000, method = "cash" to "Efectivo"),
            ),
        )

        val methods = SpendingAnalysis.of(state, march.id)!!.paymentMethods

        assertEquals(listOf(null, "card", "cash"), methods.map { it.id })
        assertEquals(listOf(null, "Tarjeta", "Efectivo"), methods.map { it.name })
        assertEquals(listOf(6_000L, 2_000L, 2_000L), methods.map { it.netSpendMinor })
        assertEquals(listOf(60, 20, 20), methods.map { it.sharePercent })
        assertEquals(listOf(1, 2, 1), methods.map { it.movementCount })
    }

    @Test
    fun foreign_currency_spending_reports_original_and_accounting_amounts_and_pending_estimates() {
        val state = state(
            movements = listOf(
                movement("a", march, LocalDate.of(2026, 2, 26), 1_000, original = 5_000L to SupportedCurrency.MXN),
                movement("b", march, LocalDate.of(2026, 2, 27), 2_000, original = 10_000L to SupportedCurrency.MXN, estimated = true),
                movement("c", march, LocalDate.of(2026, 2, 28), 375, original = 100L to SupportedCurrency.USD),
                movement("d", march, LocalDate.of(2026, 3, 1), 6_625),
            ),
        )

        val analysis = SpendingAnalysis.of(state, march.id)!!

        assertEquals(listOf(SupportedCurrency.MXN, SupportedCurrency.USD), analysis.foreignCurrencies.map { it.currency })
        val mxn = analysis.foreignCurrencies.first()
        assertEquals(15_000L, mxn.originalNetMinor)
        assertEquals(3_000L, mxn.accountingNetMinor)
        assertEquals(2, mxn.movementCount)
        assertEquals(1, mxn.estimatedCount)
        assertEquals(2_000L, mxn.estimatedAccountingNetMinor)
        assertEquals(33, analysis.foreignSharePercent)
    }

    @Test
    fun the_trend_covers_earlier_periods_in_the_same_currency_and_marks_where_it_stops() {
        val state = state(
            summaries = mapOf(
                january.id to listOf(summary(market, expense = 9_300)),
                february.id to listOf(summary(market, expense = 6_200), summary(travel, expense = 3_100, rollover = 1_000)),
                march.id to listOf(summary(market, expense = 1_800), summary(travel, expense = 0, rollover = 2_000, eligible = true)),
            ),
        )

        val analysis = SpendingAnalysis.of(state, march.id)!!

        assertEquals(listOf(february.id, march.id), analysis.trend.map { it.period.id })
        assertTrue(analysis.earlierPeriodsInOtherCurrency)
        assertEquals(listOf(300L, 200L), analysis.trend.map { it.averageDailySpendMinor })
        assertEquals(listOf(market.id, travel.id), analysis.pocketTrends.map { it.pocket.id })
        assertEquals(listOf(200L, 200L), analysis.pocketTrends.first().averageDailyByPeriodMinor)
        assertEquals(listOf(100L, 0L), analysis.pocketTrends.last().averageDailyByPeriodMinor)
    }

    @Test
    fun a_pocket_of_this_period_without_spending_is_still_listed_after_the_others() {
        val state = state(
            summaries = mapOf(
                february.id to listOf(summary(market, expense = 6_200)),
                march.id to listOf(summary(travel, expense = 0), summary(market, expense = 1_800)),
            ),
        )

        val trends = SpendingAnalysis.of(state, march.id)!!.pocketTrends

        assertEquals(listOf(market.id, travel.id), trends.map { it.pocket.id })
        assertEquals(listOf(0L, 0L), trends.last().averageDailyByPeriodMinor)
    }

    @Test
    fun rollover_history_lists_received_rollover_and_what_would_carry_forward() {
        val state = state(
            summaries = mapOf(
                february.id to listOf(summary(market, expense = 6_200), summary(travel, expense = 3_100, rollover = 1_000)),
                march.id to listOf(
                    summary(market, budget = 5_000, expense = 1_800),
                    summary(travel, budget = 4_000, expense = 500, rollover = 2_000, eligible = true),
                ),
            ),
        )

        val rollover = SpendingAnalysis.of(state, march.id)!!.rollover

        val travelRollover = rollover.single()
        assertEquals(travel.id, travelRollover.pocket.id)
        assertEquals(listOf(1_000L, 2_000L), travelRollover.rolloverByPeriodMinor)
        assertEquals(5_500L, travelRollover.carriesForwardMinor)
    }

    @Test
    fun a_pocket_that_carries_money_forward_stays_in_the_rollover_history_after_opting_out() {
        // Supermercado is not opted in any more, but this period's snapshot still carries its availability.
        val state = state(
            summaries = mapOf(
                february.id to listOf(summary(market, expense = 0)),
                march.id to listOf(summary(market, budget = 1_000, expense = 0, eligible = true)),
            ),
        )

        val rollover = SpendingAnalysis.of(state, march.id)!!.rollover

        assertEquals(1_000L, rollover.single { it.pocket.id == market.id }.carriesForwardMinor)
    }

    @Test
    fun the_typical_earlier_pace_is_a_true_median() {
        assertEquals(200L, medianMinor(listOf(300L, 100L)))
        assertEquals(300L, medianMinor(listOf(500L, 100L, 300L)))
        assertEquals(Long.MAX_VALUE - 1, medianMinor(listOf(Long.MAX_VALUE, Long.MAX_VALUE - 2)))
        assertNull(medianMinor(emptyList()))
    }

    @Test
    fun an_unknown_period_has_no_analysis_and_an_empty_period_reports_nothing() {
        assertNull(SpendingAnalysis.of(state(), "missing"))
        val empty = SpendingAnalysis.of(state(), march.id)!!

        assertTrue(empty.merchants.isEmpty())
        assertTrue(empty.paymentMethods.isEmpty())
        assertTrue(empty.foreignCurrencies.isEmpty())
        assertNull(empty.foreignSharePercent)
        // The period's Pockets are still listed, with no spending, so the screen can name them.
        assertTrue(empty.pocketTrends.all { trend -> trend.averageDailyByPeriodMinor.all { it == 0L } })
        assertTrue(empty.rollover.single().rolloverByPeriodMinor.all { it == 0L })
    }

    private fun state(
        summaries: Map<String, List<PocketPeriodSummary>> = emptyMap(),
        movements: List<Movement> = emptyList(),
    ): LedgerState {
        val periods = listOf(january, february, march)
        // Movements count only where their Pocket is in the period's snapshot, as in the ledger.
        val withPockets = periods.associate { period ->
            period.id to (summaries[period.id] ?: listOf(summary(market, expense = 0), summary(travel, expense = 0)))
        }
        return LedgerState(
            periods = periods,
            currentPeriod = march,
            pocketCatalog = listOf(market, travel),
            pocketSummariesByPeriod = withPockets,
            unallocatedMinorByPeriod = periods.associate { it.id to 0L },
            movements = movements,
            elapsedDays = 9,
            totalDays = 28,
            currentLocalDate = LocalDate.of(2026, 3, 5),
        )
    }

    private fun pocket(id: String, name: String, rollover: Boolean = false) =
        Pocket(id, name, PocketIconKey.forName(name), 0, false, rollover)

    private fun summary(pocket: Pocket, expense: Long, budget: Long = 0, rollover: Long = 0, eligible: Boolean = false) = PocketPeriodSummary(
        pocket = pocket,
        budgetMinor = budget,
        rolloverMinor = rollover,
        rolloverEligible = eligible,
        expenseMinor = expense,
        netSpendMinor = expense,
        availabilityMinor = budget + rollover - expense,
        consumedPercent = 0,
        atRisk = false,
        exhausted = false,
    )

    private fun movement(
        id: String,
        period: Period,
        date: LocalDate,
        amount: Long,
        type: MovementType = MovementType.EXPENSE,
        merchant: String? = null,
        method: Pair<String, String>? = null,
        original: Pair<Long, SupportedCurrency>? = null,
        estimated: Boolean = false,
        occurredAt: Long = 0,
    ) = Movement(
        id = id, pocketId = market.id, pocketName = market.name, periodId = period.id, type = type,
        accountingAmountMinor = amount, occurredAtUtcMillis = occurredAt, localDate = date, zoneId = "Asia/Riyadh",
        merchant = merchant, note = null, paymentMethodId = method?.first, paymentMethodName = method?.second,
        originalAmountMinor = original?.first, originalCurrencyCode = (original?.second ?: period.accountingCurrency).name,
        conversionStatus = if (estimated) ConversionStatus.ESTIMATED else ConversionStatus.CONFIRMED,
        rate = null, conversionEffectiveDate = null, conversionSource = null,
    )
}
