package com.aif31.pocket.data

import androidx.room.withTransaction
import com.aif31.pocket.domain.FrozenRate
import com.aif31.pocket.domain.SupportedCurrency
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames

internal object BackupCodec {
    private const val VERSION = 4
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true; prettyPrint = true }

    suspend fun encode(database: FinanceDatabase): ByteArray {
        val payload = database.withTransaction {
            val dao = database.financeDao()
            BackupPayload(
                version = VERSION,
                periods = dao.periods().map {
                    PeriodDto(
                        it.id,
                        it.startEpochDay,
                        it.endExclusiveEpochDay,
                        it.newFundsMinor,
                        it.configuredStartDay,
                        it.isTransition,
                        it.needsReview,
                        it.accountingCurrencyCode,
                        it.priorBoundaryFromCurrencyCode,
                        it.priorBoundaryRate,
                        it.priorBoundaryEffectiveEpochDay,
                        it.priorBoundarySource,
                    )
                },
                pockets = dao.pockets().map { PocketDto(it.id, it.name, it.sortOrder, it.archived, it.rolloverEnabled, it.iconKey) },
                allocations = dao.allocations().map { AllocationDto(it.periodId, it.pocketId, it.budgetMinor, it.rolloverMinor) },
                periodPockets = dao.periodPockets().map {
                    PeriodPocketDto(it.periodId, it.pocketId, it.rolloverEligible, it.retired)
                },
                rolloverReleases = dao.rolloverReleases().map {
                    RolloverReleaseDto(it.periodId, it.pocketId, it.amountMinor)
                },
                paymentMethods = dao.paymentMethods().map { PaymentMethodDto(it.id, it.name, it.archived) },
                movements = dao.movements().map {
                    MovementDto(
                        id = it.id,
                        periodId = it.periodId,
                        pocketId = it.pocketId,
                        type = it.type,
                        accountingAmountMinor = it.accountingAmountMinor,
                        occurredAt = it.occurredAtUtcMillis,
                        localDate = it.localEpochDay,
                        zoneId = it.zoneId,
                        merchant = it.merchant,
                        note = it.note,
                        paymentMethodId = it.paymentMethodId,
                        originalAmountMinor = it.originalAmountMinor,
                        currency = it.originalCurrencyCode,
                        conversion = it.conversionStatus,
                        rate = it.rate,
                    )
                },
                templates = dao.templates().map {
                    TemplateDto(
                        it.id,
                        it.name,
                        it.amountMinor,
                        it.pocketId,
                        it.paymentMethodId,
                        it.archived,
                        it.inputCurrencyCode,
                    )
                },
                pendingCurrencyChange = dao.pendingCurrencyChange()?.let {
                    PendingCurrencyChangeDto(
                        it.fromCurrencyCode,
                        it.targetCurrencyCode,
                        it.rate,
                        it.effectiveEpochDay,
                        it.source,
                    )
                },
                ledgerPreferences = dao.ledgerPreferences()?.let { LedgerPreferencesDto(it.defaultPaymentMethodId) },
            )
        }
        return json.encodeToString(BackupPayload.serializer(), payload).toByteArray(StandardCharsets.UTF_8)
    }

    fun preview(bytes: ByteArray): BackupPreview = try {
        val payload = decodeAndValidate(bytes)
        BackupPreview(payload.version, payload.periods.size, payload.pockets.size, payload.movements.size, valid = true)
    } catch (error: Exception) {
        BackupPreview(0, 0, 0, 0, valid = false, message = error.message ?: "Backup inválido")
    }

    suspend fun restore(database: FinanceDatabase, bytes: ByteArray): LedgerResult {
        val payload = try {
            decodeAndValidate(bytes)
        } catch (error: Exception) {
            return LedgerResult.Rejected(error.message ?: "Backup inválido")
        }
        return try {
            database.withTransaction {
                val dao = database.financeDao()
                dao.clearPendingCurrencyChange()
                dao.clearLedgerPreferences()
                dao.clearTemplates()
                dao.clearMovements()
                dao.clearRolloverReleases()
                dao.clearAllocations()
                dao.clearPeriodPockets()
                dao.clearPaymentMethods()
                dao.clearPockets()
                dao.clearPeriods()
                dao.putPeriodEntities(payload.periods.map { it.toEntity() })
                dao.putPockets(payload.pockets.map { it.toEntity() })
                dao.putPaymentMethods(payload.paymentMethods.map { it.toEntity() })
                dao.putPeriodPockets(payload.periodPockets.map { it.toEntity() })
                dao.putAllocations(payload.allocations.map { it.toEntity() })
                dao.putRolloverReleases(payload.rolloverReleases.map { it.toEntity() })
                dao.putMovements(payload.movements.map { it.toEntity() })
                dao.putTemplates(payload.templates.map { it.toEntity() })
                payload.pendingCurrencyChange?.let { dao.putPendingCurrencyChange(it.toEntity()) }
                val restoredPreferences = payload.ledgerPreferences ?: LedgerPreferencesDto(
                    payload.paymentMethods.firstOrNull {
                        !it.archived && it.name.equals("Tarjeta", ignoreCase = true)
                    }?.id
                )
                dao.putLedgerPreferences(restoredPreferences.toEntity())
            }
            LedgerResult.Success
        } catch (error: Exception) {
            LedgerResult.Rejected(error.message ?: "No se pudo restaurar el backup")
        }
    }

    suspend fun csv(database: FinanceDatabase): ByteArray {
        val dao = database.financeDao()
        val pockets = dao.pockets().associateBy { it.id }
        val periods = dao.periods().associateBy { it.id }
        val methods = dao.paymentMethods().associateBy { it.id }
        val rows = buildString {
            appendLine("id,tipo,fecha,zona,pocket,importe_contable,moneda_contable,moneda_original,importe_original,conversion,metodo,comercio,nota")
            dao.movements().sortedBy { it.occurredAtUtcMillis }.forEach { movement ->
                appendLine(
                    listOf(
                        movement.id,
                        movement.type,
                        java.time.LocalDate.ofEpochDay(movement.localEpochDay).toString(),
                        movement.zoneId,
                        pockets[movement.pocketId]?.name.orEmpty(),
                        minorString(movement.accountingAmountMinor),
                        periods[movement.periodId]?.accountingCurrencyCode.orEmpty(),
                        movement.originalCurrencyCode,
                        movement.originalAmountMinor?.let(::minorString).orEmpty(),
                        movement.conversionStatus,
                        movement.paymentMethodId?.let { methods[it]?.name }.orEmpty(),
                        movement.merchant.orEmpty(),
                        movement.note.orEmpty(),
                    ).joinToString(",", transform = ::csvCell)
                )
            }
        }
        return rows.toByteArray(StandardCharsets.UTF_8)
    }

    private fun decodeAndValidate(bytes: ByteArray): BackupPayload {
        require(bytes.isNotEmpty() && bytes.size <= 10 * 1024 * 1024) { "Tamaño de backup inválido" }
        val decoded = json.decodeFromString(BackupPayload.serializer(), bytes.toString(StandardCharsets.UTF_8))
        require(decoded.version in 1..VERSION) { "Versión de backup incompatible" }
        val payload = if (decoded.version < 3 && decoded.periodPockets.isEmpty()) {
            decoded.copy(
                periodPockets = decoded.periods.flatMap { period ->
                    decoded.pockets.map { pocket ->
                        PeriodPocketDto(period.id, pocket.id, pocket.rollover, retired = false)
                    }
                }
            )
        } else {
            decoded
        }
        require(payload.periods.isNotEmpty()) { "El backup no contiene ningún periodo" }
        require(payload.periods.map { it.id }.distinct().size == payload.periods.size) { "Periodos duplicados" }
        require(payload.pockets.map { it.id }.distinct().size == payload.pockets.size) { "Pockets duplicados" }
        require(payload.paymentMethods.map { it.id }.distinct().size == payload.paymentMethods.size) { "Métodos duplicados" }
        require(payload.movements.map { it.id }.distinct().size == payload.movements.size) { "Movimientos duplicados" }
        require(payload.templates.map { it.id }.distinct().size == payload.templates.size) { "Plantillas duplicadas" }
        val periodIds = payload.periods.mapTo(mutableSetOf()) { it.id }
        val pocketIds = payload.pockets.mapTo(mutableSetOf()) { it.id }
        val methodIds = payload.paymentMethods.mapTo(mutableSetOf()) { it.id }
        require(payload.periods.all { it.start < it.endExclusive && it.newFundsMinor >= 0 && it.startDay in 1..31 }) { "Periodo inválido" }
        val orderedPeriods = payload.periods.sortedBy { it.start }
        require(orderedPeriods.all { runCatching { SupportedCurrency.fromCode(it.accountingCurrencyCode) }.isSuccess }) {
            "Moneda de periodo inválida"
        }
        require(orderedPeriods.map { it.start }.distinct().size == orderedPeriods.size) { "Inicios de periodo duplicados" }
        require(orderedPeriods.zipWithNext().all { (current, next) -> current.endExclusive == next.start }) {
            "Los periodos deben ser contiguos y no solaparse"
        }
        require(orderedPeriods.zipWithNext().all { (current, next) ->
            val currentCurrency = SupportedCurrency.fromCode(current.accountingCurrencyCode)
            val nextCurrency = SupportedCurrency.fromCode(next.accountingCurrencyCode)
            if (currentCurrency == nextCurrency) {
                next.priorBoundaryRate == null && next.priorBoundaryFromCurrencyCode == null &&
                    next.priorBoundaryEffectiveEpochDay == null && next.priorBoundarySource == null
            } else {
                next.priorBoundaryFromCurrencyCode == currentCurrency.name &&
                    next.priorBoundaryEffectiveEpochDay == next.start &&
                    !next.priorBoundarySource.isNullOrBlank() &&
                    next.priorBoundaryRate?.let { rate ->
                        runCatching { FrozenRate(currentCurrency, nextCurrency, rate) }.isSuccess
                    } == true
            }
        }) { "Conversión entre periodos inválida" }
        require(payload.pockets.all { it.name.isNotBlank() }) { "Pocket inválido" }
        require(payload.pockets.all { it.iconKey == null || PocketIconKey.entries.any { key -> key.name == it.iconKey } }) { "Icono de Pocket inválido" }
        require(payload.pockets.map { it.name.trim().lowercase(Locale.ROOT) }.distinct().size == payload.pockets.size) {
            "Nombres de Pocket duplicados"
        }
        require(payload.paymentMethods.all { it.name.isNotBlank() }) { "Método de pago inválido" }
        require(payload.paymentMethods.map { it.name.trim().lowercase(Locale.ROOT) }.distinct().size == payload.paymentMethods.size) {
            "Nombres de método duplicados"
        }
        require(payload.allocations.map { it.periodId to it.pocketId }.distinct().size == payload.allocations.size) {
            "Presupuestos duplicados"
        }
        require(payload.periodPockets.map { it.periodId to it.pocketId }.distinct().size == payload.periodPockets.size) {
            "Estados de Pocket por periodo duplicados"
        }
        require(payload.rolloverReleases.map { it.periodId to it.pocketId }.distinct().size == payload.rolloverReleases.size) {
            "Liberaciones de rollover duplicadas"
        }
        require(payload.periodPockets.all { it.periodId in periodIds && it.pocketId in pocketIds }) {
            "Relación de Pocket por periodo inválida"
        }
        val periodPocketKeys = payload.periodPockets.map { it.periodId to it.pocketId }.toSet()
        require(payload.version < 3 || payload.allocations.all { (it.periodId to it.pocketId) in periodPocketKeys }) {
            "Presupuesto sin estado de Pocket por periodo"
        }
        require(payload.version < 3 || payload.movements.all { (it.periodId to it.pocketId) in periodPocketKeys }) {
            "Movimiento sin estado de Pocket por periodo"
        }
        require(payload.rolloverReleases.all {
            it.periodId in periodIds && it.pocketId in pocketIds && it.amountMinor >= 0
        }) {
            "Liberación de rollover inválida"
        }
        val periodPocketsByKey = payload.periodPockets.associateBy { it.periodId to it.pocketId }
        require(payload.version < 3 || payload.rolloverReleases.all {
            periodPocketsByKey[it.periodId to it.pocketId]?.retired == true
        }) {
            "Liberación de rollover sin Pocket retirado"
        }
        require(payload.allocations.all { it.periodId in periodIds && it.pocketId in pocketIds && it.budgetMinor >= 0 && it.rolloverMinor >= 0 }) { "Relación de presupuesto inválida" }
        val periodsById = payload.periods.associateBy { it.id }
        require(payload.allocations.groupBy { it.periodId }.all { (periodId, values) ->
            values.fold(0L) { total, allocation -> Math.addExact(total, allocation.budgetMinor) } <= periodsById.getValue(periodId).newFundsMinor
        }) { "Los presupuestos superan los fondos del periodo" }
        require(payload.movements.all {
            it.periodId in periodIds && it.pocketId in pocketIds && (it.paymentMethodId == null || it.paymentMethodId in methodIds) &&
                it.accountingAmountMinor > 0 && it.type in MovementType.entries.map { type -> type.name } &&
                it.currency.matches(Regex("[A-Z]{3}")) && it.conversion in ConversionStatus.entries.map { status -> status.name } &&
                it.localDate >= periodsById.getValue(it.periodId).start && it.localDate < periodsById.getValue(it.periodId).endExclusive &&
                (it.currency == "SAR" || (it.originalAmountMinor ?: 0) > 0) && it.zoneId == "Asia/Riyadh"
        }) { "Relación de movimiento inválida" }
        require(payload.templates.all {
            it.name.isNotBlank() && it.amountMinor > 0 && it.pocketId in pocketIds &&
                (it.paymentMethodId == null || it.paymentMethodId in methodIds) &&
                runCatching { SupportedCurrency.fromCode(it.inputCurrencyCode) }.isSuccess
        }) {
            "Relación de plantilla inválida"
        }
        require(payload.ledgerPreferences?.defaultPaymentMethodId.let { it == null || it in methodIds }) {
            "Método predeterminado inválido"
        }
        payload.pendingCurrencyChange?.let { pending ->
            val latest = orderedPeriods.last()
            val from = SupportedCurrency.fromCode(pending.fromCurrencyCode)
            val target = SupportedCurrency.fromCode(pending.targetCurrencyCode)
            require(
                from.name == latest.accountingCurrencyCode && from != target &&
                    pending.effectiveEpochDay == latest.endExclusive && pending.source.isNotBlank() &&
                    runCatching { FrozenRate(from, target, pending.rate) }.isSuccess
            ) { "Cambio de moneda pendiente inválido" }
        }
        return payload
    }

    private fun csvCell(value: String): String {
        val firstContent = value.firstOrNull { !it.isWhitespace() }
        val safeValue = if (firstContent in CSV_FORMULA_PREFIXES) "'$value" else value
        return "\"${safeValue.replace("\"", "\"\"")}\""
    }
    private fun minorString(value: Long): String = java.math.BigDecimal.valueOf(value, 2).toPlainString()

    private val CSV_FORMULA_PREFIXES = setOf('=', '+', '-', '@')
}

