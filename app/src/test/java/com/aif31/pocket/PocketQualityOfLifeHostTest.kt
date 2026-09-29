package com.aif31.pocket

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.WindowInsets
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.ui.MoneyText
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.RoomPocketLedger
import com.aif31.pocket.notifications.autoRecordedMovementId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(ExperimentalTestApi::class)
class PocketQualityOfLifeHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var database: FinanceDatabase
    private val zone = ZoneId.of("Asia/Riyadh")

    @Before fun setUp() {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    /** Previous period (25 Jan – 24 Feb) with SAR 31.00 spent; current period starts 25 Feb. */
    private fun ledgerWithPreviousPeriod(newFundsMinor: Long = 20_000): RoomPocketLedger {
        val previous = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-10T09:00:00Z"), zone), zone)
        runBlocking {
            previous.execute(LedgerCommand.Initialize(newFundsMinor))
            val state = previous.state.first { !it.needsOnboarding }
            previous.execute(
                LedgerCommand.AddMovement(
                    id = "previous-spend",
                    pocketId = state.pockets.first { it.pocket.name == "Supermercado" }.pocket.id,
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = 3_100,
                    occurredAtUtcMillis = Instant.parse("2026-02-10T09:00:00Z").toEpochMilli(),
                    localDate = LocalDate.of(2026, 2, 10),
                    merchant = "Mercado anterior",
                ),
            )
            previous.execute(LedgerCommand.CreateNextPeriod())
        }
        return RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
    }

    @Test
    fun hero_details_show_spend_pace_and_open_the_period_comparison() {
        val ledger = ledgerWithPreviousPeriod()
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasContentDescription("Mostrar métricas del periodo"), 10_000)
        compose.onNodeWithContentDescription("Mostrar métricas del periodo").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Puedes gastar al día"))
        compose.onNodeWithText("Puedes gastar al día").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Promedio diario del periodo anterior"))
        compose.onNodeWithText("SAR 1.00").assertIsDisplayed()

        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Comparar periodos"))
        // Semantic click: at this test's screen size the button can sit under the extended FAB.
        compose.onNodeWithText("Comparar periodos").performSemanticsAction(SemanticsActions.OnClick)

        compose.waitUntilExactlyOneExists(hasTestTag("comparison_list"), 5_000)
        compose.onNodeWithTag("comparison_baseline").assertTextContains("25 ene – 24 feb 2026", substring = true)
        compose.onNodeWithTag("comparison_list").performScrollToNode(hasText("Resumen"))
        compose.onNodeWithTag("comparison_list").performScrollToNode(hasContentDescription("Gasto neto. ", substring = true))
        compose.onNode(
            hasContentDescription("Gasto neto. 25 feb – 24 mar 2026: SAR", substring = true) and
                hasContentDescription(". 25 ene – 24 feb 2026: SAR", substring = true),
        ).assertExists()
        compose.onNodeWithTag("comparison_list").performScrollToNode(hasText("Gasto diario promedio por Pocket"))
        compose.onNodeWithContentDescription("Atrás").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)
    }

    @Test
    fun pocket_quick_add_preselects_that_pocket() {
        val ledger = ledgerWithPreviousPeriod()
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("pocket_row_Supermercado"))
        compose.onNodeWithTag("pocket_row_Supermercado")
            .assert(SemanticsMatcher("labelled for quick entry") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Registrar gasto en Supermercado" })
            .performClick()

        compose.waitUntilExactlyOneExists(hasTestTag("movement_form"), 5_000)
        compose.onNodeWithTag("movement_form").performScrollToNode(hasTestTag("movement_pocket_Supermercado"))
        compose.onNodeWithTag("movement_pocket_Supermercado").assertIsSelected()
    }

    @Test
    fun back_from_another_root_returns_to_inicio_and_tab_state_survives() {
        val ledger = ledgerWithPreviousPeriod()
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithTag("history_search").performTextInput("fruta")
        compose.onNodeWithText("Inicio").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithTag("history_search").assertTextContains("fruta")

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)
    }

    @Test
    fun swiping_a_movement_deletes_it_with_undo() {
        val ledger = ledgerWithPreviousPeriod()
        runBlocking {
            val state = ledger.state.first { it.currentPeriod?.start == LocalDate.of(2026, 2, 25) }
            ledger.execute(
                LedgerCommand.AddMovement(
                    id = "swipe", pocketId = state.pockets.first().pocket.id, type = MovementType.EXPENSE,
                    accountingAmountMinor = 1_234, occurredAtUtcMillis = Instant.parse("2026-02-26T08:00:00Z").toEpochMilli(),
                    localDate = LocalDate.of(2026, 2, 26), merchant = "Deslizable",
                ),
            )
        }
        compose.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasText("Movimientos"), 10_000)
        compose.onNodeWithText("Movimientos").performClick()
        compose.waitUntilExactlyOneExists(hasText("Deslizable"), 5_000)

        // Start mid-card: on the small Robolectric screen the FAB overlaps the row's end edge.
        compose.onNodeWithText("Deslizable").performTouchInput { swipeLeft(startX = centerX, endX = left) }

        compose.waitUntilExactlyOneExists(hasText("Deshacer"), 5_000)
        // The snackbar appears once the delete commits; the list drops the row when the ledger flow emits after that.
        compose.waitUntilDoesNotExist(hasText("Deslizable"), 5_000)
        compose.onNodeWithText("Deshacer").performClick()
        compose.waitUntilExactlyOneExists(hasText("Deslizable"), 5_000)
    }

    @Test
    fun an_auto_recorded_expense_is_labelled_detectado_in_movements() {
        val ledger = ledgerWithPreviousPeriod()
        runBlocking {
            val state = ledger.state.first { it.currentPeriod?.start == LocalDate.of(2026, 2, 25) }
            ledger.execute(
                LedgerCommand.AddMovement(
                    id = autoRecordedMovementId("suggestion-1"), pocketId = state.pockets.first().pocket.id,
                    type = MovementType.EXPENSE, accountingAmountMinor = 1_500,
                    occurredAtUtcMillis = Instant.parse("2026-02-26T08:00:00Z").toEpochMilli(),
                    localDate = LocalDate.of(2026, 2, 26), merchant = "Café detectado",
                ),
            )
        }
        compose.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasText("Movimientos"), 10_000)
        compose.onNodeWithText("Movimientos").performClick()
        compose.waitUntilExactlyOneExists(hasText("Café detectado"), 5_000)

        compose.onNodeWithText("Detectado").assertIsDisplayed()
    }

    @Test
    fun selecting_a_previous_period_shows_its_spending_metrics() {
        val ledger = ledgerWithPreviousPeriod()
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithText("Pockets").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("pockets_list"), 5_000)
        val previousId = runBlocking { ledger.state.first().periods.minBy { it.start }.id }
        compose.onNodeWithTag("period_$previousId").performClick()

        compose.waitUntilExactlyOneExists(hasText("Vista histórica · Solo lectura"), 5_000)
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasText("Mayor gasto"))
        compose.onNodeWithText("Mercado anterior").assertIsDisplayed()
        // Net spend and the largest expense are both SAR 31.00 in this fixture.
        assertEquals(2, compose.onAllNodesWithText("SAR 31.00").fetchSemanticsNodes().size)
        compose.onNodeWithText("SAR 1.00").assertIsDisplayed()
        compose.waitUntilDoesNotExist(hasText("Puedes gastar al día"), 1_000)
    }

    @Test
    fun dashboard_pocket_card_names_its_pocket_even_when_its_text_is_scrolled_out_of_view() {
        compose.setContent { PocketApp(ledgerWithPreviousPeriod()) }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("pocket_row_Supermercado"))

        // A partly visible card exposes only its visible children, so the card itself must carry the label.
        compose.onNode(
            hasTestTag("pocket_row_Supermercado") and hasClickAction() and
                hasContentDescription("Supermercado", substring = true) and
                hasContentDescription("disponibles", substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun screen_readers_hear_the_dashboard_pocket_card_once_not_its_label_and_then_each_text_again() {
        compose.setContent { PocketApp(ledgerWithPreviousPeriod()) }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("pocket_row_Supermercado"))

        compose.onNode(
            hasAnyAncestor(hasTestTag("pocket_row_Supermercado")) and hasText("Supermercado") and exposedToAccessibility,
            useUnmergedTree = true,
        ).assertDoesNotExist()
    }

    private val exposedToAccessibility = !SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)

    @Test
    fun screen_readers_hear_the_pockets_list_card_once_and_can_still_reach_its_manage_button() {
        compose.setContent { PocketApp(ledgerWithPreviousPeriod()) }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithText("Pockets").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("pockets_list"), 5_000)
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("pocket_Supermercado"))

        compose.onNode(
            hasAnyAncestor(hasTestTag("pocket_Supermercado")) and hasText("Supermercado") and exposedToAccessibility,
            useUnmergedTree = true,
        ).assertDoesNotExist()
        compose.onNode(hasContentDescription("Gestionar Supermercado") and exposedToAccessibility, useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun pockets_list_card_names_its_pocket_even_when_its_text_is_scrolled_out_of_view() {
        compose.setContent { PocketApp(ledgerWithPreviousPeriod()) }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithText("Pockets").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("pockets_list"), 5_000)
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("pocket_Supermercado"))

        compose.onNode(
            hasTestTag("pocket_Supermercado") and hasClickAction() and
                hasContentDescription("Supermercado", substring = true) and
                hasContentDescription("disponibles", substring = true),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // Real text measurement; legacy mode fakes glyph widths.
    @Config(qualifiers = "w384dp-h823dp") // The Galaxy S23 width where the split was seen.
    fun comparison_summary_amounts_are_never_split_across_lines_at_the_largest_font_size() {
        val ledger = ledgerWithPreviousPeriod(newFundsMinor = 750_000)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) { PocketApp(ledger) }
        }
        compose.waitUntilExactlyOneExists(hasContentDescription("Mostrar métricas del periodo"), 10_000)
        compose.onNodeWithContentDescription("Mostrar métricas del periodo").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Comparar periodos"))
        compose.onNodeWithText("Comparar periodos").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasTestTag("comparison_list"), 5_000)
        compose.onNodeWithTag("comparison_list").performScrollToNode(hasContentDescription("Fondos nuevos. ", substring = true))

        for (column in listOf("current", "baseline")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("comparison_value_${column}_Fondos nuevos", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val text = layout.layoutInput.text.text
            // A line may break between "SAR" and the number, but never inside "7,500.00".
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                val splitsNumber = text[end - 1].isAmountChar() && text[end].isAmountChar()
                assertFalse("\"$text\" breaks inside a number after \"${text.substring(0, end)}\"", splitsNumber)
            }
        }
    }

    private fun Char.isAmountChar() = isDigit() || this == '.' || this == ','

    private val isPocketCard = SemanticsMatcher("is a Pocket card") {
        it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith("pocket_") == true
    }

    @Test
    fun screen_readers_hear_each_comparison_summary_row_once() {
        val ledger = ledgerWithPreviousPeriod()
        compose.setContent { PocketApp(ledger) }
        compose.waitUntilExactlyOneExists(hasContentDescription("Mostrar métricas del periodo"), 10_000)
        compose.onNodeWithContentDescription("Mostrar métricas del periodo").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Comparar periodos"))
        compose.onNodeWithText("Comparar periodos").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasTestTag("comparison_list"), 5_000)
        compose.onNodeWithTag("comparison_list").performScrollToNode(hasContentDescription("Fondos nuevos. ", substring = true))

        // The row's spoken description already names the label and both values.
        compose.onNode(hasText("Fondos nuevos") and exposedToAccessibility, useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasTestTag("comparison_value_current_Fondos nuevos") and exposedToAccessibility, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun a_handled_new_expense_launch_does_not_reopen_after_state_restoration_but_a_new_one_does() {
        val ledger = ledgerWithPreviousPeriod()
        var request by mutableIntStateOf(1)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PocketApp(ledger, newExpenseRequest = request) }

        compose.waitUntilExactlyOneExists(hasTestTag("movement_form"), 10_000)
        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)

        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 5_000)
        assertEquals(0, compose.onAllNodes(hasTestTag("movement_form")).fetchSemanticsNodes().size)

        request = 2
        compose.waitUntilExactlyOneExists(hasTestTag("movement_form"), 5_000)
    }

    @Test
    fun new_expense_shortcut_opens_over_an_edit_form_and_returns_to_its_draft() {
        val ledger = ledgerWithCurrentCafeSpend()
        var request by mutableIntStateOf(0)
        compose.setContent { PocketApp(ledger, newExpenseRequest = request) }

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithText("Café").performClick()
        compose.onNodeWithText("Editar").performClick()
        compose.waitUntilExactlyOneExists(hasText("Editar movimiento"), 5_000)
        compose.onNodeWithTag("movement_amount").performTextReplacement("45.00")

        request = 1
        compose.waitUntilExactlyOneExists(hasText("Nuevo gasto"), 5_000)
        compose.onNodeWithTag("movement_amount").assert(hasText("45.00", substring = true).not())

        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.waitUntilExactlyOneExists(hasText("Editar movimiento"), 5_000)
        compose.onNodeWithTag("movement_amount").assertTextContains("45.00", substring = true)
    }

    @Test
    fun saving_the_new_expense_opened_over_an_edit_returns_to_that_edit_with_its_draft() {
        val ledger = ledgerWithCurrentCafeSpend()
        var request by mutableIntStateOf(0)
        compose.setContent { PocketApp(ledger, newExpenseRequest = request) }

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithText("Café").performClick()
        compose.onNodeWithText("Editar").performClick()
        compose.waitUntilExactlyOneExists(hasText("Editar movimiento"), 5_000)
        compose.onNodeWithTag("movement_amount").performTextReplacement("45.00")

        request = 1
        compose.waitUntilExactlyOneExists(hasText("Nuevo gasto"), 5_000)
        compose.onNodeWithTag("movement_amount").performTextInput("5.00")
        compose.onNodeWithTag("movement_pocket_Supermercado").performClick()
        compose.onNodeWithText("Guardar gasto", substring = true).performClick()

        compose.waitUntil(5_000) { runBlocking { ledger.state.first() }.movements.any { it.accountingAmountMinor == 500L } }
        compose.waitUntilExactlyOneExists(hasText("Editar movimiento"), 5_000)
        compose.onNodeWithTag("movement_amount").assertTextContains("45.00", substring = true)
        // The save is confirmed on the edit the user returns to, not only after that edit closes.
        compose.waitUntilExactlyOneExists(hasText("Gasto guardado"), 5_000)
        compose.onNodeWithText("Gasto guardado").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // Real text measurement; legacy mode fakes glyph widths.
    @Config(qualifiers = "w384dp-h823dp") // The Galaxy S23 width where "Movimientos" was cut off.
    fun root_navigation_labels_are_shown_in_full_at_a_large_font_size() {
        val ledger = ledgerWithPreviousPeriod()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1.5f)) { PocketApp(ledger) }
        }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)

        for (label in listOf("Inicio", "Movimientos", "Pockets", "Ajustes")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(label) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals("$label wraps", 1, layout.lineCount)
            assertFalse("$label is cut off with an ellipsis", layout.isLineEllipsized(0))
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // Real text measurement; legacy mode fakes glyph widths.
    @Config(qualifiers = "w384dp-h823dp") // The Galaxy S23 width where "SAR 4,200.00" wrapped.
    fun dashboard_metric_amounts_stay_on_one_line_at_a_large_font_size() {
        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        runBlocking { ledger.execute(LedgerCommand.Initialize(420_000)) }
        val unallocated = MoneyText.format(runBlocking { ledger.state.first { !it.needsOnboarding } }.unallocatedMinor, SupportedCurrency.SAR)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1.5f)) { PocketApp(ledger) }
        }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Sin asignar", substring = true))

        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(unallocated), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("\"$unallocated\" wraps", 1, layouts.single().lineCount)
    }

    @Test
    fun onboarding_content_starts_below_the_status_bar() {
        val statusBarPx = 94 // The Galaxy S23 status bar height from the 1.0.6 hardware test.
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBarPx, 0, 0))
            .build()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.WindowInsets(insets)) {
                PocketApp(RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone))
            }
        }
        compose.waitUntilExactlyOneExists(hasText("Configura tu primer periodo"), 10_000)

        val titleTopPx = with(compose.density) { compose.onNodeWithText("Pocket").getBoundsInRoot().top.toPx() }
        assertTrue("The title starts at ${titleTopPx}px, under the ${statusBarPx}px status bar", titleTopPx >= statusBarPx)
    }

    @Test
    fun editing_a_movement_from_an_earlier_period_shows_that_periods_pocket_availability() {
        compose.setContent { PocketApp(ledgerWithPreviousPeriod()) }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithText("Movimientos").performClick()
        compose.waitUntilExactlyOneExists(hasText("Mercado anterior"), 5_000)
        compose.onNodeWithText("Mercado anterior").performClick()
        compose.onNodeWithText("Editar").performClick()
        compose.waitUntilExactlyOneExists(hasText("Editar movimiento"), 5_000)

        // In 25 Jan – 24 Feb, Supermercado had no budget and SAR 31.00 of spending; the current period has neither.
        compose.onNodeWithTag("movement_pocket_Supermercado").assertTextContains("SAR -31.00 disponibles", substring = true)
        compose.onNodeWithText("Disponible en 25 ene – 24 feb 2026", substring = true).assertExists()
    }

    /** Current period (from 25 Feb) with a SAR 12.00 "Café" expense in Supermercado. */
    private fun ledgerWithCurrentCafeSpend(): RoomPocketLedger {
        val ledger = ledgerWithPreviousPeriod()
        runBlocking {
            val state = ledger.state.first { it.currentPeriod?.start == LocalDate.of(2026, 2, 25) }
            ledger.execute(
                LedgerCommand.AddMovement(
                    id = "current-spend",
                    pocketId = state.pockets.first { it.pocket.name == "Supermercado" }.pocket.id,
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = 1_200,
                    occurredAtUtcMillis = Instant.parse("2026-02-26T08:00:00Z").toEpochMilli(),
                    localDate = LocalDate.of(2026, 2, 26),
                    merchant = "Café",
                ),
            )
        }
        return ledger
    }

    @Test
    fun pockets_header_opens_the_pockets_tab() {
        val ledger = ledgerWithPreviousPeriod()
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Ver todos"))
        compose.onNodeWithText("Ver todos")
            .assert(SemanticsMatcher("labelled for Pockets") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Ver todos los Pockets" })
            .performClick()

        compose.waitUntilExactlyOneExists(hasTestTag("pockets_list"), 5_000)
    }

    @Test
    fun earliest_period_explains_that_there_is_nothing_before_it_to_compare() {
        val ledger = ledgerWithPreviousPeriod()
        compose.setContent { PocketApp(ledger) }

        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        compose.onNodeWithText("Pockets").performClick()
        val earliestId = runBlocking { ledger.state.first().periods.minBy { it.start }.id }
        compose.onNodeWithTag("period_$earliestId").performClick()

        compose.onNodeWithTag("pockets_list").performScrollToNode(hasText("No hay un periodo anterior para comparar."))
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasText("Comparar periodos"))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // Real text measurement; legacy mode fakes glyph widths.
    @Config(qualifiers = "w384dp-h823dp") // The Galaxy S23 geometry where "Registrar gasto" covered "Ver todos".
    fun every_dashboard_item_can_scroll_clear_of_the_extended_fab_at_a_large_font_size() {
        val ledger = ledgerWithPreviousPeriod(newFundsMinor = 420_000)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1.5f)) { PocketApp(ledger) }
        }
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), 10_000)
        val list = compose.onNodeWithTag("dashboard_list")
        val rows = list.fetchSemanticsNode().config[SemanticsProperties.CollectionInfo].rowCount
        list.performScrollToIndex(rows - 1)
        compose.waitForIdle()

        val fabTop = compose.onNodeWithTag("contextual_add").getBoundsInRoot().top
        val lastBottom = with(compose.density) {
            compose.onAllNodes(isPocketCard).fetchSemanticsNodes().maxOf { it.boundsInRoot.bottom }.toDp()
        }
        assertTrue("The last Pocket ends at $lastBottom, under the button starting at $fabTop", lastBottom <= fabTop)
    }
}
