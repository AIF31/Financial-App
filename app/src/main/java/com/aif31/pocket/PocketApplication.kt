package com.aif31.pocket

import android.app.Application
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.PocketLedger
import com.aif31.pocket.data.RoomPocketLedger
import com.aif31.pocket.fx.DefaultExchangeRateRepository
import com.aif31.pocket.fx.ExchangeRateRepository
import com.aif31.pocket.fx.HttpsBanxicoClient
import com.aif31.pocket.fx.RoomFxQuoteCache
import com.aif31.pocket.settings.DataStorePreferences
import com.aif31.pocket.settings.PreferencesStore
import com.aif31.pocket.settings.ReminderScheduler
import com.aif31.pocket.settings.WorkReminderScheduler
import kotlinx.coroutines.flow.first

class PocketApplication : Application() {
    val database: FinanceDatabase by lazy { FinanceDatabase.open(this) }
    val ledger: PocketLedger by lazy { RoomPocketLedger(database) }
    val preferences: PreferencesStore by lazy { DataStorePreferences(this) }
    val exchangeRates: ExchangeRateRepository by lazy {
        DefaultExchangeRateRepository(
            banxico = HttpsBanxicoClient(BuildConfig.POCKET_BANXICO_TOKEN),
            cache = RoomFxQuoteCache(database.financeDao()),
            onlineFxEnabled = { preferences.state.first().onlineFxEnabled },
        )
    }
    val reminderScheduler: ReminderScheduler by lazy { WorkReminderScheduler(this) }
}
