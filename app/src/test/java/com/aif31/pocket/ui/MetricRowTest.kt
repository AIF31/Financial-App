package com.aif31.pocket.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MetricRowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun at_a_large_font_scale_the_value_moves_under_the_label() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                PocketTheme { MetricRow(label = "MXN", value = "SAR 212.30", supporting = "Original MXN 3,950.00") }
            }
        }

        val label = compose.onNodeWithText("MXN", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val value = compose.onNodeWithText("SAR 212.30", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("The value sits below the label", value.top >= label.bottom)
        assertTrue("The value starts where the label starts", value.left == label.left)
    }
}
