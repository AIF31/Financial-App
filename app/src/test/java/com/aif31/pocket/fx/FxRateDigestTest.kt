package com.aif31.pocket.fx

import com.aif31.pocket.domain.SupportedCurrency
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FxRateDigestTest {
    @Test
    fun `latest FIX converts through the SAMA peg and compares with the previous FIX`() {
        val digest = sarToMxn(
            listOf(
                observation(2026, 10, 19, "18.7500"),
                observation(2026, 10, 20, "18.9375"),
            ),
        )!!

        assertEquals(LocalDate.of(2026, 10, 20), digest.date)
        assertEquals(BigDecimal("5.05"), digest.rate.stripTrailingZeros())
        assertEquals(LocalDate.of(2026, 10, 19), digest.previousDate)
        assertEquals(BigDecimal("1.00"), digest.changePercent)
    }

    @Test
    fun `week average covers FIX dates in the seven days ending on the latest FIX`() {
        // 13 Oct falls outside 14–20 Oct; the weekend has no FIX.
        val digest = sarToMxn(
            listOf(
                observation(2026, 10, 13, "30.0000"),
                observation(2026, 10, 14, "18.7500"),
                observation(2026, 10, 15, "18.7500"),
                observation(2026, 10, 16, "18.7500"),
                observation(2026, 10, 19, "18.7500"),
                observation(2026, 10, 20, "19.1250"),
            ),
        )!!

        assertEquals(5, digest.weekObservations)
        // (5.00 × 4 + 5.10) / 5 = 5.02
        assertEquals(BigDecimal("5.02"), digest.weekAverage.stripTrailingZeros())
        assertEquals(BigDecimal("1.59"), digest.versusAveragePercent)
    }

    @Test
    fun `a falling rate reports a negative change and a below-average position`() {
        val digest = sarToMxn(
            listOf(
                observation(2026, 10, 19, "19.1250"),
                observation(2026, 10, 20, "18.7500"),
            ),
        )!!

        assertEquals(BigDecimal("-1.96"), digest.changePercent)
        assertEquals(-1, digest.versusAveragePercent.signum())
    }

    @Test
    fun `a single FIX has no change and averages to itself`() {
        val digest = sarToMxn(listOf(observation(2026, 10, 20, "18.7500")))!!

        assertNull(digest.previousDate)
        assertNull(digest.changePercent)
        assertEquals(1, digest.weekObservations)
        assertEquals(0, digest.versusAveragePercent.signum())
    }

    @Test
    fun `no observations give no digest and unordered duplicates are tolerated`() {
        assertNull(sarToMxn(emptyList()))

        val digest = sarToMxn(
            listOf(
                observation(2026, 10, 20, "18.9375"),
                observation(2026, 10, 19, "18.7500"),
                observation(2026, 10, 20, "18.9375"),
            ),
        )!!

        assertEquals(LocalDate.of(2026, 10, 19), digest.previousDate)
        assertEquals(2, digest.weekObservations)
    }

    @Test
    fun `other pairs derive from the same FIX`() {
        val observations = listOf(observation(2026, 10, 19, "18.7500"), observation(2026, 10, 20, "18.9375"))

        val mxnToSar = FxRateDigest.of(observations, SupportedCurrency.MXN, SupportedCurrency.SAR)!!
        val usdToMxn = FxRateDigest.of(observations, SupportedCurrency.USD, SupportedCurrency.MXN)!!
        val mxnToUsd = FxRateDigest.of(observations, SupportedCurrency.MXN, SupportedCurrency.USD)!!

        assertEquals(BigDecimal("0.198019801980198"), mxnToSar.rate.setScale(15, java.math.RoundingMode.HALF_UP))
        assertEquals(BigDecimal("-0.99"), mxnToSar.changePercent)
        assertEquals(BigDecimal("18.9375"), usdToMxn.rate)
        assertEquals(BigDecimal("1.00"), usdToMxn.changePercent)
        assertEquals(BigDecimal("-0.99"), mxnToUsd.changePercent)
        assertEquals(SupportedCurrency.MXN, mxnToUsd.base)
        assertEquals(SupportedCurrency.USD, mxnToUsd.quote)
    }

    private fun sarToMxn(observations: List<FxObservation>) =
        FxRateDigest.of(observations, SupportedCurrency.SAR, SupportedCurrency.MXN)

    private fun observation(year: Int, month: Int, day: Int, usdToMxn: String) =
        FxObservation(LocalDate.of(year, month, day), BigDecimal(usdToMxn))
}
