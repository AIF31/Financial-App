package com.aif31.pocket

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.*
import com.aif31.pocket.ui.MoneyText
import com.aif31.pocket.ui.PocketTopAppBar
import com.aif31.pocket.ui.SettingsSection
import com.aif31.pocket.ui.counted
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun MerchantMemorySettings(
    state: LedgerState,
    ledger: PocketLedger,
    padding: PaddingValues,
    snackbar: SnackbarHostState?,
    onBack: () -> Unit,
) {
    val entries = remember(state.movements, state.pocketCatalog, state.periods) { MerchantMemory.entries(state) }
    val scope = rememberCoroutineScope()
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val selected = entries.firstOrNull { it.merchantKey == selectedKey }
    MerchantMemoryContent(entries, padding, onBack) {
        selectedKey = it.merchantKey
        error = null
    }
    if (selected != null) {
        val choices = state.pocketSummariesByPeriod[selected.latestExpense.periodId].orEmpty()
            .filter { !it.pocket.archived && !it.retiredThisPeriod && it.pocket.id != selected.pocket.id }
            .map { it.pocket }
        MerchantMemoryDialog(
            entry = selected,
            choices = choices,
            error = error,
            saving = saving,
            onDismiss = { if (!saving) selectedKey = null },
            onConfirm = { pocketId ->
                if (!saving) {
                    saving = true
                    scope.launch {
                        try {
                            val latestState = ledger.state.first()
                            val command = MerchantMemory.reassign(latestState, selected.merchantKey, pocketId)
                            require(latestState.movements.first { it.id == command.id } == selected.latestExpense) {
                                "El último gasto cambió. Revisa sus datos antes de confirmar."
                            }
                            when (val result = ledger.execute(command)) {
                                is LedgerResult.Rejected -> error = result.message
                                LedgerResult.Success -> {
                                    selectedKey = null
                                    scope.launch { snackbar?.showSnackbar("Pocket del comercio actualizado") }
                                }
                                is LedgerResult.Deleted -> Unit
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (rejected: IllegalArgumentException) {
                            error = rejected.message
                        } catch (_: Exception) {
                            error = "No se pudo mover el gasto. Inténtalo de nuevo."
                        } finally {
                            saving = false
                        }
                    }
                }
            },
        )
    }
}

@Composable
internal fun MerchantMemoryContent(
    entries: List<MerchantMemoryEntry>,
    padding: PaddingValues,
    onBack: () -> Unit,
    onSelect: (MerchantMemoryEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        PocketTopAppBar(SettingsSection.MERCHANT_MEMORY.title, onBack, windowInsets = WindowInsets(0))
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("merchant_memory_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    "El Pocket se recuerda a partir del último gasto de cada comercio. Cambiarlo mueve ese gasto y guía los próximos registros automáticos.",
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                )
            }
            if (entries.isEmpty()) {
                item { Text("Aún no hay comercios. Aparecen cuando registras un gasto con comercio.") }
            }
            items(entries, key = { it.merchantKey }) { entry ->
                Card(
                    onClick = { onSelect(entry) },
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().testTag("merchant_memory_${entry.merchantKey}"),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(entry.pocket.name)
                        if (entry.pocketArchived) Text("Pocket archivado · no se registra automáticamente")
                        Text(latestExpenseText(entry), style = MaterialTheme.typography.bodyMedium)
                        Text(counted(entry.expenseCount, "gasto", "gastos"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MerchantMemoryDialog(
    entry: MerchantMemoryEntry,
    choices: List<Pocket>,
    error: String?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pocketId by rememberSaveable(entry.latestExpense.id, entry.pocket.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(latestExpenseText(entry))
                Text("Se moverá este gasto al Pocket que elijas. Los futuros registros automáticos de este comercio irán allí.")
                if (choices.isEmpty()) Text("No hay otro Pocket activo en el periodo de este gasto.")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(choices, key = { it.id }) { pocket ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .selectable(pocketId == pocket.id, enabled = !saving, role = Role.RadioButton) { pocketId = pocket.id }
                                .testTag("merchant_memory_pocket_${pocket.id}").padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = pocketId == pocket.id, onClick = null, enabled = !saving)
                            Text(pocket.name, modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (error != null) Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("merchant_memory_error").semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { pocketId?.let(onConfirm) },
                enabled = !saving && choices.any { it.id == pocketId },
                modifier = Modifier.testTag("merchant_memory_confirm"),
            ) { Text(if (saving) "Moviendo…" else "Mover gasto y recordar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
    )
}

private fun latestExpenseText(entry: MerchantMemoryEntry): String {
    val date = entry.latestExpense.localDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es")))
    return "Último gasto: $date · ${MoneyText.format(entry.latestExpense.accountingAmountMinor, entry.accountingCurrency)}"
}
