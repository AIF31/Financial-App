package com.aif31.pocket

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.*
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.notifications.NotificationSuggestionStore
import com.aif31.pocket.notifications.ParsedPayment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.*

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(ExperimentalTestApi::class)
class PocketNavigationHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.of("Asia/Riyadh")
    private val clock = Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone)
    private val database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    private val ledger = RoomPocketLedger(database, clock, zone).also {
        runBlocking { it.execute(LedgerCommand.Initialize(100_000)) }
    }
    private fun back() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun tabs_keep_search_and_filters_and_back_exits_through_inicio() {
        val restore = StateRestorationTester(compose)
        restore.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasText("Movimientos"), 5_000)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("open_analysis"))
        val dashboardScroll = compose.onNodeWithTag("dashboard_list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithTag("history_search").performTextInput("fruta")
        compose.onNodeWithTag("filter_pocket").performClick()
        compose.onNodeWithTag("filter_pocket_option_1").performClick()
        compose.onNodeWithText("Pockets").performClick()
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithTag("history_search").assertTextContains("fruta")
        compose.onNodeWithTag("clear_filters").assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.waitUntilExactlyOneExists(hasTestTag("history_search"), 5_000)
        compose.onNodeWithTag("history_search").assertTextContains("fruta")
        back()
        compose.onNodeWithTag("contextual_add").assertExists()
        compose.onNodeWithTag("history_search").assertDoesNotExist()
        assertEquals(dashboardScroll, compose.onNodeWithTag("dashboard_list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value())
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithTag("history_search").assertTextContains("fruta")
    }

    @Test fun shortcut_preserves_review_draft_and_reports_hidden_suggestion_then_discards_popped_draft() {
        val suggestionId = runBlocking {
            NotificationSuggestionStore(database, clock).ingest(
                sourcePackage = "synthetic.payments", notificationIdentity = "navigation-review",
                postedAtUtcMillis = clock.millis(),
                payment = ParsedPayment(2_500, SupportedCurrency.SAR, "Tienda"),
            )
            ledger.state.first { it.movementSuggestions.isNotEmpty() }.movementSuggestions.single().id
        }
        val request = mutableIntStateOf(0)
        var reviewed = emptySet<String>()
        val restore = StateRestorationTester(compose)
        restore.setContent {
            PocketApp(ledger, newExpenseRequest = request.intValue, onSuggestionsUnderReviewChange = { reviewed = it })
        }
        compose.waitUntilExactlyOneExists(hasText("Movimientos"), 5_000)
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithTag("movement_suggestion").performClick()
        compose.onNodeWithTag("movement_amount").performTextReplacement("31.00")
        compose.runOnIdle { request.intValue++ }
        compose.onNodeWithTag("movement_amount").performTextInput("12.34")
        compose.runOnIdle { request.intValue++ }
        compose.onNodeWithTag("movement_amount").assertTextContains("12.34")
        compose.runOnIdle { assertEquals(setOf(suggestionId), reviewed) }
        restore.emulateSavedInstanceStateRestore()
        compose.waitUntilExactlyOneExists(hasTestTag("movement_amount"), 5_000)
        compose.onNodeWithTag("movement_amount").assertTextContains("12.34")
        back()
        compose.onNodeWithTag("movement_amount").assertTextContains("31.00")
        compose.runOnIdle { request.intValue++ }
        compose.onNodeWithTag("movement_amount").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        back()
        runBlocking { ledger.execute(LedgerCommand.RejectSuggestion(suggestionId)) }
        compose.waitUntilExactlyOneExists(hasText("Sugerencia no disponible"), 5_000)
        compose.onNodeWithText("Cerrar").performClick()
        compose.runOnIdle { assertEquals(emptySet<String>(), reviewed) }
    }

    @Test fun saved_edit_returns_to_analysis_drill_and_back_returns_to_analysis() {
        runBlocking {
            val pocketId = ledger.state.first().pockets.first().pocket.id
            ledger.execute(LedgerCommand.AddMovement(
                id = "drill-expense", pocketId = pocketId, type = MovementType.EXPENSE,
                accountingAmountMinor = 2_500, occurredAtUtcMillis = clock.millis(),
                localDate = LocalDate.of(2026, 2, 26), merchant = "Tienda",
            ))
        }
        compose.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("open_analysis"))
        compose.onNodeWithTag("open_analysis").performClick()
        compose.onNodeWithTag("analysis_list").performScrollToNode(hasTestTag("merchant_row"))
        compose.onNodeWithTag("merchant_row").performClick()
        compose.onNode(hasText("Tienda") and hasClickAction()).performClick()
        compose.onNodeWithText("Editar").performClick()
        compose.onNodeWithTag("movement_amount").performTextReplacement("31.00")
        compose.onNodeWithTag("movement_save").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("drill_summary"), 5_000)
        compose.onNodeWithText("Inicio").assertDoesNotExist()
        back()
        compose.onNodeWithTag("analysis_list").assertExists()
    }

    @Test fun movements_request_clears_forms_and_settings_detail_back_returns_to_hub() {
        val revision = mutableIntStateOf(0)
        compose.setContent { PocketApp(ledger, openMovementsRevision = revision.intValue) }
        compose.waitUntilExactlyOneExists(hasTestTag("contextual_add"), 5_000)
        compose.onNodeWithTag("contextual_add").performClick()
        compose.onNodeWithTag("movement_amount").performTextInput("42")
        compose.runOnIdle { revision.intValue++ }
        compose.onNodeWithTag("history_search").assertExists()
        back()
        compose.onNodeWithTag("contextual_add").performClick()
        compose.onNodeWithTag("movement_amount").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        back()
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("Moneda y conversión").performClick()
        compose.onNodeWithTag("settings_hub").assertDoesNotExist()
        back()
        compose.onNodeWithTag("settings_hub").assertExists()
        back()
        compose.onNodeWithTag("contextual_add").assertExists()
    }
}
