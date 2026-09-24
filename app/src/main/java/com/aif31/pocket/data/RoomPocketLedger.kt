package com.aif31.pocket.data

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.aif31.pocket.domain.BudgetCalendar
import com.aif31.pocket.domain.FrozenRate
import com.aif31.pocket.domain.PeriodSchedule
import com.aif31.pocket.domain.PocketMath
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.domain.sumMoneyExact
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomPocketLedger(
    private val database: FinanceDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.of("Asia/Riyadh"),
    private val codecDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val recordNotificationConfirmation: (amountCorrected: Boolean, currencyCorrected: Boolean) -> Unit = { _, _ -> },
) : PocketLedger {
    private val dao = database.financeDao()
    private val restoreMutex = Mutex()
    private val dateRefreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val state: Flow<LedgerState> = merge(
        database.invalidationTracker.createFlow(
            "periods",
            "pockets",
            "allocations",
            "period_pockets",
            "rollover_releases",
            "payment_methods",
            "movements",
            "recurring_templates",
            "pending_currency_change",
            "ledger_preferences",
            "movement_suggestions",
            emitInitialState = true,
        ).map { Unit },
        dateRefreshes,
    ).map {
        val snapshot = database.withTransaction {
            LedgerSnapshot(
                periods = dao.periods(),
                pockets = dao.pockets(),
                allocations = dao.allocations(),
                periodPockets = dao.periodPockets(),
                rolloverReleases = dao.rolloverReleases(),
                paymentMethods = dao.paymentMethods(),
                movements = dao.movements(),
                templates = dao.templates(),
                pendingCurrencyChange = dao.pendingCurrencyChange(),
                ledgerPreferences = dao.ledgerPreferences(),
                suggestions = dao.pendingMovementSuggestions(),
            )
        }
        buildState(
            periodEntities = snapshot.periods,
            pocketEntities = snapshot.pockets,
            allocations = snapshot.allocations,
            periodPockets = snapshot.periodPockets,
            rolloverReleases = snapshot.rolloverReleases,
            methodEntities = snapshot.paymentMethods,
            movementEntities = snapshot.movements,
            templateEntities = snapshot.templates,
            pendingCurrencyChangeEntity = snapshot.pendingCurrencyChange,
            ledgerPreferencesEntity = snapshot.ledgerPreferences,
            suggestionEntities = snapshot.suggestions,
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
            is LedgerCommand.ConfirmSuggestion -> confirmSuggestion(command)
            is LedgerCommand.RejectSuggestion -> rejectSuggestion(command.suggestionId)
            is LedgerCommand.DeleteMovement -> deleteMovement(command)
            is LedgerCommand.RestoreMovement -> restoreMovement(command)
            is LedgerCommand.CreateNextPeriod -> createNextPeriod(command.startDay)
            is LedgerCommand.CatchUpPeriods -> catchUpPeriods(command.preferredStartDay)
            is LedgerCommand.MarkPeriodReviewed -> markPeriodReviewed(command.periodId)
            is LedgerCommand.ScheduleCurrencyChange -> scheduleCurrencyChange(command)
            LedgerCommand.CancelCurrencyChange -> cancelCurrencyChange()
            is LedgerCommand.UpsertPaymentMethod -> upsertPaymentMethod(command)
            is LedgerCommand.ArchivePaymentMethod -> archivePaymentMethod(command)
            is LedgerCommand.SetDefaultPaymentMethod -> setDefaultPaymentMethod(command)
            is LedgerCommand.UpsertTemplate -> upsertTemplate(command)
            is LedgerCommand.ArchiveTemplate -> archiveTemplate(command)
        }
    } catch (error: IllegalArgumentException) {
        LedgerResult.Rejected(error.message ?: "Datos inválidos")
    } catch (_: ArithmeticException) {
        LedgerResult.Rejected("El resultado monetario supera el rango admitido")
    } catch (_: SQLiteConstraintException) {
        LedgerResult.Rejected("Ya existe un elemento con esos datos")
    } catch (_: SQLiteException) {
        LedgerResult.Rejected("No se pudo guardar el cambio", RejectionKind.PERSISTENCE)
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
                accountingCurrencyCode = command.accountingCurrency.name,
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
        val paymentMethods = listOf("Efectivo", "Tarjeta")
            .map { PaymentMethodEntity(UUID.randomUUID().toString(), it, archived = false) }
        dao.putPaymentMethods(paymentMethods)
        dao.putLedgerPreferences(
            LedgerPreferencesEntity(defaultPaymentMethodId = paymentMethods.single { it.name == "Tarjeta" }.id)
        )
        LedgerResult.Success
    }

    private suspend fun updateFunds(command: LedgerCommand.UpdatePeriodFunds): LedgerResult = database.withTransaction {
        require(command.newFundsMinor >= 0) { "Los fondos no pueden ser negativos" }
        val periods = dao.periods()
        val period = requireNotNull(periods.firstOrNull { it.id == command.periodId }) { "Periodo inexistente" }
        val updated = period.copy(newFundsMinor = command.newFundsMinor)
        validateLedgerProjection(
            periods.filterNot { it.id == updated.id } + updated,
            dao.allocations(),
            dao.movements(),
            dao.rolloverReleases(),
        )
        dao.updatePeriod(updated)
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
        val updated = AllocationEntity(command.periodId, command.pocketId, command.amountMinor, existing?.rolloverMinor ?: 0)
        val allocations = dao.allocations().filterNot {
            it.periodId == command.periodId && it.pocketId == command.pocketId
        } + updated
        rolloverProjection(
            command.periodId,
            dao.periods(),
            dao.periodPockets(),
            dao.movements(),
            allocations,
            dao.rolloverReleases(),
        )
        dao.putAllocation(updated)
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
        val updatedPocket = PocketEntity(
            id = pocketId,
            name = name,
            iconKey = (command.iconKey ?: existing?.let { PocketIconKey.fromStored(it.iconKey, it.name) } ?: PocketIconKey.forName(name)).name,
            sortOrder = existing?.sortOrder ?: nextOrder,
            archived = existing?.archived ?: false,
            rolloverEnabled = command.rolloverEnabled,
        )
        val todayEpochDay = LocalDate.now(clock.withZone(zoneId)).toEpochDay()
        val periods = dao.periods()
        val currentPeriod = periods.firstOrNull {
            todayEpochDay >= it.startEpochDay && todayEpochDay < it.endExclusiveEpochDay
        }
        val periodPockets = dao.periodPockets()
        val updatedSnapshot = currentPeriod?.let { period ->
            val currentSnapshot = periodPockets.firstOrNull {
                it.periodId == period.id && it.pocketId == pocketId
            }
            PeriodPocketEntity(
                periodId = period.id,
                pocketId = pocketId,
                rolloverEligible = command.rolloverEnabled,
                retired = currentSnapshot?.retired ?: false,
            )
        }
        if (currentPeriod != null && updatedSnapshot != null) {
            rolloverProjection(
                currentPeriod.id,
                periods,
                periodPockets.filterNot {
                    it.periodId == updatedSnapshot.periodId && it.pocketId == updatedSnapshot.pocketId
                } + updatedSnapshot,
                dao.movements(),
                dao.allocations(),
                dao.rolloverReleases(),
            )
        }
        dao.putPocket(updatedPocket)
        updatedSnapshot?.let { dao.putPeriodPocket(it) }
        currentPeriod?.let { recalculateRolloverFrom(it.id) }
        LedgerResult.Success
    }

    private suspend fun archivePocket(command: LedgerCommand.ArchivePocket): LedgerResult = database.withTransaction {
        val pocket = requireNotNull(dao.pockets().firstOrNull { it.id == command.pocketId }) { "Pocket inexistente" }
        if (!command.archived) {
            val periods = dao.periods()
            val currentPeriod = periods.firstOrNull { today().toEpochDay() in it.startEpochDay until it.endExclusiveEpochDay }
            currentPeriod?.let { period ->
                val periodPockets = dao.periodPockets()
                val snapshot = periodPockets.firstOrNull { it.periodId == period.id && it.pocketId == pocket.id }
                val restoredSnapshot = snapshot?.copy(retired = false)
                    ?: PeriodPocketEntity(period.id, pocket.id, pocket.rolloverEnabled, retired = false)
                val releases = dao.rolloverReleases()
                val release = releases.firstOrNull { it.periodId == period.id && it.pocketId == pocket.id }
                val allocations = dao.allocations()
                val restoredAllocation = release?.let {
                    val current = allocations.firstOrNull { value ->
                        value.periodId == period.id && value.pocketId == pocket.id
                    }
                    AllocationEntity(period.id, pocket.id, current?.budgetMinor ?: 0, it.amountMinor)
                }
                rolloverProjection(
                    period.id,
                    periods,
                    periodPockets.filterNot { it.periodId == period.id && it.pocketId == pocket.id } + restoredSnapshot,
                    dao.movements(),
                    restoredAllocation?.let { restored ->
                        allocations.filterNot { it.periodId == period.id && it.pocketId == pocket.id } + restored
                    } ?: allocations,
                    releases.filterNot { it.periodId == period.id && it.pocketId == pocket.id },
                )
                dao.putPocket(pocket.copy(archived = false))
                restoredAllocation?.let { dao.putAllocation(it) }
                release?.let { dao.deleteRolloverRelease(period.id, pocket.id) }
                dao.putPeriodPocket(restoredSnapshot)
                recalculateRolloverFrom(period.id)
            }
            if (currentPeriod == null) dao.putPocket(pocket.copy(archived = false))
            return@withTransaction LedgerResult.Success
        }
        val activeDependencies = dao.templates().filter { it.pocketId == pocket.id && !it.archived }
        require(activeDependencies.isEmpty()) {
            "Archiva primero estas plantillas: ${activeDependencies.joinToString(", ") { it.name }}"
        }
        val todayEpochDay = today().toEpochDay()
        val periods = dao.periods()
        val currentPeriod = requireNotNull(periods.firstOrNull {
            todayEpochDay >= it.startEpochDay && todayEpochDay < it.endExclusiveEpochDay
        }) { "No hay un periodo activo" }
        val periodPockets = dao.periodPockets()
        val snapshot = requireNotNull(periodPockets.firstOrNull {
            it.periodId == currentPeriod.id && it.pocketId == pocket.id
        }) { "El Pocket no está activo en este periodo" }
        val allocations = dao.allocations()
        val allocation = allocations.firstOrNull { it.periodId == currentPeriod.id && it.pocketId == pocket.id }
        val movements = dao.movements()
        val pocketMovements = movements.filter {
            it.periodId == currentPeriod.id && it.pocketId == pocket.id
        }
        val expenses = pocketMovements.filter { it.type == MovementType.EXPENSE.name }
            .map { it.accountingAmountMinor }.sumMoneyExact()
        val refunds = pocketMovements.filter { it.type == MovementType.REFUND.name }
            .map { it.accountingAmountMinor }.sumMoneyExact()
        val availability = PocketMath.summary(
            budgetMinor = allocation?.budgetMinor ?: 0,
            rolloverMinor = allocation?.rolloverMinor ?: 0,
            expensesMinor = expenses,
            refundsMinor = refunds,
        ).availabilityMinor
        val releasedRollover = minOf(allocation?.rolloverMinor ?: 0, availability.coerceAtLeast(0))
        val releases = dao.rolloverReleases()
        val release = RolloverReleaseEntity(currentPeriod.id, pocket.id, releasedRollover)
        val currentAllocation = AllocationEntity(currentPeriod.id, pocket.id, budgetMinor = 0, rolloverMinor = 0)
        val futurePeriodIds = periods
            .filter { it.startEpochDay > currentPeriod.startEpochDay }
            .mapTo(mutableSetOf()) { it.id }
        val prospectiveAllocations = allocations.filterNot {
            it.pocketId == pocket.id && (it.periodId == currentPeriod.id || it.periodId in futurePeriodIds)
        } + currentAllocation
        val prospectiveReleases = releases.filterNot {
            it.periodId == currentPeriod.id && it.pocketId == pocket.id
        } + listOfNotNull(release.takeIf { releasedRollover > 0 })
        val retiredSnapshot = snapshot.copy(retired = true)
        val prospectivePeriodPockets = periodPockets.filterNot {
            it.pocketId == pocket.id && (it.periodId == currentPeriod.id || it.periodId in futurePeriodIds)
        } + retiredSnapshot
        rolloverProjection(
            currentPeriod.id,
            periods,
            prospectivePeriodPockets,
            movements,
            prospectiveAllocations,
            prospectiveReleases,
        )
        if (releasedRollover > 0) {
            dao.putRolloverRelease(release)
        } else {
            dao.deleteRolloverRelease(currentPeriod.id, pocket.id)
        }
        dao.putAllocation(currentAllocation)
        dao.putPeriodPocket(retiredSnapshot)
        dao.putPocket(pocket.copy(archived = true))
        if (futurePeriodIds.isNotEmpty()) {
            dao.deleteAllocations(pocket.id, futurePeriodIds.toList())
            dao.deletePeriodPockets(pocket.id, futurePeriodIds.toList())
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
        if (command.createOnly && command.id != null && dao.movement(command.id) != null) {
            return@withTransaction LedgerResult.Success
        }
        require(command.accountingAmountMinor > 0) { "El importe debe ser mayor que cero" }
        require(command.rate == null || (command.rate.toBigDecimalOrNull()?.signum() ?: 0) > 0) { "Tipo de cambio inválido" }
        val periods = dao.periods()
        val period = requireNotNull(periods.firstOrNull {
            command.localDate.toEpochDay() >= it.startEpochDay && command.localDate.toEpochDay() < it.endExclusiveEpochDay
        }) { "La fecha no pertenece a un periodo existente" }
        val accountingCurrency = SupportedCurrency.fromCode(period.accountingCurrencyCode)
        val originalCurrency = command.originalCurrencyCode?.let(SupportedCurrency::fromCode) ?: accountingCurrency
        require(command.accountingCurrency == null || command.accountingCurrency == accountingCurrency) {
            "La moneda contable no coincide con el periodo"
        }
        if (originalCurrency == accountingCurrency) {
            require(
                command.originalAmountMinor == null && command.conversionStatus == ConversionStatus.CONFIRMED &&
                    command.rate == null && command.conversionEffectiveDate == null && command.conversionSource == null
            ) { "La conversión no corresponde a un movimiento en la moneda contable" }
        } else {
            require((command.originalAmountMinor ?: 0) > 0) { "Falta el importe en la moneda original" }
            // Legacy manual conversions have no provider provenance. Preserve them unchanged.
            if (command.conversionEffectiveDate != null || command.conversionSource != null) {
                require(command.rate != null) { "Falta el tipo de cambio confirmado" }
                val effectiveDate = requireNotNull(command.conversionEffectiveDate) { "Falta la fecha efectiva del tipo de cambio" }
                require(!effectiveDate.isAfter(command.localDate) && !effectiveDate.isBefore(command.localDate.minusDays(7))) {
                    "Fecha efectiva del tipo de cambio inválida"
                }
                require(!command.conversionSource.isNullOrBlank()) { "Falta la fuente del tipo de cambio" }
            }
        }
        val existing = command.id?.let { dao.movement(it) }
        val pocket = requireNotNull(dao.pockets().firstOrNull { it.id == command.pocketId }) { "Pocket inexistente" }
        val editsSamePocket = existing?.pocketId == pocket.id
        require(!pocket.archived || editsSamePocket) { "El Pocket está archivado" }
        val snapshot = dao.periodPockets().firstOrNull { it.periodId == period.id && it.pocketId == command.pocketId }
        require(snapshot != null && (!snapshot.retired || editsSamePocket)) { "El Pocket no está activo en este periodo" }
        val updated = command.toEntity(period.id, zoneId.id, originalCurrency.name)
        val movements = dao.movements().filterNot { it.id == updated.id } + updated
        val allocations = dao.allocations()
        val releases = dao.rolloverReleases()
        val sourcePeriodId = listOfNotNull(existing?.periodId, period.id)
            .minBy { id -> periods.first { it.id == id }.startEpochDay }
        rolloverProjection(sourcePeriodId, periods, dao.periodPockets(), movements, allocations, releases)
        dao.putMovement(updated)
        recalculateRolloverFrom(sourcePeriodId)
        LedgerResult.Success
    }

    private suspend fun confirmSuggestion(command: LedgerCommand.ConfirmSuggestion): LedgerResult {
        var corrections: Pair<Boolean, Boolean>? = null
        val result = database.withTransaction {
            if (command.submissionId != null && dao.movement(command.submissionId) != null) {
                return@withTransaction LedgerResult.Success
            }
            val suggestion = dao.movementSuggestion(command.suggestionId)
            require(suggestion?.status == "PENDING" && suggestion.expiresAtUtcMillis > clock.millis()) { "La sugerencia ya no está disponible" }
            // A confirmed suggestion always creates a new Movement. A caller-provided ID could overwrite one.
            val movementResult = addMovement(command.movement.copy(
                id = command.submissionId,
                createOnly = command.submissionId != null,
            ))
            if (movementResult == LedgerResult.Success) {
                dao.putMovementSuggestion(suggestion.asTombstone("CONFIRMED"))
                val confirmedAmount = command.movement.originalAmountMinor ?: command.movement.accountingAmountMinor
                corrections = (confirmedAmount != suggestion.amountMinor) to
                    (command.movement.originalCurrencyCode != suggestion.currencyCode)
            }
            movementResult
        }
        corrections?.let { (amountCorrected, currencyCorrected) ->
            runCatching { recordNotificationConfirmation(amountCorrected, currencyCorrected) }
        }
        return result
    }

    private suspend fun rejectSuggestion(id: String): LedgerResult = database.withTransaction {
        val suggestion = dao.movementSuggestion(id)
        require(suggestion?.status == "PENDING" && suggestion.expiresAtUtcMillis > clock.millis()) { "La sugerencia ya no está disponible" }
        dao.putMovementSuggestion(suggestion.asTombstone("REJECTED"))
        LedgerResult.Success
    }

    private suspend fun deleteMovement(command: LedgerCommand.DeleteMovement): LedgerResult = database.withTransaction {
        val entity = requireNotNull(dao.movement(command.movementId)) { "Movimiento inexistente" }
        val movement = entity.toModel(dao.pockets().associateBy { it.id }, dao.paymentMethods().associateBy { it.id })
        requireNotNull(dao.period(entity.periodId)) { "Periodo inexistente" }
        val prospectiveMovements = dao.movements().filterNot { it.id == entity.id }
        rolloverProjection(
            entity.periodId,
            dao.periods(),
            dao.periodPockets(),
            prospectiveMovements,
            dao.allocations(),
            dao.rolloverReleases(),
        )
        dao.deleteMovement(entity.id)
        recalculateRolloverFrom(entity.periodId)
        LedgerResult.Deleted(movement)
    }

    private suspend fun restoreMovement(command: LedgerCommand.RestoreMovement): LedgerResult = database.withTransaction {
        val entity = command.movement.toEntity()
        requireNotNull(dao.period(entity.periodId)) { "Periodo inexistente" }
        val prospectiveMovements = dao.movements().filterNot { it.id == entity.id } + entity
        rolloverProjection(
            entity.periodId,
            dao.periods(),
            dao.periodPockets(),
            prospectiveMovements,
            dao.allocations(),
            dao.rolloverReleases(),
        )
        dao.putMovement(entity)
        recalculateRolloverFrom(entity.periodId)
        LedgerResult.Success
    }

    private suspend fun createNextPeriod(requestedStartDay: Int?): LedgerResult = database.withTransaction {
        val previous = requireNotNull(dao.periods().maxByOrNull { it.startEpochDay }) { "No existe un periodo anterior" }
        createPeriodAfter(previous, requestedStartDay ?: previous.configuredStartDay, needsReview = false)
        LedgerResult.Success
    }

    private suspend fun catchUpPeriods(preferredStartDay: Int): LedgerResult {
        database.withTransaction {
            val periods = dao.periods()
            val allocations = dao.allocations()
            val periodPockets = dao.periodPockets()
            val rolloverReleases = dao.rolloverReleases()
            val pendingCurrencyChange = dao.pendingCurrencyChange()
            val plan = PeriodLedgerRules.catchUp(
                periods = periods,
                pockets = dao.pockets(),
                allocations = allocations,
                periodPockets = periodPockets,
                rolloverReleases = rolloverReleases,
                movements = dao.movements(),
                pendingCurrencyChange = pendingCurrencyChange,
                preferredStartDay = preferredStartDay,
                today = today(),
                zoneId = zoneId,
            )
            val now = clock.millis()
            if (dao.pendingMovementSuggestions().any { it.expiresAtUtcMillis <= now }) {
                dao.deleteExpiredMovementSuggestions(now)
            }
            if (plan.periods != periods) dao.putPeriodEntities(plan.periods)
            if (plan.periodPockets != periodPockets) dao.putPeriodPockets(plan.periodPockets)
            if (plan.allocations != allocations) dao.putAllocations(plan.allocations)
            if (plan.rolloverReleases != rolloverReleases) dao.putRolloverReleases(plan.rolloverReleases)
            if (plan.pendingCurrencyChange != pendingCurrencyChange) {
                if (plan.pendingCurrencyChange == null) dao.clearPendingCurrencyChange()
                else dao.putPendingCurrencyChange(plan.pendingCurrencyChange)
            }
        }
        dateRefreshes.emit(Unit)
        return LedgerResult.Success
    }

    private suspend fun markPeriodReviewed(periodId: String): LedgerResult = database.withTransaction {
        val period = requireNotNull(dao.period(periodId)) { "Periodo inexistente" }
        dao.updatePeriod(period.copy(needsReview = false))
        LedgerResult.Success
    }

    private suspend fun scheduleCurrencyChange(command: LedgerCommand.ScheduleCurrencyChange): LedgerResult =
        database.withTransaction {
            val previous = requireNotNull(dao.periods().maxByOrNull { it.startEpochDay }) { "No existe un periodo anterior" }
            val from = SupportedCurrency.fromCode(previous.accountingCurrencyCode)
            require(command.targetCurrency != from) { "Elige una moneda diferente" }
            require(command.effectiveDate.toEpochDay() == previous.endExclusiveEpochDay) {
                "La fecha efectiva debe coincidir con el próximo periodo"
            }
            val source = command.source.trim()
            require(source.isNotEmpty()) { "Falta la fuente del tipo de cambio" }
            command.quoteEffectiveDate?.let { observed ->
                require(!observed.isAfter(today()) && !observed.isBefore(today().minusDays(7))) {
                    "La cotización debe ser reciente y no futura"
                }
                require(!observed.isAfter(command.effectiveDate)) { "Cotización posterior al cambio" }
            }
            FrozenRate(from, command.targetCurrency, command.rate)
            dao.putPendingCurrencyChange(
                PendingCurrencyChangeEntity(
                    fromCurrencyCode = from.name,
                    targetCurrencyCode = command.targetCurrency.name,
                    rate = command.rate,
                    effectiveEpochDay = command.effectiveDate.toEpochDay(),
                    source = source,
                    quoteEffectiveEpochDay = command.quoteEffectiveDate?.toEpochDay(),
                )
            )
            LedgerResult.Success
        }

    private suspend fun cancelCurrencyChange(): LedgerResult = database.withTransaction {
        dao.clearPendingCurrencyChange()
        LedgerResult.Success
    }

    private fun validateLedgerProjection(
        periods: List<PeriodEntity>,
        allocations: List<AllocationEntity>,
        movements: List<MovementEntity>,
        rolloverReleases: List<RolloverReleaseEntity>,
    ) = PeriodLedgerRules.validateLedgerProjection(periods, allocations, movements, rolloverReleases, today())

    private suspend fun recalculateRolloverFrom(sourcePeriodId: String) {
        val periods = dao.periods().sortedBy { it.startEpochDay }
        val periodPockets = dao.periodPockets()
        val movements = dao.movements()
        val allocations = dao.allocations()
        val rolloverReleases = dao.rolloverReleases()
        val projection = rolloverProjection(
            sourcePeriodId,
            periods,
            periodPockets,
            movements,
            allocations,
            rolloverReleases,
        )
        dao.putAllocations(projection.changedAllocations)
        projection.changedReleases.forEach { (key, release) ->
            release?.let { dao.putRolloverRelease(it) }
                ?: dao.deleteRolloverRelease(key.first, key.second)
        }
    }

    private fun rolloverProjection(
        sourcePeriodId: String,
        periods: List<PeriodEntity>,
        periodPockets: List<PeriodPocketEntity>,
        movements: List<MovementEntity>,
        allocations: List<AllocationEntity>,
        releases: List<RolloverReleaseEntity>,
    ): RolloverProjection {
        val orderedPeriods = periods.sortedBy { it.startEpochDay }
        val startIndex = orderedPeriods.indexOfFirst { it.id == sourcePeriodId }
        require(startIndex >= 0) { "Periodo inexistente" }
        val projectedAllocations = allocations
            .associateByTo(mutableMapOf()) { it.periodId to it.pocketId }
        val projectedReleases = releases
            .associateByTo(mutableMapOf()) { it.periodId to it.pocketId }
        val changedAllocations = mutableListOf<AllocationEntity>()
        val changedReleases = mutableMapOf<Pair<String, String>, RolloverReleaseEntity?>()

        for (index in startIndex until orderedPeriods.lastIndex) {
            val source = orderedPeriods[index]
            val target = orderedPeriods[index + 1]
            val sourceSnapshots = periodPockets.filter { it.periodId == source.id }.associateBy { it.pocketId }
            val targetSnapshots = periodPockets.filter { it.periodId == target.id }
            val sourceMovements = movements.filter { it.periodId == source.id }

            targetSnapshots.forEach { targetSnapshot ->
                val pocketId = targetSnapshot.pocketId
                val sourceAllocation = projectedAllocations[source.id to pocketId]
                val pocketMovements = sourceMovements.filter { it.pocketId == pocketId }
                val expenses = pocketMovements.filter { it.type == MovementType.EXPENSE.name }
                    .map { it.accountingAmountMinor }.sumMoneyExact()
                val refunds = pocketMovements.filter { it.type == MovementType.REFUND.name }
                    .map { it.accountingAmountMinor }.sumMoneyExact()
                val netSpend = Math.subtractExact(expenses, refunds)
                val sourceSnapshot = sourceSnapshots[pocketId]
                val rollover = PocketMath.rollover(
                    allocatedMinor = Math.addExact(sourceAllocation?.budgetMinor ?: 0, sourceAllocation?.rolloverMinor ?: 0),
                    netSpendMinor = netSpend,
                    enabled = sourceSnapshot?.rolloverEligible == true && !sourceSnapshot.retired,
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
                if (targetSnapshot.retired) {
                    val targetKey = target.id to pocketId
                    if (targetRollover > 0) {
                        val release = RolloverReleaseEntity(target.id, pocketId, targetRollover)
                        projectedReleases[targetKey] = release
                        changedReleases[targetKey] = release
                    } else {
                        projectedReleases.remove(targetKey)
                        changedReleases[targetKey] = null
                    }
                } else {
                    val targetKey = target.id to pocketId
                    val targetAllocation = projectedAllocations[targetKey]
                    val updated = AllocationEntity(
                        periodId = target.id,
                        pocketId = pocketId,
                        budgetMinor = targetAllocation?.budgetMinor ?: 0,
                        rolloverMinor = targetRollover,
                    )
                    projectedAllocations[targetKey] = updated
                    changedAllocations += updated
                }
            }
        }
        validateLedgerProjection(
            orderedPeriods,
            projectedAllocations.values.toList(),
            movements,
            projectedReleases.values.toList(),
        )
        return RolloverProjection(changedAllocations, changedReleases)
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
        val pending = dao.pendingCurrencyChange()
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
        val nextId = UUID.randomUUID().toString()
        val next = previous.copy(
            id = nextId,
            startEpochDay = schedule.start.toEpochDay(),
            endExclusiveEpochDay = schedule.endExclusive.toEpochDay(),
            configuredStartDay = schedule.configuredStartDay,
            isTransition = schedule.isTransition,
            needsReview = needsReview,
            newFundsMinor = convertForTarget(previous.newFundsMinor),
            accountingCurrencyCode = nextCurrency.name,
            priorBoundaryFromCurrencyCode = boundary?.from?.name,
            priorBoundaryRate = pending?.rate,
            priorBoundaryEffectiveEpochDay = pending?.effectiveEpochDay,
            priorBoundarySource = pending?.source,
            priorBoundaryQuoteEffectiveEpochDay = pending?.quoteEffectiveEpochDay,
        )
        val activePockets = dao.pockets().filterNot { it.archived }
            .sortedWith(compareBy<PocketEntity> { it.sortOrder }.thenBy { it.id })
        val previousPeriodPockets = dao.periodPockets()
            .filter { it.periodId == previous.id }
            .associateBy { it.pocketId }
        val previousAllocations = dao.allocations()
            .filter { it.periodId == previous.id }
            .associateBy { it.pocketId }
        val previousMovements = dao.movements().filter { it.periodId == previous.id }
        val nextPeriodPockets = activePockets.map { pocket ->
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
            val pocketMovements = previousMovements.filter { it.pocketId == pocket.id }
            val expenses = pocketMovements.filter { it.type == MovementType.EXPENSE.name }
                .map { it.accountingAmountMinor }.sumMoneyExact()
            val refunds = pocketMovements.filter { it.type == MovementType.REFUND.name }
                .map { it.accountingAmountMinor }.sumMoneyExact()
            val spent = Math.subtractExact(expenses, refunds)
            val rollover = PocketMath.rollover(
                allocatedMinor = Math.addExact(previousAllocation?.budgetMinor ?: 0, previousAllocation?.rolloverMinor ?: 0),
                netSpendMinor = spent,
                enabled = previousPeriodPockets[pocket.id]?.rolloverEligible == true,
            )
            AllocationEntity(
                nextId,
                pocket.id,
                convertedBudgets.getValue(pocket.id),
                convertForTarget(rollover),
            )
        }
        validateLedgerProjection(
            dao.periods() + next,
            dao.allocations() + nextAllocations,
            dao.movements(),
            dao.rolloverReleases(),
        )
        dao.putPeriod(next)
        dao.putPeriodPockets(nextPeriodPockets)
        dao.putAllocations(nextAllocations)
        if (pending != null) dao.clearPendingCurrencyChange()
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
        val preferences = dao.ledgerPreferences()
        if (command.archived && preferences?.defaultPaymentMethodId == command.id) {
            dao.putLedgerPreferences(preferences.copy(defaultPaymentMethodId = null))
        }
        LedgerResult.Success
    }

    private suspend fun setDefaultPaymentMethod(command: LedgerCommand.SetDefaultPaymentMethod): LedgerResult =
        database.withTransaction {
            command.id?.let { id ->
                val method = requireNotNull(dao.paymentMethods().firstOrNull { it.id == id }) { "Método inexistente" }
                require(!method.archived) { "El método está archivado" }
            }
            dao.putLedgerPreferences(LedgerPreferencesEntity(defaultPaymentMethodId = command.id))
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
                command.inputCurrency.name,
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
        pendingCurrencyChangeEntity: PendingCurrencyChangeEntity?,
        ledgerPreferencesEntity: LedgerPreferencesEntity?,
        suggestionEntities: List<MovementSuggestionEntity>,
    ): LedgerState {
        val periods = periodEntities.map { it.toModel() }
        val today = today()
        val current = periods.firstOrNull { today >= it.start && today < it.endExclusive }
        val pocketsById = pocketEntities.associateBy { it.id }
        val pocketCatalog = pocketEntities.sortedWith(compareBy<PocketEntity> { it.sortOrder }.thenBy { it.name }).map { it.toModel() }
        val methodsById = methodEntities.associateBy { it.id }
        val movements = movementEntities.map { it.toModel(pocketsById, methodsById) }
        val suggestions = suggestionEntities.filter { it.expiresAtUtcMillis > clock.millis() }.mapNotNull { it.toModel() }
        if (current == null) return LedgerState(periods = periods, pocketCatalog = pocketCatalog, movements = movements, movementSuggestions = suggestions)
        fun summariesFor(periodId: String): List<PocketPeriodSummary> {
            val periodMovements = movements.filter { it.periodId == periodId }
            val periodAllocations = allocations.filter { it.periodId == periodId }.associateBy { it.pocketId }
            val periodSnapshots = periodPockets.filter { it.periodId == periodId }.associateBy { it.pocketId }
            val periodReleases = rolloverReleases.filter { it.periodId == periodId }.associateBy { it.pocketId }
            return periodSnapshots.values.mapNotNull { snapshot ->
                val pocketEntity = pocketsById[snapshot.pocketId] ?: return@mapNotNull null
                val allocation = periodAllocations[pocketEntity.id]
                val pocketMovements = periodMovements.filter { it.pocketId == pocketEntity.id }
                val expenses = pocketMovements.filter { it.type == MovementType.EXPENSE }
                    .map { it.accountingAmountMinor }.sumMoneyExact()
                val refunds = pocketMovements.filter { it.type == MovementType.REFUND }
                    .map { it.accountingAmountMinor }.sumMoneyExact()
                val math = PocketMath.summary(allocation?.budgetMinor ?: 0, allocation?.rolloverMinor ?: 0, expenses, refunds)
                PocketPeriodSummary(
                    pocket = pocketEntity.toModel(),
                    budgetMinor = math.budgetMinor,
                    rolloverMinor = math.rolloverMinor,
                    rolloverEligible = snapshot.rolloverEligible,
                    retiredThisPeriod = snapshot.retired,
                    rolloverReleasedMinor = periodReleases[pocketEntity.id]?.amountMinor ?: 0,
                    expenseMinor = expenses,
                    refundMinor = refunds,
                    netSpendMinor = math.netSpendMinor,
                    availabilityMinor = math.availabilityMinor,
                    consumedPercent = math.consumedPercent,
                    atRisk = math.atRisk,
                    exhausted = math.exhausted,
                )
            }
        }
        val allSummaries = periods.associate { it.id to summariesFor(it.id) }
        val unallocatedByPeriod = periods.associate { period ->
            val periodSummaries = allSummaries.getValue(period.id)
            period.id to Math.addExact(
                Math.subtractExact(period.newFundsMinor, periodSummaries.map { it.budgetMinor }.sumMoneyExact()),
                periodSummaries.map { it.rolloverReleasedMinor }.sumMoneyExact(),
            )
        }
        val summaries = allSummaries.getValue(current.id)
        val previous = periods.filter { it.start < current.start }.maxByOrNull { it.start }
        val previousSpendInPreviousCurrency = previous?.let { period ->
            val periodMovements = movements.filter { it.periodId == period.id }
            Math.subtractExact(
                periodMovements.filter { it.type == MovementType.EXPENSE }.map { it.accountingAmountMinor }.sumMoneyExact(),
                periodMovements.filter { it.type == MovementType.REFUND }.map { it.accountingAmountMinor }.sumMoneyExact(),
            )
        }
        val currentEntity = periodEntities.first { it.id == current.id }
        val previousSpend = previous?.let { previousPeriod ->
            previousSpendInPreviousCurrency?.let { amount ->
                if (previousPeriod.accountingCurrency == current.accountingCurrency) {
                    amount
                } else {
                    currentEntity.frozenRateFrom(previousPeriod.accountingCurrency)?.convertMinor(amount)
                }
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
        val netSpend = summaries.map { it.netSpendMinor }.sumMoneyExact()
        return LedgerState(
            periods = periods,
            currentPeriod = current,
            pockets = summaries,
            pocketCatalog = pocketCatalog,
            pocketSummariesByPeriod = allSummaries,
            movements = movements,
            paymentMethods = methodEntities.map { PaymentMethod(it.id, it.name, it.archived) },
            templates = templateEntities.map {
                RecurringTemplate(
                    it.id,
                    it.name,
                    it.amountMinor,
                    it.pocketId,
                    it.paymentMethodId,
                    it.archived,
                    SupportedCurrency.fromCode(it.inputCurrencyCode),
                )
            },
            unallocatedMinorByPeriod = unallocatedByPeriod,
            unallocatedMinor = unallocatedByPeriod.getValue(current.id),
            newFundsMinor = current.newFundsMinor,
            rolloverTotalMinor = summaries.map { it.rolloverMinor }.sumMoneyExact(),
            netSpendMinor = netSpend,
            trackedAvailabilityMinor = summaries.map { it.availabilityMinor }.sumMoneyExact(),
            previousPeriodNetSpendMinor = previousSpend,
            comparisonMode = comparisonMode,
            previousPeriodComparisonMinor = previousComparison,
            elapsedDays = elapsed,
            totalDays = totalDays,
            projectionMinor = PocketMath.project(netSpend, elapsed, totalDays).amountMinor,
            currentLocalDate = today,
            currentInstantMillis = clock.instant().toEpochMilli(),
            pendingCurrencyChange = pendingCurrencyChangeEntity?.toModel(),
            defaultPaymentMethodId = ledgerPreferencesEntity?.defaultPaymentMethodId,
            movementSuggestions = suggestions,
        )
    }

    private fun today(): LocalDate = clock.instant().atZone(zoneId).toLocalDate()

    override suspend fun exportBackup(settings: PortableSettings): ByteArray = withContext(codecDispatcher) { BackupCodec.encode(database, settings) }
    override suspend fun previewBackup(bytes: ByteArray): BackupPreview = withContext(codecDispatcher) {
        BackupCodec.preview(bytes, today(), zoneId)
    }
    override suspend fun restoreBackup(bytes: ByteArray): LedgerResult = restoreMutex.withLock {
        withContext(codecDispatcher) { BackupCodec.restore(database, bytes, today(), zoneId) }
    }
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

    private data class LedgerSnapshot(
        val periods: List<PeriodEntity>,
        val pockets: List<PocketEntity>,
        val allocations: List<AllocationEntity>,
        val periodPockets: List<PeriodPocketEntity>,
        val rolloverReleases: List<RolloverReleaseEntity>,
        val paymentMethods: List<PaymentMethodEntity>,
        val movements: List<MovementEntity>,
        val templates: List<RecurringTemplateEntity>,
        val pendingCurrencyChange: PendingCurrencyChangeEntity?,
        val ledgerPreferences: LedgerPreferencesEntity?,
        val suggestions: List<MovementSuggestionEntity>,
    )

    private data class RolloverProjection(
        val changedAllocations: List<AllocationEntity>,
        val changedReleases: Map<Pair<String, String>, RolloverReleaseEntity?>,
    )
}

private fun MovementSuggestionEntity.asTombstone(newStatus: String) = copy(
    amountMinor = null,
    currencyCode = null,
    effectiveAtUtcMillis = null,
    sourcePackage = null,
    merchant = null,
    status = newStatus,
)

private fun MovementSuggestionEntity.toModel(): MovementSuggestion? = runCatching {
    MovementSuggestion(
        id = identityHash,
        amountMinor = requireNotNull(amountMinor),
        currency = SupportedCurrency.fromCode(requireNotNull(currencyCode)),
        effectiveAtUtcMillis = requireNotNull(effectiveAtUtcMillis),
        sourcePackage = requireNotNull(sourcePackage),
        merchant = merchant,
    )
}.getOrNull()

private fun PeriodEntity.toModel() = Period(
    id = id,
    start = LocalDate.ofEpochDay(startEpochDay),
    endExclusive = LocalDate.ofEpochDay(endExclusiveEpochDay),
    newFundsMinor = newFundsMinor,
    configuredStartDay = configuredStartDay,
    isTransition = isTransition,
    needsReview = needsReview,
    accountingCurrency = SupportedCurrency.fromCode(accountingCurrencyCode),
    priorCurrencyBoundary = priorBoundaryRate?.let { rate ->
        CurrencyBoundary(
            from = SupportedCurrency.fromCode(requireNotNull(priorBoundaryFromCurrencyCode)),
            to = SupportedCurrency.fromCode(accountingCurrencyCode),
            rate = rate,
            effectiveDate = LocalDate.ofEpochDay(requireNotNull(priorBoundaryEffectiveEpochDay)),
            source = requireNotNull(priorBoundarySource),
            quoteEffectiveDate = priorBoundaryQuoteEffectiveEpochDay?.let(LocalDate::ofEpochDay),
        )
    },
)

private fun PendingCurrencyChangeEntity.toModel() = PendingCurrencyChange(
    CurrencyBoundary(
        from = SupportedCurrency.fromCode(fromCurrencyCode),
        to = SupportedCurrency.fromCode(targetCurrencyCode),
        rate = rate,
        effectiveDate = LocalDate.ofEpochDay(effectiveEpochDay),
        source = source,
        quoteEffectiveDate = quoteEffectiveEpochDay?.let(LocalDate::ofEpochDay),
    )
)

internal fun PeriodEntity.frozenRateFrom(source: SupportedCurrency): FrozenRate? {
    val storedRate = priorBoundaryRate ?: return null
    val storedFrom = priorBoundaryFromCurrencyCode ?: return null
    return runCatching {
        FrozenRate(
            from = SupportedCurrency.fromCode(storedFrom),
            to = SupportedCurrency.fromCode(accountingCurrencyCode),
            value = storedRate,
        ).takeIf { it.from == source }
    }.getOrNull()
}

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
    accountingAmountMinor = accountingAmountMinor,
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
    conversionEffectiveDate = conversionEffectiveEpochDay?.let(LocalDate::ofEpochDay),
    conversionSource = conversionSource,
)

private fun LedgerCommand.AddMovement.toEntity(
    periodId: String,
    zoneId: String,
    resolvedOriginalCurrencyCode: String,
) = MovementEntity(
    id = id ?: UUID.randomUUID().toString(),
    periodId = periodId,
    pocketId = pocketId,
    type = type.name,
    accountingAmountMinor = accountingAmountMinor,
    occurredAtUtcMillis = occurredAtUtcMillis,
    localEpochDay = localDate.toEpochDay(),
    zoneId = zoneId,
    merchant = merchant?.trim()?.takeIf { it.isNotEmpty() },
    note = note?.trim()?.takeIf { it.isNotEmpty() },
    paymentMethodId = paymentMethodId,
    originalAmountMinor = originalAmountMinor,
    originalCurrencyCode = resolvedOriginalCurrencyCode,
    conversionStatus = conversionStatus.name,
    rate = rate,
    conversionEffectiveEpochDay = conversionEffectiveDate?.toEpochDay(),
    conversionSource = conversionSource?.trim()?.takeIf { it.isNotEmpty() },
)

private fun Movement.toEntity() = MovementEntity(
    id = id,
    periodId = periodId,
    pocketId = pocketId,
    type = type.name,
    accountingAmountMinor = accountingAmountMinor,
    occurredAtUtcMillis = occurredAtUtcMillis,
    localEpochDay = localDate.toEpochDay(),
    zoneId = zoneId,
    merchant = merchant,
    note = note,
    paymentMethodId = paymentMethodId,
    originalAmountMinor = originalAmountMinor,
    originalCurrencyCode = originalCurrencyCode,
    conversionStatus = conversionStatus.name,
    rate = rate,
    conversionEffectiveEpochDay = conversionEffectiveDate?.toEpochDay(),
    conversionSource = conversionSource,
)
