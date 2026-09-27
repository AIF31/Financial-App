package com.aif31.pocket.notifications

import android.app.Notification
import android.content.Context
import android.os.Process
import android.service.notification.StatusBarNotification
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

/** Bank apps post a plain notification and may update it in place with the same or a new transaction. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BankAppNotificationCaptureTest {
    private lateinit var context: Context
    private lateinit var database: FinanceDatabase
    private lateinit var ledger: RoomPocketLedger
    private lateinit var capture: NotificationCapture
    private val identities = NotificationLifecycleIdentities()
    private val instant = Instant.parse("2026-09-05T12:00:00Z")

    @Before fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        database = FinanceDatabase.inMemory(context)
        val clock = Clock.fixed(instant, ZoneOffset.UTC)
        ledger = RoomPocketLedger(database, clock)
        ledger.execute(LedgerCommand.Initialize(100_000))
        capture = NotificationCapture(NotificationSuggestionStore(database, clock))
    }

    @After fun tearDown() = database.close()

    private suspend fun post(secondsLater: Long, text: String) {
        val notification = Notification.Builder(context, "bank")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Bank")
            .setContentText(text)
            .build()
        val posted = StatusBarNotification(
            BANK, BANK, 7, null, 10_001, 0, 0, notification, Process.myUserHandle(),
            instant.toEpochMilli() + secondsLater * 1_000,
        )
        val identity = identities.identityForPosted(posted.packageName, posted.key, posted.postTime)
        capture.ingestMessages(setOf(BANK), BANK, notificationMessages(posted, identity))
    }

    @Test fun updating_a_notification_with_the_same_text_does_not_duplicate_the_payment() = runTest {
        post(0, "Purchase at Corner Cafe for SAR 12.00")
        post(5, "Purchase at Corner Cafe for SAR 12.00")

        assertEquals(1, ledger.state.first().movementSuggestions.size)
    }

    @Test fun reusing_a_notification_for_a_new_transaction_keeps_both_payments() = runTest {
        post(0, "Purchase at Corner Cafe for SAR 12.00")
        post(60, "Purchase at Green Market for SAR 80.50")

        assertEquals(setOf("Corner Cafe", "Green Market"), ledger.state.first().movementSuggestions.map { it.merchant }.toSet())
    }

    private companion object { const val BANK = "bank.app" }
}
