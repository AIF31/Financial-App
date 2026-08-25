package com.aif31.pocket.data

import androidx.room.withTransaction
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object BackupCodec {
    private const val VERSION = 2
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true; prettyPrint = true }

    suspend fun encode(database: FinanceDatabase): ByteArray {
        val dao = database.financeDao()
        val payload = BackupPayload(
            version = VERSION,
            periods = dao.periods().map { PeriodDto(it.id, it.startEpochDay, it.endExclusiveEpochDay, it.newFundsMinor, it.configuredStartDay) },
            pockets = dao.pockets().map { PocketDto(it.id, it.name, it.sortOrder, it.archived, it.rolloverEnabled, it.iconKey) },
            allocations = dao.allocations().map { AllocationDto(it.periodId, it.pocketId, it.budgetMinor, it.rolloverMinor) },
            paymentMethods = dao.paymentMethods().map { PaymentMethodDto(it.id, it.name, it.archived) },
            movements = dao.movements().map {
                MovementDto(
                    it.id, it.periodId, it.pocketId, it.type, it.sarAmountMinor, it.occurredAtUtcMillis,
                    it.localEpochDay, it.zoneId, it.merchant, it.note, it.paymentMethodId,
                    it.originalAmountMinor, it.originalCurrencyCode, it.conversionStatus, it.rate,
                )
            },
            templates = dao.templates().map {
                TemplateDto(it.id, it.name, it.amountMinor, it.pocketId, it.paymentMethodId, it.archived)
            },
        )
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
                dao.clearTemplates()
                dao.clearMovements()
                dao.clearAllocations()
                dao.clearPaymentMethods()
                dao.clearPockets()
                dao.clearPeriods()
                dao.putPeriodEntities(payload.periods.map { it.toEntity() })
                dao.putPockets(payload.pockets.map { it.toEntity() })
                dao.putPaymentMethods(payload.paymentMethods.map { it.toEntity() })
                dao.putAllocations(payload.allocations.map { it.toEntity() })
                dao.putMovements(payload.movements.map { it.toEntity() })
                dao.putTemplates(payload.templates.map { it.toEntity() })
            }
            LedgerResult.Success
        } catch (error: Exception) {
            LedgerResult.Rejected(error.message ?: "No se pudo restaurar el backup")
        }
    }

    suspend fun csv(database: FinanceDatabase): ByteArray {
        val dao = database.financeDao()
        val pockets = dao.pockets().associateBy { it.id }
        val methods = dao.paymentMethods().associateBy { it.id }
        val rows = buildString {
            appendLine("id,tipo,fecha,zona,pocket,importe_sar,moneda_original,importe_original,conversion,metodo,comercio,nota")
            dao.movements().sortedBy { it.occurredAtUtcMillis }.forEach { movement ->
                appendLine(
                    listOf(
                        movement.id,
                        movement.type,
                        java.time.LocalDate.ofEpochDay(movement.localEpochDay).toString(),
                        movement.zoneId,
                        pockets[movement.pocketId]?.name.orEmpty(),
                        minorString(movement.sarAmountMinor),
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
        val payload = json.decodeFromString(BackupPayload.serializer(), bytes.toString(StandardCharsets.UTF_8))
        require(payload.version in 1..VERSION) { "Versión de backup incompatible" }
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
        require(orderedPeriods.map { it.start }.distinct().size == orderedPeriods.size) { "Inicios de periodo duplicados" }
        require(orderedPeriods.zipWithNext().all { (current, next) -> current.endExclusive == next.start }) {
            "Los periodos deben ser contiguos y no solaparse"
        }
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
        require(payload.allocations.all { it.periodId in periodIds && it.pocketId in pocketIds && it.budgetMinor >= 0 && it.rolloverMinor >= 0 }) { "Relación de presupuesto inválida" }
        val periodsById = payload.periods.associateBy { it.id }
        require(payload.allocations.groupBy { it.periodId }.all { (periodId, values) ->
            values.fold(0L) { total, allocation -> Math.addExact(total, allocation.budgetMinor) } <= periodsById.getValue(periodId).newFundsMinor
        }) { "Los presupuestos superan los fondos del periodo" }
        require(payload.movements.all {
            it.periodId in periodIds && it.pocketId in pocketIds && (it.paymentMethodId == null || it.paymentMethodId in methodIds) &&
                it.sarAmountMinor > 0 && it.type in MovementType.entries.map { type -> type.name } &&
                it.currency.matches(Regex("[A-Z]{3}")) && it.conversion in ConversionStatus.entries.map { status -> status.name } &&
                it.localDate >= periodsById.getValue(it.periodId).start && it.localDate < periodsById.getValue(it.periodId).endExclusive &&
                (it.currency == "SAR" || (it.originalAmountMinor ?: 0) > 0) && it.zoneId == "Asia/Riyadh"
        }) { "Relación de movimiento inválida" }
        require(payload.templates.all {
            it.name.isNotBlank() && it.amountMinor > 0 && it.pocketId in pocketIds &&
                (it.paymentMethodId == null || it.paymentMethodId in methodIds)
        }) {
            "Relación de plantilla inválida"
        }
        return payload
    }

    private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun minorString(value: Long): String = java.math.BigDecimal.valueOf(value, 2).toPlainString()
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
    val paymentMethods: List<PaymentMethodDto>,
    val movements: List<MovementDto>,
    val templates: List<TemplateDto>,
)

@Serializable private data class PeriodDto(val id: String, val start: Long, val endExclusive: Long, val newFundsMinor: Long, val startDay: Int) {
    fun toEntity() = PeriodEntity(id, start, endExclusive, newFundsMinor, startDay)
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
@Serializable private data class PaymentMethodDto(val id: String, val name: String, val archived: Boolean) {
    fun toEntity() = PaymentMethodEntity(id, name, archived)
}
@Serializable
private data class MovementDto(
    val id: String,
    val periodId: String,
    val pocketId: String,
    val type: String,
    val sarAmountMinor: Long,
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
        id, periodId, pocketId, type, sarAmountMinor, occurredAt, localDate, zoneId, merchant, note,
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
) {
    fun toEntity() = RecurringTemplateEntity(id, name, amountMinor, pocketId, paymentMethodId, archived)
}
