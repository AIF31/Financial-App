package com.aif31.pocket.data

import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.domain.sumMoneyExact

/** How the period before the current one went, shown once the new period begins. */
data class PeriodRecap(
    val insights: PeriodInsights,
    /** Pockets that ended below zero, the furthest below first. */
    val overspentPockets: List<PocketPeriodSummary>,
    /** The budgeted Pocket that used the smallest share of its budget without going over. */
    val bestPocket: PocketPeriodSummary?,
    val topMerchant: MerchantSpend?,
    /** Rollover the current period received, in its own currency. */
    val rolloverCarriedMinor: Long,
    val carriedCurrency: SupportedCurrency,
) {
    companion object {
        fun forCurrentPeriod(state: LedgerState): PeriodRecap? {
            val current = state.currentPeriod ?: return null
            val previousId = PeriodComparison.previousPeriodId(state, current.id) ?: return null
            val analysis = SpendingAnalysis.of(state, previousId) ?: return null
            val pockets = analysis.insights.pockets
            return PeriodRecap(
                insights = analysis.insights,
                overspentPockets = pockets.filter { it.availabilityMinor < 0 }.sortedBy { it.availabilityMinor },
                bestPocket = pockets.filter { it.budgetMinor > 0 && it.availabilityMinor >= 0 }.minByOrNull { it.consumedPercent },
                topMerchant = analysis.merchants.firstOrNull()?.takeIf { it.netSpendMinor > 0 },
                rolloverCarriedMinor = state.pocketSummariesByPeriod[current.id].orEmpty().map { it.rolloverMinor }.sumMoneyExact(),
                carriedCurrency = current.accountingCurrency,
            )
        }
    }
}
