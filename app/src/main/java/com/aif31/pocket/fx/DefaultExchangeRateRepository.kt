package com.aif31.pocket.fx

import com.aif31.pocket.domain.SupportedCurrency
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

class DefaultExchangeRateRepository(
    private val banxico: BanxicoRateSource,
    private val cache: FxQuoteCache,
    private val onlineFxEnabled: suspend () -> Boolean,
    private val clock: Clock = Clock.systemUTC(),
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
            buildQuote(requestedDate, base, quote).also { cache.put(it) }.withFinality(live = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            cache.latestEligible(requestedDate, base, quote)?.withFinality(live = false)
                ?: throw (failure as? QuoteFailure ?: QuoteFailure.Unavailable())
        }
    }

    /**
     * A rate observed on the requested date is final. An older observation is final only when it came from the
     * provider after the requested Mexico City day ended, because Banxico can no longer publish a FIX for it;
     * a cached older observation may predate a FIX the cache never received.
     */
    private fun FxQuote.withFinality(live: Boolean): FxQuote = copy(
        isFinal = effectiveDate == requestedDate ||
            (live && requestedDate.isBefore(LocalDate.now(clock.withZone(BANXICO_ZONE)))),
    )

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

    private companion object {
        val BANXICO_ZONE: ZoneId = ZoneId.of("America/Mexico_City")
    }
}
