package com.aif31.pocket.fx

import com.aif31.pocket.domain.SupportedCurrency
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BanxicoClientTest {
    private val requested = LocalDate.of(2026, 8, 31)

    @Test
    fun `parser chooses nearest prior numeric FIX observation`() {
        val quote = parseBanxicoUsdToMxn(fixture("fix_numeric.json"), requested)

        assertEquals(requested, quote.requestedDate)
        assertEquals(LocalDate.of(2026, 8, 29), quote.effectiveDate)
        assertEquals(SupportedCurrency.USD, quote.base)
        assertEquals(SupportedCurrency.MXN, quote.quote)
        assertEquals("17.25", quote.rate)
        assertEquals("BANXICO_FIX_SF43718", quote.source)
    }

    @Test
    fun `parser rejects stale or malformed provider data with a safe error`() {
        assertThrows(QuoteFailure.Unavailable::class.java) {
            parseBanxicoUsdToMxn(fixture("fix_non_business.json"), requested)
        }
        assertThrows(QuoteFailure.Unavailable::class.java) {
            parseBanxicoUsdToMxn(fixture("fix_malformed.json"), requested)
        }
        assertThrows(QuoteFailure.Unavailable::class.java) {
            parseBanxicoUsdToMxn("not-json", requested)
        }
    }

    @Test
    fun `client refuses empty configuration without calling transport`() = runTest {
        var calls = 0
        val client = HttpsBanxicoClient(
            token = "",
            transport = BanxicoTransport { _, _, _ -> calls += 1; fixture("fix_numeric.json") },
        )

        val failure = runCatching { client.fetchUsdToMxn(requested) }.exceptionOrNull()

        assertTrue(failure is QuoteFailure.ConfigurationUnavailable)
        assertEquals(0, calls)
    }

    @Test
    fun `client preserves coroutine cancellation`() {
        val client = HttpsBanxicoClient(
            token = "configured",
            transport = BanxicoTransport { _, _, _ -> throw CancellationException("cancelled") },
        )

        assertThrows(CancellationException::class.java) {
            runTest { client.fetchUsdToMxn(requested) }
        }
    }

    private fun fixture(name: String): String = requireNotNull(
        javaClass.classLoader?.getResource("banxico/$name"),
    ).readText()
}
