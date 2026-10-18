package com.aif31.pocket.data

/**
 * A Pocket's budget state for one period, derived from the ledger's 80%/100% consumption flags. [FAST_PACE] comes
 * from the period's pace instead: see [budgetStatusAt].
 */
enum class PocketBudgetStatus { EXHAUSTED, AT_RISK, FAST_PACE, UNBUDGETED, ON_TRACK }

val PocketPeriodSummary.budgetStatus: PocketBudgetStatus
    get() = when {
        exhausted -> PocketBudgetStatus.EXHAUSTED
        atRisk -> PocketBudgetStatus.AT_RISK
        budgetMinor <= 0L -> PocketBudgetStatus.UNBUDGETED
        else -> PocketBudgetStatus.ON_TRACK
    }

/**
 * [budgetStatus], except that a Pocket forecast to run out before the period ends is [PocketBudgetStatus.FAST_PACE]
 * unless the ledger already flags it at risk or exhausted. That includes a Pocket funded only by rollover, which has no
 * budget of its own but can still run out.
 */
fun PocketPeriodSummary.budgetStatusAt(insights: PeriodInsights?): PocketBudgetStatus {
    val status = budgetStatus
    val runsOut = insights?.runOutDateByPocket?.containsKey(pocket.id) == true
    val canBeFaster = status == PocketBudgetStatus.ON_TRACK || status == PocketBudgetStatus.UNBUDGETED
    return if (canBeFaster && runsOut) PocketBudgetStatus.FAST_PACE else status
}
