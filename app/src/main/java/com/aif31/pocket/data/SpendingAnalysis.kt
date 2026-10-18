package com.aif31.pocket.data

import com.aif31.pocket.domain.PocketMath
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.domain.sumMoneyExact
import java.time.DayOfWeek

/** Net spending at one merchant within a period, merchants matched the same way merchant memory matches them. */
data class MerchantSpend(
    /** The normalized merchant, as [merchantKey] returns it. */
    val key: String,
    /** The merchant as written on its most recent Movement. */
    val name: String,
    val netSpendMinor: Long,
    val expenseCount: Int,
    val averageExpenseMinor: Long?,
    /** Net spend at this merchant in the previous period; null when that period is missing or uses another currency. */
    val previousNetSpendMinor: Long?,
)

/** Net spending dated on one weekday, over the days of the period that have passed. */
data class WeekdaySpend(
    val dayOfWeek: DayOfWeek,
    val netSpendMinor: Long,
    /** How many times this weekday has occurred in the period so far. */
    val dayCount: Int,
    val averagePerDayMinor: Long?,
)

data class PaymentMethodSpend(
    /** Null groups Movements recorded without a payment method. */
    val id: String?,
    val name: String?,
    val netSpendMinor: Long,
    val movementCount: Int,
    /** Share of the period's net spend; null when the period has no positive net spend. */
    val sharePercent: Int?,
)

/** Movements entered in one currency other than the period's accounting currency. */
data class ForeignCurrencySpend(
    val currency: SupportedCurrency,
    val originalNetMinor: Long,
    val accountingNetMinor: Long,
    val movementCount: Int,
    /** Movements whose accounting amount is still an estimate awaiting the final rate of their date. */
    val estimatedCount: Int,
    val estimatedAccountingNetMinor: Long,
)

/** A Pocket's average daily spending in each [SpendingAnalysis.trend] period, aligned with that list. */
data class PocketTrend(val pocket: Pocket, val averageDailyByPeriodMinor: List<Long?>)

/** Rollover a Pocket received in each [SpendingAnalysis.trend] period, aligned with that list. */
data class PocketRolloverTrend(
    val pocket: Pocket,
    val rolloverByPeriodMinor: List<Long>,
    /** Positive availability the Pocket carries into the next period, or would if the period ended today. */
    val carriesForwardMinor: Long,
)

/**
 * Read-only breakdowns of one budget period, derived from the same ledger state and Movements as [PeriodInsights]
 * so they add up to the figures every other screen reports. Amounts are in the period's accounting currency.
 */
