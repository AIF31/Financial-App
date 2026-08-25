package com.aif31.pocket.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.aif31.pocket.domain.BudgetCalendar
import com.aif31.pocket.domain.PocketMath
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomPocketLedger(
    private val database: FinanceDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.of("Asia/Riyadh"),
) : PocketLedger {
    private val dao = database.financeDao()

    private val budgetData = combine(
        dao.observePeriods(),
        dao.observePockets(),
        dao.observeAllocations(),
    ) { periods, pockets, allocations -> Triple(periods, pockets, allocations) }

    private val activityData = combine(
        dao.observePaymentMethods(),
        dao.observeMovements(),
        dao.observeTemplates(),
    ) { methods, movements, templates -> Triple(methods, movements, templates) }

    override val state: Flow<LedgerState> = combine(budgetData, activityData) { budget, activity ->
        buildState(
            periodEntities = budget.first,
            pocketEntities = budget.second,
            allocations = budget.third,
            methodEntities = activity.first,
            movementEntities = activity.second,
            templateEntities = activity.third,
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
        dao.putPockets(
            INITIAL_POCKETS.mapIndexed { index, (name, iconKey) ->
                PocketEntity(UUID.randomUUID().toString(), name, iconKey.name, index, archived = false, rolloverEnabled = false)
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
        val existing = dao.allocation(command.periodId, command.pocketId)
        val resultingTotal = dao.allocated(command.periodId) - (existing?.budgetMinor ?: 0) + command.amountMinor
        require(resultingTotal <= period.newFundsMinor) { "No puedes asignar más que los fondos nuevos" }
        dao.putAllocation(
            AllocationEntity(command.periodId, command.pocketId, command.amountMinor, existing?.rolloverMinor ?: 0)
        )
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
        dao.putPocket(
            PocketEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = name,
                iconKey = (command.iconKey ?: existing?.let { PocketIconKey.fromStored(it.iconKey, it.name) } ?: PocketIconKey.forName(name)).name,
                sortOrder = existing?.sortOrder ?: nextOrder,
                archived = existing?.archived ?: false,
                rolloverEnabled = command.rolloverEnabled,
            )
        )
        LedgerResult.Success
    }

    private suspend fun archivePocket(command: LedgerCommand.ArchivePocket): LedgerResult = database.withTransaction {
        val pocket = requireNotNull(dao.pockets().firstOrNull { it.id == command.pocketId }) { "Pocket inexistente" }
        dao.putPocket(pocket.copy(archived = command.archived))
        LedgerResult.Success
    }

    private suspend fun movePocket(command: LedgerCommand.MovePocket): LedgerResult = database.withTransaction {
        val pockets = dao.pockets().sortedBy { it.sortOrder }.toMutableList()
        val from = pockets.indexOfFirst { it.id == command.pocketId }
        require(from >= 0) { "Pocket inexistente" }
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
        require(dao.pockets().any { it.id == command.pocketId }) { "Pocket inexistente" }
        dao.putMovement(command.toEntity(period.id, zoneId.id))
        LedgerResult.Success
    }

    private suspend fun deleteMovement(command: LedgerCommand.DeleteMovement): LedgerResult = database.withTransaction {
        val entity = requireNotNull(dao.movement(command.movementId)) { "Movimiento inexistente" }
        val movement = entity.toModel(dao.pockets().associateBy { it.id }, dao.paymentMethods().associateBy { it.id })
        dao.deleteMovement(entity.id)
        LedgerResult.Deleted(movement)
    }

    private suspend fun restoreMovement(command: LedgerCommand.RestoreMovement): LedgerResult = database.withTransaction {
        dao.putMovement(command.movement.toEntity())
        LedgerResult.Success
    }

    private suspend fun createNextPeriod(requestedStartDay: Int?): LedgerResult = database.withTransaction {
        val previous = requireNotNull(dao.periods().maxByOrNull { it.startEpochDay }) { "No existe un periodo anterior" }
        val startDay = requestedStartDay ?: previous.configuredStartDay
        require(startDay in 1..31) { "Día de inicio inválido" }
        val nextStart = LocalDate.ofEpochDay(previous.endExclusiveEpochDay)
        val nextBounds = BudgetCalendar(startDay, zoneId).periodContaining(nextStart)
        val nextId = UUID.randomUUID().toString()
        dao.putPeriod(
            previous.copy(
                id = nextId,
                startEpochDay = nextStart.toEpochDay(),
                endExclusiveEpochDay = nextBounds.endExclusive.toEpochDay(),
                configuredStartDay = startDay,
            )
        )
        val pockets = dao.pockets().associateBy { it.id }
        val previousAllocations = dao.allocations().filter { it.periodId == previous.id }
        val previousMovements = dao.movements().filter { it.periodId == previous.id }
        val nextAllocations = previousAllocations.map { allocation ->
            val spent = previousMovements.filter { it.pocketId == allocation.pocketId }.sumOf {
                if (it.type == MovementType.EXPENSE.name) it.sarAmountMinor else -it.sarAmountMinor
            }
            val rollover = PocketMath.rollover(
                allocatedMinor = allocation.budgetMinor + allocation.rolloverMinor,
                netSpendMinor = spent,
                enabled = pockets[allocation.pocketId]?.rolloverEnabled == true,
            )
            allocation.copy(periodId = nextId, rolloverMinor = rollover)
        }
        dao.putAllocations(nextAllocations)
        LedgerResult.Success
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
        dao.putTemplate(existing.copy(archived = command.archived))
        LedgerResult.Success
    }

    private fun buildState(
        periodEntities: List<PeriodEntity>,
        pocketEntities: List<PocketEntity>,
        allocations: List<AllocationEntity>,
        methodEntities: List<PaymentMethodEntity>,
        movementEntities: List<MovementEntity>,
        templateEntities: List<RecurringTemplateEntity>,
    ): LedgerState {
        val periods = periodEntities.map { it.toModel() }
        val today = today()
        val current = periods.firstOrNull { today >= it.start && today < it.endExclusive } ?: periods.lastOrNull()
        val pocketsById = pocketEntities.associateBy { it.id }
        val methodsById = methodEntities.associateBy { it.id }
        val movements = movementEntities.map { it.toModel(pocketsById, methodsById) }
        if (current == null) return LedgerState(periods = periods, movements = movements)
        fun summariesFor(periodId: String): List<PocketPeriodSummary> {
            val periodMovements = movements.filter { it.periodId == periodId }
            val periodAllocations = allocations.filter { it.periodId == periodId }.associateBy { it.pocketId }
            return pocketEntities.map { pocketEntity ->
                val allocation = periodAllocations[pocketEntity.id]
                val pocketMovements = periodMovements.filter { it.pocketId == pocketEntity.id }
                val expenses = pocketMovements.filter { it.type == MovementType.EXPENSE }.sumOf { it.sarAmountMinor }
                val refunds = pocketMovements.filter { it.type == MovementType.REFUND }.sumOf { it.sarAmountMinor }
                val math = PocketMath.summary(allocation?.budgetMinor ?: 0, allocation?.rolloverMinor ?: 0, expenses, refunds)
                PocketPeriodSummary(
                    pocket = pocketEntity.toModel(),
                    budgetMinor = math.budgetMinor,
                    rolloverMinor = math.rolloverMinor,
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
        val previousId = periods.filter { it.start < current.start }.maxByOrNull { it.start }?.id
        val previousSpend = previousId?.let { id ->
            movements.filter { it.periodId == id }.sumOf {
                if (it.type == MovementType.EXPENSE) it.sarAmountMinor else -it.sarAmountMinor
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
            elapsedDays = elapsed,
            totalDays = totalDays,
            projectionMinor = PocketMath.project(netSpend, elapsed, totalDays).amountMinor,
            currentLocalDate = today,
            currentInstantMillis = clock.instant().toEpochMilli(),
        )
    }

    private fun today(): LocalDate = clock.instant().atZone(zoneId).toLocalDate()

    override suspend fun exportBackup(): ByteArray = BackupCodec.encode(database)
    override suspend fun previewBackup(bytes: ByteArray): BackupPreview = BackupCodec.preview(bytes)
    override suspend fun restoreBackup(bytes: ByteArray): LedgerResult = BackupCodec.restore(database, bytes)
    override suspend fun exportCsv(): ByteArray = BackupCodec.csv(database)

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
}

private fun PeriodEntity.toModel() = Period(
    id = id,
    start = LocalDate.ofEpochDay(startEpochDay),
    endExclusive = LocalDate.ofEpochDay(endExclusiveEpochDay),
    newFundsMinor = newFundsMinor,
    configuredStartDay = configuredStartDay,
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
