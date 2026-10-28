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
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.PeriodInsights
import com.aif31.pocket.data.PocketBudgetStatus
import com.aif31.pocket.data.budgetStatusAt
import com.aif31.pocket.settings.PreferencesStore
import com.aif31.pocket.ui.MoneyText
import com.aif31.pocket.ui.runOutText
import kotlin.math.abs
import kotlinx.coroutines.flow.first

/** How far a Pocket has gone this period, from the first warning to running out; later levels are worse. */
internal enum class PaceAlertLevel { AT_RISK, EXHAUSTED }

internal data class PaceAlert(val periodId: String, val pocketId: String, val level: PaceAlertLevel, val title: String, val text: String)

/**
 * Decides which Pocket pace alerts are new. Each period, Pocket, and level alerts at most once, and reaching a
 * level also counts every lower one as alerted, so a Pocket only alerts again when it gets worse.
 */
internal object PaceAlerts {
    fun levels(insights: PeriodInsights): Map<String, PaceAlertLevel> =
        insights.pockets
            .filterNot { it.pocket.archived || it.retiredThisPeriod }
            .mapNotNull { summary ->
                when (summary.budgetStatusAt(insights)) {
                    PocketBudgetStatus.EXHAUSTED -> PaceAlertLevel.EXHAUSTED
                    PocketBudgetStatus.AT_RISK -> PaceAlertLevel.AT_RISK
                    PocketBudgetStatus.UNCONFIRMED_PACE, PocketBudgetStatus.UNBUDGETED, PocketBudgetStatus.ON_TRACK,
                    PocketBudgetStatus.SMALL_BUDGET -> null
                }?.let { summary.pocket.id to it }
            }
            .toMap()

    fun due(insights: PeriodInsights, sent: Set<String>): List<PaceAlert> =
        levels(insights)
            .filter { (pocketId, level) -> key(insights.period.id, pocketId, level) !in sent }
            .map { (pocketId, level) -> alert(insights, pocketId, level) }

    /** [sent] plus every level reached now, keeping only this period's entries. */
    fun seen(insights: PeriodInsights, sent: Set<String>): Set<String> {
        val periodId = insights.period.id
        val reached = levels(insights).flatMap { (pocketId, level) ->
            PaceAlertLevel.entries.filter { it <= level }.map { key(periodId, pocketId, it) }
        }
        return sent.filterTo(HashSet()) { it.startsWith("$periodId/") } + reached
    }

    private fun key(periodId: String, pocketId: String, level: PaceAlertLevel) = "$periodId/$pocketId/$level"

    private fun alert(insights: PeriodInsights, pocketId: String, level: PaceAlertLevel): PaceAlert {
        val summary = insights.pockets.first { it.pocket.id == pocketId }
        fun money(minor: Long) = MoneyText.format(minor, insights.accountingCurrency)
        val name = summary.pocket.name
        // Alerts are only for the period in progress, whose elapsed days end today.
        val today = insights.period.start.plusDays(insights.elapsedDays - 1L)
        val (title, text) = when (level) {
            PaceAlertLevel.AT_RISK -> "$name lleva el ${summary.consumedPercent}%" to
                "${runOutText(insights.runOutDateByPocket.getValue(pocketId), today)}. " +
                "Quedan ${money(summary.availabilityMinor)} de ${money(Math.addExact(summary.budgetMinor, summary.rolloverMinor))} en este periodo."
            PaceAlertLevel.EXHAUSTED -> "$name sin disponible" to
                if (summary.availabilityMinor < 0) "Vas ${money(abs(summary.availabilityMinor))} por encima de lo asignado."
                else "Ya usaste todo lo asignado a este Pocket."
        }
        return PaceAlert(insights.period.id, pocketId, level, title, text)
    }
}

/**
 * Posts pace alerts for Pocket changes the person did not see happen, such as payments auto-recorded from
 * notifications. Changes seen in the app are only marked, since the dashboard already shows them. The first check
 * records a silent baseline, so existing warnings never arrive all at once.
 */
internal class PaceAlertCoordinator(
    private val preferences: PreferencesStore,
    private val post: (PaceAlert) -> Unit,
) {
    suspend fun notifyNew(state: LedgerState) = check(state, notify = true)

    suspend fun markSeen(state: LedgerState) = check(state, notify = false)

    private suspend fun check(state: LedgerState, notify: Boolean) {
        val period = state.currentPeriod ?: return
        val insights = PeriodInsights.of(state, period.id) ?: return
        val enabled = notify && preferences.state.first().paceAlertsEnabled
        var due = emptyList<PaceAlert>()
        preferences.updatePaceAlertsSent { sent ->
            due = if (enabled && sent != null) PaceAlerts.due(insights, sent) else emptyList()
            PaceAlerts.seen(insights, sent.orEmpty())
        }
        due.forEach(post)
    }
}

internal class PaceAlertNotifier(private val context: Context) {
    fun show(alert: PaceAlert) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createPaceAlertChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(context, OPEN_DASHBOARD_REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // The lock screen shows only the redacted public version; amounts stay private.
        val publicVersion = NotificationCompat.Builder(context, PACE_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Aviso de un Pocket")
            .build()
        val notification = NotificationCompat.Builder(context, PACE_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
        // One notification per Pocket and period: a worse level replaces the earlier one.
        runCatching { NotificationManagerCompat.from(context).notify("${alert.periodId}/${alert.pocketId}".hashCode(), notification) }
    }

    private companion object {
        const val OPEN_DASHBOARD_REQUEST = 27
    }
}

internal const val PACE_ALERT_CHANNEL_ID = "pocket_pace"

internal fun createPaceAlertChannel(context: Context) {
    val channel = NotificationChannel(PACE_ALERT_CHANNEL_ID, "Ritmo de Pockets", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "Avisa cuando un gasto detectado deja un Pocket en riesgo, sin disponible o camino de agotarse."
        lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}