data class SpendingAnalysis(
    val insights: PeriodInsights,
    /** Largest net spend first. */
    val merchants: List<MerchantSpend>,
    /** Net spend of Movements recorded without a merchant. */
    val unnamedNetSpendMinor: Long,
    /** Monday to Sunday; only Movements dated through today count. */
    val weekdays: List<WeekdaySpend>,
    /** Net spend dated on each elapsed day, starting with the period's first day. */
    val dailyNetSpendMinor: List<Long>,
    /** Largest net spend first. */
    val paymentMethods: List<PaymentMethodSpend>,
    /** Largest accounting amount first. */
    val foreignCurrencies: List<ForeignCurrencySpend>,
    /** Share of net spend entered in a foreign currency; null when the period has no positive net spend. */
    val foreignSharePercent: Int?,
    /**
     * Up to [TREND_PERIODS] consecutive periods ending with this one, oldest first. The trend stops at a change of
     * accounting currency because amounts on either side are not comparable without converting them.
     */
    val trend: List<PeriodInsights>,
    val earlierPeriodsInOtherCurrency: Boolean,
    /** Pockets with spending in any trend period, largest net spend in this period first. */
    val pocketTrends: List<PocketTrend>,
    /** Pockets opted into rollover now or that received rollover in a trend period. */
    val rollover: List<PocketRolloverTrend>,
) {
    val period: Period get() = insights.period

    companion object {
        const val TREND_PERIODS = 12

        fun of(state: LedgerState, periodId: String): SpendingAnalysis? {
            val insights = PeriodInsights.of(state, periodId) ?: return null
            val period = insights.period
            val movements = state.periodMovements(periodId)
            // Shares are of the same Movements being broken down, so each breakdown adds up to 100%.
            val netSpend = movements.netSpendMinor()
            val (trend, stoppedAtCurrency) = trend(state, period)
            val foreign = foreignCurrencies(movements, period.accountingCurrency)
            return SpendingAnalysis(
                insights = insights,
                merchants = merchants(state, period, movements),
                unnamedNetSpendMinor = movements.filter { merchantKey(it.merchant) == null }.netSpendMinor(),
                weekdays = weekdays(period, insights.elapsedDays, movements),
                dailyNetSpendMinor = dailyNetSpend(period, insights.elapsedDays, movements),
                paymentMethods = paymentMethods(movements, netSpend),
                foreignCurrencies = foreign,
                foreignSharePercent = shareOf(foreign.map { it.accountingNetMinor }.sumMoneyExact(), netSpend),
                trend = trend,
                earlierPeriodsInOtherCurrency = stoppedAtCurrency,
                pocketTrends = pocketTrends(state, trend),
                rollover = rollover(state, trend),
            )
        }

        private fun shareOf(partMinor: Long, wholeMinor: Long): Int? =
            wholeMinor.takeIf { it > 0 }?.let { PocketMath.percent(partMinor, it) }

        private fun merchants(state: LedgerState, period: Period, movements: List<Movement>): List<MerchantSpend> {
            val previous = PeriodComparison.previousPeriodId(state, period.id)
                ?.let { id -> state.periods.first { it.id == id } }
                ?.takeIf { it.accountingCurrency == period.accountingCurrency }
            val previousByMerchant = previous?.let { prior ->
                state.periodMovements(prior.id).groupBy { merchantKey(it.merchant) }.mapValues { it.value.netSpendMinor() }
            }
            return movements.groupBy { merchantKey(it.merchant) }
                .mapNotNull { (key, group) ->
                    key ?: return@mapNotNull null
                    val expenses = group.filter { it.type == MovementType.EXPENSE }
                    MerchantSpend(
                        key = key,
                        name = group.maxBy { it.occurredAtUtcMillis }.merchant!!.trim(),
                        netSpendMinor = group.netSpendMinor(),
                        expenseCount = expenses.size,
                        averageExpenseMinor = expenses.takeIf { it.isNotEmpty() }
                            ?.let { it.map(Movement::accountingAmountMinor).sumMoneyExact() / it.size },
                        previousNetSpendMinor = previousByMerchant?.let { it[key] ?: 0 },
                    )
                }
                .sortedByDescending { it.netSpendMinor }
        }

        private fun elapsedDates(period: Period, elapsedDays: Int) = (0 until elapsedDays).map { period.start.plusDays(it.toLong()) }

        private fun weekdays(period: Period, elapsedDays: Int, movements: List<Movement>): List<WeekdaySpend> {
            val dates = elapsedDates(period, elapsedDays)
            val counted = dates.toSet()
            val netByWeekday = movements.filter { it.localDate in counted }
                .groupBy { it.localDate.dayOfWeek }
                .mapValues { it.value.netSpendMinor() }
            val daysByWeekday = dates.groupingBy { it.dayOfWeek }.eachCount()
            return DayOfWeek.entries.map { day ->
                val net = netByWeekday[day] ?: 0
                val count = daysByWeekday[day] ?: 0
                WeekdaySpend(day, net, count, count.takeIf { it > 0 }?.let { net / it })
            }
        }

        private fun dailyNetSpend(period: Period, elapsedDays: Int, movements: List<Movement>): List<Long> {
            val byDate = movements.groupBy { it.localDate }
            return elapsedDates(period, elapsedDays).map { byDate[it].orEmpty().netSpendMinor() }
        }

        private fun paymentMethods(movements: List<Movement>, netSpend: Long): List<PaymentMethodSpend> =
            movements.groupBy { it.paymentMethodId }
                .map { (id, group) ->
                    val net = group.netSpendMinor()
                    PaymentMethodSpend(
                        id = id,
                        name = group.firstNotNullOfOrNull { it.paymentMethodName },
                        netSpendMinor = net,
                        movementCount = group.size,
                        sharePercent = shareOf(net, netSpend),
                    )
                }
                .sortedByDescending { it.netSpendMinor }

        private fun foreignCurrencies(movements: List<Movement>, accountingCurrency: SupportedCurrency): List<ForeignCurrencySpend> =
            movements.filter { it.originalCurrencyCode != accountingCurrency.name }
                .groupBy { it.originalCurrencyCode }
                .mapNotNull { (code, group) ->
                    val currency = SupportedCurrency.entries.firstOrNull { it.name == code } ?: return@mapNotNull null
                    val estimated = group.filter { it.conversionStatus == ConversionStatus.ESTIMATED }
                    ForeignCurrencySpend(
                        currency = currency,
                        originalNetMinor = group.map { it.signed(it.originalAmountMinor ?: it.accountingAmountMinor) }.sumMoneyExact(),
                        accountingNetMinor = group.netSpendMinor(),
                        movementCount = group.size,
                        estimatedCount = estimated.size,
                        estimatedAccountingNetMinor = estimated.netSpendMinor(),
                    )
                }
                .sortedByDescending { it.accountingNetMinor }

        private fun Movement.signed(amountMinor: Long) = if (type == MovementType.REFUND) -amountMinor else amountMinor

        private fun trend(state: LedgerState, period: Period): Pair<List<PeriodInsights>, Boolean> {
            val earlier = state.periods.filter { it.start <= period.start }.sortedByDescending { it.start }
            val sameCurrency = earlier.takeWhile { it.accountingCurrency == period.accountingCurrency }
            val included = sameCurrency.take(TREND_PERIODS)
            val stoppedAtCurrency = included.size == sameCurrency.size && sameCurrency.size < earlier.size
            return included.reversed().mapNotNull { PeriodInsights.of(state, it.id) } to stoppedAtCurrency
        }

        private fun pocketIn(state: LedgerState, pocket: Pocket) = state.pocketCatalog.firstOrNull { it.id == pocket.id } ?: pocket

        private fun pocketTrends(state: LedgerState, trend: List<PeriodInsights>): List<PocketTrend> {
            val selected = trend.lastOrNull() ?: return emptyList()
            return trend.flatMap { it.pockets }
                .filter { it.netSpendMinor != 0L }
                .map { it.pocket }
                .distinctBy { it.id }
                .sortedByDescending { pocket -> selected.pockets.firstOrNull { it.pocket.id == pocket.id }?.netSpendMinor ?: 0 }
                .map { pocket ->
                    PocketTrend(
                        pocket = pocketIn(state, pocket),
                        averageDailyByPeriodMinor = trend.map { insights ->
                            insights.elapsedDays.takeIf { it > 0 }?.let { insights.pocketNetSpendThroughTodayMinor(pocket.id) / it }
                        },
                    )
                }
        }

        private fun rollover(state: LedgerState, trend: List<PeriodInsights>): List<PocketRolloverTrend> {
            val selected = trend.lastOrNull() ?: return emptyList()
            val optedIn = state.pocketCatalog.filter { it.rolloverEnabled && !it.archived }
            val received = trend.flatMap { it.pockets }.filter { it.rolloverMinor > 0 }.map { it.pocket }
            return (optedIn + received).distinctBy { it.id }.map { pocket ->
                val current = selected.pockets.firstOrNull { it.pocket.id == pocket.id }
                PocketRolloverTrend(
                    pocket = pocketIn(state, pocket),
                    rolloverByPeriodMinor = trend.map { insights ->
                        insights.pockets.firstOrNull { it.pocket.id == pocket.id }?.rolloverMinor ?: 0
                    },
                    carriesForwardMinor = current
                        ?.takeIf { it.rolloverEligible && !it.retiredThisPeriod }
                        ?.availabilityMinor?.coerceAtLeast(0) ?: 0,
                )
            }
        }
    }
}
