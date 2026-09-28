package com.aif31.pocket

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Insights
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.*
import com.aif31.pocket.domain.Money
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.domain.sumMoneyExact
import com.aif31.pocket.settings.*
import com.aif31.pocket.ui.*
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun PocketsScreen(
    state: LedgerState,
    ledger: PocketLedger,
    padding: PaddingValues,
    onComparePeriod: (periodId: String) -> Unit = {},
) {
    var selected by remember { mutableStateOf<PocketPeriodSummary?>(null) }
    var selectedPeriodId by rememberSaveable(state.currentPeriod?.id) { mutableStateOf(state.currentPeriod?.id) }
    var editing by remember { mutableStateOf<PocketPeriodSummary?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selectedPeriod = state.periods.firstOrNull { it.id == selectedPeriodId } ?: state.currentPeriod
    val selectedCurrency = selectedPeriod?.accountingCurrency ?: SupportedCurrency.SAR
    fun money(minor: Long): String = MoneyText.format(minor, selectedCurrency)
    val shownPockets = state.pocketSummariesByPeriod[selectedPeriodId].orEmpty()
    val archivedPockets = state.pocketCatalog.filter { it.archived }
    val isHistorical = selectedPeriod?.id != null && selectedPeriod.id != state.currentPeriod?.id
    val activePockets = shownPockets.filterNot { summary ->
        summary.retiredThisPeriod || (!isHistorical && summary.pocket.archived)
    }
    val retiredPockets = shownPockets.filter { it.retiredThisPeriod }
    val allocatedMinor = shownPockets.map { it.budgetMinor }.sumMoneyExact()
    val availableMinor = shownPockets.map { it.availabilityMinor }.sumMoneyExact()
    val unallocatedForPeriodMinor = selectedPeriod?.id
        ?.let(state.unallocatedMinorByPeriod::get)
        ?: state.unallocatedMinor

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("pockets_list"),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Pockets", style = MaterialTheme.typography.headlineMedium)
        }
        if (isHistorical) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Vista histórica · Solo lectura", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Moneda del periodo · ${selectedCurrency.name}",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
        if (selectedPeriod?.id == state.currentPeriod?.id && selectedPeriod?.needsReview == true) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Revisa el presupuesto de este periodo", style = MaterialTheme.typography.titleMedium)
                        Text("Comprueba las asignaciones y el rollover antes de continuar.")
                        Button(onClick = {
                            scope.launch { ledger.execute(LedgerCommand.MarkPeriodReviewed(selectedPeriod.id)) }
                        }) {
                            Text("Marcar periodo como revisado")
                        }
                    }
                }
            }
        }
        item {
            val periodListState = rememberLazyListState(
                initialFirstVisibleItemIndex = state.periods.indexOfFirst { it.id == selectedPeriodId }.coerceAtLeast(0),
            )
            LazyRow(
                state = periodListState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("period_selector"),
            ) {
                items(state.periods, key = { it.id }) { period ->
                    val isSelected = selectedPeriodId == period.id
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .testTag("period_${period.id}")
                            .clip(MaterialTheme.shapes.extraLarge)
                            .selectable(selected = isSelected, role = Role.Tab) { selectedPeriodId = period.id },
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                formatPeriodRange(period.start, period.endExclusive),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                listOfNotNull(
                                    "Actual".takeIf { period.id == state.currentPeriod?.id },
                                    period.accountingCurrency.name,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (period.isTransition) {
                                Text(
                                    "Periodo de transición",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PocketSummaryMetric("Asignado", money(allocatedMinor), Modifier.weight(1f))
                        VerticalDivider(Modifier.height(64.dp))
                        PocketSummaryMetric("Disponible", money(availableMinor), Modifier.weight(1f))
                    }
                    Text(
                        "${money(unallocatedForPeriodMinor)} sin asignar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    selectedPeriod?.let { period ->
                        PeriodDetailsSection(
                            state = state,
                            periodId = period.id,
                            initiallyExpanded = isHistorical,
                            onCompare = { onComparePeriod(period.id) },
                        )
                    }
                }
            }
        }
        if (!isHistorical) {
            item {
                Button(
                    onClick = { creating = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Crear Pocket", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pockets activos", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (isHistorical) "Toca uno para ver el detalle" else "Toca uno para ver y administrar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(activePockets, key = { it.pocket.id }) { summary ->
            val status = summary.budgetStatus
            val statusColor = status.presentation().indicator
            Card(
                Modifier
                    .fillMaxWidth()
                    .testTag("pocket_${summary.pocket.name}")
                    .clickable { selected = summary },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    PocketArtworkPlate(summary.pocket.iconKey, plateSize = 56.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(summary.pocket.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (!isHistorical) {
                                IconButton(
                                    onClick = { selected = summary },
                                    modifier = Modifier.semantics { contentDescription = "Gestionar ${summary.pocket.name}" },
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = null)
                                }
                            }
                        }
                        Text(
                            availableText(summary.availabilityMinor, selectedCurrency),
                            color = if (summary.availabilityMinor < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LinearProgressIndicator(
                                progress = { (summary.consumedPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.weight(1f).height(6.dp),
                                color = statusColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                drawStopIndicator = {},
                            )
                            Text("${summary.consumedPercent}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Presupuesto ${money(summary.budgetMinor)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            PocketStatusBadge(status)
                        }
                    }
                }
            }
        }
        if (activePockets.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (isHistorical) "No hubo Pockets activos en este periodo" else "Aún no hay Pockets activos",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (!isHistorical) {
                            Text("Crea un Pocket para asignar fondos y seguir su disponibilidad.")
                        }
                    }
                }
            }
        }
        if (retiredPockets.isNotEmpty()) {
            item { Text("Retirado este periodo", style = MaterialTheme.typography.titleMedium) }
            items(retiredPockets, key = { "retired-${it.pocket.id}" }) { summary ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .testTag("retired_${summary.pocket.name}")
                        .clickable { selected = summary },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(summary.pocket.name, style = MaterialTheme.typography.titleMedium)
                        Text("Gastos ${money(summary.expenseMinor)}")
                        Text("Reembolsos ${money(summary.refundMinor)}")
                        Text("Movimientos conservados · ${money(summary.rolloverReleasedMinor)} de rollover liberado")
                    }
                }
            }
        }
        if (!isHistorical && archivedPockets.isNotEmpty()) {
            item { Text("Archivados", style = MaterialTheme.typography.titleMedium) }
            items(archivedPockets, key = { "archived-${it.id}" }) { pocket ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(pocket.name)
                    TextButton(onClick = { scope.launch { ledger.execute(LedgerCommand.ArchivePocket(pocket.id, false)) } }) {
                        Text("Restaurar")
                    }
                }
            }
        }
    }
    selected?.let { summary ->
        PocketManagementDialog(
            state = state,
            periodId = selectedPeriodId ?: state.currentPeriod!!.id,
            summary = summary,
            ledger = ledger,
            readOnly = isHistorical,
            currency = selectedCurrency,
            onEdit = { selected = null; editing = summary },
            onDismiss = { selected = null },
        )
    }
    if (creating || editing != null) {
        PocketEditorDialog(editing, ledger) { creating = false; editing = null }
    }
}

