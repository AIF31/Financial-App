package com.aif31.pocket.ui

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TimeOfDayPickerDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun a_phone_on_the_12_hour_clock_picks_an_evening_time_on_a_12_hour_dial() {
        Settings.System.putString(compose.activity.contentResolver, Settings.System.TIME_12_24, "12")
        var picked: LocalTime? = null
        compose.setContent {
            PocketTheme { TimeOfDayPickerDialog(LocalTime.of(22, 9), onPicked = { picked = it }, onDismiss = {}) }
        }

        // A 24-hour dial shows "22" as the selected hour and on its inner ring; a 12-hour dial shows 10 p.m.
        compose.onNode(hasText("22"), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Aceptar").performClick()

        assertEquals(LocalTime.of(22, 9), picked)
    }
}
