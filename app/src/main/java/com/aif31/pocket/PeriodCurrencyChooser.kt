package com.aif31.pocket

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.Period
import com.aif31.pocket.data.PocketLedger
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.fx.ExchangeRateRepository
import com.aif31.pocket.fx.QuoteFailure
import com.aif31.pocket.ui.ChoiceOption
import com.aif31.pocket.ui.MoneyText
import com.aif31.pocket.ui.SegmentedChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Whether [period] is the current, latest period, the only one whose accounting currency can still change. */
internal fun LedgerState.canChangeCurrencyOf(period: Period?): Boolean =
    period != null && period == currentPeriod && period == periods.maxByOrNull { it.start }

/**
 * Chooses [period]'s accounting currency. Back to the currency it changed from is a reversal; any other currency is
 * quoted from the currency the period started with, on the period's first day, and converts the whole period.
 */
@Composable
internal fun PeriodCurrencyChooser(
    state: LedgerState,
    period: Period,
    ledger: PocketLedger,
    exchangeRates: ExchangeRateRepository?,
    onlineFxEnabled: Boolean,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val current = period.accountingCurrency
    val change = period.priorCurrencyBoundary
    val base = change?.from ?: current
    var chosen by rememberSaveable(period.id, current) { mutableStateOf<SupportedCurrency?>(null) }
    var quoteState by remember { mutableStateOf<CurrencyQuoteState>(CurrencyQuoteState.Idle) }
    var retry by remember { mutableIntStateOf(0) }

    fun execute(command: LedgerCommand, target: SupportedCurrency) {
        chosen = null
        scope.launch {
            when (val result = ledger.execute(command)) {
                LedgerResult.Success -> onMessage("Este periodo ahora está en ${target.name}")
                is LedgerResult.Rejected -> onMessage(result.message)
                is LedgerResult.Deleted -> Unit
            }
        }
    }

    Column {
        SegmentedChoice(
            options = SupportedCurrency.entries.map { ChoiceOption(it, it.name) },
            selected = current,
            onSelect = { target ->
                val pending = state.pendingCurrencyChange?.boundary
                when {
                    target == current -> Unit
                    pending != null && pending.to != target ->
                        onMessage("Cancela el cambio pendiente en Moneda y conversión para cambiar a ${target.name}.")
                    target != base && !onlineFxEnabled ->
                        onMessage("Activa la conversión en línea en Moneda y conversión para cambiar a ${target.name}.")
                    else -> chosen = target
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("period_currency"),
        )
        Text(
            change?.let { "Este periodo cambió de ${it.from.name} a ${it.to.name} el ${it.effectiveDate}." }
                ?: "Cambiar la moneda convierte los fondos, los presupuestos y los gastos de este periodo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val target = chosen ?: return
    if (change != null && target == change.from) {
        RevertCurrencyDialog(
            change = change,
            onConfirm = { execute(LedgerCommand.RevertCurrencyChange(period.id), target) },
            onDismiss = { chosen = null },
        )
        return
    }
    LaunchedEffect(target, base, period.start, retry) {
        val repository = exchangeRates
        if (repository == null) {
            quoteState = CurrencyQuoteState.Error(QuoteFailure.ConfigurationUnavailable().message.orEmpty())
            return@LaunchedEffect
        }
        quoteState = CurrencyQuoteState.Loading
        quoteState = try {
            val quote = repository.quote(period.start, base, target, forceRefresh = retry > 0)
            if (quote.base != base || quote.quote != target) throw QuoteFailure.Unavailable()
            CurrencyQuoteState.Ready(quote)
        } catch (error: CancellationException) {
            throw error
        } catch (error: QuoteFailure) {
            CurrencyQuoteState.Error(error.message.orEmpty())
        } catch (_: Exception) {
            CurrencyQuoteState.Error(QuoteFailure.Unavailable().message.orEmpty())
        }
    }
    val ready = (quoteState as? CurrencyQuoteState.Ready)?.quote?.takeIf { it.base == base && it.quote == target }
    AlertDialog(
        onDismissRequest = { chosen = null },
        title = { Text("¿Cambiar este periodo a ${target.name}?") },
        text = {
            Text(
                when {
                    ready != null ->
                        "Los fondos, los presupuestos y el rollover se convertirán a " +
                            "${MoneyText.rate(ready.base, ready.quote, ready.rate)} (${ready.source}, ${ready.effectiveDate}). " +
                            "Los gastos registrados en ${target.name} recuperan su importe; los de otras monedas quedan " +
                            "estimados hasta confirmar el tipo de cambio de su fecha."
                    quoteState is CurrencyQuoteState.Error -> (quoteState as CurrencyQuoteState.Error).message
                    else -> "Consultando el tipo de cambio de ${base.name} a ${target.name}…"
                },
            )
        },
        confirmButton = {
            when {
                ready != null -> TextButton(
                    onClick = {
                        execute(
                            LedgerCommand.ChangePeriodCurrency(period.id, target, ready.rate, ready.source, ready.effectiveDate),
                            target,
                        )
                    },
                ) { Text("Cambiar a ${target.name}") }
                quoteState is CurrencyQuoteState.Error -> TextButton(onClick = { retry++ }) { Text("Reintentar") }
                else -> Unit
            }
        },
        dismissButton = { TextButton(onClick = { chosen = null }) { Text("Cancelar") } },
    )
}
