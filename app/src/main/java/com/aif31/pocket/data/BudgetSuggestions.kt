package com.aif31.pocket.data

import java.math.BigDecimal
import java.math.RoundingMode

/** How a Pocket's spending has compared with its budget in every recent period. */
enum class BudgetFit { OVER, UNDER }

data class BudgetSuggestion(
    val pocketId: String,
    /** Rounded up to a whole currency unit. */
    val suggestedMinor: Long,
    val basedOnPeriods: Int,
    /** Set only when at least two recent periods agree. */
    val fit: BudgetFit?,
)

/**
 * Suggests each Pocket's budget from what it actually spent. Recent periods are those that have ended, up to
 * [LOOKBACK], in the target period's accounting currency and without crossing a currency change. Each period's net
 * spend is scaled to the target period's length, so a long transition period counts by its daily pace, and the median
 * is suggested. A Pocket with no spending to go on gets no suggestion rather than zero, so applying suggestions never
 * empties a budget kept for occasional or emergency spending.
 */
object BudgetSuggestions {
    const val LOOKBACK = 3

    fun of(state: LedgerState, periodId: String): Map<String, BudgetSuggestion> {
        val target = state.periods.firstOrNull { it.id == periodId } ?: return emptyMap()
        val targetDays = days(target)
        val recent = state.periods
            .filter { it.start < target.start }
            .sortedByDescending { it.start }
            .takeWhile { it.accountingCurrency == target.accountingCurrency }
            .filter { it.endExclusive <= state.currentLocalDate }
            .take(LOOKBACK)
        val targetPockets = state.pocketSummariesByPeriod[periodId].orEmpty()
            .filterNot { it.pocket.archived || it.retiredThisPeriod }
        return targetPockets.mapNotNull { current ->
            val history = recent.mapNotNull { period ->
                state.pocketSummariesByPeriod[period.id].orEmpty().firstOrNull { it.pocket.id == current.pocket.id }?.let { period to it }
            }
            if (history.isEmpty()) return@mapNotNull null
            val scaled = history.map { (period, summary) -> scale(summary.netSpendMinor.coerceAtLeast(0), targetDays, days(period)) }.sorted()
            val median = if (scaled.size % 2 == 1) {
                scaled[scaled.size / 2]
            } else {
                Math.addExact(scaled[scaled.size / 2 - 1], scaled[scaled.size / 2]) / 2
            }
            if (median <= 0) return@mapNotNull null
            BudgetSuggestion(
                pocketId = current.pocket.id,
                suggestedMinor = roundUpToUnit(median),
                basedOnPeriods = history.size,
                fit = fit(history.map { it.second }),
            )
        }.associateBy { it.pocketId }
    }

    private fun days(period: Period) = period.endExclusive.toEpochDay() - period.start.toEpochDay()

    private fun scale(amountMinor: Long, toDays: Long, fromDays: Long): Long =
        // BigDecimal rather than BigInteger: BigInteger.longValueExact needs API 31.
        BigDecimal.valueOf(amountMinor).multiply(BigDecimal.valueOf(toDays))
            .divide(BigDecimal.valueOf(fromDays), 0, RoundingMode.DOWN)
            .longValueExact()

    private fun roundUpToUnit(minor: Long): Long = Math.multiplyExact(Math.floorDiv(Math.addExact(minor, 99), 100), 100)

    private fun fit(history: List<PocketPeriodSummary>): BudgetFit? {
        if (history.size < 2) return null
        fun plan(summary: PocketPeriodSummary) = Math.addExact(summary.budgetMinor, summary.rolloverMinor)
        return when {
            history.all { it.availabilityMinor < 0 } -> BudgetFit.OVER
            history.all { plan(it) > 0 && Math.multiplyExact(it.netSpendMinor, 2) < plan(it) } -> BudgetFit.UNDER
            else -> null
        }
    }
}
