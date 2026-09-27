package com.aif31.pocket.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.aif31.pocket.PocketApplication
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.domain.SupportedCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PocketNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleIdentities = NotificationLifecycleIdentities()
    private val processing = Mutex()
    private val pocketApplication get() = application as PocketApplication
    private val capture by lazy {
        NotificationCapture(
            NotificationSuggestionStore(pocketApplication.database),
            pocketApplication.notificationBetaMetrics,
        )
    }
    private val autoRecorder by lazy {
        NotificationAutoRecorder(pocketApplication.ledger, pocketApplication.budgetZone, catchUpPeriods = {
            val startDay = pocketApplication.preferences.state.first().futurePeriodStartDay
            pocketApplication.ledger.execute(LedgerCommand.CatchUpPeriods(startDay))
        })
    }
    private val notifier by lazy { DetectedMovementNotifier(this) }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        val notificationIdentity = lifecycleIdentities.identityForPosted(
            sourcePackage = notification.packageName,
            notificationKey = notification.key,
            postedAtUtcMillis = notification.postTime,
        )
        scope.launch {
            try {
                val preferences = pocketApplication.preferences.state.first()
                val allowedPackages = preferences.notificationSourcePackages
                if (notification.packageName !in allowedPackages) return@launch
                if (notification.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return@launch
                val dollarCurrency = preferences.defaultExpenseCurrency.takeIf { it != SupportedCurrency.SAR }
                processing.withLock {
                    val captured = capture.ingestMessages(
                        allowedPackages = allowedPackages,
                        sourcePackage = notification.packageName,
                        messages = notificationMessages(notification, notificationIdentity),
                        dollarCurrency = dollarCurrency,
                    )
                    captured.filter { it.outcome != IngestOutcome.IGNORED }.forEach { payment ->
                        val outcome = if (preferences.notificationAutoRecord) {
                            autoRecorder.record(payment.suggestionId)
                        } else {
                            AutoRecordOutcome.NeedsReview(ReviewReason.UNAVAILABLE)
                        }
                        val isFresh = System.currentTimeMillis() - payment.effectiveAtUtcMillis <= ALERT_FRESHNESS_MILLIS
                        if (preferences.notificationDetectionAlerts && payment.outcome == IngestOutcome.CREATED && isFresh) {
                            notifier.show(
                                payment.suggestionId,
                                detectedMovementAlert(payment.payment, appLabel(this@PocketNotificationListener, payment.sourcePackage), outcome),
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Never include notification content in logs or crash metadata.
            }
        }
    }

    override fun onNotificationRemoved(notification: StatusBarNotification) {
        lifecycleIdentities.onRemoved(notification.packageName, notification.key)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        runCatching {
            lifecycleIdentities.onListenerConnected(
                activeNotifications.orEmpty().map {
                    ActiveNotificationIdentity(it.packageName, it.key, it.postTime)
                },
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        /** Messages re-posted from the shade (for example after enabling access) are captured silently. */
        const val ALERT_FRESHNESS_MILLIS = 15L * 60 * 1000
    }
}

private const val MAX_MESSAGE_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000

/**
 * SMS and chat apps keep one notification per conversation and append every new message to it, so
 * each message is parsed and identified on its own. Otherwise a new bank SMS would overwrite the previous
 * suggestion, and an OTP in the same conversation would hide a purchase.
 */
internal fun notificationMessages(notification: StatusBarNotification, lifecycleIdentity: String): List<NotificationMessage> {
    val extras = notification.notification.extras
    val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
        ?: extras.getCharSequence(Notification.EXTRA_TITLE)
    val style = runCatching {
        NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification.notification)
    }.getOrNull()
    val messages = style?.messages.orEmpty().filter { !it.text.isNullOrBlank() }
    if (messages.isNotEmpty()) {
        val oldest = notification.postTime - MAX_MESSAGE_AGE_MILLIS
        return messages
            .filter { it.timestamp <= 0 || it.timestamp >= oldest }
            .map { message ->
                val postedAt = message.timestamp.takeIf { it > 0 } ?: notification.postTime
                NotificationMessage(
                    conversation = title?.toString().orEmpty(),
                    postedAtUtcMillis = postedAt,
                    title = title,
                    text = message.text,
                )
            }
    }
    val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT)
    // Apps that reuse one notification slot for every transaction still get one suggestion per distinct text.
    return listOf(NotificationMessage(lifecycleIdentity, notification.postTime, title, text, sentAtUtcMillis = null))
}
