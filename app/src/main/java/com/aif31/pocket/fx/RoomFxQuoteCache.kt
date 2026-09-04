package com.aif31.pocket.fx

import com.aif31.pocket.data.FinanceDao
import com.aif31.pocket.data.FxRateCacheEntity
import com.aif31.pocket.domain.SupportedCurrency
import java.time.Clock
import java.time.LocalDate

interface FxQuoteCache {
    suspend fun put(quote: FxQuote)
    suspend fun latestEligible(
        requestedDate: LocalDate,
        base: SupportedCurrency,
        quote: SupportedCurrency,
    ): FxQuote?
}

class RoomFxQuoteCache(
    private val dao: FinanceDao,
    private val clock: Clock = Clock.systemUTC(),
) : FxQuoteCache {
    override suspend fun put(quote: FxQuote) {
        dao.putFxRate(
            FxRateCacheEntity(
                baseCurrencyCode = quote.base.name,
                quoteCurrencyCode = quote.quote.name,
                effectiveEpochDay = quote.effectiveDate.toEpochDay(),
                rate = quote.rate,
                source = quote.source.removeSuffix(CACHE_SUFFIX),
                cachedAtUtcMillis = clock.millis(),
            )
        )
    }

    override suspend fun latestEligible(
        requestedDate: LocalDate,
        base: SupportedCurrency,
        quote: SupportedCurrency,
    ): FxQuote? = dao.latestFxRate(
        baseCurrencyCode = base.name,
        quoteCurrencyCode = quote.name,
        minimumEpochDay = requestedDate.minusDays(FxQuote.MAX_QUOTE_AGE_DAYS).toEpochDay(),
        requestedEpochDay = requestedDate.toEpochDay(),
    )?.let { cached ->
        FxQuote(
            requestedDate = requestedDate,
            effectiveDate = LocalDate.ofEpochDay(cached.effectiveEpochDay),
            base = base,
            quote = quote,
            rate = cached.rate,
            source = cached.source + CACHE_SUFFIX,
        )
    }

    private companion object {
        const val CACHE_SUFFIX = " · CACHÉ"
    }
}
