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
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppPreferences(
    val futurePeriodStartDay: Int = 25,
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime = LocalTime.of(21, 0),
    val onlineFxEnabled: Boolean = false,
    val defaultExpenseCurrency: SupportedCurrency = SupportedCurrency.SAR,
)

interface PreferencesStore {
    val state: Flow<AppPreferences>
    suspend fun setFuturePeriodStartDay(day: Int)
    suspend fun setReminder(enabled: Boolean, time: LocalTime)
    suspend fun setOnlineFxEnabled(enabled: Boolean)
    suspend fun setDefaultExpenseCurrency(currency: SupportedCurrency)
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
            onlineFxEnabled = values[ONLINE_FX_ENABLED] ?: false,
            defaultExpenseCurrency = values[DEFAULT_EXPENSE_CURRENCY]
                ?.let { runCatching { SupportedCurrency.fromCode(it) }.getOrNull() }
                ?: SupportedCurrency.SAR,
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
        }
    }

    override suspend fun setOnlineFxEnabled(enabled: Boolean) {
        dataStore.edit { it[ONLINE_FX_ENABLED] = enabled }
    }

    override suspend fun setDefaultExpenseCurrency(currency: SupportedCurrency) {
        dataStore.edit { it[DEFAULT_EXPENSE_CURRENCY] = currency.name }
    }

    private companion object {
        val START_DAY = intPreferencesKey("future_period_start_day")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val ONLINE_FX_ENABLED = booleanPreferencesKey("online_fx_enabled")
        val DEFAULT_EXPENSE_CURRENCY = stringPreferencesKey("default_expense_currency")
    }
}
