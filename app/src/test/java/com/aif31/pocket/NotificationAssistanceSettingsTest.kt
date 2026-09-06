package com.aif31.pocket

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.RoomPocketLedger
import com.aif31.pocket.notifications.PocketNotificationListener
import com.aif31.pocket.settings.AppPreferences
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NotificationAssistanceSettingsTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: FinanceDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = FinanceDatabase.inMemory(context)
    }

    @After fun tearDown() {
        Settings.Secure.putString(context.contentResolver, ENABLED_NOTIFICATION_LISTENERS, "")
        database.close()
    }

    @Test fun notification_access_refreshes_on_resume_after_revocation_without_changing_ledger_data() {
        val instant = Instant.parse("2026-09-05T12:00:00Z")
        val ledger = RoomPocketLedger(database, Clock.fixed(instant, ZoneOffset.UTC))
        runBlocking {
            assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.Initialize(100_000)))
            val pocketId = ledger.state.first().pockets.first().pocket.id
            assertEquals(
                LedgerResult.Success,
                ledger.execute(
                    LedgerCommand.AddMovement(
                        pocketId = pocketId,
                        type = MovementType.EXPENSE,
                        accountingAmountMinor = 1_200,
                        occurredAtUtcMillis = instant.toEpochMilli(),
                        localDate = LocalDate.of(2026, 9, 5),
                    )
                ),
            )
        }
        val dao = database.financeDao()
        val ledgerBeforeRevocation = runBlocking { Triple(dao.periods(), dao.pockets(), dao.movements()) }
        val listener = ComponentName(context, PocketNotificationListener::class.java).flattenToString()
        Settings.Secure.putString(context.contentResolver, ENABLED_NOTIFICATION_LISTENERS, listener)
        val lifecycleOwner = TestLifecycleOwner()

        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                NotificationAssistanceSettings(
                    preferences = AppPreferences(),
                    preferencesStore = null,
                    padding = PaddingValues(),
                    onBack = {},
                )
            }
        }
        compose.runOnIdle {
            lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        compose.onNodeWithText("Acceso a notificaciones concedido").assertIsDisplayed()

        compose.runOnIdle { lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
        Settings.Secure.putString(context.contentResolver, ENABLED_NOTIFICATION_LISTENERS, "")
        compose.runOnIdle { lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }

        compose.onNodeWithText("Acceso a notificaciones no concedido").assertIsDisplayed()
        assertEquals(ledgerBeforeRevocation, runBlocking { Triple(dao.periods(), dao.pockets(), dao.movements()) })
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }

    private companion object {
        const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
    }
}
