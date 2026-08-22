package com.aif31.pocket.ui.prototype

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val PrototypeContentMaxWidth = 1_040.dp

@Composable
fun ActionableDashboardPrototype(
    state: DashboardPrototypeState,
    onRecordExpense: () -> Unit,
    onManagePocket: (String) -> Unit,
    modifier: Modifier = Modifier,
    onToggleSupportingMetrics: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = PrototypeContentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 32.dp else 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Inicio", style = MaterialTheme.typography.headlineMedium)
                Text(
                    state.periodLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AvailabilityHero(
                state = state,
                wide = wide,
                onRecordExpense = onRecordExpense,
            )

            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    SpendingStatus(state, Modifier.weight(1f))
                    PocketProgressSection(state.pockets, onManagePocket, Modifier.weight(1.35f))
                }
            } else {
                SpendingStatus(state)
                PocketProgressSection(state.pockets, onManagePocket)
            }

            SupportingMetrics(
                state = state,
                onToggle = onToggleSupportingMetrics,
            )
        }
    }
}

@Composable
private fun AvailabilityHero(
    state: DashboardPrototypeState,
    wide: Boolean,
    onRecordExpense: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvailabilityCopy(state, Modifier.weight(1f))
                Button(onClick = onRecordExpense) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Registrar gasto")
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                AvailabilityCopy(state)
                Button(onClick = onRecordExpense, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Registrar gasto")
                }
            }
        }
    }
}