private suspend fun FinanceDao.putPeriodEntities(values: List<PeriodEntity>) {
    values.forEach { putPeriod(it) }
}

@Serializable
private data class BackupPayload(
    val version: Int,
    val periods: List<PeriodDto>,
    val pockets: List<PocketDto>,
    val allocations: List<AllocationDto>,
    val periodPockets: List<PeriodPocketDto> = emptyList(),
    val rolloverReleases: List<RolloverReleaseDto> = emptyList(),
    val paymentMethods: List<PaymentMethodDto>,
    val movements: List<MovementDto>,
    val templates: List<TemplateDto>,
    val pendingCurrencyChange: PendingCurrencyChangeDto? = null,
    val ledgerPreferences: LedgerPreferencesDto? = null,
)

@Serializable
private data class PeriodDto(
    val id: String,
    val start: Long,
    val endExclusive: Long,
    val newFundsMinor: Long,
    val startDay: Int,
    val isTransition: Boolean = false,
    val needsReview: Boolean = false,
    val accountingCurrencyCode: String = "SAR",
    val priorBoundaryFromCurrencyCode: String? = null,
    val priorBoundaryRate: String? = null,
    val priorBoundaryEffectiveEpochDay: Long? = null,
    val priorBoundarySource: String? = null,
) {
    fun toEntity() = PeriodEntity(
        id,
        start,
        endExclusive,
        newFundsMinor,
        startDay,
        isTransition,
        needsReview,
        accountingCurrencyCode,
        priorBoundaryFromCurrencyCode,
        priorBoundaryRate,
        priorBoundaryEffectiveEpochDay,
        priorBoundarySource,
    )
}
@Serializable private data class PocketDto(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val archived: Boolean,
    val rollover: Boolean,
    val iconKey: String? = null,
) {
    fun toEntity() = PocketEntity(id, name, PocketIconKey.fromStored(iconKey, name).name, sortOrder, archived, rollover)
}
@Serializable private data class AllocationDto(val periodId: String, val pocketId: String, val budgetMinor: Long, val rolloverMinor: Long) {
    fun toEntity() = AllocationEntity(periodId, pocketId, budgetMinor, rolloverMinor)
}
@Serializable
private data class PeriodPocketDto(
    val periodId: String,
    val pocketId: String,
    val rolloverEligible: Boolean,
    val retired: Boolean,
) {
    fun toEntity() = PeriodPocketEntity(periodId, pocketId, rolloverEligible, retired)
}
@Serializable
private data class RolloverReleaseDto(
    val periodId: String,
    val pocketId: String,
    val amountMinor: Long,
) {
    fun toEntity() = RolloverReleaseEntity(periodId, pocketId, amountMinor)
}
@Serializable private data class PaymentMethodDto(val id: String, val name: String, val archived: Boolean) {
    fun toEntity() = PaymentMethodEntity(id, name, archived)
}
@Serializable
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private data class MovementDto(
    val id: String,
    val periodId: String,
    val pocketId: String,
    val type: String,
    @JsonNames("sarAmountMinor") val accountingAmountMinor: Long,
    val occurredAt: Long,
    val localDate: Long,
    val zoneId: String,
    val merchant: String?,
    val note: String?,
    val paymentMethodId: String?,
    val originalAmountMinor: Long?,
    val currency: String,
    val conversion: String,
    val rate: String?,
) {
    fun toEntity() = MovementEntity(
        id, periodId, pocketId, type, accountingAmountMinor, occurredAt, localDate, zoneId, merchant, note,
        paymentMethodId, originalAmountMinor, currency, conversion, rate,
    )
}
@Serializable private data class TemplateDto(
    val id: String,
    val name: String,
    val amountMinor: Long,
    val pocketId: String,
    val paymentMethodId: String?,
    val archived: Boolean,
    val inputCurrencyCode: String = "SAR",
) {
    fun toEntity() = RecurringTemplateEntity(id, name, amountMinor, pocketId, paymentMethodId, archived, inputCurrencyCode)
}

@Serializable
private data class PendingCurrencyChangeDto(
    val fromCurrencyCode: String,
    val targetCurrencyCode: String,
    val rate: String,
    val effectiveEpochDay: Long,
    val source: String,
) {
    fun toEntity() = PendingCurrencyChangeEntity(
        fromCurrencyCode = fromCurrencyCode,
        targetCurrencyCode = targetCurrencyCode,
        rate = rate,
        effectiveEpochDay = effectiveEpochDay,
        source = source,
    )
}

@Serializable
private data class LedgerPreferencesDto(val defaultPaymentMethodId: String?) {
    fun toEntity() = LedgerPreferencesEntity(defaultPaymentMethodId = defaultPaymentMethodId)
}
