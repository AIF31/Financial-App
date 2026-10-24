package com.aif31.pocket.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PocketThemeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "notnight")
    fun the_app_stays_dark_when_the_phone_uses_a_light_theme() {
        var contentColor = Color.Unspecified
        var background = Color.Unspecified
        compose.setContent {
            PocketTheme {
                contentColor = LocalContentColor.current
                background = MaterialTheme.colorScheme.background
            }
        }

        compose.waitForIdle()
        assertEquals(Color(0xFFE6F0ED), contentColor)
        assertEquals(Color(0xFF0E1616), background)
    }

    @Test
    @Config(qualifiers = "night")
    fun text_outside_any_surface_is_readable_on_the_dark_window_background() {
        var contentColor = Color.Unspecified
        compose.setContent { PocketTheme { contentColor = LocalContentColor.current } }

        compose.waitForIdle()
        // Dark onBackground from the Pocket palette; the dark window background is #0E1616.
        assertEquals(Color(0xFFE6F0ED), contentColor)
    }
}
