package com.aif31.pocket.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
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
    @Config(qualifiers = "night")
    fun text_outside_any_surface_is_readable_on_the_dark_window_background() {
        var contentColor = Color.Unspecified
        compose.setContent { PocketTheme { contentColor = LocalContentColor.current } }

        compose.waitForIdle()
        // Dark onBackground from the Pocket palette; the dark window background is #0E1616.
        assertEquals(Color(0xFFE6F0ED), contentColor)
    }
}
