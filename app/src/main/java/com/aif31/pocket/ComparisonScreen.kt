package com.aif31.pocket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.Period
import com.aif31.pocket.data.PeriodComparison
import com.aif31.pocket.data.PeriodInsights
import com.aif31.pocket.ui.CumulativeSpendChart
import com.aif31.pocket.ui.MoneyText
import com.aif31.pocket.ui.PocketComparisonBars
import com.aif31.pocket.ui.PocketTopAppBar
import com.aif31.pocket.ui.chartCutoffNote
import com.aif31.pocket.ui.formatPeriodRange
import com.aif31.pocket.ui.merchantOrPocket
import androidx.compose.foundation.layout.Spacer
import com.aif31.pocket.ui.spendDeltaText

/**
 * Focused comparison task: two periods side by side, compared by average daily spending so a period in
 * progress can be measured fairly against a closed one.
 */
@Composable
internal fun ComparisonScreen(
    state: LedgerState,
    periodId: String,
    baselinePeriodId: String?,
    onPeriodsChange: (periodId: String, baselinePeriodId: String?) -> Unit,
    onBack: () -> Unit,
) {
    val comparison = baselinePeriodId?.let { PeriodComparison.of(state, periodId, it) }
    val insights = comparison?.current ?: PeriodInsights.of(state, periodId)
    Scaffold(
        topBar = {
            PocketTopAppBar(title = "Comparar periodos", onNavigateBack = onBack)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("comparison_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val bounded = Modifier.fillMaxWidth().widthIn(max = 760.dp)
            item {
                Row(bounded, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PeriodPicker(
                        label = "Periodo",
                        periods = state.periods,
                        selectedId = periodId,
                        onSelect = { onPeriodsChange(it, baselinePeriodId?.takeIf { base -> base != it }) },
                        modifier = Modifier.weight(1f),
                    )
                    PeriodPicker(
                        label = "Comparado con",
                        periods = state.periods.filter { it.id != periodId },
                        selectedId = baselinePeriodId,
                        onSelect = { onPeriodsChange(periodId, it) },
                        modifier = Modifier.weight(1f),
                        buttonModifier = Modifier.testTag("comparison_baseline"),
                    )
                }
            }
            if (insights == null) return@LazyColumn
            val currency = insights.accountingCurrency
            val baseline = comparison?.convertedBaseline
            item {
                Card(
                    bounded,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(20.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Gasto diario promedio", style = MaterialTheme.typography.titleMedium)
                        Text(
                            insights.averageDailySpendMinor?.let { MoneyText.format(it, currency) } ?: "Sin días transcurridos",
                            style = MaterialTheme.typography.displaySmall,
                        )
                        when {
                            comparison == null -> Text("Elige un periodo para comparar.")
                            baseline == null -> {
                                // No frozen rate links the two currencies: each pace stays in its own currency, with no
                                // difference computed between them.
                                comparison.baseline.averageDailySpendMinor?.let {
                                    Text("Periodo comparado: ${MoneyText.format(it, comparison.baseline.accountingCurrency)} al día")
                                }
                                Text(
                                    "Los periodos usan monedas distintas sin un tipo congelado entre ellos; " +
                                        "se muestran por separado, sin diferencia.",
                                )
                            }
                            else -> {
                                baseline.averageDailySpendMinor?.let {
                                    Text("Periodo comparado: ${MoneyText.format(it, currency)} al día")
                                }
                                baseline.averageDailyDeltaMinor?.let {
                                    Text(
                                        spendDeltaText(it, baseline.averageDailyDeltaPercent, currency),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(bounded) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Gasto acumulado por día", style = MaterialTheme.typography.titleMedium)
                        CumulativeSpendChart(
                            current = insights.cumulativeNetSpendByDayMinor,
                            baseline = baseline?.cumulativeNetSpendByDayMinor,
                            totalDays = insights.totalDays,
                            availableBudgetMinor = insights.availableBudgetMinor,
                            currency = currency,
                            currentLabel = "Periodo elegido",
                            baselineLabel = "Comparado",
                            footnote = insights.chartCutoffNote(),
                        )
                    }
                }
            }
            item {
                Card(bounded) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Resumen", style = MaterialTheme.typography.titleMedium)
                        ComparisonTable(insights, comparison?.baseline)
                    }
                }
            }
            comparison?.takeIf { baseline != null && it.pockets.isNotEmpty() }?.let {
                item {
                    Card(bounded) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Gasto diario promedio por Pocket", style = MaterialTheme.typography.titleMedium)
                            PocketComparisonBars(it.pockets, currency)
                        }
                    }
                }
            }
        }
    }
}

/** One summary line; [amount] rows use the tabular monospace face, counts do not. */
private class SummaryRow(val label: String, val amount: Boolean, val value: (PeriodInsights) -> String)

@Composable
private fun ComparisonTable(current: PeriodInsights, baseline: PeriodInsights?) {
    fun money(value: Long, insights: PeriodInsights) = MoneyText.format(value, insights.accountingCurrency)
    val rows = listOf(
        SummaryRow("Gasto diario promedio", amount = true) { insights ->
            insights.averageDailySpendMinor?.let { money(it, insights) } ?: "—"
        },
        SummaryRow("Fondos nuevos", amount = true) { money(it.newFundsMinor, it) },
        SummaryRow("Asignado a Pockets", amount = true) { money(it.budgetedMinor, it) },
        SummaryRow("Rollover recibido", amount = true) { money(it.rolloverMinor, it) },
        SummaryRow("Gastos", amount = true) { money(it.expenseMinor, it) },
        SummaryRow("Devoluciones", amount = true) { money(it.refundMinor, it) },
        SummaryRow("Gasto neto", amount = true) { money(it.netSpendMinor, it) },
        SummaryRow("Disponible", amount = true) { money(it.availabilityMinor, it) },
        SummaryRow("Días", amount = false) { "${it.elapsedDays} de ${it.totalDays}" },
        SummaryRow("Gastos registrados", amount = false) { it.expenseCount.toString() },
        SummaryRow("Mayor gasto", amount = true) { insights -> insights.largestExpense?.let { money(it.accountingAmountMinor, insights) } ?: "—" },
    )
    val currentName = formatPeriodRange(current.period.start, current.period.endExclusive)
    val baselineName = baseline?.let { formatPeriodRange(it.period.start, it.period.endExclusive) }
    val style = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        // Beside the label, each value column gets 1 part of 2.1 (or 3.1) of the width. When a number such as
        // "7,500.00" cannot fit there, as with large fonts, labels move above their values so the numbers get
        // the full width instead of wrapping mid-number.
        val valueColumns = if (baseline == null) 1 else 2
        val besideLabelWidth = constraints.maxWidth / (LABEL_WEIGHT + valueColumns)
        val widestNumber = rows.filter { it.amount }
            .flatMap { row -> listOfNotNull(row.value(current), baseline?.let(row.value)) }
            .flatMap { it.split(' ') }
            .maxOf { measurer.measure(it, style.copy(fontFamily = FontFamily.Monospace)).size.width }
        val stacked = widestNumber > besideLabelWidth
        Column {
            TableHeader(currentName, baselineName, stacked)
            HorizontalDivider()
            rows.forEach { row ->
                val currentValue = row.value(current)
                val baselineValue = baseline?.let(row.value)
                // Screen readers do not see the column headers, so each value is announced with its period.
                val spoken = buildString {
                    append("${row.label}. $currentName: $currentValue")
                    if (baselineName != null && baselineValue != null) append(". $baselineName: $baselineValue")
                }
                TableRow(row.label, currentValue, baselineValue, if (row.amount) FontFamily.Monospace else null, spoken, stacked)
            }
            current.largestExpense?.let { movement ->
                Text(
                    "Mayor gasto del periodo elegido: ${movement.merchantOrPocket}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private const val LABEL_WEIGHT = 1.1f

/** Cells repeat the row's spoken description, so screen readers would otherwise hear each row twice. */
private val CoveredByRowDescription = Modifier.semantics { hideFromAccessibility() }

@Composable
private fun TableHeader(current: String, baseline: String?, stacked: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!stacked) Spacer(Modifier.weight(LABEL_WEIGHT))
        Text(current, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        if (baseline != null) {
            Text(baseline, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun TableRow(
    label: String,
    current: String,
    baseline: String?,
    valueFont: FontFamily?,
    spokenDescription: String,
    stacked: Boolean,
) {
    val style = MaterialTheme.typography.bodyMedium
    // The row is announced once, with its spoken description in place of the individual cells.
    val rowSemantics = Modifier.semantics(mergeDescendants = true) { contentDescription = spokenDescription }
    if (stacked) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).then(rowSemantics)) {
            Text(label, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = CoveredByRowDescription)
            Row(Modifier.fillMaxWidth()) { TableValues(label, current, baseline, valueFont, style) }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).then(rowSemantics),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = style, modifier = CoveredByRowDescription.weight(LABEL_WEIGHT), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TableValues(label, current, baseline, valueFont, style)
    }
}

@Composable
private fun RowScope.TableValues(label: String, current: String, baseline: String?, valueFont: FontFamily?, style: TextStyle) {
    Text(
        current,
        style = style,
        modifier = CoveredByRowDescription.weight(1f).testTag("comparison_value_current_$label"),
        textAlign = TextAlign.End,
        fontFamily = valueFont,
    )
    if (baseline != null) {
        Text(
            baseline,
            style = style,
            modifier = CoveredByRowDescription.weight(1f).testTag("comparison_value_baseline_$label"),
            textAlign = TextAlign.End,
            fontFamily = valueFont,
        )
    }
}

@Composable
private fun PeriodPicker(
    label: String,
    periods: List<Period>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = periods.firstOrNull { it.id == selectedId }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = periods.isNotEmpty(),
                modifier = buttonModifier.fillMaxWidth(),
            ) {
                Text(
                    selected?.let { formatPeriodRange(it.start, it.endExclusive) } ?: "Elegir",
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                periods.sortedByDescending { it.start }.forEach { period ->
                    DropdownMenuItem(
                        text = { Text(formatPeriodRange(period.start, period.endExclusive) + " · ${period.accountingCurrency.name}") },
                        onClick = {
                            expanded = false
                            onSelect(period.id)
                        },
                    )
                }
            }
        }
    }
}
