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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.aif31.pocket.MainActivity
import com.aif31.pocket.PocketApplication
import com.aif31.pocket.R
import com.aif31.pocket.fx.BanxicoHistorySource
import com.aif31.pocket.fx.QuoteFailure
import com.aif31.pocket.fx.FxRateDigest
import com.aif31.pocket.settings.DailyNotificationScheduler
import com.aif31.pocket.settings.FxRateAlertFrequency
import com.aif31.pocket.settings.FxRateAlertSettings
import com.aif31.pocket.settings.PreferencesStore
import com.aif31.pocket.settings.ReminderScheduler
import com.aif31.pocket.settings.notificationsPermitted
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal data class FxRateAlert(val title: String, val text: String, val details: String)

internal fun fxRateAlert(digest: FxRateDigest): FxRateAlert {
    val quote = digest.quote.name
    val average = "Promedio 7 días: ${rateText(digest.weekAverage)} $quote"
    val change = digest.previousDate?.let { previous ->
        digest.changePercent?.let { "${signedPercent(it)} vs. ${dayText(previous)}" }
    }
    val position = when (digest.versusAveragePercent.signum()) {
        1 -> "${digest.versusAveragePercent.abs().toPlainString()}% por encima del promedio"
        -1 -> "${digest.versusAveragePercent.abs().toPlainString()}% por debajo del promedio"
        else -> "igual al promedio"
    }
    return FxRateAlert(
        title = "1 ${digest.base.name} = ${rateText(digest.rate)} $quote",
        text = listOfNotNull(change, average).joinToString(" · "),
        details = listOfNotNull(
            "FIX de Banxico del ${dayText(digest.date)}.",
            change?.let { "$it." },
            "$average ($position).",
        ).joinToString("\n"),
    )
}

/** Four decimals, or six below 1 so rates such as MXN → USD keep their movement visible. */
internal fun rateText(value: BigDecimal): String =
    value.setScale(if (value < BigDecimal.ONE) 6 else 4, RoundingMode.HALF_UP).toPlainString()

private fun signedPercent(value: BigDecimal): String =
    (if (value.signum() > 0) "+" else "") + value.toPlainString() + "%"

