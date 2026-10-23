package com.aif31.pocket.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.data.PortableSettings
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppPreferences(
    val futurePeriodStartDay: Int = 25,
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime = LocalTime.of(21, 0),
    val reminderAwaitingConfirmation: Boolean = false,
    val plaintextBackupAcknowledged: Boolean = false,
    val onlineFxEnabled: Boolean = false,
    val defaultExpenseCurrency: SupportedCurrency = SupportedCurrency.SAR,
    val notificationSourcePackages: Set<String> = emptySet(),
    val notificationAutoRecord: Boolean = true,
    val notificationDetectionAlerts: Boolean = true,
    val paceAlertsEnabled: Boolean = true,
    /** Pace-alert levels already alerted or seen; null until the first check records a silent baseline. */
    val paceAlertsSent: Set<String>? = null,
    /** The period whose recap the person closed on the dashboard. */
    val recapDismissedPeriodId: String? = null,
    val fxRateAlert: FxRateAlertSettings = FxRateAlertSettings(),
    /** The latest rate alert sent per family, as `family=value`, so one day or one pair's FIX never alerts twice. */
    val fxRateAlertSent: Set<String> = emptySet(),
)

interface PreferencesStore {
    val state: Flow<AppPreferences>
    suspend fun setFuturePeriodStartDay(day: Int)
    suspend fun setReminder(enabled: Boolean, time: LocalTime)
    suspend fun applyRestoredPortableSettings(settings: PortableSettings) {
        setFuturePeriodStartDay(settings.futurePeriodStartDay)
        setReminder(false, settings.reminderTime)
    }
    suspend fun acknowledgePlaintextBackup() {}
    suspend fun setOnlineFxEnabled(enabled: Boolean)
    suspend fun setDefaultExpenseCurrency(currency: SupportedCurrency)
    suspend fun setNotificationSourcePackages(packages: Set<String>) {}
    suspend fun setNotificationSourcePackage(packageName: String, selected: Boolean) {
        val packages = state.first().notificationSourcePackages.toMutableSet()
        if (selected) packages += packageName else packages -= packageName
        setNotificationSourcePackages(packages)
    }
    suspend fun setNotificationAutoRecord(enabled: Boolean) {}
    suspend fun setNotificationDetectionAlerts(enabled: Boolean) {}
    suspend fun setPaceAlertsEnabled(enabled: Boolean) {}

    /** Replaces the alerted pace levels with [transform] of the current ones, atomically. */
    suspend fun updatePaceAlertsSent(transform: (Set<String>?) -> Set<String>) {}
    suspend fun dismissRecap(periodId: String) {}
    suspend fun setFxRateAlert(settings: FxRateAlertSettings) {}

    /**
     * Records [value] as [family]'s latest rate alert and returns true, unless it already is, atomically. Families
     * are independent, so a claim for one never undoes another's.
     */
    suspend fun claimFxRateAlert(family: String, value: String): Boolean = false

    /** Forgets [family]'s claim of [value], when its alert could not be shown. */
    suspend fun releaseFxRateAlert(family: String, value: String) {}
}
private val Context.pocketPreferences by preferencesDataStore("pocket_preferences")

