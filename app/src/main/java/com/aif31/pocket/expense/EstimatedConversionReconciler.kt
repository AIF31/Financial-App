package com.aif31.pocket.expense

import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.PocketLedger
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.fx.ExchangeRateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Re-quotes provider-estimated Movements and confirms each one at the final rate of its own date. Legacy manual
 * estimates carry no provider source and are left for the person to confirm.
 */
internal class EstimatedConversionReconciler(
    private val ledger: PocketLedger,
    private val exchangeRates: ExchangeRateRepository,
) {
    /** A Movement whose rate is still provisional, or whose quote fails, stays estimated for a later pass. */
    suspend fun reconcile() {
        val state = ledger.state.first()
        val currencyByPeriod = state.periods.associate { it.id to it.accountingCurrency }
        state.movements
            .filter { it.conversionStatus == ConversionStatus.ESTIMATED && it.conversionSource != null }
            .forEach { estimate ->
                val originalMinor = estimate.originalAmountMinor ?: return@forEach
                val accounting = currencyByPeriod[estimate.periodId] ?: return@forEach
                val original = runCatching { SupportedCurrency.fromCode(estimate.originalCurrencyCode) }.getOrNull()
                    ?.takeIf { it != accounting } ?: return@forEach
                val quote = try {
                    exchangeRates.quote(estimate.localDate, original, accounting)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@forEach
                }
                if (!quote.isFinal || quote.requestedDate != estimate.localDate) return@forEach
                val amount = runCatching { quote.convertMinor(originalMinor) }.getOrNull()?.takeIf { it > 0 } ?: return@forEach
                ledger.execute(LedgerCommand.ConfirmConversion(estimate, amount, quote.rate, quote.effectiveDate, quote.source))
            }
    }
}