@Composable
private fun AvailabilityCopy(state: DashboardPrototypeState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Disponibilidad de Pockets", style = MaterialTheme.typography.titleMedium)
        Text(
            state.availability,
            style = MaterialTheme.typography.displaySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
        Text(state.unallocated, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SpendingStatus(state: DashboardPrototypeState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ritmo del periodo", style = MaterialTheme.typography.titleLarge)
        Text(state.spendingStatus, style = MaterialTheme.typography.titleMedium)
        Text(
            state.netSpending,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            state.previousPeriodComparison,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PocketProgressSection(
    pockets: List<PocketProgressPrototype>,
    onManagePocket: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visiblePockets = pockets.sortedBy { pocket ->
        when (pocket.status) {
            PocketStatusPrototype.EXHAUSTED -> 0
            PocketStatusPrototype.AT_RISK -> 1
            PocketStatusPrototype.ON_TRACK -> 2
        }
    }.take(3)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pockets que necesitan atención", style = MaterialTheme.typography.titleLarge)
        if (pockets.isEmpty()) {
            Text(
                "Aún no hay Pockets para este periodo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            visiblePockets.forEachIndexed { index, pocket ->
                DashboardPocketRow(pocket, onManagePocket)
                if (index != visiblePockets.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DashboardPocketRow(
    pocket: PocketProgressPrototype,
    onManagePocket: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(pocket.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${pocket.availability} disponibles",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onManagePocket(pocket.id) }) { Text("Gestionar") }
        }
        PocketStatus(pocket)
        PocketProgressIndicator(pocket)
    }
}

@Composable
private fun SupportingMetrics(
    state: DashboardPrototypeState,
    onToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onToggle) {
            Text(if (state.supportingMetricsExpanded) "Ocultar proyección" else "Ver proyección y promedio")
            Icon(
                imageVector = if (state.supportingMetricsExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = null,
            )
        }
        AnimatedVisibility(visible = state.supportingMetricsExpanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SupportingMetric("Promedio diario", state.averageDailySpending)
                    SupportingMetric("Proyección", state.projection)
                }
            }
        }
    }
}

@Composable
private fun SupportingMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun QuickExpensePrototype(
    state: QuickExpensePrototypeState,
    onAmountChange: (String) -> Unit,
    onPocketSelected: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onPaymentMethodSelected: (String?) -> Unit,
    onToggleDetails: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onMovementTypeSelected: (MovementTypePrototype) -> Unit = {},
    onCurrencySelected: (MovementCurrencyPrototype) -> Unit = {},
    onOriginalAmountChange: (String) -> Unit = {},
    onConversionStatusSelected: (ConversionStatusPrototype) -> Unit = {},
    onDateTimeChange: (String) -> Unit = {},
    onNoteChange: (String) -> Unit = {},
) {
    val amountFocusRequester = remember { FocusRequester() }
    LaunchedEffect(amountFocusRequester) {
        withFrameNanos { }
        amountFocusRequester.requestFocus()
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                    Text(
                        if (state.movementType == MovementTypePrototype.REFUND) "Nueva devolución" else "Nuevo gasto",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 3.dp) {
                Button(
                    onClick = onSave,
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(if (state.movementType == MovementTypePrototype.REFUND) "Guardar devolución" else "Guardar gasto")
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .testTag("quick_expense_form"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                state.saveFeedback?.let { message ->
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Text(message, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = onAmountChange,
                        label = { Text("Importe SAR") },
                        isError = state.amountError != null,
                        supportingText = state.amountError?.let { message -> { Text(message) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(amountFocusRequester)
                            .testTag("expense_amount")
                            .semantics { state.amountError?.let { error(it) } },
                    )
                }
                item {
                    SelectionSection(
                        title = "Pocket",
                        options = state.pockets,
                        selectedId = state.selectedPocketId,
                        onSelected = onPocketSelected,
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.merchant,
                        onValueChange = onMerchantChange,
                        label = { Text("Comercio (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    SelectionSection(
                        title = "Método de pago (opcional)",
                        options = listOf(SelectionOptionPrototype("", "Ninguno")) + state.paymentMethods,
                        selectedId = state.selectedPaymentMethodId.orEmpty(),
                        onSelected = { onPaymentMethodSelected(it.ifEmpty { null }) },
                    )
                }
                item {
                    OutlinedButton(onClick = onToggleDetails, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.detailsExpanded) "Ocultar detalles" else "Más detalles")
                        Icon(
                            imageVector = if (state.detailsExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = null,
                        )
                    }
                }
                item {
                    AnimatedVisibility(visible = state.detailsExpanded) {
                        AdvancedExpenseDetails(
                            state = state,
                            onMovementTypeSelected = onMovementTypeSelected,
                            onCurrencySelected = onCurrencySelected,
                            onOriginalAmountChange = onOriginalAmountChange,
                            onConversionStatusSelected = onConversionStatusSelected,
                            onDateTimeChange = onDateTimeChange,
                            onNoteChange = onNoteChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionSection(
    title: String,
    options: List<SelectionOptionPrototype>,
    selectedId: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = selectedId == option.id,
                        onClick = { onSelected(option.id) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selectedId == option.id, onClick = null)
                Text(option.label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun AdvancedExpenseDetails(
    state: QuickExpensePrototypeState,
    onMovementTypeSelected: (MovementTypePrototype) -> Unit,
    onCurrencySelected: (MovementCurrencyPrototype) -> Unit,
    onOriginalAmountChange: (String) -> Unit,
    onConversionStatusSelected: (ConversionStatusPrototype) -> Unit,
    onDateTimeChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Detalles del movimiento", style = MaterialTheme.typography.titleMedium)
            SelectionSection(
                title = "Tipo",
                options = listOf(
                    SelectionOptionPrototype(MovementTypePrototype.EXPENSE.name, "Gasto"),
                    SelectionOptionPrototype(MovementTypePrototype.REFUND.name, "Devolución"),
                ),
                selectedId = state.movementType.name,
                onSelected = { onMovementTypeSelected(MovementTypePrototype.valueOf(it)) },
            )
            SelectionSection(
                title = "Moneda original",
                options = MovementCurrencyPrototype.entries.map { SelectionOptionPrototype(it.name, it.code) },
                selectedId = state.currency.name,
                onSelected = { onCurrencySelected(MovementCurrencyPrototype.valueOf(it)) },
            )
            if (state.currency != MovementCurrencyPrototype.SAR) {
                OutlinedTextField(
                    value = state.originalAmount,
                    onValueChange = onOriginalAmountChange,
                    label = { Text("Importe original ${state.currency.code}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectionSection(
                    title = "Estado de conversión",
                    options = listOf(
                        SelectionOptionPrototype(ConversionStatusPrototype.ESTIMATED.name, "Estimado"),
                        SelectionOptionPrototype(ConversionStatusPrototype.CONFIRMED.name, "Confirmado"),
                    ),
                    selectedId = state.conversionStatus.name,
                    onSelected = { onConversionStatusSelected(ConversionStatusPrototype.valueOf(it)) },
                )
            }
            OutlinedTextField(
                value = state.dateTime,
                onValueChange = onDateTimeChange,
                label = { Text("Fecha y hora") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text("Nota (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun PocketsOverviewPrototype(
    state: PocketsOverviewPrototypeState,
    onAction: (PocketManagementAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = PrototypeContentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 32.dp else 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pockets", style = MaterialTheme.typography.headlineMedium)
                    Text(state.periodLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { onAction(PocketManagementAction.Create) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Crear Pocket")
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(state.available, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                    Text(state.allocated, style = MaterialTheme.typography.bodyLarge)
                }
            }

            state.managementFeedback?.let { message ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(message, modifier = Modifier.padding(16.dp))
                }
            }

            if (state.pockets.isEmpty()) {
                EmptyPockets { onAction(PocketManagementAction.Create) }
            } else {
                AdaptivePocketCards(
                    pockets = state.pockets,
                    wide = wide,
                    onAction = onAction,
                )
                state.managingPocketId
                    ?.let { id -> state.pockets.firstOrNull { it.id == id } }
                    ?.let { pocket -> PocketManagementSurface(pocket, onAction) }
            }
        }
    }
}

@Composable
private fun EmptyPockets(onCreatePocket: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Aún no hay Pockets", style = MaterialTheme.typography.titleLarge)
        Text("Crea uno para asignar presupuesto y seguir su disponibilidad.")
        OutlinedButton(onClick = onCreatePocket) { Text("Crear primer Pocket") }
    }
}

@Composable
private fun AdaptivePocketCards(
    pockets: List<PocketProgressPrototype>,
    wide: Boolean,
    onAction: (PocketManagementAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        pockets.withIndex().toList().chunked(if (wide) 2 else 1).forEach { rowPockets ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rowPockets.forEach { indexedPocket ->
                    val pocket = indexedPocket.value
                    PocketManagementCard(
                        pocket = pocket,
                        onAction = onAction,
                        canMoveUp = indexedPocket.index > 0,
                        canMoveDown = indexedPocket.index < pockets.lastIndex,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (wide && rowPockets.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PocketManagementCard(
    pocket: PocketProgressPrototype,
    onAction: (PocketManagementAction) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(pocket.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Row {
                    IconButton(
                        onClick = { onAction(PocketManagementAction.Move(pocket.id, PocketMoveDirection.UP)) },
                        enabled = canMoveUp,
                        modifier = Modifier.semantics { contentDescription = "Mover ${pocket.name} arriba" },
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null)
                    }
                    IconButton(
                        onClick = { onAction(PocketManagementAction.Move(pocket.id, PocketMoveDirection.DOWN)) },
                        enabled = canMoveDown,
                        modifier = Modifier.semantics { contentDescription = "Mover ${pocket.name} abajo" },
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null)
                    }
                }
            }
            PocketStatus(pocket)
            PocketProgressIndicator(pocket)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PocketValue("Gastado", pocket.spending, Modifier.weight(1f))
                PocketValue("Disponible", pocket.availability, Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = { onAction(PocketManagementAction.Open(pocket.id)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Gestionar Pocket")
            }
        }
    }
}

@Composable
private fun PocketManagementSurface(
    pocket: PocketProgressPrototype,
    onAction: (PocketManagementAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Gestionar ${pocket.name}", style = MaterialTheme.typography.titleLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PocketValue("Presupuesto", pocket.budget, Modifier.weight(1f))
                PocketValue("Rollover", pocket.rollover, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aplicar rollover")
                Switch(
                    checked = pocket.rolloverEnabled,
                    onCheckedChange = {
                        onAction(PocketManagementAction.SetRollover(pocket.id, it))
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (pocket.rolloverEnabled) {
                            "Desactivar rollover de ${pocket.name}"
                        } else {
                            "Activar rollover de ${pocket.name}"
                        }
                    },
                )
            }
            OutlinedButton(
                onClick = { onAction(PocketManagementAction.SetAllocation(pocket.id)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Asignar presupuesto a ${pocket.name}" },
            ) {
                Text("Asignar presupuesto")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onAction(PocketManagementAction.View(pocket.id)) }) { Text("Ver") }
                TextButton(onClick = { onAction(PocketManagementAction.Edit(pocket.id)) }) { Text("Editar") }
                TextButton(onClick = { onAction(PocketManagementAction.Archive(pocket.id)) }) { Text("Archivar") }
            }
        }
    }
}

@Composable
private fun PocketStatus(pocket: PocketProgressPrototype, modifier: Modifier = Modifier) {
    val presentation = pocketStatusPresentation(pocket)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(presentation.icon, contentDescription = null, tint = presentation.color, modifier = Modifier.size(20.dp))
        Text(presentation.label, color = presentation.color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PocketProgressIndicator(
    pocket: PocketProgressPrototype,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { pocket.consumedFraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp),
        color = pocketStatusPresentation(pocket).color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

private data class PocketStatusPresentation(
    val icon: ImageVector,
    val label: String,
    val color: Color,
)

@Composable
private fun pocketStatusPresentation(pocket: PocketProgressPrototype): PocketStatusPresentation = when (pocket.status) {
    PocketStatusPrototype.ON_TRACK -> PocketStatusPresentation(
        icon = Icons.Default.CheckCircle,
        label = "En curso · ${pocket.consumedPercent}% consumido",
        color = MaterialTheme.colorScheme.primary,
    )
    PocketStatusPrototype.AT_RISK -> PocketStatusPresentation(
        icon = Icons.Default.Warning,
        label = "En riesgo · ${pocket.consumedPercent}% consumido",
        color = MaterialTheme.colorScheme.tertiary,
    )
    PocketStatusPrototype.EXHAUSTED -> PocketStatusPresentation(
        icon = Icons.Default.Error,
        label = "Agotado · ${pocket.consumedPercent}% consumido",
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun PocketValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
    }
}