@Composable
private fun PocketSummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PeriodDetailsSection(
    state: LedgerState,
    periodId: String,
    initiallyExpanded: Boolean,
    onCompare: () -> Unit,
) {
    var expanded by rememberSaveable(periodId) { mutableStateOf(initiallyExpanded) }
    val insights = remember(state, periodId) { PeriodInsights.of(state, periodId) } ?: return
    val comparison = remember(state, periodId) {
        PeriodComparison.previousPeriodId(state, periodId)?.let { PeriodComparison.of(state, periodId, it) }
    }
    val currency = insights.accountingCurrency
    fun money(minor: Long) = MoneyText.format(minor, currency)
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider()
        DisclosureRow(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            label = if (expanded) "Ocultar detalles del periodo" else "Ver detalles del periodo",
        )
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MetricRow("Fondos nuevos", money(insights.newFundsMinor))
                MetricRow(
                    "Gasto neto",
                    money(insights.netSpendMinor),
                    supporting = "${insights.expenseCount} gastos · ${money(insights.refundMinor)} en devoluciones",
                )
                SpendPaceMetrics(insights)
                insights.largestExpense?.let { movement ->
                    MetricRow("Mayor gasto", money(movement.accountingAmountMinor), supporting = movement.merchantOrPocket)
                }
                val baseline = comparison?.convertedBaseline
                when {
                    comparison == null -> ComparisonNote("No hay un periodo anterior para comparar.")
                    baseline == null -> ComparisonNote("El periodo anterior usa otra moneda sin un tipo congelado; ábrelo en Comparar para verlos por separado.")
                    else -> MetricRow(
                        "Frente al periodo anterior",
                        baseline.averageDailySpendMinor?.let { "${money(it)}/día" } ?: "—",
                        supporting = baseline.averageDailyDeltaMinor?.let { spendDeltaText(it, baseline.averageDailyDeltaPercent, currency) },
                    )
                }
                if (insights.elapsedDays > 0) {
                    CumulativeSpendChart(
                        current = insights.cumulativeNetSpendByDayMinor,
                        baseline = baseline?.cumulativeNetSpendByDayMinor,
                        totalDays = insights.totalDays,
                        availableBudgetMinor = insights.availableBudgetMinor,
                        currency = currency,
                        currentLabel = "Este periodo",
                        footnote = insights.chartCutoffNote(),
                    )
                }
                // Any other period can be chosen on the comparison screen, even without a predecessor.
                if (state.periods.size > 1) {
                    OutlinedButton(onClick = onCompare, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Comparar periodos")
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun PocketEditorDialog(
    existing: PocketPeriodSummary?,
    ledger: PocketLedger,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(existing?.pocket?.id) { mutableStateOf(existing?.pocket?.name.orEmpty()) }
    var rollover by rememberSaveable(existing?.pocket?.id) { mutableStateOf(existing?.pocket?.rolloverEnabled ?: false) }
    var selectedIcon by rememberSaveable(existing?.pocket?.id) { mutableStateOf(existing?.pocket?.iconKey ?: PocketIconKey.SUPERMARKET) }
    var error by rememberSaveable(existing?.pocket?.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Crear Pocket" else "Editar Pocket") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().testTag("pocket_name"),
                )
                Text("Elige un icono", style = MaterialTheme.typography.titleSmall)
                pocketIconOptions.chunked(3).forEach { options ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        options.forEach { option ->
                            val isSelected = selectedIcon == option.key
                            OutlinedButton(
                                onClick = { selectedIcon = option.key },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .testTag("pocket_icon_${option.key.name.lowercase(Locale.ROOT)}")
                                    .semantics {
                                        contentDescription = "Icono ${option.label}${if (isSelected) ", seleccionado" else ""}"
                                    },
                                contentPadding = PaddingValues(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                ),
                            ) {
                                PocketArtworkPlate(option.key, plateSize = 52.dp, selected = isSelected)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Aplicar rollover")
                    Switch(rollover, { rollover = it })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    when (val result = ledger.execute(LedgerCommand.UpsertPocket(existing?.pocket?.id, name, rollover, selectedIcon))) {
                        LedgerResult.Success -> onDismiss()
                        is LedgerResult.Rejected -> error = result.message
                        is LedgerResult.Deleted -> Unit
                    }
                }
            }) { Text("Guardar Pocket") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PocketManagementDialog(
    state: LedgerState,
    periodId: String,
    summary: PocketPeriodSummary,
    ledger: PocketLedger,
    readOnly: Boolean,
    currency: SupportedCurrency,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    fun money(minor: Long): String = MoneyText.format(minor, currency)
    if (readOnly) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Detalle histórico") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(summary.pocket.name, style = MaterialTheme.typography.titleMedium)
                    Text("Presupuesto: ${money(summary.budgetMinor)}")
                    Text("Rollover recibido: ${money(summary.rolloverMinor)}")
                    Text("Gastos: ${money(summary.expenseMinor)}")
                    Text("Reembolsos: ${money(summary.refundMinor)}")
                    Text("Rollover liberado: ${money(summary.rolloverReleasedMinor)}")
                    Text("Disponibilidad final: ${money(summary.availabilityMinor)}")
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        )
        return
    }
    if (summary.retiredThisPeriod) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pocket retirado") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(summary.pocket.name, style = MaterialTheme.typography.titleMedium)
                    Text("Gastos: ${money(summary.expenseMinor)}")
                    Text("Reembolsos: ${money(summary.refundMinor)}")
                    Text("Rollover liberado: ${money(summary.rolloverReleasedMinor)}")
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        )
        return
    }
    val initialAmount = if (summary.budgetMinor == 0L) "" else minorNumber(summary.budgetMinor)
    var amount by rememberSaveable(summary.pocket.id, periodId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialAmount))
    }
    var hasReceivedInitialFocus by rememberSaveable(summary.pocket.id, periodId) { mutableStateOf(false) }
    var amountIsFocused by remember { mutableStateOf(false) }
    var error by rememberSaveable(summary.pocket.id, periodId) { mutableStateOf<String?>(null) }
    var confirmingArchive by rememberSaveable(summary.pocket.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(amountIsFocused) {
        if (amountIsFocused && !hasReceivedInitialFocus) {
            hasReceivedInitialFocus = true
            if (amount.text.isNotEmpty()) {
                amount = amount.copy(selection = TextRange(0, amount.text.length))
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(summary.pocket.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Gestiona el presupuesto y las opciones de este Pocket sin saturar la vista general.")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    label = { Text("Presupuesto ${currency.name}") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            amountIsFocused = focusState.isFocused
                        }
                        .testTag("allocation_amount"),
                )
                Text("Fondos del periodo: ${money(state.periods.firstOrNull { it.id == periodId }?.newFundsMinor ?: 0)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { scope.launch { ledger.execute(LedgerCommand.MovePocket(summary.pocket.id, -1)) } },
                        modifier = Modifier.semantics { contentDescription = "Mover ${summary.pocket.name} arriba" },
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Subir")
                    }
                    TextButton(
                        onClick = { scope.launch { ledger.execute(LedgerCommand.MovePocket(summary.pocket.id, 1)) } },
                        modifier = Modifier.semantics { contentDescription = "Mover ${summary.pocket.name} abajo" },
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Bajar")
                    }
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Editar Pocket") }
                if (confirmingArchive) {
                    Text("¿Archivar este Pocket? Sus movimientos se conservarán.", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { confirmingArchive = false }) { Text("Conservar") }
                        Button(onClick = {
                            scope.launch {
                                when (val result = ledger.execute(LedgerCommand.ArchivePocket(summary.pocket.id))) {
                                    LedgerResult.Success -> onDismiss()
                                    is LedgerResult.Rejected -> error = result.message
                                    is LedgerResult.Deleted -> Unit
                                }
                            }
                        }) { Text("Confirmar archivo") }
                    }
                } else {
                    TextButton(onClick = { confirmingArchive = true }) { Text("Archivar Pocket") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    val parsed = runCatching { Money.parse(amount.text, currency.name).minor }.getOrNull() ?: run {
                        error = "Escribe un presupuesto válido"
                        return@launch
                    }
                    when (val result = ledger.execute(LedgerCommand.SetAllocation(periodId, summary.pocket.id, parsed))) {
                        LedgerResult.Success -> onDismiss()
                        is LedgerResult.Rejected -> error = result.message
                        is LedgerResult.Deleted -> Unit
                    }
                }
            }) { Text("Guardar presupuesto") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

private fun minorNumber(minor: Long): String = MoneyText.editable(minor)
