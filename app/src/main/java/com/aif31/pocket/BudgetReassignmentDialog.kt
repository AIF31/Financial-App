package com.aif31.pocket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.BudgetReassignment
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.PocketLedger
import com.aif31.pocket.domain.Money
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.ui.MoneyText
import com.aif31.pocket.ui.SelectableRadioRow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun BudgetReassignmentDialog(
    state: LedgerState,
    periodId: String,
    targetPocketId: String,
    ledger: PocketLedger,
    currency: SupportedCurrency,
    onDismiss: () -> Unit,
    onCovered: () -> Unit,
) {
    val options = remember(state, periodId, targetPocketId) { BudgetReassignment.of(state, periodId, targetPocketId) }
    val sources = options?.sources.orEmpty()
    var amount by rememberSaveable(periodId, targetPocketId) { mutableStateOf(options?.neededMinor?.let(MoneyText::editable).orEmpty()) }
    var sourcePocketId by rememberSaveable(periodId, targetPocketId) { mutableStateOf(sources.firstOrNull()?.pocketId) }
    var error by rememberSaveable(periodId, targetPocketId) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Cubrir Pocket") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val targetName = state.pocketSummariesByPeriod[periodId]?.firstOrNull { it.pocket.id == targetPocketId }?.pocket?.name.orEmpty()
                Text("Reasignar presupuesto a $targetName")
                Text(options?.neededMinor?.let { "Importe para cubrir: ${MoneyText.format(it, currency)}" }
                    ?: "Escribe cuánto presupuesto quieres reasignar.")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    enabled = !saving,
                    isError = error != null,
                    supportingText = error?.let { message ->
                        { Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    },
                    label = { Text("Importe ${currency.name}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("reassignment_amount"),
                )
                Text("Cubrir con", style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    sources.forEach { source ->
                        SelectableRadioRow(
                            selected = sourcePocketId == source.pocketId,
                            enabled = !saving,
                            onClick = { sourcePocketId = source.pocketId; error = null },
                        ) {
                            Text(source.name)
                            Text("Hasta ${MoneyText.format(source.maximumMinor, currency)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (sources.isEmpty()) Text("No hay presupuesto disponible para reasignar.")
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && sources.isNotEmpty(),
                modifier = Modifier.testTag("confirm_budget_reassignment"),
                onClick = {
                    scope.launch {
                        val parsed = runCatching { Money.parse(amount, currency.name).minor }.getOrNull()
                        if (parsed == null) {
                            error = "Escribe un importe válido"
                            return@launch
                        }
                        saving = true
                        try {
                            val command = try {
                                BudgetReassignment.command(ledger.state.first(), periodId, targetPocketId, sourcePocketId, parsed)
                            } catch (invalid: IllegalArgumentException) {
                                error = invalid.message
                                return@launch
                            } catch (_: ArithmeticException) {
                                error = "El importe supera el límite permitido"
                                return@launch
                            }
                            when (val result = ledger.execute(command)) {
                                LedgerResult.Success -> onCovered()
                                is LedgerResult.Rejected -> error = result.message
                                is LedgerResult.Deleted -> Unit
                            }
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text("Reasignar presupuesto") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
    )
}
