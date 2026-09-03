package com.aif31.pocket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.CurrencyBoundary
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.fx.FxQuote

internal sealed interface CurrencyQuoteState {
    data object Idle : CurrencyQuoteState
    data object Loading : CurrencyQuoteState
    data class Ready(val quote: FxQuote) : CurrencyQuoteState
    data class Error(val message: String) : CurrencyQuoteState
}

internal data class CurrencySettingsUiState(
    val currentCurrency: SupportedCurrency,
    val onlineFxEnabled: Boolean,
    val defaultExpenseCurrency: SupportedCurrency,
    val targetCurrency: SupportedCurrency? = null,
    val quoteState: CurrencyQuoteState = CurrencyQuoteState.Idle,
    val pendingChange: CurrencyBoundary? = null,
)

@Composable
internal fun CurrencySettingsContent(
    state: CurrencySettingsUiState,
    contentPadding: PaddingValues,
    onOnlineFxEnabledChange: (Boolean) -> Unit = {},
    onDefaultExpenseCurrencyChange: (SupportedCurrency) -> Unit = {},
    onTargetCurrencyChange: (SupportedCurrency) -> Unit = {},
    onRefreshQuote: () -> Unit = {},
    onCancelQuote: () -> Unit = {},
    onConfirmTransition: () -> Unit = {},
    onCancelPendingTransition: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("currency_settings_list"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Moneda y conversión", style = MaterialTheme.typography.headlineMedium)
            Text("Moneda contable actual: ${state.currentCurrency.name}")
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Conversión en línea", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.onlineFxEnabled) "Conversión en línea activada" else "Conversión en línea desactivada",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.onlineFxEnabled,
                    onCheckedChange = onOnlineFxEnabledChange,
                    modifier = Modifier.testTag("fx_consent"),
                )
            }
        }
        item {
            Text("Moneda predeterminada para gastos", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SupportedCurrency.entries.forEach { currency ->
                    OutlinedButton(onClick = { onDefaultExpenseCurrencyChange(currency) }) {
                        Text(if (state.defaultExpenseCurrency == currency) "✓ ${currency.name}" else currency.name)
                    }
                }
            }
        }
        state.pendingChange?.let { pending ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cambio pendiente: ${pending.to.name}", style = MaterialTheme.typography.titleMedium)
                        Text("Se aplicará al próximo periodo desde ${pending.effectiveDate}")
                        Text("1 ${pending.from.name} = ${pending.rate} ${pending.to.name}")
                        Text("Fuente: ${pending.source}")
                        OutlinedButton(onClick = onCancelPendingTransition) { Text("Cancelar cambio pendiente") }
                    }
                }
            }
        }
        if (state.onlineFxEnabled && state.pendingChange == null) {
            item {
                Text("Moneda del próximo periodo", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SupportedCurrency.entries.filterNot { it == state.currentCurrency }.forEach { currency ->
                        OutlinedButton(onClick = { onTargetCurrencyChange(currency) }) {
                            Text(if (state.targetCurrency == currency) "✓ ${currency.name}" else currency.name)
                        }
                    }
                }
            }
            item {
                when (val quoteState = state.quoteState) {
                    CurrencyQuoteState.Idle -> Text("Selecciona la moneda del próximo periodo")
                    CurrencyQuoteState.Loading -> {
                        Text("Consultando tipo de cambio…")
                        OutlinedButton(onClick = onCancelQuote) { Text("Cancelar consulta") }
                    }
                    is CurrencyQuoteState.Error -> {
                        Text(quoteState.message, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = onRefreshQuote) { Text("Reintentar") }
                    }
                    is CurrencyQuoteState.Ready -> {
                        Text(
                            "1 ${quoteState.quote.base.name} = ${quoteState.quote.rate} ${quoteState.quote.quote.name}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("Efectiva: ${quoteState.quote.effectiveDate}")
                        Text("Fuente: ${quoteState.quote.source}")
                        OutlinedButton(onClick = onRefreshQuote) { Text("Actualizar cotización") }
                    }
                }
                Button(
                    onClick = onConfirmTransition,
                    enabled = state.quoteState is CurrencyQuoteState.Ready,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Confirmar cambio próximo periodo")
                }
            }
        }
    }
}
