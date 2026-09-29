package com.aif31.pocket.data

import com.aif31.pocket.domain.FrozenRate
import com.aif31.pocket.domain.PocketMath
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.domain.sumMoneyExact

/** How the period's spending relates to its plan; the dashboard's status line reads this. */
enum class SpendPaceStatus { NO_FUNDS, OVERSPENT, OVER_PACE, ON_PLAN }

/** Net spending of Movements: expenses add, refunds subtract. */
fun Iterable<Movement>.netSpendMinor(): Long =
    map { if (it.type == MovementType.REFUND) -it.accountingAmountMinor else it.accountingAmountMinor }.sumMoneyExact()

/**
 * Read-only spending metrics for one budget period, derived from the ledger's own Pocket summaries and
 * Movements so every screen reports the same figures.
 */
data class PeriodInsights(
    val period: Period,
    val inProgress: Boolean,
    val totalDays: Int,
    val elapsedDays: Int,
    /** Days after today until the period ends; zero for closed periods. */
    val remainingDays: Int,
    val newFundsMinor: Long,
    val budgetedMinor: Long,
    val rolloverMinor: Long,
    val unallocatedMinor: Long,
    val expenseMinor: Long,
    val refundMinor: Long,
    val netSpendMinor: Long,
    val availabilityMinor: Long,
    val expenseCount: Int,
    val largestExpense: Movement?,
    /** Net spend dated through today over the elapsed days; later-dated Movements are not averaged in. */
    val averageDailySpendMinor: Long?,
    /** Availability spread over the remaining days including today; only while in progress and positive. */
    val safeDailySpendMinor: Long?,
    /** Pace through today extrapolated to the whole period, plus later-dated Movements counted once. */
    val projectedSpendMinor: Long?,
    /** Net spend as a share of Pocket budgets plus rollover, the same base availability is measured from. */
    val budgetUsedPercent: Int?,
    val periodElapsedPercent: Int,
    val paceStatus: SpendPaceStatus,
    /** Net spending accumulated at the end of each elapsed day, starting with the period's first day. */
    val cumulativeNetSpendByDayMinor: List<Long>,
    /** Net spend of Movements dated after today: counted in [netSpendMinor] but not yet on the daily curve. */
    val netSpendAfterTodayMinor: Long,
    /** [netSpendAfterTodayMinor] by Pocket ID; Pockets with nothing dated after today are absent. */
    val netSpendAfterTodayByPocketMinor: Map<String, Long>,
    val pockets: List<PocketPeriodSummary>,
) {
    val accountingCurrency: SupportedCurrency get() = period.accountingCurrency

    /** Pocket budgets plus received rollover: what the period's Pocket availability is measured from. */
    val availableBudgetMinor: Long get() = Math.addExact(budgetedMinor, rolloverMinor)

    /** Net spend dated through today: what [averageDailySpendMinor] is measured from. */
    val netSpendThroughTodayMinor: Long get() = Math.subtractExact(netSpendMinor, netSpendAfterTodayMinor)

    /** A Pocket's net spend dated through today: what its daily average is measured from. */
    fun pocketNetSpendThroughTodayMinor(pocketId: String): Long {
        val netSpend = pockets.firstOrNull { it.pocket.id == pocketId }?.netSpendMinor ?: 0
        return Math.subtractExact(netSpend, netSpendAfterTodayByPocketMinor[pocketId] ?: 0)
    }

    companion object {
        fun of(state: LedgerState, periodId: String): PeriodInsights? {
            val period = state.periods.firstOrNull { it.id == periodId } ?: return null
            val today = state.currentLocalDate
            val totalDays = (period.endExclusive.toEpochDay() - period.start.toEpochDay()).toInt()
            val inProgress = period.id == state.currentPeriod?.id
            val elapsedDays = when {
                inProgress -> state.elapsedDays.coerceIn(1, totalDays)
                today >= period.endExclusive -> totalDays
                else -> 0
            }
            val pockets = state.pocketSummariesByPeriod[period.id].orEmpty()
            val netSpend = pockets.map { it.netSpendMinor }.sumMoneyExact()
            val availability = pockets.map { it.availabilityMinor }.sumMoneyExact()
            val budgeted = pockets.map { it.budgetMinor }.sumMoneyExact()
            val rollover = pockets.map { it.rolloverMinor }.sumMoneyExact()
            val availableBudget = Math.addExact(budgeted, rollover)
            // Rollover is spendable money too, so a period funded only by rollover still has a plan.
            val periodFunds = Math.addExact(period.newFundsMinor, rollover)
            val remainingDays = if (inProgress) totalDays - elapsedDays else 0
            // Match the ledger's net spend, which only counts Pockets present in this period's snapshot.
            val snapshotPocketIds = pockets.mapTo(HashSet()) { it.pocket.id }
            val periodMovements = state.movements.filter { it.periodId == period.id && it.pocketId in snapshotPocketIds }
            val expenses = periodMovements.filter { it.type == MovementType.EXPENSE }
            val netSpendAfterTodayByPocket = if (inProgress) {
                periodMovements.filter { it.localDate > today }.groupBy { it.pocketId }.mapValues { it.value.netSpendMinor() }
            } else {
                emptyMap()
            }
            val netSpendAfterToday = netSpendAfterTodayByPocket.values.sumMoneyExact()
            // Pace is what has been spent through today. Movements dated later are already-committed spending: they
            // count once in the projection and in period totals, but are not extrapolated or averaged over past days.
            val netSpendThroughToday = Math.subtractExact(netSpend, netSpendAfterToday)
            val projected = if (inProgress) {
                Math.addExact(PocketMath.project(netSpendThroughToday, elapsedDays, totalDays).amountMinor, netSpendAfterToday)
            } else {
                null
            }
            return PeriodInsights(
                period = period,
                inProgress = inProgress,
                totalDays = totalDays,
                elapsedDays = elapsedDays,
                remainingDays = remainingDays,
                newFundsMinor = period.newFundsMinor,
                budgetedMinor = budgeted,
                rolloverMinor = rollover,
                unallocatedMinor = state.unallocatedMinorByPeriod[period.id] ?: 0,
                expenseMinor = pockets.map { it.expenseMinor }.sumMoneyExact(),
                refundMinor = pockets.map { it.refundMinor }.sumMoneyExact(),
                netSpendMinor = netSpend,
                availabilityMinor = availability,
                expenseCount = expenses.size,
                largestExpense = expenses.maxByOrNull { it.accountingAmountMinor },
                averageDailySpendMinor = elapsedDays.takeIf { it > 0 }?.let { netSpendThroughToday / it },
                safeDailySpendMinor = if (inProgress && availability > 0) availability / (remainingDays + 1) else null,
                projectedSpendMinor = projected,
                budgetUsedPercent = availableBudget.takeIf { it > 0 }?.let { PocketMath.percent(netSpend, it) },
                periodElapsedPercent = if (totalDays == 0) 0 else elapsedDays * 100 / totalDays,
                paceStatus = when {
                    availability < 0L -> SpendPaceStatus.OVERSPENT
                    periodFunds <= 0L -> SpendPaceStatus.NO_FUNDS
                    (projected ?: netSpend) > periodFunds -> SpendPaceStatus.OVER_PACE
                    else -> SpendPaceStatus.ON_PLAN
                },
                cumulativeNetSpendByDayMinor = cumulativeByDay(period, elapsedDays, periodMovements),
                netSpendAfterTodayMinor = netSpendAfterToday,
                netSpendAfterTodayByPocketMinor = netSpendAfterTodayByPocket,
                pockets = pockets,
            )
        }

        private fun cumulativeByDay(period: Period, elapsedDays: Int, movements: List<Movement>): List<Long> {
            val byDay = movements.groupBy { (it.localDate.toEpochDay() - period.start.toEpochDay()).toInt() }
            var running = 0L
            return (0 until elapsedDays).map { day ->
                running = Math.addExact(running, byDay[day].orEmpty().netSpendMinor())
                running
            }
        }
    }
}

