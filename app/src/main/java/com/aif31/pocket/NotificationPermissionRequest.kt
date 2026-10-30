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

/** Shared recovery for detection alerts and rate alerts; permission state is always read on a tap. */
internal class NotificationPermissionRequest(
    private val activity: Activity,
    private val launchRequest: () -> Unit,
) {
    private val history = activity.getSharedPreferences("notification_permission", Context.MODE_PRIVATE)
    var showRationale by mutableStateOf(false)
        private set

    fun request(rationaleAccepted: Boolean = false) {
        val runtimeDenied = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val rationale = runtimeDenied && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        when {
            !runtimeDenied -> {
                showRationale = false
                // A card can still be blocked by app or channel settings after the runtime grant.
                openSettings()
            }
            rationale && !showRationale && !rationaleAccepted -> showRationale = true
            !rationale && history.getBoolean("denied", false) -> {
                showRationale = false
                openSettings()
            }
            else -> {
                showRationale = false
                launchRequest()
            }
        }
    }

    fun onResult(granted: Boolean) {
        // Android can also return false after dismissal. If it offers no rationale on the next
        // tap, prefer a working settings route; never open settings just because of a denial.
        history.edit().putBoolean("denied", !granted).apply()
    }

    private fun openSettings() {
        activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
    }
}

@Composable
internal fun rememberNotificationPermissionRequest(onRequest: () -> Unit): NotificationPermissionRequest? {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
    }
    return remember(activity, onRequest) { activity?.let { NotificationPermissionRequest(it, onRequest) } }
}

internal const val NOTIFICATION_PERMISSION_RATIONALE =
    "Permite las notificaciones para recibir los avisos que actives en Pocket. Puedes cambiarlas en los ajustes de Android."
