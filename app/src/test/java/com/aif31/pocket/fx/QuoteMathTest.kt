package com.aif31.pocket.fx

import com.aif31.pocket.domain.SupportedCurrency
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuoteMathTest {
    private val date = LocalDate.of(2026, 8, 31)

    @Test
    fun `direct inverse and usd bridged quotes preserve direction`() {
        val usdToMxn = FxQuote(date, date, SupportedCurrency.USD, SupportedCurrency.MXN, "17.25", "BANXICO_FIX")
        val usdToSar = FxQuote(date, date, SupportedCurrency.USD, SupportedCurrency.SAR, "3.75", "SAMA_PARITY")

        assertEquals(1_725L, usdToMxn.convertMinor(100))
        assertEquals(100L, inverseQuote(usdToMxn).convertMinor(1_725))
        assertEquals(375L, usdToSar.convertMinor(100))
        assertEquals(100L, inverseQuote(usdToSar).convertMinor(375))
        assertEquals(
            1_725L,
            composeQuotes(inverseQuote(usdToSar), usdToMxn, "SAMA_PARITY + BANXICO_FIX")
                .convertMinor(375),
        )
        assertEquals(
            375L,
            composeQuotes(inverseQuote(usdToMxn), usdToSar, "BANXICO_FIX + SAMA_PARITY")
                .convertMinor(1_725),
        )
    }

    @Test
    fun `conversion rounds half up into supported minor units`() {
        val quote = FxQuote(date, date, SupportedCurrency.USD, SupportedCurrency.MXN, "1.005", "TEST")

        assertEquals(101L, quote.convertMinor(100))
        assertEquals(-101L, quote.convertMinor(-100))
    }

    @Test
    fun `same currency quote is exact and offline`() {
        val quote = sameCurrencyQuote(date, SupportedCurrency.SAR)

        assertEquals("1", quote.rate)
        assertEquals("MISMA_MONEDA", quote.source)
        assertEquals(12_345L, quote.convertMinor(12_345))
    }

    @Test
    fun `quote validation rejects invalid rates and dates`() {
        assertThrows(IllegalArgumentException::class.java) {
            FxQuote(date, date, SupportedCurrency.USD, SupportedCurrency.MXN, "0", "TEST")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FxQuote(date, date.plusDays(1), SupportedCurrency.USD, SupportedCurrency.MXN, "17.25", "TEST")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FxQuote(date, date.minusDays(8), SupportedCurrency.USD, SupportedCurrency.MXN, "17.25", "TEST")
        }
    }

    @Test
    fun `composition rejects mismatched bridge and requested dates`() {
        val usdToMxn = FxQuote(date, date, SupportedCurrency.USD, SupportedCurrency.MXN, "17.25", "BANXICO_FIX")
        val sarToMxn = FxQuote(date, date, SupportedCurrency.SAR, SupportedCurrency.MXN, "4.6", "COMPOSED")

        assertThrows(IllegalArgumentException::class.java) {
            composeQuotes(usdToMxn, sarToMxn, "INVALID")
        }
        assertThrows(IllegalArgumentException::class.java) {
            composeQuotes(
                inverseQuote(usdToMxn),
                FxQuote(date.minusDays(1), date.minusDays(1), SupportedCurrency.USD, SupportedCurrency.SAR, "3.75", "SAMA_PARITY"),
                "INVALID",
            )
        }
    }
}
