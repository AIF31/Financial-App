package com.aif31.pocket.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aif31.pocket.R
import com.aif31.pocket.data.ComparisonMode
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.SpendPaceStatus
import com.aif31.pocket.data.budgetStatus
import com.aif31.pocket.data.PeriodComparison
import com.aif31.pocket.data.PeriodInsights
import com.aif31.pocket.data.PocketPeriodSummary
import com.aif31.pocket.domain.SupportedCurrency

@Composable
internal fun ActionableDashboardContent(
    state: LedgerState,
    contentPadding: PaddingValues,
    onManagePockets: () -> Unit,
    modifier: Modifier = Modifier,
    onRecordExpenseIn: (pocketId: String) -> Unit = {},
    onComparePeriods: () -> Unit = {},
) {
    val activePockets = state.pockets
        .filterNot { it.pocket.archived || it.retiredThisPeriod }
        .sortedBy { summary -> summary.budgetStatus.ordinal }
    val period = state.currentPeriod
    val accountingCurrency = period?.accountingCurrency ?: SupportedCurrency.SAR
    fun money(minor: Long): String = MoneyText.format(minor, accountingCurrency)
    val insights = remember(state) { period?.let { PeriodInsights.of(state, it.id) } }
    val comparison = remember(state) {
        period?.let { current ->
            PeriodComparison.previousPeriodId(state, current.id)?.let { PeriodComparison.of(state, current.id, it) }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    Text("Tu periodo", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            Text(
                                period?.let { formatPeriodRange(it.start, it.endExclusive) } ?: "Sin periodo activo",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    if (period?.isTransition == true) {
                        Text(
                            "Periodo de transición",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Image(
                    painter = painterResource(R.drawable.pocket_logo),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(CircleShape),
                )
            }
        }
        if (period?.needsReview == true) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Revisa el presupuesto de este periodo", style = MaterialTheme.typography.titleMedium)
                        Text("Se crearon periodos pendientes mientras la app estaba cerrada. Confirma que los importes siguen siendo correctos.")
                        Button(onClick = onManagePockets) { Text("Revisar Pockets") }
                    }
                }
            }
        }
        item {
            AvailabilityHero(
                state = state,
                insights = insights,
                comparison = comparison,
                accountingCurrency = accountingCurrency,
                onComparePeriods = onComparePeriods,
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CompactMetric(
                    label = "Sin asignar",
                    value = money(state.unallocatedMinor),
                    modifier = Modifier.weight(1f),
                )
                CompactMetric(
                    label = "Gastado",
                    value = money(state.netSpendMinor),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            // The whole header opens Pockets. A separate "Ver todos" button at the row's end would sit in the
            // floating action button's column, where it can scroll into place almost entirely covered.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = "Ver todos los Pockets", role = Role.Button, onClick = onManagePockets),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tus Pockets",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text("Ver todos", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (activePockets.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Aún no hay Pockets activos", style = MaterialTheme.typography.titleMedium)
                        Text("Crea un Pocket para asignar presupuesto y seguir su disponibilidad.")
                        TextButton(onClick = onManagePockets) { Text("Crear Pocket") }
                    }
                }
            }
        } else {
            items(activePockets, key = { it.pocket.id }) { summary ->
                PocketProgressRow(
                    summary = summary,
                    accountingCurrency = accountingCurrency,
                    onRecordExpense = { onRecordExpenseIn(summary.pocket.id) },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).animateItem(),
                )
            }
        }
    }
}

@Composable
private fun AvailabilityHero(
    state: LedgerState,
    insights: PeriodInsights?,
    comparison: PeriodComparison?,
    accountingCurrency: SupportedCurrency,
    onComparePeriods: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    fun money(minor: Long) = MoneyText.format(minor, accountingCurrency)
    val paceStatus = insights?.paceStatus ?: SpendPaceStatus.ON_PLAN
    val status = when (paceStatus) {
        SpendPaceStatus.NO_FUNDS -> "Asigna fondos para orientar el periodo"
        SpendPaceStatus.OVERSPENT -> "Gastaste más de lo asignado a tus Pockets"
        SpendPaceStatus.OVER_PACE -> "Tu ritmo supera los fondos del periodo"
        SpendPaceStatus.ON_PLAN -> "Vas dentro del plan"
    }
    val needsAttention = paceStatus == SpendPaceStatus.OVERSPENT || paceStatus == SpendPaceStatus.OVER_PACE
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        contentColor = onPrimary,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Disponible", style = MaterialTheme.typography.titleMedium)
                Text(money(state.trackedAvailabilityMinor), style = MaterialTheme.typography.displaySmall)
                Text(daysLeftText(insights?.remainingDays ?: (state.totalDays - state.elapsedDays).coerceAtLeast(0)), style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (needsAttention) Icons.Default.Warning else Icons.Default.CheckCircle, contentDescription = null)
                    Text(status, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (insights != null) {
                HorizontalDivider(color = onPrimary.copy(alpha = 0.24f))
                DisclosureRow(
                    expanded = detailsExpanded,
                    onExpandedChange = { detailsExpanded = it },
                    label = if (detailsExpanded) "Ocultar detalles" else "Ver detalles",
                    color = onPrimary,
                    modifier = Modifier.semantics {
                        contentDescription = if (detailsExpanded) "Ocultar métricas del periodo" else "Mostrar métricas del periodo"
                    },
                )
                AnimatedVisibility(
                    visible = detailsExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    HeroDetails(state, insights, comparison, onComparePeriods)
                }
            }
        }
    }
}

