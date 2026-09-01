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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.PocketIconKey
import com.aif31.pocket.data.PocketLedger
import com.aif31.pocket.ui.MoneyText
import com.aif31.pocket.ui.PocketArtwork
import java.time.Instant
import java.time.LocalDate
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
) {
    var query by rememberSaveable { mutableStateOf("") }
    var periodIndex by rememberSaveable { mutableStateOf(0) }
    var pocketIndex by rememberSaveable { mutableStateOf(0) }
    var currencyIndex by rememberSaveable { mutableStateOf(0) }
    var methodIndex by rememberSaveable { mutableStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.movements.firstOrNull { it.id == selectedId }

    val scope = rememberCoroutineScope()
    val periodOptions = listOf<String?>(null) + state.periods.map { it.id }
    val pocketOptions = listOf<String?>(null) + state.pockets.map { it.pocket.id }
    val currencyOptions = listOf<String?>(null) + state.movements.map { it.originalCurrencyCode }.distinct()
    val methodOptions = listOf<String?>(null) + state.paymentMethods.map { it.id }
    val filtered = state.movements.filter { movement ->
        val text = listOfNotNull(movement.merchant, movement.note).joinToString(" ")
        (query.isBlank() || text.contains(query, ignoreCase = true)) &&
            (periodOptions.getOrNull(periodIndex) == null || movement.periodId == periodOptions[periodIndex]) &&
            (pocketOptions.getOrNull(pocketIndex) == null || movement.pocketId == pocketOptions[pocketIndex]) &&
            (currencyOptions.getOrNull(currencyIndex) == null || movement.originalCurrencyCode == currencyOptions[currencyIndex]) &&
            (methodOptions.getOrNull(methodIndex) == null || movement.paymentMethodId == methodOptions[methodIndex])
    }
    val periodLabels = listOf("Todos los periodos") + state.periods.map { it.start.toString() }
    val pocketLabels = listOf("Todos los Pockets") + state.pockets.map { it.pocket.name }
    val currencyLabels = listOf("Todas las monedas") + currencyOptions.drop(1).map { it.orEmpty() }
    val methodLabels = listOf("Todos los métodos") + state.paymentMethods.map { it.name }
    val groupedMovements = filtered.groupBy { it.localDate }.entries.sortedByDescending { it.key }
    val filtersActive = query.isNotBlank() || periodIndex != 0 || pocketIndex != 0 ||
        currencyIndex != 0 || methodIndex != 0
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Movimientos", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar comercio o nota") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().testTag("history_search"),
            )
        }
        item {
            Text("Filtros", style = MaterialTheme.typography.titleMedium)
            LazyRow(
                modifier = Modifier.testTag("history_filters"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { HistoryFilter("filter_period", periodLabels, periodIndex) { periodIndex = it } }
                item { HistoryFilter("filter_pocket", pocketLabels, pocketIndex) { pocketIndex = it } }
                item { HistoryFilter("filter_currency", currencyLabels, currencyIndex) { currencyIndex = it } }
                item { HistoryFilter("filter_method", methodLabels, methodIndex) { methodIndex = it } }
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
                        pocketIndex = 0
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
        if (filtered.isEmpty()) item { Text("No hay movimientos para estos filtros") }
        groupedMovements.forEach { (date, movements) ->
            item(key = "date-$date") {
                Text(
                    formatMovementDate(date, state.currentLocalDate),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(movements, key = { it.id }) { movement ->
                MovementCard(
                    movement = movement,
                    iconKey = state.pockets.firstOrNull { it.pocket.id == movement.pocketId }?.pocket?.iconKey
                        ?: PocketIconKey.forName(movement.pocketName),
                    onClick = { selectedId = movement.id },
                )
            }
        }
    }
    selected?.let { movement ->
        AlertDialog(
            onDismissRequest = { selectedId = null },
            title = { Text(movement.pocketName) },
            text = { Text("${if (movement.type == MovementType.EXPENSE) "Gasto" else "Devolución"} ${money(movement.sarAmountMinor)}\n${movement.localDate}") },
            confirmButton = {
                TextButton(onClick = { onEditMovement(movement); selectedId = null }) { Text("Editar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        selectedId = null
                        scope.launch {
                            val result = ledger.execute(LedgerCommand.DeleteMovement(movement.id))
                            if (result is LedgerResult.Deleted) {
                                val action = withTimeoutOrNull(undoWindowMillis) {
                                    snackbar.showSnackbar("Movimiento eliminado", "Deshacer", duration = SnackbarDuration.Indefinite)
                                }
                                if (action == SnackbarResult.ActionPerformed) ledger.execute(LedgerCommand.RestoreMovement(result.movement))
                                else snackbar.currentSnackbarData?.dismiss()
                            }
                        }
                    }) { Text("Eliminar") }
                    TextButton(onClick = { selectedId = null }) { Text("Cerrar") }
                }
            },
        )
    }

}

@Composable
private fun MovementCard(movement: Movement, iconKey: PocketIconKey, onClick: () -> Unit) {
    val isRefund = movement.type == MovementType.REFUND
    val amountColor = if (isRefund) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val time = Instant.ofEpochMilli(movement.occurredAtUtcMillis)
        .atZone(java.time.ZoneId.of(movement.zoneId))
        .format(DateTimeFormatter.ofPattern("HH:mm"))

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        PocketArtwork(iconKey, contentDescription = null, modifier = Modifier.size(42.dp))
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
                )
                movement.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    (if (isRefund) "+ " else "- ") + money(movement.sarAmountMinor),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = amountColor,
                )
                if (movement.originalCurrencyCode != "SAR" && movement.originalAmountMinor != null) {
                    Text(
                        "${movement.originalCurrencyCode} ${minorNumber(movement.originalAmountMinor)} · " +
                            if (movement.conversionStatus == ConversionStatus.CONFIRMED) "Confirmado" else "Estimado",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(if (isRefund) "Devolución" else "Confirmado", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Text(time, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HistoryFilter(
    testTag: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(testTag),
        ) {
            Text(labels.getOrElse(selectedIndex) { labels.first() })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(if (index == selectedIndex) "✓ $label" else label) },
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

private fun formatMovementDate(date: LocalDate, today: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))
    val formatted = date.format(formatter)
    return if (date == today) "Hoy, $formatted" else formatted
}

private fun money(minor: Long): String = MoneyText.sar(minor)

private fun minorNumber(minor: Long): String = MoneyText.grouped(minor)
