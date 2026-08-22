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
    val rolloverEnabled: Boolean,
)

enum class PocketStatusPrototype {
    ON_TRACK,
    AT_RISK,
    EXHAUSTED,
}

enum class MovementTypePrototype {
    EXPENSE,
    REFUND,
}

enum class MovementCurrencyPrototype(val code: String) {
    SAR("SAR"),
    USD("USD"),
    MXN("MXN"),
}

enum class ConversionStatusPrototype {
    ESTIMATED,
    CONFIRMED,
}

enum class PocketMoveDirection {
    UP,
    DOWN,
}

sealed interface PocketManagementAction {
    data object Create : PocketManagementAction

    data class Open(val pocketId: String) : PocketManagementAction

    data class View(val pocketId: String) : PocketManagementAction

    data class Move(
        val pocketId: String,
        val direction: PocketMoveDirection,
    ) : PocketManagementAction

    data class Edit(val pocketId: String) : PocketManagementAction

    data class Archive(val pocketId: String) : PocketManagementAction

    data class SetAllocation(val pocketId: String) : PocketManagementAction

    data class SetRollover(
        val pocketId: String,
        val enabled: Boolean,
    ) : PocketManagementAction
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
    val movementType: MovementTypePrototype,
    val currency: MovementCurrencyPrototype,
    val originalAmount: String,
    val conversionStatus: ConversionStatusPrototype,
    val dateTime: String,
    val note: String,
    val canSave: Boolean,
    val saveFeedback: String? = null,
)

@Immutable
data class PocketsOverviewPrototypeState(
    val periodLabel: String,
    val allocated: String,
    val available: String,
    val pockets: List<PocketProgressPrototype>,
    val managingPocketId: String? = null,
    val managementFeedback: String? = null,
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
        rolloverEnabled = true,
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
        rolloverEnabled = true,
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
        rolloverEnabled = false,
    ),
)

internal val dashboardPrototypeState = DashboardPrototypeState(
    periodLabel = "25 ago – 24 sep",
    availability = "SAR 842.00",
    unallocated = "SAR 540.00 sin asignar",
    spendingStatus = "Tu ritmo supera los fondos del periodo",
    netSpending = "SAR 2,218.00 gastados",
    previousPeriodComparison = "SAR 184.00 menos que el periodo anterior",
    averageDailySpending = "SAR 158.43",
    projection = "SAR 4,911.29 estimados al cierre",
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
    movementType = MovementTypePrototype.EXPENSE,
    currency = MovementCurrencyPrototype.SAR,
    originalAmount = "",
    conversionStatus = ConversionStatusPrototype.CONFIRMED,
    dateTime = "23 ago 2026 · 18:42",
    note = "",
    canSave = true,
)

internal val pocketsPrototypeState = PocketsOverviewPrototypeState(
    periodLabel = "25 ago – 24 sep",
    allocated = "SAR 2,600.00 presupuestados",
    available = "SAR 842.00 disponibles",
    pockets = prototypePockets,
)
