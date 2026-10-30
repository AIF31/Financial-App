package com.aif31.pocket

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Notification permission for the cards that need it: detection alerts and rate alerts. Permission state is read on
 * every tap. Android reports a dismissed prompt, which can be asked again, exactly like a permanent denial, which
 * cannot: refused, with no rationale. So asking stays the card's action, and after such a refusal the card also
 * offers Android's notification settings for Pocket.
 */
internal class NotificationPermissionRequest(
    private val activity: Activity,
    private val launchRequest: () -> Unit,
) {
    private val history = activity.getSharedPreferences("notification_permission", Context.MODE_PRIVATE)
    var showRationale by mutableStateOf(false)
        private set

    /** The last request was refused with no rationale, so Android may no longer show its prompt. */
    var offerSettings by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    /** Re-reads the last result, which arrives through the activity before it resumes. */
    fun refresh() {
        offerSettings = runtimeDenied() && history.getBoolean(KEY_UNEXPLAINED_REFUSAL, false)
    }

    fun request(rationaleAccepted: Boolean = false) {
        when {
            !runtimeDenied() -> {
                showRationale = false
                // A card can still be blocked by app or channel settings after the runtime grant.
                openSettings()
            }
            rationale() && !showRationale && !rationaleAccepted -> showRationale = true
            else -> {
                showRationale = false
                launchRequest()
            }
        }
    }

    fun onResult(granted: Boolean) {
        history.edit().putBoolean(KEY_UNEXPLAINED_REFUSAL, !granted && !rationale()).apply()
        refresh()
    }

    fun openSettings() {
        activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
    }

    private fun runtimeDenied() = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private fun rationale() = runtimeDenied() &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)

    private companion object {
        const val KEY_UNEXPLAINED_REFUSAL = "unexplained_refusal"
    }
}

@Composable
internal fun rememberNotificationPermissionRequest(onRequest: () -> Unit): NotificationPermissionRequest? {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
    }
    val request = remember(activity, onRequest) { activity?.let { NotificationPermissionRequest(it, onRequest) } }
    LifecycleResumeEffect(request) {
        request?.refresh()
        onPauseOrDispose {}
    }
    return request
}

internal const val NOTIFICATION_PERMISSION_RATIONALE =
    "Permite las notificaciones para recibir los avisos que actives en Pocket. Puedes cambiarlas en los ajustes de Android."

internal const val OPEN_NOTIFICATION_SETTINGS = "Abrir ajustes de Android"
