package com.aif31.pocket.ui.prototype

import androidx.compose.runtime.Immutable

@Immutable
data class PocketProgressPrototype(
    val id: String,
    val name: String,
    val budget: String,
    val rollover: String,
    val spending: String,
    val availability: String,
    val consumedFraction: Float,
    val consumedPercent: Int,
    val status: PocketStatusPrototype,
)

enum class PocketStatusPrototype {
    ON_TRACK,
    AT_RISK,
    EXHAUSTED,
}

@Immutable
data class DashboardPrototypeState(
    val periodLabel: String,
    val availability: String,
    val unallocated: String,
    val spendingStatus: String,
    val netSpending: String,
    val previousPeriodComparison: String,
    val averageDailySpending: String,
    val projection: String,
    val supportingMetricsExpanded: Boolean,
    val pockets: List<PocketProgressPrototype>,
)

@Immutable
data class SelectionOptionPrototype(
    val id: String,
    val label: String,
)

@Immutable
data class QuickExpensePrototypeState(
    val amount: String,
    val amountError: String?,
    val pockets: List<SelectionOptionPrototype>,
    val selectedPocketId: String?,
    val merchant: String,
    val paymentMethods: List<SelectionOptionPrototype>,
    val selectedPaymentMethodId: String?,
    val detailsExpanded: Boolean,
    val isRefund: Boolean,
    val currencyCode: String,
    val originalAmount: String,
    val conversionStatus: String,
    val dateTime: String,
    val note: String,
    val canSave: Boolean,
)

@Immutable
data class PocketsOverviewPrototypeState(
    val periodLabel: String,
    val allocated: String,
    val available: String,
    val pockets: List<PocketProgressPrototype>,
)

internal val prototypePockets = listOf(
    PocketProgressPrototype(
        id = "groceries",
        name = "Supermercado",
        budget = "SAR 1,200.00",
        rollover = "SAR 140.00",
        spending = "SAR 655.00",
        availability = "SAR 685.00",
        consumedFraction = 0.49f,
        consumedPercent = 49,
        status = PocketStatusPrototype.ON_TRACK,
    ),
    PocketProgressPrototype(
        id = "travel",
        name = "Viajes",
        budget = "SAR 800.00",
        rollover = "SAR 320.00",
        spending = "SAR 963.00",
        availability = "SAR 157.00",
        consumedFraction = 0.86f,
        consumedPercent = 86,
        status = PocketStatusPrototype.AT_RISK,
    ),
    PocketProgressPrototype(
        id = "university",
        name = "Universidad y materiales de investigación",
        budget = "SAR 600.00",
        rollover = "Sin rollover",
        spending = "SAR 600.00",
        availability = "SAR 0.00",
        consumedFraction = 1f,
        consumedPercent = 100,
        status = PocketStatusPrototype.EXHAUSTED,
    ),
)

internal val dashboardPrototypeState = DashboardPrototypeState(
    periodLabel = "25 ago – 24 sep",
    availability = "SAR 2,460.00",
    unallocated = "SAR 540.00 sin asignar",
    spendingStatus = "Tu ritmo está dentro del presupuesto",
    netSpending = "SAR 1,218.00 gastados",
    previousPeriodComparison = "SAR 184.00 menos que el periodo anterior",
    averageDailySpending = "SAR 87.00",
    projection = "SAR 2,610.00 estimados al cierre",
    supportingMetricsExpanded = false,
    pockets = prototypePockets,
)

internal val quickExpensePrototypeState = QuickExpensePrototypeState(
    amount = "84.50",
    amountError = null,
    pockets = listOf(
        SelectionOptionPrototype("groceries", "Supermercado"),
        SelectionOptionPrototype("restaurants", "Restaurantes y café"),
        SelectionOptionPrototype("transport", "Transporte"),
    ),
    selectedPocketId = "groceries",
    merchant = "Panda",
    paymentMethods = listOf(
        SelectionOptionPrototype("cash", "Efectivo"),
        SelectionOptionPrototype("card", "Tarjeta"),
    ),
    selectedPaymentMethodId = "card",
    detailsExpanded = false,
    isRefund = false,
    currencyCode = "SAR",
    originalAmount = "",
    conversionStatus = "Confirmado",
    dateTime = "23 ago 2026 · 18:42",
    note = "",
    canSave = true,
)

internal val pocketsPrototypeState = PocketsOverviewPrototypeState(
    periodLabel = "25 ago – 24 sep",
    allocated = "SAR 3,000.00 presupuestados",
    available = "SAR 2,460.00 disponibles",
    pockets = prototypePockets,
)
