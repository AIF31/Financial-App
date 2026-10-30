package com.aif31.pocket

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.aif31.pocket.data.LedgerCommand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app draws edge to edge, so the window does not shrink for the keyboard. Each form must end where the keyboard
 * starts: higher leaves a dead gap above it, lower hides the fields being typed into.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 35)
@OptIn(ExperimentalTestApi::class)
class KeyboardInsetsTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<PocketApplication>()

    @Before fun seedLedger() {
        runBlocking {
            app.database.clearAllTables()
            app.ledger.execute(LedgerCommand.Initialize(100_000))
            app.ledger.state.first { !it.needsOnboarding }
        }
    }

    @After fun clearLedger() = runBlocking { app.database.clearAllTables() }

    private fun launch() = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))

    /** The keyboard's top edge in window pixels, once it has finished sliding in. */
    private fun ActivityScenario<MainActivity>.settledKeyboardTop(): Float {
        var previous = -1
        var top = -1
        compose.waitUntil(10_000) {
            onActivity { activity ->
                val root = activity.window.decorView
                val insets = ViewCompat.getRootWindowInsets(root)
                top = if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                    root.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                } else {
                    -1
                }
            }
            compose.waitForIdle()
            (top > 0 && top == previous).also { previous = top }
        }
        return top.toFloat()
    }

    private fun bottomOf(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow.bottom

    @Test fun movement_form_ends_at_the_keyboard() {
        launch().use { scenario ->
            compose.waitUntilExactlyOneExists(hasTestTag("contextual_add"), 10_000)
            compose.onNodeWithTag("contextual_add").performClick()
            // The amount field takes focus when the form opens.
            compose.waitUntilExactlyOneExists(hasTestTag("movement_amount"), 10_000)
            val keyboardTop = scenario.settledKeyboardTop()
            assertEquals(keyboardTop, bottomOf("movement_form"), 2f)
        }
    }

    @Test fun settings_form_ends_at_the_keyboard() {
        launch().use { scenario ->
            compose.waitUntilExactlyOneExists(hasText("Ajustes"), 10_000)
            compose.onNodeWithText("Ajustes").performClick()
            compose.onNodeWithText("Periodo y fondos").performScrollTo().performClick()
            compose.onNodeWithTag("period_funds").performClick()
            val keyboardTop = scenario.settledKeyboardTop()
            assertEquals(keyboardTop, bottomOf("settings_list"), 2f)
        }
    }

    @Test fun movement_search_results_end_at_the_keyboard() {
        launch().use { scenario ->
            compose.waitUntilExactlyOneExists(hasText("Movimientos"), 10_000)
            compose.onNodeWithText("Movimientos").performClick()
            compose.onNodeWithTag("history_search").performClick()
            val keyboardTop = scenario.settledKeyboardTop()
            assertEquals(keyboardTop, bottomOf("movements_list"), 2f)
        }
    }
}
