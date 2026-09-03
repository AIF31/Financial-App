package com.aif31.pocket.data

import com.aif31.pocket.domain.SupportedCurrency
import java.time.LocalDate

data class CurrencyBoundary(
    val from: SupportedCurrency,
    val to: SupportedCurrency,
    val rate: String,
    val effectiveDate: LocalDate,
    val source: String,
)

data class PendingCurrencyChange(
    val boundary: CurrencyBoundary,
)

data class Period(
    val id: String,
    val start: LocalDate,
    val endExclusive: LocalDate,
    val newFundsMinor: Long,
    val configuredStartDay: Int,
    val isTransition: Boolean = false,
    val needsReview: Boolean = false,
    val accountingCurrency: SupportedCurrency = SupportedCurrency.SAR,
    val priorCurrencyBoundary: CurrencyBoundary? = null,
)

enum class ComparisonMode { TOTAL_SPEND, DAILY_PACE }

enum class PocketIconKey {
    SUPERMARKET,
    RESTAURANT,
    TRANSPORT,
    UNIVERSITY,
    HEALTH,
    TRAVEL,
    LEISURE,
    GIFTS,
    EMERGENCY,
    OTHER;

    companion object {
        fun fromStored(value: String?, pocketName: String): PocketIconKey =
            entries.firstOrNull { it.name == value } ?: forName(pocketName)

        fun forName(name: String): PocketIconKey = when {
            name.contains("supermercado", ignoreCase = true) -> SUPERMARKET
            name.contains("restaurante", ignoreCase = true) || name.contains("café", ignoreCase = true) -> RESTAURANT
            name.contains("transporte", ignoreCase = true) -> TRANSPORT
            name.contains("universidad", ignoreCase = true) -> UNIVERSITY
            name.contains("salud", ignoreCase = true) -> HEALTH
            name.contains("viaje", ignoreCase = true) -> TRAVEL
            name.contains("ocio", ignoreCase = true) -> LEISURE
            name.contains("regalo", ignoreCase = true) -> GIFTS
            name.contains("emergencia", ignoreCase = true) -> EMERGENCY
            else -> OTHER
        }
    }
}

data class Pocket(
    val id: String,
    val name: String,
    val iconKey: PocketIconKey,
    val sortOrder: Int,
    val archived: Boolean,
    val rolloverEnabled: Boolean,
)

enum class MovementType { EXPENSE, REFUND }
enum class ConversionStatus { ESTIMATED, CONFIRMED }

data class Movement(
    val id: String,
    val pocketId: String,
    val pocketName: String,
    val periodId: String,
    val type: MovementType,
    val accountingAmountMinor: Long,
    val occurredAtUtcMillis: Long,
    val localDate: LocalDate,
    val zoneId: String,
    val merchant: String?,
    val note: String?,
    val paymentMethodId: String?,
    val paymentMethodName: String?,
    val originalAmountMinor: Long?,
    val originalCurrencyCode: String,
    val conversionStatus: ConversionStatus,
    val rate: String?,
)

data class PaymentMethod(val id: String, val name: String, val archived: Boolean)

data class RecurringTemplate(
    val id: String,
    val name: String,
    val amountMinor: Long,
    val pocketId: String,
    val paymentMethodId: String?,
    val archived: Boolean,
    val inputCurrency: SupportedCurrency = SupportedCurrency.SAR,
)

data class PocketPeriodSummary(
    val pocket: Pocket,
    val budgetMinor: Long,
    val rolloverMinor: Long,
    val rolloverEligible: Boolean = false,
    val retiredThisPeriod: Boolean = false,
    val rolloverReleasedMinor: Long = 0,
    val expenseMinor: Long = 0,
    val refundMinor: Long = 0,
    val netSpendMinor: Long,
    val availabilityMinor: Long,
    val consumedPercent: Int,
    val atRisk: Boolean,
    val exhausted: Boolean,
)

