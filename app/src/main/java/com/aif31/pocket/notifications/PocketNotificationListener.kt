package com.aif31.pocket.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.aif31.pocket.PocketApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PocketNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(notification: StatusBarNotification) {
        scope.launch {
            try {
                val application = application as PocketApplication
                val allowedPackages = application.preferences.state.first().notificationSourcePackages
                if (notification.packageName !in allowedPackages) return@launch
                val extras = notification.notification.extras
                NotificationCapture(
                    NotificationSuggestionStore(application.database),
                    application.notificationBetaMetrics,
                ).ingest(
                    allowedPackages = allowedPackages,
                    sourcePackage = notification.packageName,
                    notificationIdentity = notification.key,
                    postedAtUtcMillis = notification.postTime,
                    title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE),
                    text = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)
                        ?: extras.getCharSequence(android.app.Notification.EXTRA_TEXT),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Never include notification content in logs or crash metadata.
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
