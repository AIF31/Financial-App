package com.aif31.pocket

import android.app.Application
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.PocketLedger
import com.aif31.pocket.data.RoomPocketLedger
import com.aif31.pocket.settings.DataStorePreferences
import com.aif31.pocket.settings.PreferencesStore
import com.aif31.pocket.settings.ReminderScheduler
import com.aif31.pocket.settings.WorkReminderScheduler

class PocketApplication : Application() {
    val database: FinanceDatabase by lazy { FinanceDatabase.open(this) }
    val ledger: PocketLedger by lazy { RoomPocketLedger(database) }
    val preferences: PreferencesStore by lazy { DataStorePreferences(this) }
    val reminderScheduler: ReminderScheduler by lazy { WorkReminderScheduler(this) }
}
