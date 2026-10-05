package com.aif31.pocket.data

import com.aif31.pocket.domain.BudgetCalendar
import com.aif31.pocket.domain.FrozenRate
import com.aif31.pocket.domain.PeriodSchedule
import com.aif31.pocket.domain.PocketMath
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.domain.sumMoneyExact
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal object PeriodLedgerRules {
    fun validateLedgerProjection(
        periods: List<PeriodEntity>,
        allocations: List<AllocationEntity>,
        movements: List<MovementEntity>,
        rolloverReleases: List<RolloverReleaseEntity>,
        today: LocalDate,
    ) = validateLedgerProjection(periods, allocations, MovementSpend.of(movements), rolloverReleases, today)

    private fun validateLedgerProjection(
        periods: List<PeriodEntity>,
        allocations: List<AllocationEntity>,
        spend: MovementSpend,
        rolloverReleases: List<RolloverReleaseEntity>,
        today: LocalDate,
    ) {
        val allocationsByPeriod = allocations.groupBy { it.periodId }
        val releasesByPeriod = rolloverReleases.groupBy { it.periodId }
        periods.forEach { period ->
            validateTotals(
                period,
                allocationsByPeriod[period.id].orEmpty(),
                spend.forPeriod(period.id),
                releasesByPeriod[period.id].orEmpty(),
                today,
            )
        }
        validateHistoricalComparisons(periods, spend)
    }

    /** Rejects budgets above the period's new funds and any period total outside the supported money range. */
    private fun validateTotals(
        period: PeriodEntity,
        periodAllocations: List<AllocationEntity>,
        periodSpend: Map<String, SpendTotals>,
        periodReleases: List<RolloverReleaseEntity>,
        today: LocalDate,
    ) {
        val allocated = periodAllocations.map { it.budgetMinor }.sumMoneyExact()
        require(allocated <= period.newFundsMinor) { "No puedes asignar más que los fondos nuevos" }

        // The unused sums below are computed only to reject totals outside the supported money range.
        periodSpend.values.map { it.expenseMinor }.sumMoneyExact()
        periodSpend.values.map { it.refundMinor }.sumMoneyExact()
        val allocationsByPocket = periodAllocations.associateBy { it.pocketId }
        val summaries = (allocationsByPocket.keys + periodSpend.keys).map { pocketId ->
            val allocation = allocationsByPocket[pocketId]
            val pocketSpend = periodSpend[pocketId] ?: SpendTotals.NONE
            PocketMath.summary(
                budgetMinor = allocation?.budgetMinor ?: 0,
                rolloverMinor = allocation?.rolloverMinor ?: 0,
                expensesMinor = pocketSpend.expenseMinor,
                refundsMinor = pocketSpend.refundMinor,
            )
        }
        summaries.map { it.rolloverMinor }.sumMoneyExact()
        summaries.map { it.availabilityMinor }.sumMoneyExact()
        Math.addExact(
            Math.subtractExact(period.newFundsMinor, allocated),
            periodReleases.map { it.amountMinor }.sumMoneyExact(),
        )
        val netSpend = summaries.map { it.netSpendMinor }.sumMoneyExact()
        val elapsed = if (today.toEpochDay() in period.startEpochDay until period.endExclusiveEpochDay) {
            (today.toEpochDay() - period.startEpochDay + 1).toInt().coerceAtLeast(1)
        } else {
            1
        }
        val totalDays = Math.toIntExact(Math.subtractExact(period.endExclusiveEpochDay, period.startEpochDay))
        PocketMath.project(netSpend, elapsed, totalDays)
    }

    private fun validateHistoricalComparisons(periods: List<PeriodEntity>, spend: MovementSpend) {
        periods.sortedBy { it.startEpochDay }.zipWithNext().forEach { (source, target) ->
            val sourceCurrency = SupportedCurrency.fromCode(source.accountingCurrencyCode)
            val targetCurrency = SupportedCurrency.fromCode(target.accountingCurrencyCode)
            if (sourceCurrency != targetCurrency) {
                val netSpend = spend.periodNetMinor(source.id)
                requireNotNull(target.frozenRateFrom(sourceCurrency)) {
                    "Falta la conversión para comparar periodos"
                }.convertMinor(netSpend)
            }
        }
    }

    /**
     * Recomputes rollover from [sourcePeriodId] into every later period: target allocations for active Pockets and
     * rollover releases for Pockets retired in the target. Validates the resulting ledger before returning it.
     */
    fun rolloverProjection(
        sourcePeriodId: String,
        periods: List<PeriodEntity>,
        periodPockets: List<PeriodPocketEntity>,
        movements: List<MovementEntity>,
        allocations: List<AllocationEntity>,
        releases: List<RolloverReleaseEntity>,
        today: LocalDate,
    ): RolloverProjection {
        val orderedPeriods = periods.sortedBy { it.startEpochDay }
        val startIndex = orderedPeriods.indexOfFirst { it.id == sourcePeriodId }
        require(startIndex >= 0) { "Periodo inexistente" }
        val snapshotsByPeriod = periodPockets.groupBy { it.periodId }
        val spend = MovementSpend.of(movements)
        val projectedAllocations = allocations.associateByTo(mutableMapOf()) { it.periodId to it.pocketId }
        val projectedReleases = releases.associateByTo(mutableMapOf()) { it.periodId to it.pocketId }
        val changedAllocations = mutableListOf<AllocationEntity>()
        val changedReleases = mutableMapOf<Pair<String, String>, RolloverReleaseEntity?>()

        for (index in startIndex until orderedPeriods.lastIndex) {
            val source = orderedPeriods[index]
            val target = orderedPeriods[index + 1]
            val sourceSnapshots = snapshotsByPeriod[source.id].orEmpty().associateBy { it.pocketId }
            val sourceSpend = spend.forPeriod(source.id)

            snapshotsByPeriod[target.id].orEmpty().forEach { targetSnapshot ->
                val pocketId = targetSnapshot.pocketId
                val sourceAllocation = projectedAllocations[source.id to pocketId]
                val rollover = PocketMath.rollover(
                    allocatedMinor = Math.addExact(sourceAllocation?.budgetMinor ?: 0, sourceAllocation?.rolloverMinor ?: 0),
                    netSpendMinor = (sourceSpend[pocketId] ?: SpendTotals.NONE).netMinor,
                    enabled = sourceSnapshots[pocketId].carriesRollover(),
                )
                val sourceCurrency = SupportedCurrency.fromCode(source.accountingCurrencyCode)
                val targetCurrency = SupportedCurrency.fromCode(target.accountingCurrencyCode)
                val targetRollover = if (sourceCurrency == targetCurrency) {
                    rollover
                } else {
                    requireNotNull(target.frozenRateFrom(sourceCurrency)) {
                        "Falta el tipo de cambio congelado entre periodos"
                    }.convertMinor(rollover)
                }
                val targetKey = target.id to pocketId
                if (targetSnapshot.retired) {
                    if (targetRollover > 0) {
                        val release = RolloverReleaseEntity(target.id, pocketId, targetRollover)
                        projectedReleases[targetKey] = release
                        changedReleases[targetKey] = release
                    } else {
                        projectedReleases.remove(targetKey)
                        changedReleases[targetKey] = null
                    }
                } else {
                    val existing = projectedAllocations[targetKey]
                    val updated = AllocationEntity(
                        periodId = target.id,
                        pocketId = pocketId,
                        budgetMinor = existing?.budgetMinor ?: 0,
                        rolloverMinor = targetRollover,
                    )
                    projectedAllocations[targetKey] = updated
                    if (updated != existing) changedAllocations += updated
                }
            }
        }
        validateLedgerProjection(
            orderedPeriods,
            projectedAllocations.values.toList(),
            spend,
            projectedReleases.values.toList(),
            today,
        )
        return RolloverProjection(changedAllocations, changedReleases)
    }

    /**
     * The period after [previous]: its new funds and Pocket budgets carried forward, converted at a pending currency
     * boundary, plus rollover from [previous] for every active Pocket.
     */
    fun successor(
        previous: PeriodEntity,
        previousAllocations: Map<String, AllocationEntity>,
        previousSnapshots: Map<String, PeriodPocketEntity>,
        previousSpend: Map<String, SpendTotals>,
        activePockets: List<PocketEntity>,
        pending: PendingCurrencyChangeEntity?,
        preferredStartDay: Int,
        zoneId: ZoneId,
        nextId: String,
        needsReview: Boolean,
    ): PeriodSuccessor {
        val previousSchedule = PeriodSchedule(
            start = LocalDate.ofEpochDay(previous.startEpochDay),
            endExclusive = LocalDate.ofEpochDay(previous.endExclusiveEpochDay),
            configuredStartDay = previous.configuredStartDay,
            isTransition = previous.isTransition,
        )
        val schedule = BudgetCalendar(previous.configuredStartDay, zoneId)
            .nextPeriodAfter(previousSchedule, preferredStartDay)
        val previousCurrency = SupportedCurrency.fromCode(previous.accountingCurrencyCode)
        val boundary = pending?.let {
            val from = SupportedCurrency.fromCode(it.fromCurrencyCode)
            val to = SupportedCurrency.fromCode(it.targetCurrencyCode)
            require(from == previousCurrency) { "La moneda de origen pendiente ya no coincide" }
            require(it.effectiveEpochDay == schedule.start.toEpochDay()) { "La fecha efectiva pendiente ya no coincide" }
            FrozenRate(from, to, it.rate)
        }
        fun convertForTarget(minor: Long): Long = boundary?.convertMinor(minor) ?: minor
        val next = previous.copy(
            id = nextId,
            startEpochDay = schedule.start.toEpochDay(),
            endExclusiveEpochDay = schedule.endExclusive.toEpochDay(),
            configuredStartDay = schedule.configuredStartDay,
            isTransition = schedule.isTransition,
            needsReview = needsReview,
            newFundsMinor = convertForTarget(previous.newFundsMinor),
            accountingCurrencyCode = (boundary?.to ?: previousCurrency).name,
            priorBoundaryFromCurrencyCode = boundary?.from?.name,
            priorBoundaryRate = pending?.rate,
            priorBoundaryEffectiveEpochDay = pending?.effectiveEpochDay,
            priorBoundarySource = pending?.source,
            priorBoundaryQuoteEffectiveEpochDay = pending?.quoteEffectiveEpochDay,
        )
        val nextSnapshots = activePockets.map { pocket ->
            PeriodPocketEntity(nextId, pocket.id, rolloverEligible = pocket.rolloverEnabled, retired = false)
        }
        val convertedBudgets = activePockets.associate { pocket ->
            pocket.id to convertForTarget(previousAllocations[pocket.id]?.budgetMinor ?: 0)
        }.toMutableMap()
        var roundingExcess = Math.subtractExact(convertedBudgets.values.sumMoneyExact(), next.newFundsMinor)
        // Preserve higher-priority Pockets and absorb any independent HALF_UP excess from the end of display order.
        activePockets.asReversed().forEach { pocket ->
            if (roundingExcess > 0) {
                val reduction = minOf(convertedBudgets.getValue(pocket.id), roundingExcess)
                convertedBudgets[pocket.id] = convertedBudgets.getValue(pocket.id) - reduction
                roundingExcess -= reduction
            }
        }
        val nextAllocations = activePockets.map { pocket ->
            val previousAllocation = previousAllocations[pocket.id]
            val rollover = PocketMath.rollover(
                allocatedMinor = Math.addExact(previousAllocation?.budgetMinor ?: 0, previousAllocation?.rolloverMinor ?: 0),
                netSpendMinor = (previousSpend[pocket.id] ?: SpendTotals.NONE).netMinor,
                enabled = previousSnapshots[pocket.id].carriesRollover(),
            )
            AllocationEntity(nextId, pocket.id, convertedBudgets.getValue(pocket.id), convertForTarget(rollover))
        }
        return PeriodSuccessor(next, nextSnapshots, nextAllocations)
    }

    fun catchUp(
        periods: List<PeriodEntity>,
        pockets: List<PocketEntity>,
        allocations: List<AllocationEntity>,
        periodPockets: List<PeriodPocketEntity>,
        rolloverReleases: List<RolloverReleaseEntity>,
        movements: List<MovementEntity>,
        pendingCurrencyChange: PendingCurrencyChangeEntity?,
        preferredStartDay: Int,
        today: LocalDate,
        zoneId: ZoneId,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): CatchUpPlan {
        require(preferredStartDay in 1..31) { "Día de inicio inválido" }
        val plannedPeriods = periods.toMutableList()
        val plannedAllocations = allocations.associateByTo(mutableMapOf()) { it.periodId to it.pocketId }
        val plannedPeriodPockets = periodPockets.toMutableList()
        var pending = pendingCurrencyChange
        var previous = requireNotNull(plannedPeriods.maxByOrNull { it.startEpochDay }) { "No existe un periodo anterior" }
        val createdIds = mutableListOf<String>()
        val todayEpochDay = today.toEpochDay()
        val activePockets = activePocketsInOrder(pockets)
        val spend = MovementSpend.of(movements)

        while (todayEpochDay >= previous.endExclusiveEpochDay) {
            val previousId = previous.id
            val next = successor(
                previous = previous,
                previousAllocations = plannedAllocations.values.filter { it.periodId == previousId }.associateBy { it.pocketId },
                previousSnapshots = plannedPeriodPockets.filter { it.periodId == previousId }.associateBy { it.pocketId },
                previousSpend = spend.forPeriod(previousId),
                activePockets = activePockets,
                pending = pending,
                preferredStartDay = preferredStartDay,
                zoneId = zoneId,
                nextId = newId(),
                needsReview = false,
            )
            validateTotals(next.period, next.allocations, emptyMap(), emptyList(), today)
            plannedPeriods += next.period
            plannedPeriodPockets += next.periodPockets
            next.allocations.forEach { plannedAllocations[it.periodId to it.pocketId] = it }
            createdIds += next.period.id
            previous = next.period
            pending = null
        }

        val currentId = plannedPeriods.firstOrNull {
            todayEpochDay in it.startEpochDay until it.endExclusiveEpochDay
        }?.id
        val finalPeriods = plannedPeriods.map { period ->
            when {
                period.id == createdIds.lastOrNull() -> period.copy(needsReview = true)
                period.needsReview && period.id != currentId -> period.copy(needsReview = false)
                else -> period
            }
        }
        validateLedgerProjection(finalPeriods, plannedAllocations.values.toList(), spend, rolloverReleases, today)
        return CatchUpPlan(
            periods = finalPeriods,
            allocations = plannedAllocations.values.toList(),
            periodPockets = plannedPeriodPockets,
            rolloverReleases = rolloverReleases,
            pendingCurrencyChange = pending,
        )
    }

    /** Pockets that a new period includes, in display order. */
    fun activePocketsInOrder(pockets: List<PocketEntity>): List<PocketEntity> =
        pockets.filterNot { it.archived }.sortedWith(compareBy<PocketEntity> { it.sortOrder }.thenBy { it.id })

    /** A Pocket passes its positive availability to the next period only if it was eligible and still active. */
    private fun PeriodPocketEntity?.carriesRollover(): Boolean = this != null && rolloverEligible && !retired
}

