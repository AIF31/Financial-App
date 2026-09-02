package com.aif31.pocket.fx

import com.aif31.pocket.domain.SupportedCurrency
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultExchangeRateRepositoryTest {
    private val requested = LocalDate.of(2026, 8, 31)

    @Test
    fun `same currency stays offline even without consent`() = runTest {
        val source = FakeBanxicoSource()
        val repository = DefaultExchangeRateRepository(source, FakeCache(), onlineFxEnabled = { false })

        val quote = repository.quote(requested, SupportedCurrency.MXN, SupportedCurrency.MXN)

        assertEquals("1", quote.rate)
        assertEquals(0, source.calls)
    }

    @Test
    fun `foreign quote requires explicit consent`() = runTest {
        val repository = DefaultExchangeRateRepository(FakeBanxicoSource(), FakeCache(), onlineFxEnabled = { false })

        val failure = runCatching {
            repository.quote(requested, SupportedCurrency.USD, SupportedCurrency.SAR)
        }.exceptionOrNull()

        assertTrue(failure is QuoteFailure.ConsentRequired)
    }

    @Test
    fun `repository supplies every direct inverse and USD bridged direction`() = runTest {
        val repository = DefaultExchangeRateRepository(FakeBanxicoSource(), FakeCache(), onlineFxEnabled = { true })

        assertEquals(1_725L, repository.quote(requested, SupportedCurrency.USD, SupportedCurrency.MXN).convertMinor(100))
        assertEquals(100L, repository.quote(requested, SupportedCurrency.MXN, SupportedCurrency.USD).convertMinor(1_725))
        assertEquals(375L, repository.quote(requested, SupportedCurrency.USD, SupportedCurrency.SAR).convertMinor(100))
        assertEquals(100L, repository.quote(requested, SupportedCurrency.SAR, SupportedCurrency.USD).convertMinor(375))
        assertEquals(1_725L, repository.quote(requested, SupportedCurrency.SAR, SupportedCurrency.MXN).convertMinor(375))
        assertEquals(375L, repository.quote(requested, SupportedCurrency.MXN, SupportedCurrency.SAR).convertMinor(1_725))
    }

    @Test
    fun `successful foreign quote is cached in its requested direction`() = runTest {
        val cache = FakeCache()
        val repository = DefaultExchangeRateRepository(FakeBanxicoSource(), cache, onlineFxEnabled = { true })

        val quote = repository.quote(requested, SupportedCurrency.SAR, SupportedCurrency.MXN)

        assertEquals(quote, cache.saved.single())
        assertEquals(SupportedCurrency.SAR, cache.saved.single().base)
        assertEquals(SupportedCurrency.MXN, cache.saved.single().quote)
    }

    @Test
    fun `network failure uses only an eligible exact-direction cached quote`() = runTest {
        val eligible = FxQuote(
            requested,
            requested.minusDays(3),
            SupportedCurrency.MXN,
            SupportedCurrency.SAR,
            "0.22",
            "BANXICO_FIX + SAMA_PARITY",
        )
        val repository = DefaultExchangeRateRepository(
            FakeBanxicoSource(failure = QuoteFailure.Unavailable()),
            FakeCache(eligible),
            onlineFxEnabled = { true },
        )

        val quote = repository.quote(requested, SupportedCurrency.MXN, SupportedCurrency.SAR)

        assertEquals("BANXICO_FIX + SAMA_PARITY · CACHÉ", quote.source)
        assertEquals("0.22", quote.rate)
    }

    @Test
    fun `network failure without eligible cache remains unavailable`() {
        val repository = DefaultExchangeRateRepository(
            FakeBanxicoSource(failure = IllegalStateException("secret provider detail")),
            FakeCache(),
            onlineFxEnabled = { true },
        )

        val error = assertThrows(QuoteFailure.Unavailable::class.java) {
            runTest { repository.quote(requested, SupportedCurrency.USD, SupportedCurrency.MXN) }
        }
        assertEquals("No hay un tipo de cambio disponible para esa fecha", error.message)
    }

    @Test
    fun `repository preserves cancellation instead of falling back`() {
        val repository = DefaultExchangeRateRepository(
            FakeBanxicoSource(failure = CancellationException("cancelled")),
            FakeCache(
                FxQuote(requested, requested, SupportedCurrency.USD, SupportedCurrency.MXN, "17", "CACHE")
            ),
            onlineFxEnabled = { true },
        )

        assertThrows(CancellationException::class.java) {
            runTest { repository.quote(requested, SupportedCurrency.USD, SupportedCurrency.MXN) }
        }
    }

    private class FakeBanxicoSource(
        private val failure: Throwable? = null,
    ) : BanxicoRateSource {
        var calls: Int = 0

        override suspend fun fetchUsdToMxn(requestedDate: LocalDate): FxQuote {
            calls += 1
            failure?.let { throw it }
            return FxQuote(
                requestedDate,
                requestedDate.minusDays(2),
                SupportedCurrency.USD,
                SupportedCurrency.MXN,
                "17.25",
                "BANXICO_FIX_SF43718",
            )
        }
    }

    private class FakeCache(private val available: FxQuote? = null) : FxQuoteCache {
        val saved = mutableListOf<FxQuote>()

        override suspend fun put(quote: FxQuote) {
            saved += quote
        }

        override suspend fun latestEligible(
            requestedDate: LocalDate,
            base: SupportedCurrency,
            quote: SupportedCurrency,
        ): FxQuote? = available?.takeIf {
            it.requestedDate == requestedDate && it.base == base && it.quote == quote
        }?.let { it.copy(source = it.source + " · CACHÉ") }
    }
}
