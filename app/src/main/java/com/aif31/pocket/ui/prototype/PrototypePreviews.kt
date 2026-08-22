package com.aif31.pocket.ui.prototype

import android.content.res.Configuration
import androidx.compose.runtime.Composable
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
    PocketTheme {
        ActionableDashboardPrototype(
            state = dashboardPrototypeState,
            onRecordExpense = {},
            onManagePocket = {},
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
    PocketTheme {
        PocketsOverviewPrototype(
            state = pocketsPrototypeState,
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
