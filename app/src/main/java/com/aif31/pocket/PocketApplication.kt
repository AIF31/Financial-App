package com.aif31.pocket

import android.app.Application
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.PocketLedger
import com.aif31.pocket.data.RoomPocketLedger
import com.aif31.pocket.fx.DefaultExchangeRateRepository
import com.aif31.pocket.fx.ExchangeRateRepository
import com.aif31.pocket.fx.HttpsBanxicoClient
import com.aif31.pocket.fx.RoomFxQuoteCache
import com.aif31.pocket.notifications.notificationBetaMetrics as createNotificationBetaMetrics
import com.aif31.pocket.settings.DataStorePreferences
import com.aif31.pocket.settings.PreferencesStore
import com.aif31.pocket.settings.ReminderScheduler
import com.aif31.pocket.settings.WorkReminderScheduler
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.first

class PocketApplication : Application() {
    internal val clock: Clock = Clock.systemUTC()
    internal val budgetZone: ZoneId = ZoneId.of("Asia/Riyadh")
    val database: FinanceDatabase by lazy { FinanceDatabase.open(this) }
    internal val notificationBetaMetrics by lazy { createNotificationBetaMetrics(this) }
    val ledger: PocketLedger by lazy {
        RoomPocketLedger(
            database = database,
            clock = clock,
            zoneId = budgetZone,
            recordNotificationConfirmation = { amountCorrected, currencyCorrected ->
                notificationBetaMetrics.recordConfirmation(amountCorrected, currencyCorrected)
            },
        )
    }
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
