package com.aif31.pocket.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aif31.pocket.MainActivity
import com.aif31.pocket.R
import com.aif31.pocket.ui.MoneyText

internal data class DetectedMovementAlert(val title: String, val text: String)

/** Short, glanceable copy: what was read, where it went, and whether anything is left to do. */
internal fun detectedMovementAlert(
    payment: ParsedPayment,
    sourceLabel: String,
    outcome: AutoRecordOutcome,
): DetectedMovementAlert {
    val amount = MoneyText.format(payment.amountMinor, payment.currency)
    val merchant = payment.merchant ?: "Comercio sin identificar"
    return when (outcome) {
        is AutoRecordOutcome.Recorded -> DetectedMovementAlert(
            title = "Gasto registrado · $amount",
            text = "$merchant → ${outcome.pocketName} · $sourceLabel",
        )
        is AutoRecordOutcome.NeedsReview -> DetectedMovementAlert(
            title = "Gasto detectado · $amount",
            text = "$merchant · ${reviewHint(outcome.reason)}",
        )
    }
}

internal fun reviewHint(reason: ReviewReason): String = when (reason) {
    ReviewReason.NO_MERCHANT, ReviewReason.NEW_MERCHANT -> "Elige un Pocket para registrarlo"
    ReviewReason.FOREIGN_CURRENCY -> "Moneda extranjera: confirma la conversión"
    ReviewReason.NO_PERIOD -> "Fuera de los periodos: revísalo"
    ReviewReason.REJECTED, ReviewReason.UNAVAILABLE -> "Revísalo en Movimientos"
}

internal class DetectedMovementNotifier(private val context: Context) {
    fun show(suggestionId: String, alert: DetectedMovementAlert) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createDetectedMovementChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_MOVEMENTS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            OPEN_MOVEMENTS_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // The lock screen shows only the redacted public version; amounts stay private.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Pocket leyó un movimiento")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setGroup(GROUP_KEY)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(suggestionId.hashCode(), notification) }
    }

    private companion object {
        const val OPEN_MOVEMENTS_REQUEST = 26
        const val GROUP_KEY = "com.aif31.pocket.DETECTED_MOVEMENTS"
    }
}

internal const val DETECTED_MOVEMENT_CHANNEL_ID = "detected_movements"
private const val CHANNEL_ID = DETECTED_MOVEMENT_CHANNEL_ID

internal fun createDetectedMovementChannel(context: Context) {
    val channel = NotificationChannel(CHANNEL_ID, "Movimientos detectados", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Avisa cuando Pocket lee un gasto en una notificación de tus apps seleccionadas."
        lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

internal fun appLabel(context: Context, packageName: String): String = runCatching {
    val packageManager = context.packageManager
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
}.getOrDefault(packageName)
