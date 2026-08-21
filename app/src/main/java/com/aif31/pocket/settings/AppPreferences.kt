package com.aif31.pocket.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppPreferences(
    val futurePeriodStartDay: Int = 25,
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime = LocalTime.of(21, 0),
)

interface PreferencesStore {
    val state: Flow<AppPreferences>
    suspend fun setFuturePeriodStartDay(day: Int)
    suspend fun setReminder(enabled: Boolean, time: LocalTime)
}
private val Context.pocketPreferences by preferencesDataStore("pocket_preferences")

class DataStorePreferences(private val context: Context) : PreferencesStore {
    override val state: Flow<AppPreferences> = context.pocketPreferences.data.map { values ->
        AppPreferences(
            futurePeriodStartDay = values[START_DAY] ?: 25,
            reminderEnabled = values[REMINDER_ENABLED] ?: false,
            reminderTime = LocalTime.of(values[REMINDER_HOUR] ?: 21, values[REMINDER_MINUTE] ?: 0),
        )
    }

    override suspend fun setFuturePeriodStartDay(day: Int) {
        require(day in 1..31)
        context.pocketPreferences.edit { it[START_DAY] = day }
    }

    override suspend fun setReminder(enabled: Boolean, time: LocalTime) {
        context.pocketPreferences.edit {
            it[REMINDER_ENABLED] = enabled
            it[REMINDER_HOUR] = time.hour
            it[REMINDER_MINUTE] = time.minute
        }
    }

    private companion object {
        val START_DAY = intPreferencesKey("future_period_start_day")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }
}
