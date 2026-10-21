package com.aif31.pocket

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.RoomPocketLedger
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(ExperimentalTestApi::class)
class MerchantMemoryHostFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: FinanceDatabase
    private lateinit var ledger: RoomPocketLedger
    private val zone = ZoneId.of("Asia/Riyadh")
    private val instant = Instant.parse("2026-09-05T12:00:00Z")

    @Before fun setUp() = runBlocking {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        ledger = RoomPocketLedger(database, Clock.fixed(instant, zone), zone)
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.Initialize(100_000)))
    }

    @After fun tearDown() = database.close()

    private fun openMemory() {
        compose.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)
        compose.onNode(hasText("Ajustes") and hasClickAction()).performClick()
        compose.onNodeWithTag("settings_hub").performScrollToNode(hasText("Memoria de comercios"))
        compose.onNodeWithText("Memoria de comercios").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("merchant_memory_list"), 5_000)
    }

    @Test fun settings_reassigns_a_merchant_and_the_list_shows_its_new_pocket() {
        val pockets = runBlocking {
            val state = ledger.state.first()
            val pockets = state.pocketCatalog
            assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.AddMovement(
                pocketId = pockets[0].id, type = MovementType.EXPENSE, accountingAmountMinor = 1_875,
                occurredAtUtcMillis = instant.toEpochMilli(), localDate = instant.atZone(zone).toLocalDate(), merchant = "Tamimi",
            )))
            pockets
        }
        openMemory()
        compose.onNodeWithTag("merchant_memory_tamimi").assertTextContains(pockets[0].name)
        compose.onNodeWithTag("merchant_memory_tamimi").performClick()
        compose.onNodeWithText("Se moverá este gasto al Pocket que elijas. Los futuros registros automáticos de este comercio irán allí.").assertExists()
        compose.onNodeWithTag("merchant_memory_pocket_${pockets[1].id}").performClick()
        compose.onNodeWithTag("merchant_memory_confirm").performClick()
        compose.waitUntilDoesNotExist(hasTestTag("merchant_memory_confirm"), 5_000)
        compose.onNodeWithTag("merchant_memory_tamimi").assertTextContains(pockets[1].name)
        assertEquals(pockets[1].id, runBlocking { ledger.state.first().movements.single().pocketId })
    }

    @Test fun settings_shows_the_empty_memory_state() {
        openMemory()
        compose.onNodeWithText("Aún no hay comercios. Aparecen cuando registras un gasto con comercio.").assertExists()
    }
}
