package com.aif31.pocket

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.RoomPocketLedger
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(ExperimentalTestApi::class)
class PocketAppHostFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: FinanceDatabase

    @Before fun setUp() {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun user_completes_the_core_spending_flow_through_the_public_UI() {
        val zone = ZoneId.of("Asia/Riyadh")
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasText("Configura tu primer periodo"), 5_000)
        compose.onNodeWithTag("new_funds").performTextInput("1000.00")
        compose.onNodeWithText("Comenzar").performClick()
        compose.waitUntilDoesNotExist(hasText("Configura tu primer periodo"), 10_000)
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
    }
}
