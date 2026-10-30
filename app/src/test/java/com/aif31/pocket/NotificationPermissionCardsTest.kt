package com.aif31.pocket

import android.Manifest
import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.settings.AppPreferences
import com.aif31.pocket.settings.FxRateAlertSettings
import com.aif31.pocket.settings.ReminderStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NotificationPermissionCardsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var requests = 0

    @Before fun setUp() {
        compose.activity.getSharedPreferences("notification_permission", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(compose.activity.getSystemService(android.app.NotificationManager::class.java)).setNotificationsEnabled(false)
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(compose.activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)
    }

    @Test fun detection_card_shows_inline_rationale_before_request() {
        compose.setContent {
            NotificationAssistanceSettings(AppPreferences(), null, PaddingValues(), {}, { requests++ })
        }
        compose.onNodeWithText("Permitir avisos").performScrollTo().performClick()
        compose.onNodeWithText(NOTIFICATION_PERMISSION_RATIONALE).performScrollTo().assertIsDisplayed()
        assertEquals(0, requests)
        compose.onNodeWithText("Continuar").performScrollTo().performClick()
        assertEquals(1, requests)
    }

    @Test fun detection_card_opens_settings_after_permanent_denial() {
        NotificationPermissionRequest(compose.activity) {}.onResult(false)
        shadowOf(compose.activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, false)
        compose.setContent {
            NotificationAssistanceSettings(AppPreferences(), null, PaddingValues(), {}, { requests++ })
        }
        compose.onNodeWithText("Permitir avisos").performScrollTo().performClick()
        assertEquals(0, requests)
        val intent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(compose.activity.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test fun rate_alert_card_shows_inline_rationale_before_request() {
        compose.setContent {
            CurrencySettingsContent(
                state = CurrencySettingsUiState(
                    currentCurrency = SupportedCurrency.SAR,
                    onlineFxEnabled = true,
                    defaultExpenseCurrency = SupportedCurrency.SAR,
                    fxRateAlert = FxRateAlertUiState(FxRateAlertSettings(enabled = true), ReminderStatus.PermissionRequired),
                ),
                contentPadding = PaddingValues(),
                onRequestNotificationPermission = { requests++ },
            )
        }
        val list = compose.onNodeWithTag("currency_settings_list")
        list.performScrollToNode(hasText("Permitir notificaciones"))
        compose.onNodeWithText("Permitir notificaciones").performClick()
        list.performScrollToNode(hasText(NOTIFICATION_PERMISSION_RATIONALE))
        compose.onNodeWithText(NOTIFICATION_PERMISSION_RATIONALE).performScrollTo().assertIsDisplayed()
        assertEquals(0, requests)
        list.performScrollToNode(hasText("Continuar"))
        compose.onNodeWithText("Continuar").performScrollTo().performClick()
        assertEquals(1, requests)
    }

    @Test fun rate_alert_card_opens_settings_after_permanent_denial() {
        NotificationPermissionRequest(compose.activity) {}.onResult(false)
        shadowOf(compose.activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, false)
        compose.setContent {
            CurrencySettingsContent(
                state = CurrencySettingsUiState(
                    currentCurrency = SupportedCurrency.SAR,
                    onlineFxEnabled = true,
                    defaultExpenseCurrency = SupportedCurrency.SAR,
                    fxRateAlert = FxRateAlertUiState(FxRateAlertSettings(enabled = true), ReminderStatus.PermissionRequired),
                ),
                contentPadding = PaddingValues(),
                onRequestNotificationPermission = { requests++ },
            )
        }
        compose.onNodeWithTag("currency_settings_list").performScrollToNode(hasText("Permitir notificaciones"))
        compose.onNodeWithText("Permitir notificaciones").performClick()
        assertEquals(0, requests)
        val intent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(compose.activity.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }
}
