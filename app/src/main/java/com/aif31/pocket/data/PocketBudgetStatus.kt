package com.aif31.pocket.data

/** A Pocket's budget state for one period, derived from the ledger's 80%/100% consumption flags. */
enum class PocketBudgetStatus { EXHAUSTED, AT_RISK, UNBUDGETED, ON_TRACK }

val PocketPeriodSummary.budgetStatus: PocketBudgetStatus
    get() = when {
        exhausted -> PocketBudgetStatus.EXHAUSTED
        atRisk -> PocketBudgetStatus.AT_RISK
        budgetMinor <= 0L -> PocketBudgetStatus.UNBUDGETED
        else -> PocketBudgetStatus.ON_TRACK
    }
