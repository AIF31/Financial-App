package com.aif31.pocket

import android.Manifest
import android.app.Activity
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationPermissionRequestTest {
    private lateinit var activity: Activity
    private var requests = 0
    private lateinit var permission: NotificationPermissionRequest

    @Before fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.getSharedPreferences("notification_permission", 0).edit().clear().commit()
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        permission = NotificationPermissionRequest(activity) { requests++ }
    }

    @Test fun a_dismissed_prompt_is_asked_again_and_also_offers_settings_after_recreation() {
        permission.request()
        assertEquals(1, requests)
        // Swiping the prompt away and a permanent denial both return false with no rationale.
        permission.onResult(false)
        permission = NotificationPermissionRequest(activity) { requests++ }
        assertTrue(permission.offerSettings)
        permission.request()
        assertEquals(2, requests)
        assertNull(shadowOf(activity).nextStartedActivity)
        permission.openSettings()
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(activity.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test fun a_denial_android_will_explain_does_not_offer_settings() {
        shadowOf(activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)
        permission.onResult(false)
        assertFalse(permission.offerSettings)
    }

    @Test fun a_grant_withdraws_the_settings_offer() {
        permission.onResult(false)
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        permission.onResult(true)
        assertFalse(NotificationPermissionRequest(activity) {}.offerSettings)
    }

    @Test fun first_request_launches_without_a_rationale() {
        permission.request()
        assertEquals(1, requests)
        assertFalse(permission.showRationale)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun ordinary_denial_explains_before_requesting_again() {
        shadowOf(activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)
        permission.onResult(false)
        permission.request()
        assertTrue(permission.showRationale)
        assertEquals(0, requests)
        permission.request() // The card's Continuar action.
        assertFalse(permission.showRationale)
        assertEquals(1, requests)
    }

    @Test fun an_existing_card_rationale_does_not_trigger_a_second_confirmation() {
        shadowOf(activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)
        permission.request(rationaleAccepted = true)
        assertEquals(1, requests)
        assertFalse(permission.showRationale)
    }

    @Test fun grant_is_rechecked_instead_of_reusing_denial_history() {
        permission.onResult(false)
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        permission.request()
        assertEquals(0, requests)
        assertFalse(permission.showRationale)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, shadowOf(activity).nextStartedActivity.action)
    }

    @Test @Config(sdk = [32]) fun before_android_13_blocked_notifications_open_settings() {
        shadowOf(activity.getSystemService(android.app.NotificationManager::class.java)).setNotificationsEnabled(false)
        permission.request()
        assertEquals(0, requests)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, shadowOf(activity).nextStartedActivity.action)
    }

}
