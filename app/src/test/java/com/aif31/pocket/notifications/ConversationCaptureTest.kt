package com.aif31.pocket.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.RoomPocketLedger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SMS apps keep one notification per conversation and append each new bank message to it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConversationCaptureTest {
    private lateinit var database: FinanceDatabase
    private lateinit var ledger: RoomPocketLedger
    private lateinit var capture: NotificationCapture
    private val instant = Instant.parse("2026-09-05T12:00:00Z")

    @Before fun setUp() = runTest {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        val clock = Clock.fixed(instant, ZoneOffset.UTC)
        ledger = RoomPocketLedger(database, clock)
        ledger.execute(LedgerCommand.Initialize(100_000))
        capture = NotificationCapture(NotificationSuggestionStore(database, clock))
    }

    @After fun tearDown() = database.close()

    private fun sms(minute: Long, text: String) =
        NotificationMessage(conversation = "SAB", postedAtUtcMillis = instant.toEpochMilli() + minute * 60_000, title = "SAB", text = text)

    private val coffee = sms(1, "PoS Purchase\nCard (1111) was used at Corner Cafe for SAR 12.00 on 2026-09-05 15:01:00.")
    private val groceries = sms(2, "PoS Purchase\nCard (1111) was used at Green Market for SAR 80.50 on 2026-09-05 15:02:00.")

    private suspend fun post(vararg messages: NotificationMessage) =
        capture.ingestMessages(setOf("sms.app"), "sms.app", messages.toList())

    private suspend fun inboxMerchants() = ledger.state.first().movementSuggestions.map { it.merchant }.toSet()

    @Test fun each_bank_message_in_one_conversation_becomes_its_own_suggestion() = runTest {
        post(coffee, groceries)

        assertEquals(setOf("Corner Cafe", "Green Market"), inboxMerchants())
    }

    @Test fun reposting_a_conversation_with_a_new_message_keeps_earlier_suggestions_without_duplicates() = runTest {
        post(coffee)
        post(coffee, groceries)

        assertEquals(2, ledger.state.first().movementSuggestions.size)
        assertEquals(setOf("Corner Cafe", "Green Market"), inboxMerchants())
    }

    @Test fun reposted_messages_are_counted_once_in_beta_metrics() = runTest {
        val metrics = CountingMetrics()
        val countedCapture = NotificationCapture(
            NotificationSuggestionStore(database, Clock.fixed(instant, ZoneOffset.UTC)),
            metrics,
        )

        countedCapture.ingestMessages(setOf("sms.app"), "sms.app", listOf(coffee))
        countedCapture.ingestMessages(setOf("sms.app"), "sms.app", listOf(coffee, groceries))

        assertEquals(2, metrics.snapshot().parserAttempts)
        assertEquals(2, metrics.snapshot().parserSuccesses)
    }

    private class CountingMetrics : NotificationBetaMetrics {
        private var attempts = 0
        private var successes = 0
        override fun recordParserOutcome(parsed: Boolean) {
            attempts++
            if (parsed) successes++
        }
        override fun recordConfirmation(amountCorrected: Boolean, currencyCorrected: Boolean) = Unit
        override fun snapshot() = NotificationBetaMetricsSnapshot(attempts, successes, 0, 0, 0, 0)
        override fun reset() = Unit
    }

    @Test fun a_one_time_password_beside_a_purchase_does_not_hide_the_purchase() = runTest {
        post(sms(0, "OTP Code: 1778\nReason: Mobile Login\nSharing OTP Exposes You to Fraud"), coffee)

        assertEquals(setOf("Corner Cafe"), inboxMerchants())
    }
}
