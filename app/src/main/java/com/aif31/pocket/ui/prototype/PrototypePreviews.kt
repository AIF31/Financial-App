package com.aif31.pocket.ui.prototype

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.aif31.pocket.ui.PocketTheme

@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Preview(name = "Phone · Light", widthDp = 360, heightDp = 780, showBackground = true)
@Preview(
    name = "Phone · Dark",
    widthDp = 360,
    heightDp = 780,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Tablet · Light", widthDp = 800, heightDp = 1_200, showBackground = true)
@Preview(
    name = "Tablet · Dark",
    widthDp = 800,
    heightDp = 1_200,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
private annotation class PrototypeConfigurations

@PrototypeConfigurations
@Composable
private fun ActionableDashboardPreview() {
    var state by remember { mutableStateOf(dashboardPrototypeState) }
    PocketTheme {
        ActionableDashboardPrototype(
            state = state,
            onRecordExpense = {},
            onManagePocket = {},
            onToggleSupportingMetrics = {
                state = state.copy(supportingMetricsExpanded = !state.supportingMetricsExpanded)
            },
        )
    }
}

@Preview(
    name = "Dashboard · Low availability · Large text",
    widthDp = 360,
    heightDp = 780,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
private fun ActionableDashboardLowAvailabilityPreview() {
    PocketTheme {
        ActionableDashboardPrototype(
            state = dashboardPrototypeState.copy(
                availability = "SAR -120.00",
                unallocated = "Sin fondos por asignar",
                spendingStatus = "Tu ritmo supera los fondos del periodo",
                supportingMetricsExpanded = true,
            ),
            onRecordExpense = {},
            onManagePocket = {},
        )
    }
}

@PrototypeConfigurations
@Composable
private fun QuickExpensePreview() {
    var state by remember { mutableStateOf(quickExpensePrototypeState) }
    var returnedToDashboard by remember { mutableStateOf(false) }
    PocketTheme {
        if (returnedToDashboard) {
            ActionableDashboardPrototype(
                state = dashboardPrototypeState,
                onRecordExpense = { returnedToDashboard = false },
                onManagePocket = {},
            )
        } else {
            QuickExpensePrototype(
                state = state,
                onAmountChange = { state = state.copy(amount = it) },
                onPocketSelected = { state = state.copy(selectedPocketId = it) },
                onMerchantChange = { state = state.copy(merchant = it) },
                onPaymentMethodSelected = { state = state.copy(selectedPaymentMethodId = it) },
                onToggleDetails = { state = state.copy(detailsExpanded = !state.detailsExpanded) },
                onSave = { state = state.copy(saveFeedback = "Gasto guardado") },
                onBack = {
                    state = state.copy(saveFeedback = null)
                    returnedToDashboard = true
                },
                onMovementTypeSelected = { state = state.copy(movementType = it) },
                onCurrencySelected = { state = state.copy(currency = it) },
                onOriginalAmountChange = { state = state.copy(originalAmount = it) },
                onConversionStatusSelected = { state = state.copy(conversionStatus = it) },
                onDateTimeChange = { state = state.copy(dateTime = it) },
                onNoteChange = { state = state.copy(note = it) },
            )
        }
    }
}

@Preview(
    name = "Quick expense · Saved",
    widthDp = 360,
    heightDp = 780,
    showBackground = true,
)
@Composable
private fun QuickExpenseSavedPreview() {
    PocketTheme {
        QuickExpensePrototype(
            state = quickExpensePrototypeState.copy(saveFeedback = "Gasto guardado"),
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

@Preview(
    name = "Quick expense · Validation · Large text",
    widthDp = 360,
    heightDp = 780,
    fontScale = 1.5f,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun QuickExpenseValidationPreview() {
    PocketTheme {
        QuickExpensePrototype(
            state = quickExpensePrototypeState.copy(
                amount = "",
                amountError = "Escribe un importe válido",
                detailsExpanded = true,
                canSave = false,
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

@PrototypeConfigurations
@Composable
private fun PocketsOverviewPreview() {
    var state by remember { mutableStateOf(pocketsPrototypeState) }
    PocketTheme {
        PocketsOverviewPrototype(
            state = state,
            onAction = { action ->
                val pocketName = state.pockets
                    .firstOrNull { it.id == action.pocketId }
                    ?.name
                    .orEmpty()
                state = when (action) {
                    PocketManagementAction.Create -> state.copy(
                        managementFeedback = "Abrir creación de Pocket",
                    )
                    PocketManagementAction.CloseManagement -> state.copy(
                        managingPocketId = null,
                        managementFeedback = null,
                    )
                    is PocketManagementAction.OpenManagement -> state.copy(
                        managingPocketId = action.pocketId,
                        managementFeedback = null,
                    )
                    is PocketManagementAction.Move -> state.copy(
                        managementFeedback = "Mover $pocketName ${action.direction.spanishLabel()}",
                    )
                    is PocketManagementAction.ViewDetails -> state.copy(
                        managementFeedback = "Abrir detalle de $pocketName",
                    )
                    is PocketManagementAction.Edit -> state.copy(
                        managementFeedback = "Abrir edición de $pocketName",
                    )
                    is PocketManagementAction.Archive -> state.copy(
                        managementFeedback = "Confirmar archivo de $pocketName; aún no se modificó",
                    )
                    is PocketManagementAction.SetAllocation -> state.copy(
                        managementFeedback = "Abrir asignación de $pocketName",
                    )
                    is PocketManagementAction.SetRollover -> state.copy(
                        managementFeedback = "Confirmar cambio de rollover de $pocketName",
                    )
                }
            },
        )
    }
}

@Preview(
    name = "Pockets · Long labels · Large text",
    widthDp = 360,
    heightDp = 780,
    fontScale = 1.5f,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PocketsLargeTextPreview() {
    PocketTheme {
        PocketsOverviewPrototype(
            state = pocketsPrototypeState.copy(
                allocated = "SAR 600.00 presupuestados",
                available = "SAR 0.00 disponibles",
                pockets = listOf(prototypePockets.last()),
            ),
            onAction = {},
        )
    }
}

@Preview(
    name = "Pockets · Empty",
    widthDp = 360,
    heightDp = 780,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
private fun EmptyPocketsOverviewPreview() {
    PocketTheme {
        PocketsOverviewPrototype(
            state = pocketsPrototypeState.copy(
                allocated = "SAR 0.00 presupuestados",
                available = "SAR 0.00 disponibles",
                pockets = emptyList(),
            ),
            onAction = {},
        )
    }
}

private fun PocketMoveDirection.spanishLabel(): String = when (this) {
    PocketMoveDirection.UP -> "arriba"
    PocketMoveDirection.DOWN -> "abajo"
}
