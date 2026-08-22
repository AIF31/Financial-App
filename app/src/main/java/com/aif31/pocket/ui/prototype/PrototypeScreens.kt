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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pockets que necesitan atención", style = MaterialTheme.typography.titleLarge)
        if (pockets.isEmpty()) {
            Text(
                "Aún no hay Pockets para este periodo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            pockets.take(3).forEachIndexed { index, pocket ->
                DashboardPocketRow(pocket, onManagePocket)
                if (index != pockets.take(3).lastIndex) HorizontalDivider()
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

@OptIn(ExperimentalMaterial3Api::class)
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
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Nuevo gasto") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
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
                    Text(if (state.isRefund) "Guardar devolución" else "Guardar gasto")
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
                        AdvancedExpenseDetails(state)
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun AdvancedExpenseDetails(state: QuickExpensePrototypeState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Detalles del movimiento", style = MaterialTheme.typography.titleMedium)
            ReadOnlyDetail("Tipo", if (state.isRefund) "Devolución" else "Gasto")
            ReadOnlyDetail("Moneda original", state.currencyCode)
            if (state.currencyCode != "SAR") {
                ReadOnlyDetail("Importe original", state.originalAmount)
                ReadOnlyDetail("Estado de conversión", state.conversionStatus)
            }
            ReadOnlyDetail("Fecha y hora", state.dateTime)
            ReadOnlyDetail("Nota", state.note.ifBlank { "Sin nota" })
        }
    }
}

@Composable
private fun ReadOnlyDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PocketsOverviewPrototype(
    state: PocketsOverviewPrototypeState,
    onCreatePocket: () -> Unit,
    onPocketSelected: (String) -> Unit,
    onMovePocket: (String, Int) -> Unit,
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
                Button(onClick = onCreatePocket) {
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

            if (state.pockets.isEmpty()) {
                EmptyPockets(onCreatePocket)
            } else {
                AdaptivePocketCards(
                    pockets = state.pockets,
                    wide = wide,
                    onPocketSelected = onPocketSelected,
                    onMovePocket = onMovePocket,
                )
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
    onPocketSelected: (String) -> Unit,
    onMovePocket: (String, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        pockets.chunked(if (wide) 2 else 1).forEach { rowPockets ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rowPockets.forEach { pocket ->
                    PocketManagementCard(
                        pocket = pocket,
                        onPocketSelected = onPocketSelected,
                        onMovePocket = onMovePocket,
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
    onPocketSelected: (String) -> Unit,
    onMovePocket: (String, Int) -> Unit,
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
                        onClick = { onMovePocket(pocket.id, -1) },
                        modifier = Modifier.semantics { contentDescription = "Mover ${pocket.name} arriba" },
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null)
                    }
                    IconButton(
                        onClick = { onMovePocket(pocket.id, 1) },
                        modifier = Modifier.semantics { contentDescription = "Mover ${pocket.name} abajo" },
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null)
                    }
                }
            }
            PocketStatus(pocket)
            PocketProgressIndicator(pocket)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PocketValue("Presupuesto", pocket.budget, Modifier.weight(1f))
                PocketValue("Rollover", pocket.rollover, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PocketValue("Gastado", pocket.spending, Modifier.weight(1f))
                PocketValue("Disponible", pocket.availability, Modifier.weight(1f))
            }
            TextButton(onClick = { onPocketSelected(pocket.id) }, modifier = Modifier.align(Alignment.End)) {
                Text("Gestionar Pocket")
            }
        }
    }
}

@Composable
private fun PocketStatus(pocket: PocketProgressPrototype) {
    val (icon, label) = when (pocket.status) {
        PocketStatusPrototype.ON_TRACK -> Icons.Default.CheckCircle to "En curso · ${pocket.consumedPercent}% consumido"
        PocketStatusPrototype.AT_RISK -> Icons.Default.Warning to "En riesgo · ${pocket.consumedPercent}% consumido"
        PocketStatusPrototype.EXHAUSTED -> Icons.Default.Error to "Agotado · ${pocket.consumedPercent}% consumido"
    }
    val color = pocketStatusColor(pocket.status)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PocketProgressIndicator(pocket: PocketProgressPrototype) {
    LinearProgressIndicator(
        progress = { pocket.consumedFraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        color = pocketStatusColor(pocket.status),
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

@Composable
private fun pocketStatusColor(status: PocketStatusPrototype): Color = when (status) {
    PocketStatusPrototype.ON_TRACK -> MaterialTheme.colorScheme.primary
    PocketStatusPrototype.AT_RISK -> MaterialTheme.colorScheme.tertiary
    PocketStatusPrototype.EXHAUSTED -> MaterialTheme.colorScheme.error
}

@Composable
private fun PocketValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
    }
}
