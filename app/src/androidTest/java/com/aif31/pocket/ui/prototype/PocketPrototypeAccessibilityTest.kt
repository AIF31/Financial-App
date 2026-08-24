package com.aif31.pocket.ui.prototype

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.aif31.pocket.ui.PocketTheme
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class PocketPrototypeAccessibilityTest {
    @get:Rule
    val compose = createComposeRule(StandardTestDispatcher())

    @Before
    fun enableAccessibilityValidation() {
        compose.enableAccessibilityChecks()
    }

    @Test
    fun dashboard_passes_device_accessibility_validation() {
        compose.setContent {
            PocketTheme {
                ActionableDashboardPrototype(
                    state = dashboardPrototypeState,
                    onRecordExpense = {},
                    onManagePocket = {},
                )
            }
        }

        compose.onNodeWithText("Registrar gasto").performClick()
    }

    @Test
    fun quick_expense_and_expanded_details_pass_device_accessibility_validation() {
        var state by mutableStateOf(quickExpensePrototypeState)
        compose.setContent {
            PocketTheme {
                QuickExpensePrototype(
                    state = state,
                    onAmountChange = { state = state.copy(amount = it) },
                    onPocketSelected = { state = state.copy(selectedPocketId = it) },
                    onMerchantChange = { state = state.copy(merchant = it) },
                    onPaymentMethodSelected = { state = state.copy(selectedPaymentMethodId = it) },
                    onToggleDetails = { state = state.copy(detailsExpanded = !state.detailsExpanded) },
                    onSave = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("quick_expense_form").performScrollToNode(hasText("Más detalles"))
        compose.onNodeWithText("Más detalles").performClick()
        compose.onNodeWithTag("quick_expense_form").performScrollToNode(hasText("Fecha y hora"))
        compose.onNodeWithText("Fecha y hora").performClick()
    }

    @Test
    fun pockets_overview_and_focused_management_pass_device_accessibility_validation() {
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
        compose.onNodeWithContentDescription("Volver a Pockets").performClick()
    }
}
