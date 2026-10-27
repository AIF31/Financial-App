package com.aif31.pocket.data

import com.aif31.pocket.domain.FrozenRate
import com.aif31.pocket.domain.PocketMath
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.domain.sumMoneyExact
import java.time.LocalDate

/**
 * How the period's spending relates to its plan; the dashboard's status line reads this. [EARLY_OVER_PACE] is a pace
 * that would exceed the funds by the period's end but not by its middle, before
 * [PeriodInsights.FORECAST_WARMUP_DAYS] have elapsed: too early to warn, too fast to call on plan.
 */
enum class SpendPaceStatus { NO_FUNDS, OVERSPENT, OVER_PACE, EARLY_OVER_PACE, ON_PLAN }

/**
 * The period's Movements that count toward its totals: those whose Pocket is in the period's snapshot, matching the
 * ledger's own net spend.
 */
fun LedgerState.periodMovements(periodId: String): List<Movement> {
    val snapshotPocketIds = pocketSummariesByPeriod[periodId].orEmpty().mapTo(HashSet()) { it.pocket.id }
    return movements.filter { it.periodId == periodId && it.pocketId in snapshotPocketIds }
}

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
    /** [SpendPaceStatus.OVER_PACE] reads the pace only up to the same horizon as [runOutDateByPocket]. */
    val paceStatus: SpendPaceStatus,
    /** Net spending accumulated at the end of each elapsed day, starting with the period's first day. */
    val cumulativeNetSpendByDayMinor: List<Long>,
    /** Net spend of Movements dated after today: counted in [netSpendMinor] but not yet on the daily curve. */
    val netSpendAfterTodayMinor: Long,
    /** [netSpendAfterTodayMinor] by Pocket ID; Pockets with nothing dated after today are absent. */
    val netSpendAfterTodayByPocketMinor: Map<String, Long>,
    /**
     * The day each Pocket's availability runs out at its pace through today, for Pockets that would run out before
     * the period ends. Only Pockets spent on at least two days are forecast, so a single large expense such as rent
     * is not extrapolated. Until [FORECAST_WARMUP_DAYS] have elapsed, only a run-out before the middle of the period
     * is forecast. Empty for closed periods.
     */
    val runOutDateByPocket: Map<String, LocalDate>,
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
        /**
         * Elapsed days after which pace is trusted over the whole period. Before then, a few ordinary days stretched
         * over a month read as a warning, so pace warnings look only as far as the middle of the period: spending
         * fast enough to run out by then still warns.
         */
        const val FORECAST_WARMUP_DAYS = 7

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
            val periodMovements = state.periodMovements(period.id)
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
            fun projectedThrough(days: Int) =
                Math.addExact(PocketMath.project(netSpendThroughToday, elapsedDays, days).amountMinor, netSpendAfterToday)
            val projected = if (inProgress) projectedThrough(totalDays) else null
            val warningHorizonDays = if (inProgress && elapsedDays < FORECAST_WARMUP_DAYS) {
                maxOf(elapsedDays, totalDays / 2)
            } else {
                totalDays
            }
            val projectedForWarning = if (inProgress) projectedThrough(warningHorizonDays) else null
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
                    (projectedForWarning ?: netSpend) > periodFunds -> SpendPaceStatus.OVER_PACE
                    (projected ?: netSpend) > periodFunds -> SpendPaceStatus.EARLY_OVER_PACE
                    else -> SpendPaceStatus.ON_PLAN
                },
                cumulativeNetSpendByDayMinor = cumulativeByDay(period, elapsedDays, periodMovements),
                netSpendAfterTodayMinor = netSpendAfterToday,
                netSpendAfterTodayByPocketMinor = netSpendAfterTodayByPocket,
                runOutDateByPocket = if (inProgress) {
                    val horizonEnd = period.start.plusDays(warningHorizonDays.toLong())
                    runOutDates(pockets, periodMovements.filter { it.localDate <= today }, today, elapsedDays, horizonEnd)
                } else {
                    emptyMap()
                },
                pockets = pockets,
            )
        }

        private fun runOutDates(
            pockets: List<PocketPeriodSummary>,
            movementsThroughToday: List<Movement>,
            today: LocalDate,
            elapsedDays: Int,
            horizonEnd: LocalDate,
        ): Map<String, LocalDate> {
            val byPocket = movementsThroughToday.groupBy { it.pocketId }
            return pockets.mapNotNull { summary ->
                val movements = byPocket[summary.pocket.id].orEmpty()
                val spendingDays = movements.filter { it.type == MovementType.EXPENSE }.distinctBy { it.localDate }.size
                val dailyPace = movements.netSpendMinor() / elapsedDays
                if (summary.availabilityMinor <= 0 || spendingDays < 2 || dailyPace <= 0) return@mapNotNull null
                // Availability covers this many whole days after today; it runs out on the day after them. The
                // distance is bounded by the horizon before it becomes a date, since a huge budget would overflow one.
                val daysUntilRunOut = summary.availabilityMinor / dailyPace + 1
                if (daysUntilRunOut >= horizonEnd.toEpochDay() - today.toEpochDay()) return@mapNotNull null
                summary.pocket.id to today.plusDays(daysUntilRunOut)
            }.toMap()
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
