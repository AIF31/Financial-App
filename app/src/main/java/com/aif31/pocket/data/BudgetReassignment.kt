package com.aif31.pocket.data

import java.math.BigDecimal
import java.math.RoundingMode

/** A null Pocket ID denotes unassigned funds. */
data class BudgetReassignmentSource(val pocketId: String?, val name: String, val maximumMinor: Long)

data class BudgetReassignmentOptions(val neededMinor: Long?, val sources: List<BudgetReassignmentSource>)

/** Budget reassignment changes allocations together, without creating a Movement or reassigning rollover. */
object BudgetReassignment {
    fun of(state: LedgerState, periodId: String, targetPocketId: String): BudgetReassignmentOptions? {
        if (state.currentPeriod?.id != periodId) return null
        val pockets = state.pocketSummariesByPeriod[periodId].orEmpty()
        val target = pockets.firstOrNull { it.pocket.id == targetPocketId } ?: return null
        if (target.pocket.archived || target.retiredThisPeriod) return null
        val insights = runCatching { PeriodInsights.of(state, periodId) }.getOrNull()
        if (target.availabilityMinor >= 0 && target.budgetStatusAt(insights) != PocketBudgetStatus.AT_RISK) return null
        val needed = if (target.availabilityMinor < 0) {
            runCatching { Math.negateExact(target.availabilityMinor) }.getOrNull()
        } else {
            insights?.let { forecastShortfall(target, it) }
        }
        val donors = if (insights == null) emptyList() else pockets
            .filterNot { it.pocket.id == targetPocketId || it.pocket.archived || it.retiredThisPeriod }
            .filter { it.availabilityMinor > 0 && it.budgetMinor > 0 }
            // Nor can a Pocket spare budget when it has little left or its pace would run out, even one too small to rate.
            .filterNot { it.atRisk || it.pocket.id in insights.runOutDateByPocket || it.pocket.id in insights.unconfirmedRunOutPocketIds }
            .map { BudgetReassignmentSource(it.pocket.id, it.pocket.name, minOf(it.availabilityMinor, it.budgetMinor)) }
            .sortedByDescending { it.maximumMinor }
        val unassigned = state.unallocatedMinorByPeriod[periodId]?.takeIf { it > 0 }
            ?.let { BudgetReassignmentSource(null, "Sin asignar", it) }
        return BudgetReassignmentOptions(needed, listOfNotNull(unassigned) + donors)
    }

    /** Rechecks the source against the supplied snapshot before building one atomic ledger command. */
    fun command(state: LedgerState, periodId: String, targetPocketId: String, sourcePocketId: String?, amountMinor: Long): LedgerCommand.SetAllocations {
        require(amountMinor > 0) { "Escribe un importe mayor que cero" }
        val target = requireNotNull(state.pocketSummariesByPeriod[periodId]?.firstOrNull { it.pocket.id == targetPocketId }) { "Pocket inexistente" }
        val targetBudget = Math.addExact(target.budgetMinor, amountMinor)
        val options = requireNotNull(of(state, periodId, targetPocketId)) { "Este Pocket ya no necesita cubrirse" }
        val source = requireNotNull(options.sources.firstOrNull { it.pocketId == sourcePocketId }) { "Elige una fuente disponible" }
        require(amountMinor <= source.maximumMinor) { "El importe supera el presupuesto disponible para reasignar" }
        val amounts = mutableMapOf(targetPocketId to targetBudget)
        if (sourcePocketId != null) {
            val donor = state.pocketSummariesByPeriod.getValue(periodId).first { it.pocket.id == sourcePocketId }
            amounts[sourcePocketId] = Math.subtractExact(donor.budgetMinor, amountMinor)
        }
        return LedgerCommand.SetAllocations(periodId, amounts)
    }

    private fun forecastShortfall(target: PocketPeriodSummary, insights: PeriodInsights): Long? = runCatching {
        val elapsedDays = BigDecimal.valueOf(insights.elapsedDays.toLong())
        val shortfallNumerator = BigDecimal.valueOf(insights.pocketNetSpendThroughTodayMinor(target.pocket.id))
            .multiply(BigDecimal.valueOf(insights.remainingDays.toLong()))
            .subtract(BigDecimal.valueOf(target.availabilityMinor).multiply(elapsedDays))
        shortfallNumerator.takeIf { it.signum() > 0 }?.let {
            it.divide(elapsedDays.multiply(BigDecimal.valueOf(100)), 0, RoundingMode.CEILING)
                .multiply(BigDecimal.valueOf(100)).longValueExact()
        }
    }.getOrNull()
}
