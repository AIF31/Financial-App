package com.aif31.pocket.settings

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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.Operation
import androidx.work.WorkerParameters
import com.aif31.pocket.MainActivity
import com.aif31.pocket.R
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ReminderScheduler {
    fun apply(enabled: Boolean, time: LocalTime)
    suspend fun status(enabled: Boolean): ReminderStatus = if (enabled) ReminderStatus.Failed else ReminderStatus.Off
    suspend fun applyAndCheck(enabled: Boolean, time: LocalTime): ReminderStatus {
        apply(enabled, time)
        return status(enabled)
    }
}

class WorkReminderScheduler(private val context: Context) : ReminderScheduler {
    override fun apply(enabled: Boolean, time: LocalTime) { enqueue(enabled, time) }

    private fun enqueue(enabled: Boolean, time: LocalTime): Operation {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            return manager.cancelUniqueWork(WORK_NAME)
        }
        createReminderChannel(context)
        val now = ZonedDateTime.now(ZoneId.of("Asia/Riyadh"))
        var next = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next))
            .build()
        return manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    override suspend fun applyAndCheck(enabled: Boolean, time: LocalTime): ReminderStatus = withContext(Dispatchers.IO) {
        enqueue(enabled, time).result.get()
        status(enabled)
    }

    override suspend fun status(enabled: Boolean): ReminderStatus = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext ReminderStatus.Off
        val permissionGranted = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (android.os.Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
        val scheduled = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WORK_NAME).get()
            .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        resolveReminderStatus(enabled, permissionGranted, channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE, scheduled)
    }

    private companion object { const val WORK_NAME = "daily-spending-review" }
}

private const val CHANNEL_ID = "daily-review"

private fun createReminderChannel(context: Context) {
    val channel = NotificationChannel(CHANNEL_ID, "Recordatorio diario", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "Recordatorio sin importes para revisar gastos"
        lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        createReminderChannel(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Revisión diaria")
            .setContentText("Revisa si falta registrar algún gasto de hoy.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(DAILY_NOTIFICATION_ID, notification)
        return Result.success()
    }

    private companion object {
        const val DAILY_NOTIFICATION_ID = 25
    }
}
