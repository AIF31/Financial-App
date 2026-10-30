package com.aif31.pocket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.CurrencyBoundary
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.fx.FxQuote
import com.aif31.pocket.settings.FxRateAlertFrequency
import com.aif31.pocket.settings.FxRateAlertSettings
import com.aif31.pocket.settings.ReminderStatus
import com.aif31.pocket.ui.ChoiceOption
import com.aif31.pocket.ui.MoneyText
import com.aif31.pocket.ui.SegmentedChoice
import com.aif31.pocket.ui.SingleChoiceChips
import com.aif31.pocket.ui.TimeOfDayPickerDialog
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

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
    /** The change that opened the current period, while that period is still the latest one. */
    val revertibleChange: CurrencyBoundary? = null,
    val fxRateAlert: FxRateAlertUiState = FxRateAlertUiState(),
)

internal data class FxRateAlertUiState(
    val settings: FxRateAlertSettings = FxRateAlertSettings(),
    val status: ReminderStatus = ReminderStatus.Off,
)

/** The change that opened the current period, while that period is still the latest one and can be reverted. */
internal fun LedgerState.revertibleCurrencyChange(): CurrencyBoundary? =
    currentPeriod?.takeIf { it == periods.maxByOrNull { period -> period.start } }?.priorCurrencyBoundary

/** A pending change to a third currency blocks a reversal; one back to [CurrencyBoundary.from] is replaced by it. */
internal fun CurrencyBoundary.revertBlockedBy(pending: CurrencyBoundary?): Boolean = pending != null && pending.to != from

/** Confirms a currency change reversal, naming what returns to the prior currency and what becomes an estimate. */
@Composable
internal fun RevertCurrencyDialog(change: CurrencyBoundary, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Volver a ${change.from.name} en este periodo?") },
        text = {
            Text(
                "Los fondos, los presupuestos y el rollover vuelven a ${change.from.name}. Los gastos registrados " +
                    "en ${change.from.name} recuperan su importe; los de otras monedas quedan estimados hasta " +
                    "confirmar el tipo de cambio de su fecha.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Volver a ${change.from.name}") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

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
    onRevertCurrentPeriod: () -> Unit = {},
    onFxRateAlertChange: (FxRateAlertSettings) -> Unit = {},
    onRetryFxRateAlert: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
) {
    var confirmingTransition by rememberSaveable { mutableStateOf(false) }
    var confirmingRevert by rememberSaveable { mutableStateOf(false) }
    val readyQuote = (state.quoteState as? CurrencyQuoteState.Ready)?.quote
    if (confirmingTransition && readyQuote != null) {
        AlertDialog(
            onDismissRequest = { confirmingTransition = false },
            title = { Text("¿Cambiar a ${readyQuote.quote.name}?") },
            text = {
                Text(
                    "Desde el próximo periodo, los fondos, los presupuestos y los gastos se contarán en " +
                        "${readyQuote.quote.name}. Los importes se convertirán a " +
                        "${MoneyText.rate(readyQuote.base, readyQuote.quote, readyQuote.rate)}.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmingTransition = false; onConfirmTransition() }) {
                    Text("Cambiar a ${readyQuote.quote.name}")
                }
            },
            dismissButton = { TextButton(onClick = { confirmingTransition = false }) { Text("Cancelar") } },
        )
    }
    state.revertibleChange?.takeIf { confirmingRevert }?.let { change ->
        RevertCurrencyDialog(
            change = change,
            onConfirm = { confirmingRevert = false; onRevertCurrentPeriod() },
            onDismiss = { confirmingRevert = false },
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("currency_settings_list"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Moneda contable actual: ${state.currentCurrency.name}", style = MaterialTheme.typography.titleMedium)
        }
        state.revertibleChange?.let { change ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Este periodo cambió de ${change.from.name} a ${change.to.name}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("Desde ${change.effectiveDate} · ${MoneyText.rate(change.from, change.to, change.rate)}")
                        val blocked = change.revertBlockedBy(state.pendingChange)
                        if (blocked) {
                            Text(
                                "Cancela el cambio pendiente para volver a ${change.from.name}.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = { confirmingRevert = true },
                            enabled = !blocked,
                        ) { Text("Volver a ${change.from.name} en este periodo") }
                    }
                }
            }
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
            SegmentedChoice(
                options = SupportedCurrency.entries.map { ChoiceOption(it, it.name) },
                selected = state.defaultExpenseCurrency,
                onSelect = onDefaultExpenseCurrencyChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        state.pendingChange?.let { pending ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cambio pendiente: ${pending.to.name}", style = MaterialTheme.typography.titleMedium)
                        Text("Se aplicará al próximo periodo desde ${pending.effectiveDate}")
                        Text(MoneyText.rate(pending.from, pending.to, pending.rate))
                        Text("Fuente: ${pending.source}")
                        Text("Cotización efectiva: ${pending.quoteEffectiveDate ?: "No registrada"}")
                        OutlinedButton(onClick = onCancelPendingTransition) { Text("Cancelar cambio pendiente") }
                    }
                }
            }
        }
        if (state.onlineFxEnabled && state.pendingChange == null) {
            // The current currency is the default and means "no change"; only another currency needs a quote.
            val nextCurrency = state.targetCurrency ?: state.currentCurrency
            item {
                Text("Moneda del próximo periodo", style = MaterialTheme.typography.titleMedium)
                SingleChoiceChips(
                    options = SupportedCurrency.entries.map { ChoiceOption(it, it.name, "next_period_currency_${it.name}") },
                    selected = nextCurrency,
                    onSelect = onTargetCurrencyChange,
                )
            }
            item {
                if (nextCurrency == state.currentCurrency) {
                    Text("El próximo periodo seguirá en ${state.currentCurrency.name}")
                } else {
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
                                MoneyText.rate(quoteState.quote.base, quoteState.quote.quote, quoteState.quote.rate),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text("Efectiva: ${quoteState.quote.effectiveDate}")
                            Text("Fuente: ${quoteState.quote.source}")
                            OutlinedButton(onClick = onRefreshQuote) { Text("Actualizar cotización") }
                        }
                    }
                    Button(
                        onClick = { confirmingTransition = true },
                        enabled = (state.quoteState as? CurrencyQuoteState.Ready)?.quote?.quote == nextCurrency,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Confirmar cambio próximo periodo")
                    }
                }
            }
        }
        item {
            FxRateAlertCard(
                state = state.fxRateAlert,
                onlineFxEnabled = state.onlineFxEnabled,
                onChange = onFxRateAlertChange,
                onRetry = onRetryFxRateAlert,
                onRequestNotificationPermission = onRequestNotificationPermission,
            )
        }
    }
}

