package com.aif31.pocket.ui.prototype

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.aif31.pocket.ui.PocketTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@MediumTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PocketPrototypeScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dashboard_prioritizes_availability_and_recording_an_expense() {
        var recordExpenseRequested = false
        compose.setContent {
            PocketTheme {
                ActionableDashboardPrototype(
                    state = dashboardPrototypeState,
                    onRecordExpense = { recordExpenseRequested = true },
                    onManagePocket = {},
                )
            }
        }

        compose.onNodeWithText("Disponibilidad de Pockets").assertIsDisplayed()
        compose.onNodeWithText("SAR 842.00").assertIsDisplayed()
        compose.onNodeWithText("Registrar gasto").performClick()

        compose.runOnIdle { assertTrue(recordExpenseRequested) }
    }

    @Test
    fun quick_expense_reveals_advanced_fields_only_on_request() {
        var detailsExpanded by mutableStateOf(false)
        compose.setContent {
            PocketTheme {
                QuickExpensePrototype(
                    state = quickExpensePrototypeState.copy(detailsExpanded = detailsExpanded),
                    onAmountChange = {},
                    onPocketSelected = {},
                    onMerchantChange = {},
                    onPaymentMethodSelected = {},
                    onToggleDetails = { detailsExpanded = !detailsExpanded },
                    onSave = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Importe SAR").assertIsDisplayed()
        compose.onNodeWithText("Supermercado").assertIsDisplayed()
        compose.onNodeWithText("Fecha y hora").assertDoesNotExist()

        compose.onNodeWithTag("quick_expense_form").performScrollToNode(hasText("Más detalles"))
        compose.onNodeWithText("Más detalles").performClick()

        compose.onNodeWithTag("quick_expense_form").performScrollToNode(hasText("Fecha y hora"))
        compose.onNodeWithText("Fecha y hora").assertIsDisplayed()
        compose.onNodeWithTag("quick_expense_form").performScrollToNode(hasText("Ocultar detalles"))
        compose.onNodeWithText("Ocultar detalles").assertIsDisplayed()
    }

    @Test
    fun quick_expense_places_initial_focus_on_amount() {
        compose.setContent {
            PocketTheme {
                QuickExpensePrototype(
                    state = quickExpensePrototypeState,
                    onAmountChange = {},
                    onPocketSelected = {},
                    onMerchantChange = {},
                    onPaymentMethodSelected = {},
                    onToggleDetails = {},
                    onSave = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("expense_amount").assertIsFocused()
    }

    @Test
    fun quick_expense_advanced_fields_emit_edit_events() {
        var state by mutableStateOf(quickExpensePrototypeState.copy(detailsExpanded = true))
        compose.setContent {
            PocketTheme {
                QuickExpensePrototype(
                    state = state,
                    onAmountChange = {},
                    onPocketSelected = {},
                    onMerchantChange = {},
                    onPaymentMethodSelected = {},
                    onToggleDetails = {},
                    onSave = {},
                    onBack = {},
                    onMovementTypeSelected = { state = state.copy(movementType = it) },
                    onCurrencySelected = { state = state.copy(currency = it) },
                    onOriginalAmountChange = { state = state.copy(originalAmount = it) },
                    onConversionStatusSelected = { state = state.copy(conversionStatus = it) },
                    onDateTimeChange = { state = state.copy(dateTime = it) },
                    onNoteChange = { state = state.copy(note = it) },
                )
            }
        }

        compose.onNodeWithTag("quick_expense_form").performScrollToNode(hasText("Devolución"))
        compose.onNodeWithText("Devolución").performClick()
        compose.onNodeWithTag("quick_expense_form").performScrollToNode(hasText("USD"))
        compose.onNodeWithText("USD").performClick()
        compose.onNodeWithTag("quick_expense_form").performScrollToNode(hasText("Nota (opcional)"))
        compose.onNodeWithText("Nota (opcional)").performTextReplacement("Cena de grupo")

        compose.runOnIdle {
            assertEquals(MovementTypePrototype.REFUND, state.movementType)
            assertEquals(MovementCurrencyPrototype.USD, state.currency)
            assertEquals("Cena de grupo", state.note)
        }
    }

    @Test
    fun quick_expense_keeps_validation_next_to_the_amount() {
        compose.setContent {
            PocketTheme {
                QuickExpensePrototype(
                    state = quickExpensePrototypeState.copy(
                        amount = "",
                        amountError = "Escribe un importe válido",
                    ),
                    onAmountChange = {},
                    onPocketSelected = {},
                    onMerchantChange = {},
                    onPaymentMethodSelected = {},
                    onToggleDetails = {},
                    onSave = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Escribe un importe válido").assertIsDisplayed()
    }

    @Test
    fun pockets_expose_non_color_status_and_accessible_reorder_action() {
        var movement: Pair<String, PocketMoveDirection>? = null
        compose.setContent {
            PocketTheme {
                PocketsOverviewPrototype(
                    state = pocketsPrototypeState,
                    onCreatePocket = {},
                    onPocketSelected = {},
                    onMovePocket = { id, direction -> movement = id to direction },
                )
            }
        }

        compose.onNodeWithText("En riesgo · 86% consumido").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Mover Viajes arriba").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Mover Supermercado arriba").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mover Universidad y materiales de investigación abajo")
            .performScrollTo()
            .assertIsNotEnabled()

        compose.runOnIdle { assertEquals("travel" to PocketMoveDirection.UP, movement) }
    }

    @Test
    fun pockets_expose_allocation_and_rollover_management_actions() {
        var allocationPocketId: String? = null
        var rolloverChange: Pair<String, Boolean>? = null
        compose.setContent {
            PocketTheme {
                PocketsOverviewPrototype(
                    state = pocketsPrototypeState,
                    onCreatePocket = {},
                    onPocketSelected = {},
                    onMovePocket = { _, _ -> },
                    onSetAllocation = { allocationPocketId = it },
                    onToggleRollover = { id, enabled -> rolloverChange = id to enabled },
                )
            }
        }

        compose.onNodeWithContentDescription("Asignar presupuesto a Viajes").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Desactivar rollover de Viajes").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals("travel", allocationPocketId)
            assertEquals("travel" to false, rolloverChange)
        }
    }
}
