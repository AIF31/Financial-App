package com.aif31.pocket

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.RoomPocketLedger
import com.aif31.pocket.settings.AppPreferences
import com.aif31.pocket.settings.PreferencesStore
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
@OptIn(ExperimentalTestApi::class)
class PocketAppFlowTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var database: FinanceDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = FinanceDatabase.inMemory(context)
        compose.enableAccessibilityChecks()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun onboarding_allocation_expense_dashboard_and_history_are_consistent() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone),
            zoneId = zone,
        )

        val preferences = FakePreferences()
        compose.setContent { PocketApp(ledger = ledger, preferences = preferences) }

        compose.waitUntilExactlyOneExists(hasText("Configura tu primer periodo"), 5_000)
        compose.onNodeWithTag("new_funds").performTextInput("1000.00")
        compose.onNodeWithTag("start_day").performTextReplacement("10")
        compose.onNodeWithText("Comenzar").performClick()

        compose.waitUntil(5_000) { preferences.current.futurePeriodStartDay == 10 }
        compose.waitUntilAtLeastOneExists(hasText("SAR 1,000.00"), 5_000)
        compose.onNodeWithText("Pockets").performClick()
        compose.onNodeWithTag("pocket_Supermercado").performClick()
        compose.onNodeWithTag("allocation_amount").performTextInput("300.00")
        compose.onNodeWithText("Guardar presupuesto").performClick()

        compose.onNodeWithContentDescription("Añadir movimiento").performClick()
        compose.onNodeWithTag("movement_amount").performTextInput("100.00")
        compose.onNodeWithTag("movement_pocket_Supermercado").performClick()
        compose.onNodeWithText("Guardar gasto").performClick()

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Gasto diario promedio"))
        compose.onNodeWithText("Gasto diario promedio").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Disponible: SAR 200.00"))
        compose.onNodeWithText("Disponible: SAR 200.00").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("rollover_Supermercado"))
        compose.onNodeWithTag("rollover_Supermercado").assertIsDisplayed()
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithText("-SAR 100.00").assertIsDisplayed()
        compose.onNodeWithText("Supermercado").assertIsDisplayed()
    }

    private class FakePreferences : PreferencesStore {
        private val values = MutableStateFlow(AppPreferences())
        override val state = values
        val current: AppPreferences get() = values.value
        override suspend fun setFuturePeriodStartDay(day: Int) { values.value = values.value.copy(futurePeriodStartDay = day) }
        override suspend fun setReminder(enabled: Boolean, time: LocalTime) { values.value = values.value.copy(reminderEnabled = enabled, reminderTime = time) }
    }
}