data class LedgerState(
    val periods: List<Period> = emptyList(),
    val currentPeriod: Period? = null,
    val pockets: List<PocketPeriodSummary> = emptyList(),
    val pocketSummariesByPeriod: Map<String, List<PocketPeriodSummary>> = emptyMap(),
    val movements: List<Movement> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val templates: List<RecurringTemplate> = emptyList(),
    val unallocatedMinor: Long = 0,
    val newFundsMinor: Long = 0,
    val rolloverTotalMinor: Long = 0,
    val netSpendMinor: Long = 0,
    val trackedAvailabilityMinor: Long = 0,
    val previousPeriodNetSpendMinor: Long? = null,
    val comparisonMode: ComparisonMode = ComparisonMode.TOTAL_SPEND,
    val previousPeriodComparisonMinor: Long? = null,
    val elapsedDays: Int = 0,
    val totalDays: Int = 0,
    val projectionMinor: Long = 0,
    val currentLocalDate: LocalDate = LocalDate.of(1970, 1, 1),
    val currentInstantMillis: Long = 0,
    val pendingCurrencyChange: PendingCurrencyChange? = null,
    val defaultPaymentMethodId: String? = null,
) {
    val needsOnboarding: Boolean get() = periods.isEmpty()
}

sealed interface LedgerCommand {
    data class Initialize(
        val newFundsMinor: Long,
        val startDay: Int = 25,
        val accountingCurrency: SupportedCurrency = SupportedCurrency.SAR,
    ) : LedgerCommand
    data class UpdatePeriodFunds(val periodId: String, val newFundsMinor: Long) : LedgerCommand
    data class SetAllocation(val periodId: String, val pocketId: String, val amountMinor: Long) : LedgerCommand
    data class UpsertPocket(
        val id: String? = null,
        val name: String,
        val rolloverEnabled: Boolean = false,
        val iconKey: PocketIconKey? = null,
    ) : LedgerCommand
    data class ArchivePocket(val pocketId: String, val archived: Boolean = true) : LedgerCommand
    data class MovePocket(val pocketId: String, val direction: Int) : LedgerCommand
    data class AddMovement(
        val id: String? = null,
        val pocketId: String,
        val type: MovementType,
        val accountingAmountMinor: Long,
        val occurredAtUtcMillis: Long,
        val localDate: LocalDate,
        val merchant: String? = null,
        val note: String? = null,
        val paymentMethodId: String? = null,
        val originalAmountMinor: Long? = null,
        val originalCurrencyCode: String? = null,
        val conversionStatus: ConversionStatus = ConversionStatus.CONFIRMED,
        val rate: String? = null,
        val accountingCurrency: SupportedCurrency? = null,
    ) : LedgerCommand
    data class DeleteMovement(val movementId: String) : LedgerCommand
    data class RestoreMovement(val movement: Movement) : LedgerCommand
    data class CreateNextPeriod(val startDay: Int? = null) : LedgerCommand
    data class CatchUpPeriods(val preferredStartDay: Int) : LedgerCommand
    data class MarkPeriodReviewed(val periodId: String) : LedgerCommand
    data class ScheduleCurrencyChange(
        val targetCurrency: SupportedCurrency,
        val rate: String,
        val effectiveDate: LocalDate,
        val source: String,
    ) : LedgerCommand
    data object CancelCurrencyChange : LedgerCommand
    data class UpsertPaymentMethod(val id: String? = null, val name: String) : LedgerCommand
    data class ArchivePaymentMethod(val id: String, val archived: Boolean = true) : LedgerCommand
    data class SetDefaultPaymentMethod(val id: String?) : LedgerCommand
    data class UpsertTemplate(
        val id: String? = null,
        val name: String,
        val amountMinor: Long,
        val pocketId: String,
        val paymentMethodId: String? = null,
        val inputCurrency: SupportedCurrency = SupportedCurrency.SAR,
    ) : LedgerCommand
    data class ArchiveTemplate(val id: String, val archived: Boolean = true) : LedgerCommand
}

sealed interface LedgerResult {
    data object Success : LedgerResult
    data class Rejected(val message: String) : LedgerResult
    data class Deleted(val movement: Movement) : LedgerResult
}

interface PocketLedger {
    val state: kotlinx.coroutines.flow.Flow<LedgerState>
    fun movementDefaults(): MovementDefaults
    suspend fun execute(command: LedgerCommand): LedgerResult
    suspend fun exportBackup(): ByteArray
    suspend fun previewBackup(bytes: ByteArray): BackupPreview
    suspend fun restoreBackup(bytes: ByteArray): LedgerResult
    suspend fun exportCsv(): ByteArray
}

data class MovementDefaults(
    val localDate: LocalDate,
    val instantMillis: Long,
)

data class BackupPreview(
    val version: Int,
    val periods: Int,
    val pockets: Int,
    val movements: Int,
    val valid: Boolean,
    val message: String? = null,
)
