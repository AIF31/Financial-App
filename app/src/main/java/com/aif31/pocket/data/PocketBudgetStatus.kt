package com.aif31.pocket.data

/**
 * A Pocket's budget state for one period: see [budgetStatusAt]. [AT_RISK] needs both a consumption of at least 80% and
 * a pace that runs out before the period ends. [UNCONFIRMED_PACE] is a pace that would run out but is not at risk yet.
 * [SMALL_BUDGET] is a budget too small to rate until it is at risk, so it shows no status.
 */
enum class PocketBudgetStatus { EXHAUSTED, AT_RISK, UNCONFIRMED_PACE, UNBUDGETED, ON_TRACK, SMALL_BUDGET }

/**
 * The Pocket's status in [insights]' period. [PocketBudgetStatus.AT_RISK] is a Pocket in
 * [PeriodInsights.runOutDateByPocket]; consumption alone never puts a Pocket at risk, since what is left may still
 * cover its pace. Without insights there is no pace, so only the ledger's exhausted flag and the budget count.
 */
fun PocketPeriodSummary.budgetStatusAt(insights: PeriodInsights?): PocketBudgetStatus = when {
    exhausted -> PocketBudgetStatus.EXHAUSTED
    insights != null && pocket.id in insights.runOutDateByPocket -> PocketBudgetStatus.AT_RISK
    insights != null && insights.hasSmallBudget(this) -> PocketBudgetStatus.SMALL_BUDGET
    insights != null && pocket.id in insights.unconfirmedRunOutPocketIds -> PocketBudgetStatus.UNCONFIRMED_PACE
    budgetMinor <= 0L -> PocketBudgetStatus.UNBUDGETED
    else -> PocketBudgetStatus.ON_TRACK
}