/** A Pocket's expense and refund totals within one period. */
internal data class SpendTotals(val expenseMinor: Long, val refundMinor: Long) {
    val netMinor: Long get() = Math.subtractExact(expenseMinor, refundMinor)

    companion object {
        val NONE = SpendTotals(0, 0)
    }
}

/** Movement totals by period and Pocket, summed once with overflow checks so callers need not refilter Movements. */
internal class MovementSpend private constructor(private val byPeriod: Map<String, Map<String, SpendTotals>>) {
    /** Totals by Pocket ID; Pockets without Movements in the period are absent. */
    fun forPeriod(periodId: String): Map<String, SpendTotals> = byPeriod[periodId].orEmpty()

    fun periodNetMinor(periodId: String): Long {
        val totals = forPeriod(periodId).values
        return Math.subtractExact(totals.map { it.expenseMinor }.sumMoneyExact(), totals.map { it.refundMinor }.sumMoneyExact())
    }

    companion object {
        fun of(movements: List<MovementEntity>) = MovementSpend(
            movements.groupBy { it.periodId }.mapValues { (_, periodMovements) ->
                periodMovements.groupBy { it.pocketId }.mapValues { (_, pocketMovements) ->
                    SpendTotals(
                        expenseMinor = pocketMovements.filter { it.type == MovementType.EXPENSE.name }
                            .map { it.accountingAmountMinor }.sumMoneyExact(),
                        refundMinor = pocketMovements.filter { it.type == MovementType.REFUND.name }
                            .map { it.accountingAmountMinor }.sumMoneyExact(),
                    )
                }
            }
        )
    }
}

internal data class PeriodSuccessor(
    val period: PeriodEntity,
    val periodPockets: List<PeriodPocketEntity>,
    val allocations: List<AllocationEntity>,
)

internal data class RolloverProjection(
    val changedAllocations: List<AllocationEntity>,
    val changedReleases: Map<Pair<String, String>, RolloverReleaseEntity?>,
)

internal data class CatchUpPlan(
    val periods: List<PeriodEntity>,
    val allocations: List<AllocationEntity>,
    val periodPockets: List<PeriodPocketEntity>,
    val rolloverReleases: List<RolloverReleaseEntity>,
    val pendingCurrencyChange: PendingCurrencyChangeEntity?,
)
