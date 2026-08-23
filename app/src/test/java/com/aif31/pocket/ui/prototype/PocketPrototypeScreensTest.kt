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
    fun dashboard_prioritizes_alerts_and_explains_status_without_color() {
        val onTrack = prototypePockets.first()
        val state = dashboardPrototypeState.copy(
            pockets = listOf(
                onTrack.copy(id = "one", name = "Uno"),
                onTrack.copy(id = "two", name = "Dos"),
                onTrack.copy(id = "three", name = "Tres"),
                prototypePockets[1],
            ),
        )
        compose.setContent {
            PocketTheme {
                ActionableDashboardPrototype(state, onRecordExpense = {}, onManagePocket = {})
            }
        }

        compose.onNodeWithText("En riesgo · 86% consumido").performScrollTo().assertIsDisplayed()
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
    fun quick_expense_announces_a_clear_save_success() {
        var returnedToContext = false
        compose.setContent {
            PocketTheme {
                QuickExpensePrototype(
                    state = quickExpensePrototypeState.copy(saveFeedback = "Gasto guardado"),
                    onAmountChange = {},
                    onPocketSelected = {},
                    onMerchantChange = {},
                    onPaymentMethodSelected = {},
                    onToggleDetails = {},
                    onSave = {},
                    onBack = { returnedToContext = true },
                )
            }
        }

        compose.onNodeWithText("Gasto guardado").assertIsDisplayed()
        compose.onNodeWithText("Volver a Inicio").performClick()
        compose.runOnIdle { assertTrue(returnedToContext) }
    }

    @Test
    fun pockets_expose_non_color_status_and_accessible_reorder_action() {
        var movement: PocketManagementAction.Move? = null
        compose.setContent {
            PocketTheme {
                PocketsOverviewPrototype(
                    state = pocketsPrototypeState,
                    onAction = { if (it is PocketManagementAction.Move) movement = it },
                )
            }
        }

        compose.onNodeWithText("En riesgo · 86% consumido").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Mover Viajes arriba").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Mover Supermercado arriba").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mover Universidad y materiales de investigación abajo")
            .performScrollTo()
            .assertIsNotEnabled()

        compose.runOnIdle {
            assertEquals(PocketManagementAction.Move("travel", PocketMoveDirection.UP), movement)
        }
    }

    @Test
    fun pockets_expose_allocation_and_rollover_management_actions() {
        val actions = mutableListOf<PocketManagementAction>()
        var state by mutableStateOf(pocketsPrototypeState.copy(managingPocketId = "travel"))
        compose.setContent {
            PocketTheme {
                PocketsOverviewPrototype(
                    state = state,
                    onAction = { action ->
                        actions += action
                        if (action is PocketManagementAction.SetAllocation) {
                            state = state.copy(managementFeedback = "Abrir asignación de Viajes")
                        }
                    },
                )
            }
        }

        compose.onNodeWithContentDescription("Asignar presupuesto a Viajes").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Desactivar rollover de Viajes").performScrollTo().performClick()

        compose.runOnIdle {
            assertTrue(PocketManagementAction.SetAllocation("travel") in actions)
            assertTrue(PocketManagementAction.SetRollover("travel", false) in actions)
        }
        compose.onNodeWithText("Abrir asignación de Viajes").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pockets_open_and_close_a_focused_management_surface() {
        var state by mutableStateOf(pocketsPrototypeState)
        compose.setContent {
            PocketTheme {
                PocketsOverviewPrototype(
                    state = state,
                    onAction = { action ->
                        state = when (action) {
                            is PocketManagementAction.OpenManagement -> state.copy(
                                managingPocketId = action.pocketId,
                            )
                            PocketManagementAction.CloseManagement -> state.copy(
                                managingPocketId = null,
                            )
                            else -> state
                        }
                    },
                )
            }
        }

        compose.onNodeWithContentDescription("Gestionar Supermercado").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Volver a Pockets").assertIsDisplayed()
        compose.onNodeWithText("Gestionar Supermercado").assertExists()
        compose.onNodeWithText("Viajes").assertDoesNotExist()

        compose.onNodeWithContentDescription("Volver a Pockets").performClick()
        compose.onNodeWithText("Viajes").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pockets_show_non_destructive_preview_feedback() {
        compose.setContent {
            PocketTheme {
                PocketsOverviewPrototype(
                    state = pocketsPrototypeState.copy(
                        managementFeedback = "Abrir edición de Viajes",
                    ),
                    onAction = {},
                )
            }
        }

        compose.onNodeWithText("Abrir edición de Viajes").assertIsDisplayed()
    }
}
