package com.aif31.pocket.expense

import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.Movement
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.fx.ExchangeRateRepository
import com.aif31.pocket.fx.QuoteFailure
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

internal data class ExpenseRequest(
    val date: LocalDate,
    val inputCurrency: SupportedCurrency,
    val accountingCurrency: SupportedCurrency,
    val inputAmountMinor: Long,
)

internal data class ExpenseConversion(
    val accountingAmountMinor: Long,
    val rate: String? = null,
    val effectiveDate: LocalDate? = null,
    val source: String? = null,
    val status: ConversionStatus = ConversionStatus.CONFIRMED,
)

internal sealed interface QuoteUiState {
    data object Idle : QuoteUiState
    data object Loading : QuoteUiState
    data class Ready(val conversion: ExpenseConversion) : QuoteUiState
    data class Error(val message: String) : QuoteUiState
}

/** Resolves immutable drafts; the screen owns cancellation and request-keyed lifecycle. */
internal class ExpenseEntryStateHolder(
    private val repository: ExchangeRateRepository?,
    private val initial: Movement? = null,
    private val initialAccountingCurrency: SupportedCurrency? = null,
) {
    suspend fun resolve(request: ExpenseRequest, onlineFxEnabled: Boolean): ExpenseConversion {
        require(request.inputAmountMinor > 0)
        if (initial != null && request.accountingCurrency == initialAccountingCurrency &&
            request.date == initial.localDate && request.inputCurrency.name == initial.originalCurrencyCode &&
            request.inputAmountMinor == (initial.originalAmountMinor ?: initial.accountingAmountMinor)
        ) {
            val stored = ExpenseConversion(
                initial.accountingAmountMinor, initial.rate, initial.conversionEffectiveDate,
                initial.conversionSource, initial.conversionStatus,
            )
            // A provider estimate is quoted again so saving it can confirm it; failing that, it stays as stored.
            val providerEstimate = initial.conversionStatus == ConversionStatus.ESTIMATED && initial.conversionSource != null
            if (!providerEstimate || !onlineFxEnabled || repository == null) return stored
            return try {
                quote(request, repository)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                stored
            }
        }
        if (request.inputCurrency == request.accountingCurrency) {
            // Rate 1 is implicit in the ledger's canonical same-currency representation.
            return ExpenseConversion(request.inputAmountMinor)
        }
        if (!onlineFxEnabled) throw QuoteFailure.ConsentRequired()
        return quote(request, repository ?: throw QuoteFailure.ConfigurationUnavailable())
    }

    private suspend fun quote(request: ExpenseRequest, provider: ExchangeRateRepository): ExpenseConversion {
        val quote = provider.quote(request.date, request.inputCurrency, request.accountingCurrency)
        if (quote.base != request.inputCurrency || quote.quote != request.accountingCurrency || quote.requestedDate != request.date) {
            throw QuoteFailure.Unavailable()
        }
        val converted = try { quote.convertMinor(request.inputAmountMinor) } catch (_: ArithmeticException) {
            throw QuoteFailure.Unavailable()
        }
        if (converted <= 0) throw QuoteFailure.Unavailable()
        // A rate dated before the Movement while that day's FIX can still appear stays an estimate until reconciled.
        val status = if (quote.isFinal) ConversionStatus.CONFIRMED else ConversionStatus.ESTIMATED
        return ExpenseConversion(converted, quote.rate, quote.effectiveDate, quote.source, status)
    }
}
