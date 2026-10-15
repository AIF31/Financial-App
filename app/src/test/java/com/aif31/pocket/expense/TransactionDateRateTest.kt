package com.aif31.pocket.expense

import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.fx.DefaultExchangeRateRepository
import com.aif31.pocket.fx.FxQuote
import com.aif31.pocket.fx.FxQuoteCache
import com.aif31.pocket.fx.HttpsBanxicoClient
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** A confirmed conversion must carry the Banxico FIX of the Movement's own date. */
class TransactionDateRateTest {
    private val purchase = LocalDate.of(2026, 10, 14)
    private val fix = mapOf(
        LocalDate.of(2026, 10, 12) to "17.5000",
        LocalDate.of(2026, 10, 13) to "18.0000",
        purchase to "19.0000",
    )
    private val request = ExpenseRequest(purchase, SupportedCurrency.MXN, SupportedCurrency.SAR, 10_000)

    // 10:00 in Riyadh on the purchase day is 01:00 in Mexico City, before Banxico publishes that day's FIX.
    private val purchaseMorning = Instant.parse("2026-10-14T07:00:00Z")
    // 09:00 in Mexico City the next day: no FIX dated on the purchase day can still appear.
    private val nextMorning = Instant.parse("2026-10-15T15:00:00Z")

    @Test fun same_day_entry_before_fix_publication_is_not_confirmed_at_the_prior_day_rate() = runTest {
        val banxico = FakeBanxico(publishedThrough = purchase.minusDays(1))
        val result = holder(banxico, MemoryCache(), purchaseMorning).resolve(request, onlineFxEnabled = true)

        assertConfirmedOnlyAtPurchaseDate(result)
        assertEquals(ConversionStatus.ESTIMATED, result.status)
        assertEquals(purchase.minusDays(1), result.effectiveDate)
    }

    @Test fun provider_failure_does_not_confirm_an_older_cached_rate() = runTest {
        val cache = MemoryCache()
        holder(FakeBanxico(publishedThrough = LocalDate.of(2026, 10, 12)), cache, purchaseMorning)
            .resolve(request.copy(date = LocalDate.of(2026, 10, 12)), onlineFxEnabled = true)

        val result = holder(FakeBanxico(publishedThrough = purchase, failing = true), cache, nextMorning)
            .resolve(request, onlineFxEnabled = true)

        assertConfirmedOnlyAtPurchaseDate(result)
        assertEquals(ConversionStatus.ESTIMATED, result.status)
    }

    @Test fun published_purchase_day_fix_is_confirmed() = runTest {
        val result = holder(FakeBanxico(publishedThrough = purchase), MemoryCache(), nextMorning)
            .resolve(request, onlineFxEnabled = true)

        assertEquals(ConversionStatus.CONFIRMED, result.status)
        assertEquals(purchase, result.effectiveDate)
        // 100 MXN / 19 MXN per USD * 3.75 SAR per USD.
        assertEquals(1_974L, result.accountingAmountMinor)
    }

    @Test fun cached_purchase_day_fix_is_confirmed_offline() = runTest {
        val cache = MemoryCache()
        holder(FakeBanxico(publishedThrough = purchase), cache, nextMorning).resolve(request, onlineFxEnabled = true)

        val result = holder(FakeBanxico(publishedThrough = purchase, failing = true), cache, nextMorning)
            .resolve(request, onlineFxEnabled = true)

        assertEquals(ConversionStatus.CONFIRMED, result.status)
        assertEquals(purchase, result.effectiveDate)
    }

    @Test fun closed_non_business_day_uses_the_final_prior_fix() = runTest {
        val saturday = LocalDate.of(2026, 10, 17)
        val result = holder(FakeBanxico(publishedThrough = saturday), MemoryCache(), Instant.parse("2026-10-19T15:00:00Z"))
            .resolve(request.copy(date = saturday), onlineFxEnabled = true)

        assertEquals(ConversionStatus.CONFIRMED, result.status)
        assertEquals(purchase, result.effectiveDate)
    }

    private fun assertConfirmedOnlyAtPurchaseDate(result: ExpenseConversion) {
        if (result.status == ConversionStatus.CONFIRMED) {
            assertEquals("Confirmed rate must be dated on the purchase", purchase, result.effectiveDate)
        }
    }

    private fun holder(banxico: FakeBanxico, cache: FxQuoteCache, now: Instant) = ExpenseEntryStateHolder(
        DefaultExchangeRateRepository(
            HttpsBanxicoClient("test-token", banxico::get), cache, onlineFxEnabled = { true }, Clock.fixed(now, ZoneOffset.UTC),
        ),
    )

    private inner class FakeBanxico(private val publishedThrough: LocalDate, private val failing: Boolean = false) {
        suspend fun get(start: LocalDate, end: LocalDate, token: String): String {
            if (failing) throw IOException("offline")
            val data = fix.filterKeys { it in start..minOf(end, publishedThrough) }.entries.joinToString(",") { (date, value) ->
                """{"fecha":"%02d/%02d/%d","dato":"$value"}""".format(date.dayOfMonth, date.monthValue, date.year)
            }
            return """{"bmx":{"series":[{"idSerie":"SF43718","datos":[$data]}]}}"""
        }
    }

    private class MemoryCache : FxQuoteCache {
        private val saved = mutableListOf<FxQuote>()
        override suspend fun put(quote: FxQuote) { saved += quote }
        override suspend fun latestEligible(requestedDate: LocalDate, base: SupportedCurrency, quote: SupportedCurrency) =
            saved.filter { it.base == base && it.quote == quote && it.effectiveDate in requestedDate.minusDays(7)..requestedDate }
                .maxByOrNull { it.effectiveDate }
                ?.let { it.copy(requestedDate = requestedDate, source = it.source + " · CACHÉ") }
    }
}
