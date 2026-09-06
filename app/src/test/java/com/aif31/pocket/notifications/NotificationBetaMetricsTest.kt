package com.aif31.pocket.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationBetaMetricsTest {
    private lateinit var metrics: NotificationBetaMetrics

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        metrics = notificationBetaMetrics(context)
        metrics.reset()
    }

    @Test
    fun stores_only_aggregate_parser_and_correction_counts() {
        metrics.recordParserOutcome(parsed = true)
        metrics.recordParserOutcome(parsed = false)
        metrics.recordConfirmation(amountCorrected = false, currencyCorrected = false)
        metrics.recordConfirmation(amountCorrected = true, currencyCorrected = true)

        assertEquals(
            NotificationBetaMetricsSnapshot(
                parserAttempts = 2,
                parserSuccesses = 1,
                confirmations = 2,
                correctedConfirmations = 1,
                amountCorrections = 1,
                currencyCorrections = 1,
            ),
            metrics.snapshot(),
        )
        assertEquals(1, metrics.snapshot().parserFailures)
        assertEquals(0.5, metrics.snapshot().correctionRate, 0.0)
    }

    @Test
    fun reset_discards_the_beta_sample() {
        metrics.recordParserOutcome(parsed = true)
        metrics.recordConfirmation(amountCorrected = true, currencyCorrected = false)

        metrics.reset()

        assertEquals(NotificationBetaMetricsSnapshot(0, 0, 0, 0, 0, 0), metrics.snapshot())
        assertEquals(0.0, metrics.snapshot().correctionRate, 0.0)
    }
}
