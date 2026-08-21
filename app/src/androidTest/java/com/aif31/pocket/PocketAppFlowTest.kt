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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.RoomPocketLedger
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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

        compose.setContent { PocketApp(ledger = ledger) }

        compose.waitUntilExactlyOneExists(hasText("Configura tu primer periodo"), 5_000)
        compose.onNodeWithTag("new_funds").performTextInput("1000.00")
        compose.onNodeWithText("Comenzar").performClick()

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
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Disponible: SAR 200.00"))
        compose.onNodeWithText("Disponible: SAR 200.00").assertIsDisplayed()
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithText("-SAR 100.00").assertIsDisplayed()
        compose.onNodeWithText("Supermercado").assertIsDisplayed()
    }
}