data class PocketComparisonRow(
    val pocket: Pocket,
    val currentNetSpendMinor: Long,
    val baselineNetSpendMinor: Long?,
    val currentAverageDailyMinor: Long?,
    val baselineAverageDailyMinor: Long?,
    val averageDailyDeltaMinor: Long?,
    val averageDailyDeltaPercent: Int?,
)

/** Baseline figures expressed in the current period's accounting currency. */
data class ConvertedBaseline(
    val netSpendMinor: Long,
    val averageDailySpendMinor: Long?,
    val averageDailyDeltaMinor: Long?,
    val averageDailyDeltaPercent: Int?,
    val cumulativeNetSpendByDayMinor: List<Long>,
)

/**
 * Compares two periods by average daily spending. Baseline amounts are converted into the current period's
 * accounting currency when the currencies match or an adjacent frozen boundary rate exists; otherwise
 * [convertedBaseline] is null and the periods are shown side by side without numeric deltas.
 */
data class PeriodComparison(
    val current: PeriodInsights,
    val baseline: PeriodInsights,
    val convertedBaseline: ConvertedBaseline?,
    val pockets: List<PocketComparisonRow>,
) {
    companion object {
        fun previousPeriodId(state: LedgerState, periodId: String): String? {
            val period = state.periods.firstOrNull { it.id == periodId } ?: return null
            return state.periods.filter { it.start < period.start }.maxByOrNull { it.start }?.id
        }

        fun of(state: LedgerState, currentPeriodId: String, baselinePeriodId: String): PeriodComparison? {
            val current = PeriodInsights.of(state, currentPeriodId) ?: return null
            val baseline = PeriodInsights.of(state, baselinePeriodId) ?: return null
            val convert = conversion(state, current.period, baseline.period)
            val converted = convert?.let {
                val net = it(baseline.netSpendMinor)
                val average = averageOf(it(baseline.netSpendThroughTodayMinor), baseline.elapsedDays)
                val delta = difference(current.averageDailySpendMinor, average)
                ConvertedBaseline(
                    netSpendMinor = net,
                    averageDailySpendMinor = average,
                    averageDailyDeltaMinor = delta,
                    averageDailyDeltaPercent = percentOf(delta, average),
                    cumulativeNetSpendByDayMinor = baseline.cumulativeNetSpendByDayMinor.map(it),
                )
            }
            val currentByPocket = current.pockets.associateBy { it.pocket.id }
            val baselineByPocket = baseline.pockets.associateBy { it.pocket.id }
            val rows = (current.pockets.map { it.pocket } + baseline.pockets.map { it.pocket })
                .distinctBy { it.id }
                .map { pocket ->
                    val currentNet = currentByPocket[pocket.id]?.netSpendMinor ?: 0
                    val baselineNet = convert?.invoke(baselineByPocket[pocket.id]?.netSpendMinor ?: 0)
                    val currentAverage = averageOf(current.pocketNetSpendThroughTodayMinor(pocket.id), current.elapsedDays)
                    val baselineAverage = convert?.let { averageOf(it(baseline.pocketNetSpendThroughTodayMinor(pocket.id)), baseline.elapsedDays) }
                    val delta = difference(currentAverage, baselineAverage)
                    PocketComparisonRow(
                        pocket = pocket,
                        currentNetSpendMinor = currentNet,
                        baselineNetSpendMinor = baselineNet,
                        currentAverageDailyMinor = currentAverage,
                        baselineAverageDailyMinor = baselineAverage,
                        averageDailyDeltaMinor = delta,
                        averageDailyDeltaPercent = percentOf(delta, baselineAverage),
                    )
                }
                .filter { it.currentNetSpendMinor != 0L || (it.baselineNetSpendMinor ?: 0L) != 0L || it.pocket.id in currentByPocket }
                .sortedByDescending { maxOf(it.currentAverageDailyMinor ?: 0, it.baselineAverageDailyMinor ?: 0) }
            return PeriodComparison(current, baseline, converted, rows)
        }

        private fun averageOf(netMinor: Long, days: Int): Long? = days.takeIf { it > 0 }?.let { netMinor / it }

        private fun difference(current: Long?, baseline: Long?): Long? =
            if (current != null && baseline != null) Math.subtractExact(current, baseline) else null

        private fun percentOf(delta: Long?, base: Long?): Int? =
            if (delta != null && base != null && base > 0) PocketMath.percent(delta, base) else null

        private fun conversion(state: LedgerState, current: Period, baseline: Period): ((Long) -> Long)? {
            if (current.accountingCurrency == baseline.accountingCurrency) return { it }
            val boundary = current.priorCurrencyBoundary ?: return null
            val adjacent = previousPeriodId(state, current.id) == baseline.id
            if (!adjacent || boundary.from != baseline.accountingCurrency || boundary.to != current.accountingCurrency) return null
            val rate = runCatching { FrozenRate(boundary.from, boundary.to, boundary.rate) }.getOrNull() ?: return null
            return rate::convertMinor
        }
    }
}
