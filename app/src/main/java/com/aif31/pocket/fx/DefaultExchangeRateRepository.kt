package com.aif31.pocket.fx

import com.aif31.pocket.domain.SupportedCurrency
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

class DefaultExchangeRateRepository(
    private val banxico: BanxicoRateSource,
    private val cache: FxQuoteCache,
    private val onlineFxEnabled: suspend () -> Boolean,
) : ExchangeRateRepository {
    override suspend fun quote(
        requestedDate: LocalDate,
        base: SupportedCurrency,
        quote: SupportedCurrency,
        forceRefresh: Boolean,
    ): FxQuote {
        if (base == quote) return sameCurrencyQuote(requestedDate, base)
        if (!onlineFxEnabled()) throw QuoteFailure.ConsentRequired()

        return try {
            buildQuote(requestedDate, base, quote).also { cache.put(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            cache.latestEligible(requestedDate, base, quote) ?: throw QuoteFailure.Unavailable()
        }
    }

    private suspend fun buildQuote(
        requestedDate: LocalDate,
        base: SupportedCurrency,
        quote: SupportedCurrency,
    ): FxQuote {
        val usdToSar = FxQuote(
            requestedDate = requestedDate,
            effectiveDate = requestedDate,
            base = SupportedCurrency.USD,
            quote = SupportedCurrency.SAR,
            rate = "3.75",
            source = "SAMA_PARITY",
        )
        return when (base to quote) {
            SupportedCurrency.USD to SupportedCurrency.SAR -> usdToSar
            SupportedCurrency.SAR to SupportedCurrency.USD -> inverseQuote(usdToSar)
            SupportedCurrency.USD to SupportedCurrency.MXN -> banxico.fetchUsdToMxn(requestedDate)
            SupportedCurrency.MXN to SupportedCurrency.USD -> inverseQuote(banxico.fetchUsdToMxn(requestedDate))
            SupportedCurrency.SAR to SupportedCurrency.MXN -> composeQuotes(
                inverseQuote(usdToSar),
                banxico.fetchUsdToMxn(requestedDate),
                "SAMA_PARITY + BANXICO_FIX_SF43718",
            )
            SupportedCurrency.MXN to SupportedCurrency.SAR -> composeQuotes(
                inverseQuote(banxico.fetchUsdToMxn(requestedDate)),
                usdToSar,
                "BANXICO_FIX_SF43718 + SAMA_PARITY",
            )
            else -> error("Unsupported currency pair")
        }
    }
}