@Composable
private fun FxRateAlertCard(
    state: FxRateAlertUiState,
    onlineFxEnabled: Boolean,
    onChange: (FxRateAlertSettings) -> Unit,
    onRetry: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val settings = state.settings
    val permissionRequest = rememberNotificationPermissionRequest(onRequestNotificationPermission)
    var pickerVisible by rememberSaveable { mutableStateOf(false) }
    var minimum by rememberSaveable(settings.minimumRate) { mutableStateOf(settings.minimumRate?.toPlainString().orEmpty()) }
    var minimumError by rememberSaveable { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Aviso de tipo de cambio", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !onlineFxEnabled -> if (settings.enabled) "En pausa · activa la conversión en línea" else "Requiere la conversión en línea"
                            else -> when (state.status) {
                                ReminderStatus.Off -> "Desactivado"
                                ReminderStatus.PermissionRequired -> "Permiso necesario"
                                ReminderStatus.Scheduled -> "Programado · ${scheduleText(settings)}"
                                ReminderStatus.Failed -> "No se pudo programar"
                            }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("fx_rate_alert_status"),
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { onChange(settings.copy(enabled = it)) },
                    // Turning it off stays possible after online conversion is withdrawn.
                    enabled = onlineFxEnabled || settings.enabled,
                    modifier = Modifier.testTag("fx_rate_alert_switch").semantics { contentDescription = "Aviso de tipo de cambio" },
                )
            }
            Text(
                "Muestra el FIX de Banxico en la moneda elegida, su cambio frente al FIX anterior y el promedio de los últimos 7 días.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onlineFxEnabled && settings.enabled && state.status == ReminderStatus.PermissionRequired) {
                if (permissionRequest?.showRationale == true) Text(NOTIFICATION_PERMISSION_RATIONALE)
                Button(onClick = { permissionRequest?.request() ?: onRequestNotificationPermission() }) {
                    Text(if (permissionRequest?.showRationale == true) "Continuar" else "Permitir notificaciones")
                }
                if (permissionRequest?.offerSettings == true) {
                    TextButton(onClick = permissionRequest::openSettings) { Text(OPEN_NOTIFICATION_SETTINGS) }
                }
            }
            if (onlineFxEnabled && settings.enabled && state.status == ReminderStatus.Failed) {
                Text("Comprueba los ajustes de notificaciones y vuelve a intentarlo.")
                TextButton(onClick = onRetry) { Text("Reintentar") }
            }
            Text("Monedas", style = MaterialTheme.typography.titleSmall)
            SingleChoiceChips(
                options = FxRateAlertSettings.PAIRS.map { (base, quote) ->
                    ChoiceOption(base to quote, "${base.name} → ${quote.name}", "fx_rate_alert_pair_${base.name}_${quote.name}")
                },
                selected = settings.base to settings.quote,
                onSelect = { (base, quote) ->
                    // A minimum belongs to its pair; re-tapping the selected pair keeps it.
                    if (base != settings.base || quote != settings.quote) {
                        onChange(settings.copy(base = base, quote = quote, minimumRate = null))
                    }
                },
            )
            Text("Frecuencia", style = MaterialTheme.typography.titleSmall)
            SegmentedChoice(
                options = listOf(
                    ChoiceOption(FxRateAlertFrequency.DAILY, "Diario", "fx_rate_alert_daily"),
                    ChoiceOption(FxRateAlertFrequency.NEW_FIX, "FIX nuevo", "fx_rate_alert_new_fix"),
                    ChoiceOption(FxRateAlertFrequency.WEEKLY, "Semanal", "fx_rate_alert_weekly"),
                ),
                selected = settings.frequency,
                onSelect = { onChange(settings.copy(frequency = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (settings.frequency == FxRateAlertFrequency.WEEKLY) {
                SingleChoiceChips(
                    options = DayOfWeek.entries.map { ChoiceOption(it, weekdayText(it), "fx_rate_alert_day_${it.name}") },
                    selected = settings.weekday,
                    onSelect = { onChange(settings.copy(weekday = it)) },
                )
            }
            Text(
                when (settings.frequency) {
                    FxRateAlertFrequency.DAILY -> "Llega todos los días; los fines de semana y festivos repite el último FIX."
                    FxRateAlertFrequency.NEW_FIX -> "Llega solo cuando Banxico publica un FIX nuevo, normalmente en días hábiles."
                    FxRateAlertFrequency.WEEKLY -> "Llega una vez por semana con el último FIX publicado."
                } + " Un horario de mañana trae el FIX del día hábil anterior.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hora: ${settings.time} (Asia/Riyadh)")
                TextButton(onClick = { pickerVisible = true }, modifier = Modifier.testTag("fx_rate_alert_time")) {
                    Text("Cambiar hora")
                }
            }
            OutlinedTextField(
                value = minimum,
                onValueChange = { minimum = it; minimumError = null },
                label = { Text("Solo si 1 ${settings.base.name} ≥ (${settings.quote.name})") },
                supportingText = { Text(minimumError ?: "Déjalo vacío para recibirlo con cualquier tipo de cambio.") },
                isError = minimumError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("fx_rate_alert_minimum"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val text = minimum.trim().replace(',', '.')
                        val rate = if (text.isEmpty()) null else text.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
                        if (text.isNotEmpty() && rate == null) {
                            minimumError = "Escribe un tipo de cambio mayor que cero"
                        } else {
                            onChange(settings.copy(minimumRate = rate))
                        }
                    },
                    modifier = Modifier.testTag("fx_rate_alert_minimum_save"),
                ) { Text("Guardar mínimo") }
                if (settings.minimumRate != null) {
                    TextButton(onClick = { onChange(settings.copy(minimumRate = null)) }) { Text("Quitar mínimo") }
                }
            }
        }
    }
    if (pickerVisible) {
        TimeOfDayPickerDialog(
            initial = settings.time,
            onPicked = { pickerVisible = false; onChange(settings.copy(time = it)) },
            onDismiss = { pickerVisible = false },
        )
    }
}

private fun scheduleText(settings: FxRateAlertSettings): String = when (settings.frequency) {
    FxRateAlertFrequency.DAILY -> "a diario a las ${settings.time}"
    FxRateAlertFrequency.NEW_FIX -> "con cada FIX nuevo, a las ${settings.time}"
    FxRateAlertFrequency.WEEKLY -> "los ${weekdayText(settings.weekday).lowercase(SPANISH)} a las ${settings.time}"
}

private fun weekdayText(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.FULL, SPANISH).replaceFirstChar { it.titlecase(SPANISH) }

private val SPANISH: Locale = Locale.forLanguageTag("es")
