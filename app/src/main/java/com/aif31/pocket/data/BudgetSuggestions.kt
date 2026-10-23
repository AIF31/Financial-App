package com.aif31.pocket.data

import com.aif31.pocket.domain.sumMoneyExact
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

internal data class SuggestedBudgetPlan(
    val changes: List<Pair<PocketPeriodSummary, BudgetSuggestion>>,
    val totalMinor: Long?,
    val canApply: Boolean,
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

    internal fun plan(state: LedgerState, periodId: String): SuggestedBudgetPlan {
        val suggestions = of(state, periodId)
        val summaries = state.pocketSummariesByPeriod[periodId].orEmpty()
        val changes = summaries.filterNot { it.pocket.archived || it.retiredThisPeriod }.mapNotNull { summary ->
            suggestions[summary.pocket.id]?.takeIf { it.suggestedMinor != summary.budgetMinor }?.let { summary to it }
        }
        val replacements = changes.associate { (summary, suggestion) -> summary.pocket.id to suggestion.suggestedMinor }
        val total = runCatching {
            summaries.map { replacements[it.pocket.id] ?: it.budgetMinor }.sumMoneyExact()
        }.getOrNull()
        val newFunds = state.periods.firstOrNull { it.id == periodId }?.newFundsMinor ?: 0
        return SuggestedBudgetPlan(changes, total, changes.isNotEmpty() && total != null && total <= newFunds)
    }

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
            // A period whose refunds exceeded its spending needed no budget, not a negative one, so it counts as zero
            // rather than pulling the median below what the other periods actually needed.
            val scaled = history.map { (period, summary) -> scale(summary.netSpendMinor.coerceAtLeast(0), targetDays, days(period)) }.sorted()
            val median = if (scaled.size % 2 == 1) {
                scaled[scaled.size / 2]
            } else {
                scaled[scaled.size / 2 - 1].add(scaled[scaled.size / 2]).divide(TWO)
            }
            if (median.signum() <= 0) return@mapNotNull null
            BudgetSuggestion(
                pocketId = current.pocket.id,
                suggestedMinor = roundUpToUnit(median),
                basedOnPeriods = history.size,
                fit = fit(history.map { it.second }),
            )
        }.associateBy { it.pocketId }
    }

    private fun days(period: Period) = period.endExclusive.toEpochDay() - period.start.toEpochDay()

    private val TWO = BigDecimal.valueOf(2)

    /** Kept exact (to well below a minor unit) so the only rounding is [roundUpToUnit]'s single ceiling. */
    private fun scale(amountMinor: Long, toDays: Long, fromDays: Long): BigDecimal =
        BigDecimal.valueOf(amountMinor).multiply(BigDecimal.valueOf(toDays)).divide(BigDecimal.valueOf(fromDays), 12, RoundingMode.CEILING)

    // BigDecimal rather than BigInteger: BigInteger.longValueExact needs API 31.
    private fun roundUpToUnit(minor: BigDecimal): Long =
        minor.divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING).multiply(BigDecimal.valueOf(100)).longValueExact()

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