@Composable
private fun HeroDetails(
    state: LedgerState,
    insights: PeriodInsights,
    comparison: PeriodComparison?,
    onComparePeriods: () -> Unit,
) {
    val currency = insights.accountingCurrency
    fun money(minor: Long) = MoneyText.format(minor, currency)
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val muted = onPrimary.copy(alpha = 0.8f)
    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Ritmo de gasto", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        SpendPaceMetrics(
            insights = insights,
            color = onPrimary,
            supportingColor = muted,
            barColor = onPrimary,
            trackColor = onPrimary.copy(alpha = 0.2f),
        )
        Spacer(Modifier.height(4.dp))
        Text("Frente al periodo anterior", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        val baseline = comparison?.convertedBaseline
        when {
            comparison == null -> Text("Aún no existe un periodo anterior", color = muted)
            // No frozen rate links the currencies: the previous pace stays in its own currency, with no difference.
            baseline == null -> MetricRow(
                label = "Promedio diario del periodo anterior",
                value = comparison.baseline.averageDailySpendMinor
                    ?.let { MoneyText.format(it, comparison.baseline.accountingCurrency) } ?: "—",
                supporting = "Usa otra moneda sin un tipo congelado; no se calcula la diferencia.",
                color = onPrimary,
                supportingColor = muted,
            )
            else -> MetricRow(
                label = "Promedio diario del periodo anterior",
                value = baseline.averageDailySpendMinor?.let(::money) ?: "—",
                supporting = baseline.averageDailyDeltaMinor?.let { spendDeltaText(it, baseline.averageDailyDeltaPercent, currency) },
                color = onPrimary,
                supportingColor = onPrimary,
            )
        }
        // The ledger's own previous-period figure: total spend, or daily pace for a transition period (already shown above).
        if (state.comparisonMode == ComparisonMode.TOTAL_SPEND) {
            state.previousPeriodComparisonMinor?.let { previousTotal ->
                MetricRow(
                    label = "Gasto total del periodo anterior",
                    value = money(previousTotal),
                    supporting = "Llevas ${money(insights.netSpendMinor)} en este periodo",
                    color = onPrimary,
                    supportingColor = muted,
                )
            }
        }
        Button(
            onClick = onComparePeriods,
            colors = ButtonDefaults.buttonColors(containerColor = onPrimary, contentColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Comparar periodos")
        }
    }
}

@Composable
private fun CompactMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun PocketProgressRow(
    summary: PocketPeriodSummary,
    accountingCurrency: SupportedCurrency,
    onRecordExpense: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = summary.budgetStatus
    val colors = status.presentation()
    val available = availableText(summary.availabilityMinor, accountingCurrency)
    val rollover = "Rollover: ${MoneyText.format(summary.rolloverMinor, accountingCurrency)}"
    val consumption = "${summary.consumedPercent}% consumido · Presupuesto ${MoneyText.format(summary.budgetMinor, accountingCurrency)}"
    // The card's label already says all of this; exposing the pieces too would make screen readers repeat it.
    val coveredByLabel = Modifier.semantics { hideFromAccessibility() }
    // The whole card records a spend in this Pocket. A separate small button at the row's end would sit in the
    // floating action button's column, where a partly covered target is too small to use reliably.
    Card(
        modifier = modifier
            .clip(CardDefaults.shape)
            .clickable(onClickLabel = "Registrar gasto en ${summary.pocket.name}", onClick = onRecordExpense)
            // A card scrolled partly out of view exposes only its visible children, so it carries its own label.
            .semantics { contentDescription = listOf(summary.pocket.name, available.text, rollover, consumption, colors.label).joinToString(". ") }
            .testTag("pocket_row_${summary.pocket.name}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PocketArtworkPlate(summary.pocket.iconKey, plateSize = 52.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(summary.pocket.name, style = MaterialTheme.typography.titleMedium, modifier = coveredByLabel)
                    Text(
                        available,
                        color = if (summary.availabilityMinor < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = coveredByLabel,
                    )
                    Text(
                        rollover,
                        modifier = coveredByLabel.testTag("rollover_${summary.pocket.name}"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(8.dp))
                }
            }
            LinearProgressIndicator(
                progress = { (summary.consumedPercent / 100f).coerceIn(0f, 1f) },
                modifier = coveredByLabel.fillMaxWidth().height(6.dp),
                color = colors.indicator,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    consumption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = coveredByLabel.weight(1f),
                )
                PocketStatusBadge(status, coveredByLabel)
            }
        }
    }
}
