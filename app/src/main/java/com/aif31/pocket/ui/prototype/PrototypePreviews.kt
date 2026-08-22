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
            onMovementTypeSelected = { state = state.copy(movementType = it) },
            onCurrencySelected = { state = state.copy(currency = it) },
            onOriginalAmountChange = { state = state.copy(originalAmount = it) },
            onConversionStatusSelected = { state = state.copy(conversionStatus = it) },
            onDateTimeChange = { state = state.copy(dateTime = it) },
            onNoteChange = { state = state.copy(note = it) },
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
            onCreatePocket = {
                if (state.pockets.none { it.id == "new-pocket" }) {
                    state = state.copy(
                        pockets = state.pockets + prototypePockets.first().copy(
                            id = "new-pocket",
                            name = "Nuevo Pocket",
                            budget = "SAR 0.00",
                            rollover = "Sin rollover",
                            spending = "SAR 0.00",
                            availability = "SAR 0.00",
                            consumedFraction = 0f,
                            consumedPercent = 0,
                            rolloverEnabled = false,
                        ),
                    )
                }
            },
            onPocketSelected = {},
            onMovePocket = { _, _ -> },
            onEditPocket = { id ->
                state = state.copy(pockets = state.pockets.map { if (it.id == id) it.copy(name = "${it.name} · editado") else it })
            },
            onArchivePocket = { id -> state = state.copy(pockets = state.pockets.filterNot { it.id == id }) },
            onSetAllocation = { id ->
                state = state.copy(pockets = state.pockets.map { if (it.id == id) it.copy(budget = "SAR 900.00") else it })
            },
            onToggleRollover = { id, enabled ->
                state = state.copy(
                    pockets = state.pockets.map {
                        if (it.id == id) it.copy(
                            rolloverEnabled = enabled,
                            rollover = if (enabled) "SAR 0.00" else "Sin rollover",
                        ) else it
                    },
                )
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
            state = pocketsPrototypeState.copy(pockets = listOf(prototypePockets.last())),
            onCreatePocket = {},
            onPocketSelected = {},
            onMovePocket = { _, _ -> },
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
            onCreatePocket = {},
            onPocketSelected = {},
            onMovePocket = { _, _ -> },
        )
    }
}
