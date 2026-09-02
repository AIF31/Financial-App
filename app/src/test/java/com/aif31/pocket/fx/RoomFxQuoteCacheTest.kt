package com.aif31.pocket.fx

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.FxRateCacheEntity
import com.aif31.pocket.domain.SupportedCurrency
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RoomFxQuoteCacheTest {
    private lateinit var database: FinanceDatabase
    private lateinit var cache: RoomFxQuoteCache
    private val requested = LocalDate.of(2026, 8, 31)

    @Before
    fun setUp() {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        cache = RoomFxQuoteCache(
            database.financeDao(),
            Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `lookup returns nearest eligible prior quote in the exact direction`() = runTest {
        cache.put(FxQuote(requested, requested.minusDays(6), SupportedCurrency.USD, SupportedCurrency.MXN, "17.10", "OLD"))
        cache.put(FxQuote(requested, requested.minusDays(2), SupportedCurrency.USD, SupportedCurrency.MXN, "17.25", "RECENT"))
        cache.put(FxQuote(requested, requested.minusDays(1), SupportedCurrency.MXN, SupportedCurrency.USD, "0.058", "INVERSE"))

        val found = cache.latestEligible(requested, SupportedCurrency.USD, SupportedCurrency.MXN)

        assertEquals(requested.minusDays(2), found?.effectiveDate)
        assertEquals("17.25", found?.rate)
        assertEquals("RECENT · CACHÉ", found?.source)
        assertNull(cache.latestEligible(requested, SupportedCurrency.SAR, SupportedCurrency.MXN))
    }

    @Test
    fun `lookup rejects observations older than seven calendar days`() = runTest {
        database.financeDao().putFxRate(
            FxRateCacheEntity(
                baseCurrencyCode = "USD",
                quoteCurrencyCode = "MXN",
                effectiveEpochDay = requested.minusDays(8).toEpochDay(),
                rate = "17.00",
                source = "STALE",
                cachedAtUtcMillis = 1L,
            ),
        )

        assertNull(cache.latestEligible(requested, SupportedCurrency.USD, SupportedCurrency.MXN))
    }
}
