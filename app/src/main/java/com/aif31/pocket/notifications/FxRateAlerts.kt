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
import com.aif31.pocket.settings.PreferencesStore
import com.aif31.pocket.settings.ReminderScheduler
import com.aif31.pocket.settings.notificationsPermitted
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
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
 * turned on. A daily or weekly alert posts at most once per Asia/Riyadh day; a new-FIX alert posts at most once per
 * FIX and pair, so days without a new FIX, such as weekends and Mexican holidays, send nothing. Below the minimum
 * rate nothing posts and nothing is recorded, so a new-FIX alert still arrives once the rate reaches it.
 */
internal class FxRateAlertCoordinator(
    private val preferences: PreferencesStore,
    private val history: BanxicoHistorySource,
    private val today: () -> LocalDate,
    private val post: (FxRateAlert) -> Unit,
) {
    suspend fun run(): FxRateAlertOutcome {
        val settings = preferences.state.first()
        val alert = settings.fxRateAlert
        if (!alert.enabled || !settings.onlineFxEnabled) return FxRateAlertOutcome.SKIPPED
        val day = today()
        if (alert.frequency == FxRateAlertFrequency.WEEKLY && day.dayOfWeek != alert.weekday) return FxRateAlertOutcome.SKIPPED
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
        if (alert.minimumRate != null && digest.rate < alert.minimumRate) return FxRateAlertOutcome.SKIPPED
        val slot = when (alert.frequency) {
            FxRateAlertFrequency.NEW_FIX -> "fix/${alert.base}-${alert.quote}/${digest.date}"
            FxRateAlertFrequency.DAILY, FxRateAlertFrequency.WEEKLY -> "day/$day"
        }
        if (!preferences.claimFxRateAlertSlot(slot)) return FxRateAlertOutcome.SKIPPED
        post(fxRateAlert(digest))
        return FxRateAlertOutcome.POSTED
    }

    private companion object {
        /** Long enough to hold the previous FIX and a full seven-day window across Mexican holiday runs. */
        const val LOOKBACK_DAYS = 20L
    }
}

class FxRateAlertWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? PocketApplication ?: return Result.success()
        if (!notificationsPermitted(applicationContext)) return Result.success()
        val outcome = FxRateAlertCoordinator(
            preferences = app.preferences,
            history = app.banxico,
            today = { LocalDate.now(app.clock.withZone(app.budgetZone)) },
            post = FxRateAlertNotifier(applicationContext)::show,
        ).run()
        return if (outcome == FxRateAlertOutcome.RETRY && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}

internal class FxRateAlertNotifier(private val context: Context) {
    fun show(alert: FxRateAlert) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createFxRateAlertChannel(context)
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
        runCatching { NotificationManagerCompat.from(context).notify(FX_RATE_NOTIFICATION_TAG, 0, notification) }
    }

    private companion object {
        const val OPEN_APP_REQUEST = 28
        const val FX_RATE_NOTIFICATION_TAG = "fx_rate_daily"
    }
}

class WorkFxRateAlertScheduler(context: Context) : ReminderScheduler by DailyNotificationScheduler(
    context = context,
    workName = FX_RATE_ALERT_WORK_NAME,
    channelId = FX_RATE_ALERT_CHANNEL_ID,
    createChannel = ::createFxRateAlertChannel,
    request = { initialDelay ->
        PeriodicWorkRequestBuilder<FxRateAlertWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
    },
)

internal const val FX_RATE_ALERT_WORK_NAME = "daily-fx-rate"
internal const val FX_RATE_ALERT_CHANNEL_ID = "fx_rate_daily"

internal fun createFxRateAlertChannel(context: Context) {
    val channel = NotificationChannel(FX_RATE_ALERT_CHANNEL_ID, "Tipo de cambio diario", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "El tipo de cambio elegido según el FIX de Banxico, su cambio frente al FIX anterior y el promedio de 7 días."
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}
