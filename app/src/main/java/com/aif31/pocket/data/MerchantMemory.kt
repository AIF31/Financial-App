package com.aif31.pocket.data

import com.aif31.pocket.domain.SupportedCurrency

data class MerchantMemoryEntry(
    val merchantKey: String,
    val displayName: String,
    val pocket: Pocket,
    val latestExpense: Movement,
    val accountingCurrency: SupportedCurrency,
    val expenseCount: Int,
) {
    val pocketArchived: Boolean get() = pocket.archived
}

/** The same resolution rule drives memory management and notification auto-recording, including timestamp ties. */
internal fun Iterable<Movement>.latestExpenseForMerchant(key: String): Movement? =
    filter { it.type == MovementType.EXPENSE && merchantKey(it.merchant) == key }
        .maxByOrNull { it.occurredAtUtcMillis }

/** Merchant memory is derived entirely from existing expenses. Reassigning it edits one expense. */
object MerchantMemory {
    fun entries(state: LedgerState): List<MerchantMemoryEntry> {
        val pockets = state.pocketCatalog.associateBy { it.id }
        val periods = state.periods.associateBy { it.id }
        return state.movements.filter { it.type == MovementType.EXPENSE && merchantKey(it.merchant) != null }
            .groupBy { merchantKey(it.merchant)!! }
            .map { (key, expenses) ->
                val latest = requireNotNull(expenses.latestExpenseForMerchant(key))
                MerchantMemoryEntry(
                    merchantKey = key,
                    displayName = requireNotNull(latest.merchant),
                    pocket = pockets.getValue(latest.pocketId),
                    latestExpense = latest,
                    accountingCurrency = periods.getValue(latest.periodId).accountingCurrency,
                    expenseCount = expenses.size,
                )
            }
            .sortedByDescending { it.latestExpense.occurredAtUtcMillis }
    }

    fun reassign(state: LedgerState, merchantKey: String, newPocketId: String): LedgerCommand.AddMovement {
        val latest = requireNotNull(state.movements.latestExpenseForMerchant(merchantKey)) { "El comercio ya no tiene gastos" }
        require(newPocketId != latest.pocketId) { "El comercio ya usa este Pocket" }
        val pocket = requireNotNull(state.pocketCatalog.firstOrNull { it.id == newPocketId }) { "Pocket inexistente" }
        require(!pocket.archived) { "El Pocket está archivado" }
        require(state.pocketSummariesByPeriod[latest.periodId].orEmpty().any {
            it.pocket.id == newPocketId && !it.retiredThisPeriod
        }) { "El Pocket no está activo en este periodo" }
        return LedgerCommand.AddMovement(
            id = latest.id,
            expectedMovement = latest,
            pocketId = newPocketId,
            type = latest.type,
            accountingAmountMinor = latest.accountingAmountMinor,
            occurredAtUtcMillis = latest.occurredAtUtcMillis,
            localDate = latest.localDate,
            merchant = latest.merchant,
            note = latest.note,
            paymentMethodId = latest.paymentMethodId,
            originalAmountMinor = latest.originalAmountMinor,
            originalCurrencyCode = latest.originalCurrencyCode,
            conversionStatus = latest.conversionStatus,
            rate = latest.rate,
            conversionEffectiveDate = latest.conversionEffectiveDate,
            conversionSource = latest.conversionSource,
            accountingCurrency = state.periods.first { it.id == latest.periodId }.accountingCurrency,
        )
    }
}
