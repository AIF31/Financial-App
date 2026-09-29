package com.aif31.pocket

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.MovementSuggestion
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.notifications.ReviewReason
import com.aif31.pocket.notifications.appLabel
import com.aif31.pocket.notifications.isAutoRecordedMovement
import com.aif31.pocket.notifications.reviewHint
import com.aif31.pocket.data.PocketIconKey
import com.aif31.pocket.data.PocketLedger
import com.aif31.pocket.data.netSpendMinor
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.ui.MoneyText
import com.aif31.pocket.ui.PocketArtworkPlate
import com.aif31.pocket.ui.formatPeriodRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun MovementsScreen(
    state: LedgerState,
    ledger: PocketLedger,
    snackbar: SnackbarHostState,
    padding: PaddingValues,
    undoWindowMillis: Long,
    onRecordExpense: () -> Unit,
    onEditMovement: (Movement) -> Unit,
    onReviewSuggestion: (String) -> Unit = {},
) {
    fun currencyOf(movement: Movement): SupportedCurrency =
        state.periods.firstOrNull { it.id == movement.periodId }?.accountingCurrency ?: SupportedCurrency.SAR
    var query by rememberSaveable { mutableStateOf("") }
    var periodIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedPocketId by rememberSaveable { mutableStateOf<String?>(null) }
    var currencyIndex by rememberSaveable { mutableIntStateOf(0) }
    var methodIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.movements.firstOrNull { it.id == selectedId }
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current

    val scope = rememberCoroutineScope()
    val periodOptions = listOf<String?>(null) + state.periods.map { it.id }
    val pocketOptions = listOf<String?>(null) + state.pocketCatalog.map { it.id }
    val pocketIndex = pocketOptions.indexOf(selectedPocketId).coerceAtLeast(0)
    val currencyOptions = listOf<String?>(null) + state.movements.map { it.originalCurrencyCode }.distinct()
    val methodOptions = listOf<String?>(null) + state.paymentMethods.map { it.id }
    val filtered = state.movements.filter { movement ->
        val text = listOfNotNull(movement.merchant, movement.note).joinToString(" ")
        (query.isBlank() || text.contains(query, ignoreCase = true)) &&
            (periodOptions.getOrNull(periodIndex) == null || movement.periodId == periodOptions[periodIndex]) &&
            (selectedPocketId == null || movement.pocketId == selectedPocketId) &&
            (currencyOptions.getOrNull(currencyIndex) == null || movement.originalCurrencyCode == currencyOptions[currencyIndex]) &&
            (methodOptions.getOrNull(methodIndex) == null || movement.paymentMethodId == methodOptions[methodIndex])
    }
    val periodLabels = listOf("Todos los periodos") + state.periods.map { formatPeriodRange(it.start, it.endExclusive) }
    val pocketLabels = listOf("Todos los Pockets") + state.pocketCatalog.map { it.name }
    val currencyLabels = listOf("Todas las monedas") + currencyOptions.drop(1).map { it.orEmpty() }
    val methodLabels = listOf("Todos los métodos") + state.paymentMethods.map { it.name }
    val groupedMovements = filtered.groupBy { it.localDate }.entries.sortedByDescending { it.key }
    val filtersActive = query.isNotBlank() || periodIndex != 0 || selectedPocketId != null ||
        currencyIndex != 0 || methodIndex != 0

    fun deleteWithUndo(movement: Movement) {
        selectedId = null
        scope.launch {
            val result = ledger.execute(LedgerCommand.DeleteMovement(movement.id))
            if (result is LedgerResult.Deleted) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                val action = withTimeoutOrNull(undoWindowMillis) {
                    snackbar.showSnackbar("Movimiento eliminado", "Deshacer", duration = SnackbarDuration.Indefinite)
                }
                if (action == SnackbarResult.ActionPerformed) ledger.execute(LedgerCommand.RestoreMovement(result.movement))
                else snackbar.currentSnackbarData?.dismiss()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Movimientos", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        }
        if (state.movementSuggestions.isNotEmpty()) {
            item(key = "suggestions-header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Por revisar", style = MaterialTheme.typography.titleMedium)
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiary) {
                        Text(
                            state.movementSuggestions.size.toString(),
                            color = MaterialTheme.colorScheme.onTertiary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    "Gastos leídos de tus notificaciones que necesitan un Pocket o una conversión.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.movementSuggestions, key = { "suggestion-${it.id}" }) { suggestion ->
                SuggestionCard(
                    suggestion = suggestion,
                    // The payment's own period decides whether it needs a conversion, not the current one.
                    accountingCurrency = Instant.ofEpochMilli(suggestion.effectiveAtUtcMillis)
                        .atZone(BUDGET_ZONE).toLocalDate()
                        .let { date -> state.periods.firstOrNull { !date.isBefore(it.start) && date.isBefore(it.endExclusive) } }
                        ?.accountingCurrency ?: state.currentPeriod?.accountingCurrency,
                    today = state.currentLocalDate,
                    onReview = { onReviewSuggestion(suggestion.id) },
                    onDiscard = {
                        scope.launch {
                            if (ledger.execute(LedgerCommand.RejectSuggestion(suggestion.id)) == LedgerResult.Success) {
                                snackbar.showSnackbar("Sugerencia descartada")
                            }
                        }
                    },
                )
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar comercio o nota") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().testTag("history_search"),
            )
        }
        item {
            LazyRow(
                modifier = Modifier.testTag("history_filters"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { HistoryFilter("filter_period", "Periodo", periodLabels, periodIndex) { periodIndex = it } }
                item { HistoryFilter("filter_pocket", "Pocket", pocketLabels, pocketIndex) { selectedPocketId = pocketOptions[it] } }
                item { HistoryFilter("filter_currency", "Moneda", currencyLabels, currencyIndex) { currencyIndex = it } }
                item { HistoryFilter("filter_method", "Método", methodLabels, methodIndex) { methodIndex = it } }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${filtered.size} ${if (filtered.size == 1) "movimiento" else "movimientos"}", color = MaterialTheme.colorScheme.primary)
                TextButton(
                    onClick = {
                        query = ""
                        periodIndex = 0
                        selectedPocketId = null
                        currencyIndex = 0
                        methodIndex = 0
                    },
                    enabled = filtersActive,
                    modifier = Modifier.testTag("clear_filters"),
                ) {
                    Text("Limpiar filtros")
                }
            }
        }
        if (state.movements.isEmpty()) {
            item {
                EmptyHistory(
                    title = "Aún no hay movimientos",
                    body = "Registra tu primer gasto para ver aquí tu historial.",
                    actionLabel = "Registrar gasto",
                    onAction = onRecordExpense,
                )
            }
        } else if (filtered.isEmpty()) {
            item {
                EmptyHistory(
                    title = "No hay movimientos para estos filtros",
                    body = "Prueba otra búsqueda o limpia los filtros.",
                )
            }
        }
        groupedMovements.forEach { (date, movements) ->
            item(key = "date-$date") {
                val dayNet = movements.netSpendMinor()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics(mergeDescendants = true) { heading() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatMovementDate(date, state.currentLocalDate),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (movements.map(::currencyOf).distinct().size == 1) {
                        val dayCurrency = currencyOf(movements.first())
                        Text(
                            if (dayNet < 0) "Devuelto en el día ${MoneyText.format(-dayNet, dayCurrency)}"
                            else "Total del día ${MoneyText.format(dayNet, dayCurrency)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(movements, key = { it.id }) { movement ->
                SwipeToDeleteMovement(
                    onDelete = { deleteWithUndo(movement) },
                    modifier = Modifier.animateItem(),
                ) {
                    MovementCard(
                        movement = movement,
                        accountingCurrency = currencyOf(movement),
                        iconKey = state.pocketCatalog.firstOrNull { it.id == movement.pocketId }?.iconKey
                            ?: PocketIconKey.forName(movement.pocketName),
                        onClick = { selectedId = movement.id },
                        onEdit = { onEditMovement(movement) },
                        onDelete = { deleteWithUndo(movement) },
                    )
                }
            }
        }
    }
    selected?.let { movement ->
        MovementDetailDialog(
            movement = movement,
            accountingCurrency = currencyOf(movement),
            onEdit = { onEditMovement(movement); selectedId = null },
            onDelete = { deleteWithUndo(movement) },
            onDismiss = { selectedId = null },
        )
    }
}

@Composable
private fun EmptyHistory(title: String, body: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actionLabel?.let {
                Button(onClick = onAction) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(it, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Swipe from the end edge to delete; the list's undo snackbar provides recovery. */
@Composable
private fun SwipeToDeleteMovement(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Plain remember: an undone deletion re-enters the list under the same key and must start settled,
    // not restore its dismissed position and delete itself again.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val dismissState = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    val currentOnDelete by rememberUpdatedState(onDelete)
    // Stable callback so the box's settle effect runs once per dismissal rather than on every recomposition.
    val handleDismiss = remember { { value: SwipeToDismissBoxValue -> if (value == SwipeToDismissBoxValue.EndToStart) currentOnDelete() } }
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = handleDismiss,
        backgroundContent = {
            // Visual affordance only; the card exposes "Eliminar movimiento" as an accessibility action.
            if (dismissState.dismissDirection != SwipeToDismissBoxValue.EndToStart) return@SwipeToDismissBox
            val active = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            Surface(
                modifier = Modifier.fillMaxSize().clearAndSetSemantics { },
                shape = MaterialTheme.shapes.medium,
                color = if (active) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (active) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Eliminar", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
        },
        content = { content() },
    )
}

@Composable
private fun SuggestionCard(
    suggestion: MovementSuggestion,
    accountingCurrency: SupportedCurrency?,
    today: LocalDate,
    onReview: () -> Unit,
    onDiscard: () -> Unit,
) {
    val context = LocalContext.current
    val source = remember(suggestion.sourcePackage) { appLabel(context, suggestion.sourcePackage) }
    val detectedAt = Instant.ofEpochMilli(suggestion.effectiveAtUtcMillis).atZone(BUDGET_ZONE)
    val hint = if (accountingCurrency != null && suggestion.currency != accountingCurrency) {
        reviewHint(ReviewReason.FOREIGN_CURRENCY)
    } else {
        reviewHint(ReviewReason.NEW_MERCHANT)
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("movement_suggestion").clickable(onClick = onReview),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    suggestion.merchant ?: "Comercio sin identificar",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "$source · ${formatMovementDate(detectedAt.toLocalDate(), today)} " +
                        detectedAt.format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Text(
                "- " + MoneyText.format(suggestion.amountMinor, suggestion.currency),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDiscard, modifier = Modifier.testTag("discard_suggestion")) { Text("Descartar") }
            FilledTonalButton(onClick = onReview, modifier = Modifier.testTag("review_suggestion")) { Text("Revisar") }
        }
    }
}

@Composable
private fun MovementCard(
    movement: Movement,
    accountingCurrency: SupportedCurrency,
    iconKey: PocketIconKey,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isRefund = movement.type == MovementType.REFUND
    val amountColor = if (isRefund) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val time = Instant.ofEpochMilli(movement.occurredAtUtcMillis)
        .atZone(ZoneId.of(movement.zoneId))
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    val converted = movement.originalCurrencyCode != accountingCurrency.name && movement.originalAmountMinor != null

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Editar movimiento") { onEdit(); true },
                    CustomAccessibilityAction("Eliminar movimiento") { onDelete(); true },
                )
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (isRefund) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRefund) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else {
                        PocketArtworkPlate(iconKey, plateSize = 52.dp)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    movement.merchant?.takeIf { it.isNotBlank() }
                        ?: if (isRefund) "Devolución ${movement.pocketName}" else movement.pocketName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    listOfNotNull(movement.pocketName, movement.paymentMethodName).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                movement.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    (if (isRefund) "+ " else "- ") + MoneyText.format(movement.accountingAmountMinor, accountingCurrency),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = amountColor,
                )
                when {
                    converted -> Text(
                        movement.originalAmountLabel(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    isRefund -> MovementStatusBadge(
                        "Devolución",
                        Icons.Default.CheckCircle,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    // Manual expenses need no badge; one recorded from a notification says so.
                    isAutoRecordedMovement(movement.id) -> MovementStatusBadge(
                        "Detectado",
                        Icons.Default.NotificationsActive,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Text(time, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MovementStatusBadge(label: String, icon: ImageVector, color: Color, contentColor: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = color, contentColor = contentColor) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MovementDetailDialog(
    movement: Movement,
    accountingCurrency: SupportedCurrency,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zoned = Instant.ofEpochMilli(movement.occurredAtUtcMillis).atZone(ZoneId.of(movement.zoneId))
    val isRefund = movement.type == MovementType.REFUND
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(movement.pocketName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${if (isRefund) "Devolución" else "Gasto"} ${MoneyText.format(movement.accountingAmountMinor, accountingCurrency)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                )
                DetailLine("Fecha", zoned.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy · HH:mm", Locale.forLanguageTag("es"))))
                movement.merchant?.takeIf(String::isNotBlank)?.let { DetailLine("Comercio", it) }
                movement.paymentMethodName?.let { DetailLine("Método de pago", it) }
                movement.note?.takeIf(String::isNotBlank)?.let { DetailLine("Nota", it) }
                if (movement.originalCurrencyCode != accountingCurrency.name && movement.originalAmountMinor != null) {
                    DetailLine("Importe original", movement.originalAmountLabel())
                    movement.conversionSource?.let { DetailLine("Fuente", it) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) { Text("Editar") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Eliminar") }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HistoryFilter(
    testTag: String,
    name: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = selectedIndex != 0
    Box {
        FilterChip(
            selected = active,
            onClick = { expanded = true },
            label = { Text(if (active) labels.getOrElse(selectedIndex) { name } else name) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.testTag(testTag),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = if (index == selectedIndex) {
                        { Icon(Icons.Default.Check, contentDescription = "Seleccionado") }
                    } else {
                        null
                    },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    },
                    modifier = Modifier.testTag("${testTag}_option_$index"),
                )
            }
        }
    }
}

/** "USD 67.00 · Confirmado": the original amount with its conversion status. */
private fun Movement.originalAmountLabel(): String =
    "$originalCurrencyCode ${MoneyText.grouped(originalAmountMinor ?: 0)} · " +
        if (conversionStatus == ConversionStatus.CONFIRMED) "Confirmado" else "Estimado"

private fun formatMovementDate(date: LocalDate, today: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es"))
    val formatted = date.format(formatter)
    return when (date) {
        today -> "Hoy, ${date.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es")))}"
        today.minusDays(1) -> "Ayer, ${date.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es")))}"
        else -> formatted.replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }
    }
}


private fun minorNumber(minor: Long): String = MoneyText.grouped(minor)

/** Same budget zone the expense form stores, so suggestion times and periods match the Movement they become. */
private val BUDGET_ZONE: ZoneId = ZoneId.of("Asia/Riyadh")
