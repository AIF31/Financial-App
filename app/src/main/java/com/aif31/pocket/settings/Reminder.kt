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
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.Operation
import androidx.work.WorkerParameters
import com.aif31.pocket.MainActivity
import com.aif31.pocket.R
import com.google.common.util.concurrent.ListenableFuture
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
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

class WorkReminderScheduler(context: Context) : ReminderScheduler by DailyNotificationScheduler(
    context = context,
    workName = "daily-spending-review",
    channelId = CHANNEL_ID,
    createChannel = ::createReminderChannel,
    request = { PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS) },
)

/**
 * Runs one unique periodic worker every 24 hours from the next [LocalTime] in Asia/Riyadh, and reports whether it can
 * deliver: notifications permitted, its channel not blocked, and the work enqueued.
 */
internal class DailyNotificationScheduler(
    private val context: Context,
    private val workName: String,
    private val channelId: String,
    private val createChannel: (Context) -> Unit,
    private val request: () -> PeriodicWorkRequest.Builder,
    private val clock: Clock = Clock.systemUTC(),
) : ReminderScheduler {
    override fun apply(enabled: Boolean, time: LocalTime) { enqueue(enabled, time) }

    private fun enqueue(enabled: Boolean, time: LocalTime): Operation {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            return manager.cancelUniqueWork(workName)
        }
        createChannel(context)
        // A schedule override needs the UPDATE policy, which would keep a stale run, so the first run uses a delay.
        val first = request().setInitialDelay(Duration.ofMillis(nextRunMillis(time) - clock.millis())).build()
        return manager.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, first)
    }

    /**
     * Moves the next run of the work [id] back to the next [time]. A periodic run delayed by its constraints restarts
     * the 24-hour period from when it finished, so without this every later run would keep the delay.
     */
    fun realign(id: UUID, time: LocalTime): ListenableFuture<WorkManager.UpdateResult> =
        WorkManager.getInstance(context).updateWork(request().setId(id).setNextScheduleTimeOverride(nextRunMillis(time)).build())

    override suspend fun applyAndCheck(enabled: Boolean, time: LocalTime): ReminderStatus = withContext(Dispatchers.IO) {
        enqueue(enabled, time).result.get()
        status(enabled)
    }

    override suspend fun status(enabled: Boolean): ReminderStatus = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext ReminderStatus.Off
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(channelId)
        val scheduled = WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName).get()
            .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        resolveReminderStatus(enabled, notificationsPermitted(context), channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE, scheduled)
    }

    private fun nextRunMillis(time: LocalTime): Long {
        val now = ZonedDateTime.now(clock.withZone(ZoneId.of("Asia/Riyadh")))
        var next = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli()
    }
}

/** Whether the app may post notifications at all: enabled for the app and, on Android 13+, permission granted. */
internal fun notificationsPermitted(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

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
            .setSmallIcon(R.drawable.ic_stat_pocket)
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
