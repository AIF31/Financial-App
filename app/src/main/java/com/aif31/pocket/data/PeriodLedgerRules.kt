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
    fun validateTotals(
        period: PeriodEntity,
        allocations: List<AllocationEntity>,
        movements: List<MovementEntity>,
        rolloverReleases: List<RolloverReleaseEntity>,
        today: LocalDate,
    ) {
        val periodAllocations = allocations.filter { it.periodId == period.id }
        val allocated = periodAllocations.map { it.budgetMinor }.sumMoneyExact()
        require(allocated <= period.newFundsMinor) { "No puedes asignar más que los fondos nuevos" }

        val periodMovements = movements.filter { it.periodId == period.id }
        periodMovements.filter { it.type == MovementType.EXPENSE.name }
            .map { it.accountingAmountMinor }.sumMoneyExact()
        periodMovements.filter { it.type == MovementType.REFUND.name }
            .map { it.accountingAmountMinor }.sumMoneyExact()

        val summaries = (periodAllocations.map { it.pocketId } + periodMovements.map { it.pocketId })
            .distinct()
            .map { pocketId ->
                val allocation = periodAllocations.firstOrNull { it.pocketId == pocketId }
                val pocketMovements = periodMovements.filter { it.pocketId == pocketId }
                PocketMath.summary(
                    budgetMinor = allocation?.budgetMinor ?: 0,
                    rolloverMinor = allocation?.rolloverMinor ?: 0,
                    expensesMinor = pocketMovements.filter { it.type == MovementType.EXPENSE.name }
                        .map { it.accountingAmountMinor }.sumMoneyExact(),
                    refundsMinor = pocketMovements.filter { it.type == MovementType.REFUND.name }
                        .map { it.accountingAmountMinor }.sumMoneyExact(),
                )
            }
        summaries.map { it.rolloverMinor }.sumMoneyExact()
        summaries.map { it.availabilityMinor }.sumMoneyExact()
        Math.addExact(
            Math.subtractExact(period.newFundsMinor, allocated),
            rolloverReleases.filter { it.periodId == period.id }.map { it.amountMinor }.sumMoneyExact(),
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

    fun validateHistoricalComparisons(
        periods: List<PeriodEntity>,
        movements: List<MovementEntity>,
    ) {
        periods.sortedBy { it.startEpochDay }.zipWithNext().forEach { (source, target) ->
            val sourceCurrency = SupportedCurrency.fromCode(source.accountingCurrencyCode)
            val targetCurrency = SupportedCurrency.fromCode(target.accountingCurrencyCode)
            if (sourceCurrency != targetCurrency) {
                val sourceMovements = movements.filter { it.periodId == source.id }
                val netSpend = Math.subtractExact(
                    sourceMovements.filter { it.type == MovementType.EXPENSE.name }
                        .map { it.accountingAmountMinor }.sumMoneyExact(),
                    sourceMovements.filter { it.type == MovementType.REFUND.name }
                        .map { it.accountingAmountMinor }.sumMoneyExact(),
                )
                requireNotNull(target.frozenRateFrom(sourceCurrency)) {
                    "Falta la conversión para comparar periodos"
                }.convertMinor(netSpend)
            }
        }
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
        val plannedReleases = rolloverReleases.toMutableList()
        var pending = pendingCurrencyChange
        var previous = requireNotNull(plannedPeriods.maxByOrNull { it.startEpochDay }) { "No existe un periodo anterior" }
        val createdIds = mutableListOf<String>()
        val todayEpochDay = today.toEpochDay()

        while (todayEpochDay >= previous.endExclusiveEpochDay) {
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
            val nextCurrency = boundary?.to ?: previousCurrency
            fun convertForTarget(minor: Long): Long = boundary?.convertMinor(minor) ?: minor
            val nextId = newId()
            val next = previous.copy(
                id = nextId,
                startEpochDay = schedule.start.toEpochDay(),
                endExclusiveEpochDay = schedule.endExclusive.toEpochDay(),
                configuredStartDay = schedule.configuredStartDay,
                isTransition = schedule.isTransition,
                needsReview = false,
                newFundsMinor = convertForTarget(previous.newFundsMinor),
                accountingCurrencyCode = nextCurrency.name,
                priorBoundaryFromCurrencyCode = boundary?.from?.name,
                priorBoundaryRate = pending?.rate,
                priorBoundaryEffectiveEpochDay = pending?.effectiveEpochDay,
                priorBoundarySource = pending?.source,
                priorBoundaryQuoteEffectiveEpochDay = pending?.quoteEffectiveEpochDay,
            )
            val activePockets = pockets.filterNot { it.archived }
                .sortedWith(compareBy<PocketEntity> { it.sortOrder }.thenBy { it.id })
            val previousSnapshots = plannedPeriodPockets.filter { it.periodId == previous.id }.associateBy { it.pocketId }
            val previousMovements = movements.filter { it.periodId == previous.id }
            val nextSnapshots = activePockets.map { pocket ->
                PeriodPocketEntity(nextId, pocket.id, rolloverEligible = pocket.rolloverEnabled, retired = false)
            }
            val convertedBudgets = activePockets.associate { pocket ->
                pocket.id to convertForTarget(plannedAllocations[previous.id to pocket.id]?.budgetMinor ?: 0)
            }.toMutableMap()
            var roundingExcess = Math.subtractExact(convertedBudgets.values.sumMoneyExact(), next.newFundsMinor)
            activePockets.asReversed().forEach { pocket ->
                if (roundingExcess > 0) {
                    val reduction = minOf(convertedBudgets.getValue(pocket.id), roundingExcess)
                    convertedBudgets[pocket.id] = convertedBudgets.getValue(pocket.id) - reduction
                    roundingExcess -= reduction
                }
            }
            val nextAllocations = activePockets.map { pocket ->
                val previousAllocation = plannedAllocations[previous.id to pocket.id]
                val pocketMovements = previousMovements.filter { it.pocketId == pocket.id }
                val expenses = pocketMovements.filter { it.type == MovementType.EXPENSE.name }
                    .map { it.accountingAmountMinor }.sumMoneyExact()
                val refunds = pocketMovements.filter { it.type == MovementType.REFUND.name }
                    .map { it.accountingAmountMinor }.sumMoneyExact()
                val rollover = PocketMath.rollover(
                    allocatedMinor = Math.addExact(previousAllocation?.budgetMinor ?: 0, previousAllocation?.rolloverMinor ?: 0),
                    netSpendMinor = Math.subtractExact(expenses, refunds),
                    enabled = previousSnapshots[pocket.id]?.rolloverEligible == true,
                )
                AllocationEntity(nextId, pocket.id, convertedBudgets.getValue(pocket.id), convertForTarget(rollover))
            }
            validateTotals(next, nextAllocations, emptyList(), emptyList(), today)
            plannedPeriods += next
            plannedPeriodPockets += nextSnapshots
            nextAllocations.forEach { plannedAllocations[it.periodId to it.pocketId] = it }
            createdIds += nextId
            previous = next
            if (pending != null) pending = null
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
        validateHistoricalComparisons(finalPeriods, movements)
        return CatchUpPlan(
            periods = finalPeriods,
            allocations = plannedAllocations.values.toList(),
            periodPockets = plannedPeriodPockets,
            rolloverReleases = plannedReleases,
            pendingCurrencyChange = pending,
        )
    }
}

internal data class CatchUpPlan(
    val periods: List<PeriodEntity>,
    val allocations: List<AllocationEntity>,
    val periodPockets: List<PeriodPocketEntity>,
    val rolloverReleases: List<RolloverReleaseEntity>,
    val pendingCurrencyChange: PendingCurrencyChangeEntity?,
)