class DataStorePreferences internal constructor(
    private val dataStore: DataStore<Preferences>,
) : PreferencesStore {
    constructor(context: Context) : this(context.pocketPreferences)

    override val state: Flow<AppPreferences> = dataStore.data.map { values ->
        AppPreferences(
            futurePeriodStartDay = values[START_DAY] ?: 25,
            reminderEnabled = values[REMINDER_ENABLED] ?: false,
            reminderTime = LocalTime.of(values[REMINDER_HOUR] ?: 21, values[REMINDER_MINUTE] ?: 0),
            reminderAwaitingConfirmation = values[REMINDER_AWAITING_CONFIRMATION] ?: false,
            plaintextBackupAcknowledged = values[PLAINTEXT_BACKUP_ACKNOWLEDGED] ?: false,
            onlineFxEnabled = values[ONLINE_FX_ENABLED] ?: false,
            defaultExpenseCurrency = values[DEFAULT_EXPENSE_CURRENCY]
                ?.let { runCatching { SupportedCurrency.fromCode(it) }.getOrNull() }
                ?: SupportedCurrency.SAR,
            notificationSourcePackages = values[NOTIFICATION_SOURCE_PACKAGES] ?: emptySet(),
            notificationAutoRecord = values[NOTIFICATION_AUTO_RECORD] ?: true,
            notificationDetectionAlerts = values[NOTIFICATION_DETECTION_ALERTS] ?: true,
            paceAlertsEnabled = values[PACE_ALERTS_ENABLED] ?: true,
            paceAlertsSent = values[PACE_ALERTS_SENT],
            recapDismissedPeriodId = values[RECAP_DISMISSED_PERIOD],
            fxRateAlert = fxRateAlertSettings(values),
            fxRateAlertSent = values[FX_RATE_ALERT_SENT] ?: emptySet(),
        )
    }

    override suspend fun setFuturePeriodStartDay(day: Int) {
        require(day in 1..31)
        dataStore.edit { it[START_DAY] = day }
    }

    override suspend fun setReminder(enabled: Boolean, time: LocalTime) {
        dataStore.edit {
            it[REMINDER_ENABLED] = enabled
            it[REMINDER_HOUR] = time.hour
            it[REMINDER_MINUTE] = time.minute
            it[REMINDER_AWAITING_CONFIRMATION] = false
        }
    }

    override suspend fun applyRestoredPortableSettings(settings: PortableSettings) {
        require(settings.futurePeriodStartDay in 1..31)
        dataStore.edit {
            it[START_DAY] = settings.futurePeriodStartDay
            it[REMINDER_ENABLED] = false
            it[REMINDER_HOUR] = settings.reminderTime.hour
            it[REMINDER_MINUTE] = settings.reminderTime.minute
            it[REMINDER_AWAITING_CONFIRMATION] = true
        }
    }

    override suspend fun acknowledgePlaintextBackup() {
        dataStore.edit { it[PLAINTEXT_BACKUP_ACKNOWLEDGED] = true }
    }

    override suspend fun setOnlineFxEnabled(enabled: Boolean) {
        dataStore.edit { it[ONLINE_FX_ENABLED] = enabled }
    }

    override suspend fun setDefaultExpenseCurrency(currency: SupportedCurrency) {
        dataStore.edit { it[DEFAULT_EXPENSE_CURRENCY] = currency.name }
    }

    override suspend fun setNotificationSourcePackages(packages: Set<String>) {
        dataStore.edit { it[NOTIFICATION_SOURCE_PACKAGES] = packages }
    }

    override suspend fun setNotificationSourcePackage(packageName: String, selected: Boolean) {
        dataStore.edit { values ->
            val packages = (values[NOTIFICATION_SOURCE_PACKAGES] ?: emptySet()).toMutableSet()
            if (selected) packages += packageName else packages -= packageName
            values[NOTIFICATION_SOURCE_PACKAGES] = packages
        }
    }

    override suspend fun setNotificationAutoRecord(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATION_AUTO_RECORD] = enabled }
    }

    override suspend fun setNotificationDetectionAlerts(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATION_DETECTION_ALERTS] = enabled }
    }

    override suspend fun setPaceAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[PACE_ALERTS_ENABLED] = enabled }
    }

    override suspend fun updatePaceAlertsSent(transform: (Set<String>?) -> Set<String>) {
        dataStore.edit { it[PACE_ALERTS_SENT] = transform(it[PACE_ALERTS_SENT]) }
    }

    override suspend fun dismissRecap(periodId: String) {
        dataStore.edit { it[RECAP_DISMISSED_PERIOD] = periodId }
    }

    override suspend fun setFxRateAlert(settings: FxRateAlertSettings) {
        dataStore.edit {
            it[FX_RATE_ALERT_ENABLED] = settings.enabled
            it[FX_RATE_ALERT_HOUR] = settings.time.hour
            it[FX_RATE_ALERT_MINUTE] = settings.time.minute
            it[FX_RATE_ALERT_BASE] = settings.base.name
            it[FX_RATE_ALERT_QUOTE] = settings.quote.name
            it[FX_RATE_ALERT_FREQUENCY] = settings.frequency.name
            it[FX_RATE_ALERT_WEEKDAY] = settings.weekday.value
            settings.minimumRate?.let { rate -> it[FX_RATE_ALERT_MINIMUM] = rate.toPlainString() } ?: it.remove(FX_RATE_ALERT_MINIMUM)
        }
    }

    override suspend fun claimFxRateAlert(family: String, value: String): Boolean {
        val entry = "$family=$value"
        var claimed = false
        dataStore.edit { values ->
            val sent = values[FX_RATE_ALERT_SENT] ?: emptySet()
            claimed = entry !in sent
            if (claimed) values[FX_RATE_ALERT_SENT] = sent.filterNotTo(HashSet()) { it.startsWith("$family=") } + entry
        }
        return claimed
    }

    override suspend fun releaseFxRateAlert(family: String, value: String) {
        dataStore.edit { values ->
            values[FX_RATE_ALERT_SENT] = (values[FX_RATE_ALERT_SENT] ?: emptySet()) - "$family=$value"
        }
    }

    /** Stored alert settings; any unreadable value falls back to its default. */
    private fun fxRateAlertSettings(values: Preferences): FxRateAlertSettings {
        val defaults = FxRateAlertSettings()
        fun currency(key: Preferences.Key<String>) = values[key]?.let { runCatching { SupportedCurrency.fromCode(it) }.getOrNull() }
        val base = currency(FX_RATE_ALERT_BASE) ?: defaults.base
        val quote = currency(FX_RATE_ALERT_QUOTE) ?: defaults.quote
        val pairSupported = base to quote in FxRateAlertSettings.PAIRS
        return FxRateAlertSettings(
            enabled = values[FX_RATE_ALERT_ENABLED] ?: defaults.enabled,
            time = runCatching { LocalTime.of(values[FX_RATE_ALERT_HOUR] ?: 9, values[FX_RATE_ALERT_MINUTE] ?: 0) }
                .getOrDefault(defaults.time),
            base = if (pairSupported) base else defaults.base,
            quote = if (pairSupported) quote else defaults.quote,
            frequency = values[FX_RATE_ALERT_FREQUENCY]
                ?.let { name -> FxRateAlertFrequency.entries.firstOrNull { it.name == name } }
                ?: defaults.frequency,
            weekday = values[FX_RATE_ALERT_WEEKDAY]?.let { runCatching { DayOfWeek.of(it) }.getOrNull() } ?: defaults.weekday,
            minimumRate = values[FX_RATE_ALERT_MINIMUM]
                ?.let { runCatching { BigDecimal(it) }.getOrNull() }
                ?.takeIf { it > BigDecimal.ZERO },
        )
    }

    private companion object {
        val START_DAY = intPreferencesKey("future_period_start_day")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val REMINDER_AWAITING_CONFIRMATION = booleanPreferencesKey("reminder_awaiting_confirmation")
        val PLAINTEXT_BACKUP_ACKNOWLEDGED = booleanPreferencesKey("plaintext_backup_acknowledged")
        val ONLINE_FX_ENABLED = booleanPreferencesKey("online_fx_enabled")
        val DEFAULT_EXPENSE_CURRENCY = stringPreferencesKey("default_expense_currency")
        val NOTIFICATION_AUTO_RECORD = booleanPreferencesKey("notification_auto_record")
        val NOTIFICATION_DETECTION_ALERTS = booleanPreferencesKey("notification_detection_alerts")
        val PACE_ALERTS_ENABLED = booleanPreferencesKey("pace_alerts_enabled")
        val PACE_ALERTS_SENT = androidx.datastore.preferences.core.stringSetPreferencesKey("pace_alerts_sent")
        val RECAP_DISMISSED_PERIOD = stringPreferencesKey("recap_dismissed_period")
        val FX_RATE_ALERT_ENABLED = booleanPreferencesKey("fx_rate_alert_enabled")
        val FX_RATE_ALERT_HOUR = intPreferencesKey("fx_rate_alert_hour")
        val FX_RATE_ALERT_MINUTE = intPreferencesKey("fx_rate_alert_minute")
        val FX_RATE_ALERT_BASE = stringPreferencesKey("fx_rate_alert_base")
        val FX_RATE_ALERT_QUOTE = stringPreferencesKey("fx_rate_alert_quote")
        val FX_RATE_ALERT_FREQUENCY = stringPreferencesKey("fx_rate_alert_frequency")
        val FX_RATE_ALERT_WEEKDAY = intPreferencesKey("fx_rate_alert_weekday")
        val FX_RATE_ALERT_MINIMUM = stringPreferencesKey("fx_rate_alert_minimum")
        val FX_RATE_ALERT_SENT = androidx.datastore.preferences.core.stringSetPreferencesKey("fx_rate_alert_sent")
        val NOTIFICATION_SOURCE_PACKAGES = androidx.datastore.preferences.core.stringSetPreferencesKey("notification_source_packages")
    }
}
