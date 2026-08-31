package com.aif31.pocket.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.aif31.pocket.domain.BudgetCalendar
import com.aif31.pocket.domain.PeriodSchedule
import com.aif31.pocket.domain.PocketMath
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class RoomPocketLedger(
    private val database: FinanceDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.of("Asia/Riyadh"),
    private val codecDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PocketLedger {
    private val dao = database.financeDao()

    private val budgetData = combine(
        dao.observePeriods(),
        dao.observePockets(),
        dao.observeAllocations(),
        dao.observePeriodPockets(),
        dao.observeRolloverReleases(),
    ) { periods, pockets, allocations, periodPockets, rolloverReleases ->
        BudgetData(periods, pockets, allocations, periodPockets, rolloverReleases)
    }

    private val activityData = combine(
        dao.observePaymentMethods(),
        dao.observeMovements(),
        dao.observeTemplates(),
    ) { methods, movements, templates -> Triple(methods, movements, templates) }

    override val state: Flow<LedgerState> = combine(budgetData, activityData) { budget, activity ->
        buildState(
            periodEntities = budget.periods,
            pocketEntities = budget.pockets,
            allocations = budget.allocations,
            periodPockets = budget.periodPockets,
            rolloverReleases = budget.rolloverReleases,
            methodEntities = activity.first,
            movementEntities = activity.second,
            templateEntities = activity.third,
        )
    }

    override fun movementDefaults(): MovementDefaults {
        val instant = clock.instant()
        return MovementDefaults(
            localDate = instant.atZone(zoneId).toLocalDate(),
            instantMillis = instant.toEpochMilli(),
        )
    }

    override suspend fun execute(command: LedgerCommand): LedgerResult = try {
        when (command) {
            is LedgerCommand.Initialize -> initialize(command)
            is LedgerCommand.UpdatePeriodFunds -> updateFunds(command)
            is LedgerCommand.SetAllocation -> setAllocation(command)
            is LedgerCommand.UpsertPocket -> upsertPocket(command)
            is LedgerCommand.ArchivePocket -> archivePocket(command)
            is LedgerCommand.MovePocket -> movePocket(command)
            is LedgerCommand.AddMovement -> addMovement(command)
            is LedgerCommand.DeleteMovement -> deleteMovement(command)
            is LedgerCommand.RestoreMovement -> restoreMovement(command)
            is LedgerCommand.CreateNextPeriod -> createNextPeriod(command.startDay)
            is LedgerCommand.CatchUpPeriods -> catchUpPeriods(command.preferredStartDay)
            is LedgerCommand.MarkPeriodReviewed -> markPeriodReviewed(command.periodId)
            is LedgerCommand.UpsertPaymentMethod -> upsertPaymentMethod(command)
            is LedgerCommand.ArchivePaymentMethod -> archivePaymentMethod(command)
            is LedgerCommand.UpsertTemplate -> upsertTemplate(command)
            is LedgerCommand.ArchiveTemplate -> archiveTemplate(command)
        }
    } catch (error: IllegalArgumentException) {
        LedgerResult.Rejected(error.message ?: "Datos inválidos")
    } catch (_: SQLiteConstraintException) {
        LedgerResult.Rejected("Ya existe un elemento con esos datos")
    }

    private suspend fun initialize(command: LedgerCommand.Initialize): LedgerResult = database.withTransaction {
        require(command.newFundsMinor >= 0) { "Los fondos no pueden ser negativos" }
        require(dao.periodCount() == 0) { "La configuración inicial ya existe" }
        val bounds = BudgetCalendar(command.startDay, zoneId).periodContaining(today())
        val periodId = UUID.randomUUID().toString()
        dao.putPeriod(
            PeriodEntity(
                id = periodId,
                startEpochDay = bounds.start.toEpochDay(),
                endExclusiveEpochDay = bounds.endExclusive.toEpochDay(),
                newFundsMinor = command.newFundsMinor,
                configuredStartDay = command.startDay,
            )
        )
        val pockets = INITIAL_POCKETS.mapIndexed { index, (name, iconKey) ->
                PocketEntity(UUID.randomUUID().toString(), name, iconKey.name, index, archived = false, rolloverEnabled = false)
            }
        dao.putPockets(pockets)
        dao.putPeriodPockets(
            pockets.map { pocket ->
                PeriodPocketEntity(periodId, pocket.id, rolloverEligible = pocket.rolloverEnabled, retired = false)
            }
        )
        dao.putPaymentMethods(
            listOf("Efectivo", "Tarjeta").map { PaymentMethodEntity(UUID.randomUUID().toString(), it, archived = false) }
        )
        LedgerResult.Success
    }

    private suspend fun updateFunds(command: LedgerCommand.UpdatePeriodFunds): LedgerResult = database.withTransaction {
        require(command.newFundsMinor >= 0) { "Los fondos no pueden ser negativos" }
        val period = requireNotNull(dao.period(command.periodId)) { "Periodo inexistente" }
        require(dao.allocated(period.id) <= command.newFundsMinor) { "Reduce primero los presupuestos asignados" }
        dao.updatePeriod(period.copy(newFundsMinor = command.newFundsMinor))
        LedgerResult.Success
    }

    private suspend fun setAllocation(command: LedgerCommand.SetAllocation): LedgerResult = database.withTransaction {
        require(command.amountMinor >= 0) { "El presupuesto no puede ser negativo" }
        val period = requireNotNull(dao.period(command.periodId)) { "Periodo inexistente" }
        val pocket = requireNotNull(dao.pockets().firstOrNull { it.id == command.pocketId }) { "Pocket inexistente" }
        require(!pocket.archived) { "El Pocket está archivado" }
        val snapshot = dao.periodPockets().firstOrNull { it.periodId == command.periodId && it.pocketId == command.pocketId }
        require(snapshot != null && !snapshot.retired) { "El Pocket no está activo en este periodo" }
        val existing = dao.allocation(command.periodId, command.pocketId)
        val resultingTotal = dao.allocated(command.periodId) - (existing?.budgetMinor ?: 0) + command.amountMinor
        require(resultingTotal <= period.newFundsMinor) { "No puedes asignar más que los fondos nuevos" }
        dao.putAllocation(
            AllocationEntity(command.periodId, command.pocketId, command.amountMinor, existing?.rolloverMinor ?: 0)
        )
        recalculateRolloverFrom(command.periodId)
        LedgerResult.Success
    }

    private suspend fun upsertPocket(command: LedgerCommand.UpsertPocket): LedgerResult = database.withTransaction {
        val name = command.name.trim()
        require(name.isNotEmpty()) { "Escribe un nombre para el Pocket" }
        val pockets = dao.pockets()
        require(pockets.none { it.id != command.id && it.name.equals(name, ignoreCase = true) }) {
            "Ya existe un Pocket con ese nombre"
        }
        val existing = pockets.firstOrNull { it.id == command.id }
        val nextOrder = pockets.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        val pocketId = existing?.id ?: UUID.randomUUID().toString()
        dao.putPocket(
            PocketEntity(
                id = pocketId,
                name = name,
                iconKey = (command.iconKey ?: existing?.let { PocketIconKey.fromStored(it.iconKey, it.name) } ?: PocketIconKey.forName(name)).name,
                sortOrder = existing?.sortOrder ?: nextOrder,
                archived = existing?.archived ?: false,
                rolloverEnabled = command.rolloverEnabled,
            )
        )
        val todayEpochDay = LocalDate.now(clock.withZone(zoneId)).toEpochDay()
        dao.periods().firstOrNull {
            todayEpochDay >= it.startEpochDay && todayEpochDay < it.endExclusiveEpochDay
        }?.let { currentPeriod ->
            val currentSnapshot = dao.periodPockets().firstOrNull {
                it.periodId == currentPeriod.id && it.pocketId == pocketId
            }
            dao.putPeriodPocket(
                PeriodPocketEntity(
                    periodId = currentPeriod.id,
                    pocketId = pocketId,
                    rolloverEligible = command.rolloverEnabled,
                    retired = currentSnapshot?.retired ?: false,
                )
            )
            recalculateRolloverFrom(currentPeriod.id)
        }
        LedgerResult.Success
    }

    private suspend fun archivePocket(command: LedgerCommand.ArchivePocket): LedgerResult = database.withTransaction {
        val pocket = requireNotNull(dao.pockets().firstOrNull { it.id == command.pocketId }) { "Pocket inexistente" }
        if (!command.archived) {
            dao.putPocket(pocket.copy(archived = false))
            val currentPeriod = dao.periods().firstOrNull { today().toEpochDay() in it.startEpochDay until it.endExclusiveEpochDay }
            currentPeriod?.let { period ->
                dao.periodPockets().firstOrNull { it.periodId == period.id && it.pocketId == pocket.id }?.let { snapshot ->
                    dao.putPeriodPocket(snapshot.copy(retired = false))
                }
            }
            return@withTransaction LedgerResult.Success
        }
        val activeDependencies = dao.templates().filter { it.pocketId == pocket.id && !it.archived }
        require(activeDependencies.isEmpty()) {
            "Archiva primero estas plantillas: ${activeDependencies.joinToString(", ") { it.name }}"
        }
        val todayEpochDay = today().toEpochDay()
        val currentPeriod = requireNotNull(dao.periods().firstOrNull {
            todayEpochDay >= it.startEpochDay && todayEpochDay < it.endExclusiveEpochDay
        }) { "No hay un periodo activo" }
        val snapshot = requireNotNull(dao.periodPockets().firstOrNull {
            it.periodId == currentPeriod.id && it.pocketId == pocket.id
        }) { "El Pocket no está activo en este periodo" }
        val allocation = dao.allocation(currentPeriod.id, pocket.id)
        val releasedRollover = allocation?.rolloverMinor ?: 0
        if (releasedRollover > 0) {
            dao.putRolloverRelease(RolloverReleaseEntity(currentPeriod.id, pocket.id, releasedRollover))
        }
        dao.putAllocation(AllocationEntity(currentPeriod.id, pocket.id, budgetMinor = 0, rolloverMinor = 0))
        dao.putPeriodPocket(snapshot.copy(retired = true))
        dao.putPocket(pocket.copy(archived = true))
        val futurePeriodIds = dao.periods()
            .filter { it.startEpochDay > currentPeriod.startEpochDay }
            .map { it.id }
        if (futurePeriodIds.isNotEmpty()) {
            dao.deleteAllocations(pocket.id, futurePeriodIds)
            dao.deletePeriodPockets(pocket.id, futurePeriodIds)
        }
        recalculateRolloverFrom(currentPeriod.id)
        LedgerResult.Success
    }

    private suspend fun movePocket(command: LedgerCommand.MovePocket): LedgerResult = database.withTransaction {
        val pockets = dao.pockets().sortedBy { it.sortOrder }.toMutableList()
        val from = pockets.indexOfFirst { it.id == command.pocketId }
        require(from >= 0) { "Pocket inexistente" }
        require(!pockets[from].archived) { "El Pocket está archivado" }
        val to = (from + command.direction).coerceIn(0, pockets.lastIndex)
        if (from != to) {
            val moved = pockets.removeAt(from)
            pockets.add(to, moved)
            dao.putPockets(pockets.mapIndexed { index, pocket -> pocket.copy(sortOrder = index) })
        }
        LedgerResult.Success
    }

    private suspend fun addMovement(command: LedgerCommand.AddMovement): LedgerResult = database.withTransaction {
        require(command.sarAmountMinor > 0) { "El importe debe ser mayor que cero" }
        require(command.originalCurrencyCode.matches(Regex("[A-Z]{3}"))) { "Moneda inválida" }
        val periods = dao.periods()
        val period = requireNotNull(periods.firstOrNull {
            command.localDate.toEpochDay() >= it.startEpochDay && command.localDate.toEpochDay() < it.endExclusiveEpochDay
        }) { "La fecha no pertenece a un periodo existente" }
        val pocket = requireNotNull(dao.pockets().firstOrNull { it.id == command.pocketId }) { "Pocket inexistente" }
        require(!pocket.archived) { "El Pocket está archivado" }
        val snapshot = dao.periodPockets().firstOrNull { it.periodId == period.id && it.pocketId == command.pocketId }
        require(snapshot != null && !snapshot.retired) { "El Pocket no está activo en este periodo" }
        val existing = command.id?.let { dao.movement(it) }
        dao.putMovement(command.toEntity(period.id, zoneId.id))
        val sourcePeriodId = listOfNotNull(existing?.periodId, period.id)
            .minBy { id -> periods.first { it.id == id }.startEpochDay }
        recalculateRolloverFrom(sourcePeriodId)
        LedgerResult.Success
    }

    private suspend fun deleteMovement(command: LedgerCommand.DeleteMovement): LedgerResult = database.withTransaction {
        val entity = requireNotNull(dao.movement(command.movementId)) { "Movimiento inexistente" }
        val movement = entity.toModel(dao.pockets().associateBy { it.id }, dao.paymentMethods().associateBy { it.id })
        dao.deleteMovement(entity.id)
        recalculateRolloverFrom(entity.periodId)
        LedgerResult.Deleted(movement)
    }

    private suspend fun restoreMovement(command: LedgerCommand.RestoreMovement): LedgerResult = database.withTransaction {
        val entity = command.movement.toEntity()
        dao.putMovement(entity)
        recalculateRolloverFrom(entity.periodId)
        LedgerResult.Success
    }

    private suspend fun createNextPeriod(requestedStartDay: Int?): LedgerResult = database.withTransaction {
        val previous = requireNotNull(dao.periods().maxByOrNull { it.startEpochDay }) { "No existe un periodo anterior" }
        createPeriodAfter(previous, requestedStartDay ?: previous.configuredStartDay, needsReview = false)
        LedgerResult.Success
    }

    private suspend fun catchUpPeriods(preferredStartDay: Int): LedgerResult = database.withTransaction {
        require(preferredStartDay in 1..31) { "Día de inicio inválido" }
        var previous = requireNotNull(dao.periods().maxByOrNull { it.startEpochDay }) { "No existe un periodo anterior" }
        val created = mutableListOf<PeriodEntity>()
        val todayEpochDay = today().toEpochDay()
        while (todayEpochDay >= previous.endExclusiveEpochDay) {
            previous = createPeriodAfter(previous, preferredStartDay, needsReview = false)
            created += previous
        }
        created.lastOrNull()?.let { current ->
            dao.updatePeriod(current.copy(needsReview = true))
        }
        LedgerResult.Success
    }

    private suspend fun markPeriodReviewed(periodId: String): LedgerResult = database.withTransaction {
        val period = requireNotNull(dao.period(periodId)) { "Periodo inexistente" }
        dao.updatePeriod(period.copy(needsReview = false))
        LedgerResult.Success
    }

    private suspend fun recalculateRolloverFrom(sourcePeriodId: String) {
        val periods = dao.periods().sortedBy { it.startEpochDay }
        val startIndex = periods.indexOfFirst { it.id == sourcePeriodId }
        if (startIndex < 0 || startIndex == periods.lastIndex) return

        val periodPockets = dao.periodPockets()
        val movements = dao.movements()
        val allocations = dao.allocations()
            .associateByTo(mutableMapOf()) { it.periodId to it.pocketId }

        for (index in startIndex until periods.lastIndex) {
            val source = periods[index]
            val target = periods[index + 1]
            val sourceSnapshots = periodPockets.filter { it.periodId == source.id }.associateBy { it.pocketId }
            val targetSnapshots = periodPockets.filter { it.periodId == target.id && !it.retired }
            val sourceMovements = movements.filter { it.periodId == source.id }

            targetSnapshots.forEach { targetSnapshot ->
                val pocketId = targetSnapshot.pocketId
                val sourceAllocation = allocations[source.id to pocketId]
                val netSpend = sourceMovements.filter { it.pocketId == pocketId }.sumOf {
                    if (it.type == MovementType.EXPENSE.name) it.sarAmountMinor else -it.sarAmountMinor
                }
                val sourceSnapshot = sourceSnapshots[pocketId]
                val rollover = PocketMath.rollover(
                    allocatedMinor = (sourceAllocation?.budgetMinor ?: 0) + (sourceAllocation?.rolloverMinor ?: 0),
                    netSpendMinor = netSpend,
                    enabled = sourceSnapshot?.rolloverEligible == true && !sourceSnapshot.retired,
                )
                val targetKey = target.id to pocketId
                val targetAllocation = allocations[targetKey]
                val updated = AllocationEntity(
                    periodId = target.id,
                    pocketId = pocketId,
                    budgetMinor = targetAllocation?.budgetMinor ?: 0,
                    rolloverMinor = rollover,
                )
                dao.putAllocation(updated)
                allocations[targetKey] = updated
            }
        }
    }

    private suspend fun createPeriodAfter(
        previous: PeriodEntity,
        preferredStartDay: Int,
        needsReview: Boolean,
    ): PeriodEntity {
        val previousSchedule = PeriodSchedule(
            start = LocalDate.ofEpochDay(previous.startEpochDay),
            endExclusive = LocalDate.ofEpochDay(previous.endExclusiveEpochDay),
            configuredStartDay = previous.configuredStartDay,
            isTransition = previous.isTransition,
        )
        val schedule = BudgetCalendar(previous.configuredStartDay, zoneId)
            .nextPeriodAfter(previousSchedule, preferredStartDay)
        val nextId = UUID.randomUUID().toString()
        val next = previous.copy(
            id = nextId,
            startEpochDay = schedule.start.toEpochDay(),
            endExclusiveEpochDay = schedule.endExclusive.toEpochDay(),
            configuredStartDay = schedule.configuredStartDay,
            isTransition = schedule.isTransition,
            needsReview = needsReview,
        )
        dao.putPeriod(next)
        val activePockets = dao.pockets().filterNot { it.archived }
        val previousPeriodPockets = dao.periodPockets()
            .filter { it.periodId == previous.id }
            .associateBy { it.pocketId }
        val previousAllocations = dao.allocations()
            .filter { it.periodId == previous.id }
            .associateBy { it.pocketId }
        val previousMovements = dao.movements().filter { it.periodId == previous.id }
        dao.putPeriodPockets(
            activePockets.map { pocket ->
                PeriodPocketEntity(nextId, pocket.id, rolloverEligible = pocket.rolloverEnabled, retired = false)
            }
        )
        val nextAllocations = activePockets.map { pocket ->
            val previousAllocation = previousAllocations[pocket.id]
            val spent = previousMovements.filter { it.pocketId == pocket.id }.sumOf {
                if (it.type == MovementType.EXPENSE.name) it.sarAmountMinor else -it.sarAmountMinor
            }
            val rollover = PocketMath.rollover(
                allocatedMinor = (previousAllocation?.budgetMinor ?: 0) + (previousAllocation?.rolloverMinor ?: 0),
                netSpendMinor = spent,
                enabled = previousPeriodPockets[pocket.id]?.rolloverEligible == true,
            )
            AllocationEntity(nextId, pocket.id, previousAllocation?.budgetMinor ?: 0, rollover)
        }
        dao.putAllocations(nextAllocations)
        return next
    }

    private suspend fun upsertPaymentMethod(command: LedgerCommand.UpsertPaymentMethod): LedgerResult = database.withTransaction {
        val name = command.name.trim()
        require(name.isNotEmpty()) { "Escribe un método de pago" }
        val methods = dao.paymentMethods()
        require(methods.none { it.id != command.id && it.name.equals(name, ignoreCase = true) }) {
            "Ya existe un método de pago con ese nombre"
        }
        val existing = methods.firstOrNull { it.id == command.id }
        dao.putPaymentMethod(PaymentMethodEntity(existing?.id ?: UUID.randomUUID().toString(), name, existing?.archived ?: false))
        LedgerResult.Success
    }

    private suspend fun archivePaymentMethod(command: LedgerCommand.ArchivePaymentMethod): LedgerResult = database.withTransaction {
        val existing = requireNotNull(dao.paymentMethods().firstOrNull { it.id == command.id }) { "Método inexistente" }
        dao.putPaymentMethod(existing.copy(archived = command.archived))
        LedgerResult.Success
    }

    private suspend fun upsertTemplate(command: LedgerCommand.UpsertTemplate): LedgerResult = database.withTransaction {
        require(command.name.isNotBlank() && command.amountMinor > 0) { "Completa la plantilla" }
        val pocket = requireNotNull(dao.pockets().firstOrNull { it.id == command.pocketId }) { "Pocket inexistente" }
        require(!pocket.archived) { "El Pocket está archivado" }
        val existing = dao.templates().firstOrNull { it.id == command.id }
        dao.putTemplate(
            RecurringTemplateEntity(
                existing?.id ?: UUID.randomUUID().toString(),
                command.name.trim(),
                command.amountMinor,
                command.pocketId,
                command.paymentMethodId,
                existing?.archived ?: false,
            )
        )
        LedgerResult.Success
    }

    private suspend fun archiveTemplate(command: LedgerCommand.ArchiveTemplate): LedgerResult = database.withTransaction {
        val existing = requireNotNull(dao.templates().firstOrNull { it.id == command.id }) { "Plantilla inexistente" }
        if (!command.archived) {
            val pocket = requireNotNull(dao.pockets().firstOrNull { it.id == existing.pocketId }) { "Pocket inexistente" }
            require(!pocket.archived) { "El Pocket está archivado" }
        }
        dao.putTemplate(existing.copy(archived = command.archived))
        LedgerResult.Success
    }

    private fun buildState(
        periodEntities: List<PeriodEntity>,
        pocketEntities: List<PocketEntity>,
        allocations: List<AllocationEntity>,
        periodPockets: List<PeriodPocketEntity>,
        rolloverReleases: List<RolloverReleaseEntity>,
        methodEntities: List<PaymentMethodEntity>,
        movementEntities: List<MovementEntity>,
        templateEntities: List<RecurringTemplateEntity>,
    ): LedgerState {
        val periods = periodEntities.map { it.toModel() }
        val today = today()
        val current = periods.firstOrNull { today >= it.start && today < it.endExclusive }
        val pocketsById = pocketEntities.associateBy { it.id }
        val methodsById = methodEntities.associateBy { it.id }
        val movements = movementEntities.map { it.toModel(pocketsById, methodsById) }
        if (current == null) return LedgerState(periods = periods, movements = movements)
        fun summariesFor(periodId: String): List<PocketPeriodSummary> {
            val periodMovements = movements.filter { it.periodId == periodId }
            val periodAllocations = allocations.filter { it.periodId == periodId }.associateBy { it.pocketId }
            val periodSnapshots = periodPockets.filter { it.periodId == periodId }.associateBy { it.pocketId }
            val periodReleases = rolloverReleases.filter { it.periodId == periodId }.associateBy { it.pocketId }
            return periodSnapshots.values.mapNotNull { snapshot ->
                val pocketEntity = pocketsById[snapshot.pocketId] ?: return@mapNotNull null
                val allocation = periodAllocations[pocketEntity.id]
                val pocketMovements = periodMovements.filter { it.pocketId == pocketEntity.id }
                val expenses = pocketMovements.filter { it.type == MovementType.EXPENSE }.sumOf { it.sarAmountMinor }
                val refunds = pocketMovements.filter { it.type == MovementType.REFUND }.sumOf { it.sarAmountMinor }
                val math = PocketMath.summary(allocation?.budgetMinor ?: 0, allocation?.rolloverMinor ?: 0, expenses, refunds)
                PocketPeriodSummary(
                    pocket = pocketEntity.toModel(),
                    budgetMinor = math.budgetMinor,
                    rolloverMinor = math.rolloverMinor,
                    rolloverEligible = snapshot.rolloverEligible,
                    retiredThisPeriod = snapshot.retired,
                    rolloverReleasedMinor = periodReleases[pocketEntity.id]?.amountMinor ?: 0,
                    netSpendMinor = math.netSpendMinor,
                    availabilityMinor = math.availabilityMinor,
                    consumedPercent = math.consumedPercent,
                    atRisk = math.atRisk,
                    exhausted = math.exhausted,
                )
            }
        }
        val allSummaries = periods.associate { it.id to summariesFor(it.id) }
        val summaries = allSummaries.getValue(current.id)
        val previous = periods.filter { it.start < current.start }.maxByOrNull { it.start }
        val previousSpend = previous?.let { period ->
            movements.filter { it.periodId == period.id }.sumOf {
                if (it.type == MovementType.EXPENSE) it.sarAmountMinor else -it.sarAmountMinor
            }
        }
        val comparisonMode = if (current.isTransition) ComparisonMode.DAILY_PACE else ComparisonMode.TOTAL_SPEND
        val previousComparison = when (comparisonMode) {
            ComparisonMode.TOTAL_SPEND -> previousSpend
            ComparisonMode.DAILY_PACE -> previous?.let { period ->
                previousSpend?.div(
                    (period.endExclusive.toEpochDay() - period.start.toEpochDay()).coerceAtLeast(1),
                )
            }
        }
        val elapsed = ((today.coerceAtMost(current.endExclusive.minusDays(1)).toEpochDay() - current.start.toEpochDay()) + 1)
            .toInt().coerceAtLeast(1)
        val totalDays = (current.endExclusive.toEpochDay() - current.start.toEpochDay()).toInt()
        val netSpend = summaries.sumOf { it.netSpendMinor }
        return LedgerState(
            periods = periods,
            currentPeriod = current,
            pockets = summaries,
            pocketSummariesByPeriod = allSummaries,
            movements = movements,
            paymentMethods = methodEntities.map { PaymentMethod(it.id, it.name, it.archived) },
            templates = templateEntities.map { RecurringTemplate(it.id, it.name, it.amountMinor, it.pocketId, it.paymentMethodId, it.archived) },
            unallocatedMinor = current.newFundsMinor - summaries.sumOf { it.budgetMinor },
            newFundsMinor = current.newFundsMinor,
            rolloverTotalMinor = summaries.sumOf { it.rolloverMinor },
            netSpendMinor = netSpend,
            trackedAvailabilityMinor = summaries.sumOf { it.availabilityMinor },
            previousPeriodNetSpendMinor = previousSpend,
            comparisonMode = comparisonMode,
            previousPeriodComparisonMinor = previousComparison,
            elapsedDays = elapsed,
            totalDays = totalDays,
            projectionMinor = PocketMath.project(netSpend, elapsed, totalDays).amountMinor,
            currentLocalDate = today,
            currentInstantMillis = clock.instant().toEpochMilli(),
        )
    }

    private fun today(): LocalDate = clock.instant().atZone(zoneId).toLocalDate()

    override suspend fun exportBackup(): ByteArray = withContext(codecDispatcher) { BackupCodec.encode(database) }
    override suspend fun previewBackup(bytes: ByteArray): BackupPreview = withContext(codecDispatcher) { BackupCodec.preview(bytes) }
    override suspend fun restoreBackup(bytes: ByteArray): LedgerResult = withContext(codecDispatcher) { BackupCodec.restore(database, bytes) }
    override suspend fun exportCsv(): ByteArray = withContext(codecDispatcher) { BackupCodec.csv(database) }

    private companion object {
        val INITIAL_POCKETS = listOf(
            "Supermercado" to PocketIconKey.SUPERMARKET,
            "Restaurantes/café" to PocketIconKey.RESTAURANT,
            "Transporte" to PocketIconKey.TRANSPORT,
            "Universidad" to PocketIconKey.UNIVERSITY,
            "Salud" to PocketIconKey.HEALTH,
            "Viajes" to PocketIconKey.TRAVEL,
            "Ocio" to PocketIconKey.LEISURE,
            "Regalos" to PocketIconKey.GIFTS,
            "Emergencia" to PocketIconKey.EMERGENCY,
            "Otros" to PocketIconKey.OTHER,
        )
    }

    private data class BudgetData(
        val periods: List<PeriodEntity>,
        val pockets: List<PocketEntity>,
        val allocations: List<AllocationEntity>,
        val periodPockets: List<PeriodPocketEntity>,
        val rolloverReleases: List<RolloverReleaseEntity>,
    )
}

private fun PeriodEntity.toModel() = Period(
    id = id,
    start = LocalDate.ofEpochDay(startEpochDay),
    endExclusive = LocalDate.ofEpochDay(endExclusiveEpochDay),
    newFundsMinor = newFundsMinor,
    configuredStartDay = configuredStartDay,
    isTransition = isTransition,
    needsReview = needsReview,
)

private fun PocketEntity.toModel() = Pocket(id, name, PocketIconKey.fromStored(iconKey, name), sortOrder, archived, rolloverEnabled)

private fun MovementEntity.toModel(
    pockets: Map<String, PocketEntity>,
    methods: Map<String, PaymentMethodEntity>,
) = Movement(
    id = id,
    pocketId = pocketId,
    pocketName = pockets[pocketId]?.name ?: "Pocket archivado",
    periodId = periodId,
    type = MovementType.valueOf(type),
    sarAmountMinor = sarAmountMinor,
    occurredAtUtcMillis = occurredAtUtcMillis,
    localDate = LocalDate.ofEpochDay(localEpochDay),
    zoneId = zoneId,
    merchant = merchant,
    note = note,
    paymentMethodId = paymentMethodId,
    paymentMethodName = paymentMethodId?.let { methods[it]?.name },
    originalAmountMinor = originalAmountMinor,
    originalCurrencyCode = originalCurrencyCode,
    conversionStatus = ConversionStatus.valueOf(conversionStatus),
    rate = rate,
)

private fun LedgerCommand.AddMovement.toEntity(periodId: String, zoneId: String) = MovementEntity(
    id = id ?: UUID.randomUUID().toString(),
    periodId = periodId,
    pocketId = pocketId,
    type = type.name,
    sarAmountMinor = sarAmountMinor,
    occurredAtUtcMillis = occurredAtUtcMillis,
    localEpochDay = localDate.toEpochDay(),
    zoneId = zoneId,
    merchant = merchant?.trim()?.takeIf { it.isNotEmpty() },
    note = note?.trim()?.takeIf { it.isNotEmpty() },
    paymentMethodId = paymentMethodId,
    originalAmountMinor = originalAmountMinor,
    originalCurrencyCode = originalCurrencyCode,
    conversionStatus = conversionStatus.name,
    rate = rate,
)

private fun Movement.toEntity() = MovementEntity(
    id, periodId, pocketId, type.name, sarAmountMinor, occurredAtUtcMillis, localDate.toEpochDay(), zoneId,
    merchant, note, paymentMethodId, originalAmountMinor, originalCurrencyCode, conversionStatus.name, rate,
)
