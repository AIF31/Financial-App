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
    private val lifecycleIdentities = NotificationLifecycleIdentities()
    private val capture by lazy {
        val application = application as PocketApplication
        NotificationCapture(
            NotificationSuggestionStore(application.database),
            application.notificationBetaMetrics,
        )
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        val notificationIdentity = lifecycleIdentities.identityForPosted(
            sourcePackage = notification.packageName,
            notificationKey = notification.key,
            postedAtUtcMillis = notification.postTime,
        )
        scope.launch {
            try {
                val application = application as PocketApplication
                val allowedPackages = application.preferences.state.first().notificationSourcePackages
                if (notification.packageName !in allowedPackages) return@launch
                val extras = notification.notification.extras
                capture.ingest(
                    allowedPackages = allowedPackages,
                    sourcePackage = notification.packageName,
                    notificationIdentity = notificationIdentity,
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
}