private fun dayText(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es")))

internal enum class FxRateAlertOutcome { POSTED, SKIPPED, RETRY }

/**
 * Sends the rate alert the person configured. It runs once a day and needs both the alert and online conversion
 * turned on, before and after fetching. A daily or weekly alert posts at most once per scheduled occurrence: the
 * chosen Asia/Riyadh time on each day, or on the chosen weekday, which a delayed or retried run can still serve for
 * up to a day. A new-FIX alert posts at most once per FIX and pair, so days without a new FIX, such as weekends and
 * Mexican holidays, send nothing. Below the minimum
 * rate nothing posts and nothing is recorded, so a new-FIX alert still arrives once the rate reaches it. An alert
 * that could not be shown is not recorded either, so it is sent on a later run.
 */
internal class FxRateAlertCoordinator(
    private val preferences: PreferencesStore,
    private val history: BanxicoHistorySource,
    /** The current Asia/Riyadh time. */
    private val now: () -> ZonedDateTime,
    /** Shows the alert and returns whether it could be shown. */
    private val post: (FxRateAlert) -> Boolean,
) {
    suspend fun run(): FxRateAlertOutcome {
        val settings = preferences.state.first()
        val alert = settings.fxRateAlert
        if (!alert.enabled || !settings.onlineFxEnabled) return FxRateAlertOutcome.SKIPPED
        val now = now()
        val occurrence = latestOccurrence(alert, now)
        if (alert.frequency != FxRateAlertFrequency.NEW_FIX && Duration.between(occurrence, now) >= Duration.ofDays(1)) {
            return FxRateAlertOutcome.SKIPPED
        }
        val day = now.toLocalDate()
        val observations = try {
            history.fetchUsdToMxnHistory(day.minusDays(LOOKBACK_DAYS), day)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: QuoteFailure.ConfigurationUnavailable) {
            return FxRateAlertOutcome.SKIPPED
        } catch (_: Exception) {
            return FxRateAlertOutcome.RETRY
        }
        val digest = FxRateDigest.of(observations, alert.base, alert.quote) ?: return FxRateAlertOutcome.RETRY
        // The person may have changed the alert or withdrawn online conversion while the history was fetched.
        val current = preferences.state.first()
        if (current.fxRateAlert != alert || !current.onlineFxEnabled) return FxRateAlertOutcome.SKIPPED
        if (alert.minimumRate != null && digest.rate < alert.minimumRate) return FxRateAlertOutcome.SKIPPED
        // A new-FIX alert is tracked per pair; daily and weekly alerts share one record of the latest occurrence.
        val (family, value) = when (alert.frequency) {
            FxRateAlertFrequency.NEW_FIX -> "fix/${alert.base}-${alert.quote}" to digest.date.toString()
            FxRateAlertFrequency.DAILY, FxRateAlertFrequency.WEEKLY -> "scheduled" to occurrence.toLocalDateTime().toString()
        }
        if (!preferences.claimFxRateAlert(family, value)) return FxRateAlertOutcome.SKIPPED
        if (!post(fxRateAlert(digest))) {
            preferences.releaseFxRateAlert(family, value)
            return FxRateAlertOutcome.SKIPPED
        }
        return FxRateAlertOutcome.POSTED
    }

    /**
     * The latest scheduled time at or before [now], counting a run a few minutes early as on time. Weekly alerts
     * are scheduled only on their weekday.
     */
    private fun latestOccurrence(alert: FxRateAlertSettings, now: ZonedDateTime): ZonedDateTime {
        val reference = now.plus(EARLY_TOLERANCE)
        var occurrence = reference.with(alert.time).truncatedTo(ChronoUnit.MINUTES)
        if (occurrence.isAfter(reference)) occurrence = occurrence.minusDays(1)
        if (alert.frequency == FxRateAlertFrequency.WEEKLY) {
            occurrence = occurrence.with(TemporalAdjusters.previousOrSame(alert.weekday))
        }
        return occurrence
    }

    private companion object {
        /** Long enough to hold the previous FIX and a full seven-day window across Mexican holiday runs. */
        const val LOOKBACK_DAYS = 20L
        val EARLY_TOLERANCE: Duration = Duration.ofMinutes(5)
    }
}

class FxRateAlertWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? PocketApplication ?: return Result.success()
        val notifier = FxRateAlertNotifier(applicationContext)
        val outcome = if (notifier.canShow()) {
            FxRateAlertCoordinator(
                preferences = app.preferences,
                history = app.banxico,
                now = { ZonedDateTime.now(app.clock.withZone(app.budgetZone)) },
                post = notifier::show,
            ).run()
        } else {
            FxRateAlertOutcome.SKIPPED
        }
        if (outcome == FxRateAlertOutcome.RETRY && runAttemptCount < MAX_RETRIES) return Result.retry()
        val alert = app.preferences.state.first().fxRateAlert
        if (alert.enabled) runCatching { fxRateAlertScheduler(applicationContext).realign(id, alert.time) }
        return Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}

internal class FxRateAlertNotifier(private val context: Context) {
    /** Whether notifications are permitted and the rate alert channel is not blocked. */
    fun canShow(): Boolean {
        if (!notificationsPermitted(context)) return false
        createFxRateAlertChannel(context)
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(FX_RATE_ALERT_CHANNEL_ID)
        return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(alert: FxRateAlert): Boolean {
        if (!canShow()) return false
        // Repeated where lint can see it guarding notify().
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(context, OPEN_APP_REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // The rate is public data and holds none of the person's amounts, so it may show on the lock screen.
        val notification = NotificationCompat.Builder(context, FX_RATE_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pocket)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.details))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        // Each day's alert replaces the previous one; the tag keeps it apart from hash-numbered alerts.
        return runCatching { NotificationManagerCompat.from(context).notify(FX_RATE_NOTIFICATION_TAG, 0, notification) }.isSuccess
    }

    private companion object {
        const val OPEN_APP_REQUEST = 28
        const val FX_RATE_NOTIFICATION_TAG = "fx_rate_daily"
    }
}

class WorkFxRateAlertScheduler(context: Context) : ReminderScheduler by fxRateAlertScheduler(context)

internal fun fxRateAlertScheduler(context: Context, clock: Clock = Clock.systemUTC()) = DailyNotificationScheduler(
    context = context,
    workName = FX_RATE_ALERT_WORK_NAME,
    channelId = FX_RATE_ALERT_CHANNEL_ID,
    createChannel = ::createFxRateAlertChannel,
    request = {
        PeriodicWorkRequestBuilder<FxRateAlertWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    },
    clock = clock,
)

internal const val FX_RATE_ALERT_WORK_NAME = "daily-fx-rate"
internal const val FX_RATE_ALERT_CHANNEL_ID = "fx_rate_daily"

internal fun createFxRateAlertChannel(context: Context) {
    val channel = NotificationChannel(FX_RATE_ALERT_CHANNEL_ID, "Tipo de cambio diario", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "El tipo de cambio elegido según el FIX de Banxico, su cambio frente al FIX anterior y el promedio de 7 días."
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}
