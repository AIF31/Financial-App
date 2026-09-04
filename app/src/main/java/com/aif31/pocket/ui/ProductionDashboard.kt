package com.aif31.pocket.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aif31.pocket.R
import com.aif31.pocket.data.ComparisonMode
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.PocketPeriodSummary
import com.aif31.pocket.domain.SupportedCurrency
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun ActionableDashboardContent(
    state: LedgerState,
    contentPadding: PaddingValues,
    onManagePockets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var supportingMetricsExpanded by rememberSaveable { mutableStateOf(false) }
    val activePockets = state.pockets
        .filterNot { it.pocket.archived || it.retiredThisPeriod }
        .sortedBy { summary ->
            when {
                summary.exhausted -> 0
                summary.atRisk -> 1
                else -> 2
            }
        }
    val period = state.currentPeriod
    val accountingCurrency = period?.accountingCurrency ?: SupportedCurrency.SAR
    fun money(minor: Long): String = MoneyText.format(minor, accountingCurrency)
    val daysRemaining = (state.totalDays - state.elapsedDays).coerceAtLeast(0)
    val spendingStatus = when {
        state.newFundsMinor <= 0L -> "Asigna fondos para orientar el periodo"
        state.projectionMinor > state.newFundsMinor -> "Tu ritmo supera los fondos del periodo"
        else -> "Vas dentro del plan"
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tu periodo", style = MaterialTheme.typography.headlineMedium)
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                period?.let { formatPeriod(it.start, it.endExclusive.minusDays(1)) } ?: "Sin periodo activo",
                                color = MaterialTheme.colorScheme.primary,
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
                availabilityMinor = state.trackedAvailabilityMinor,
                accountingCurrency = accountingCurrency,
                daysRemaining = daysRemaining,
                status = spendingStatus,
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
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tus Pockets", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onManagePockets) { Text("Ver todos") }
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
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                )
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                TextButton(
                    onClick = { supportingMetricsExpanded = !supportingMetricsExpanded },
                    modifier = Modifier.semantics {
                        contentDescription = if (supportingMetricsExpanded) {
                            "Ocultar métricas del periodo"
                        } else {
                            "Mostrar métricas del periodo"
                        }
                    },
                ) {
                    Icon(
                        if (supportingMetricsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(if (supportingMetricsExpanded) "Ocultar más información" else "Más información")
                }
            }
        }
        if (supportingMetricsExpanded) {
            item {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                    SupportingMetric(
                        "Gasto diario promedio",
                        money(state.netSpendMinor / state.elapsedDays.coerceAtLeast(1)),
                    )
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                    SupportingMetric("Proyección estimada", money(state.projectionMinor))
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                    SupportingMetric(
                        if (state.comparisonMode == ComparisonMode.DAILY_PACE) {
                            "Ritmo diario del periodo anterior"
                        } else {
                            "Periodo anterior"
                        },
                        state.previousPeriodComparisonMinor?.let(::money)
                            ?: "Aún no existe un periodo anterior",
                    )
                }
            }
        }
    }
}

@Composable
private fun AvailabilityHero(
    availabilityMinor: Long,
    accountingCurrency: SupportedCurrency,
    daysRemaining: Int,
    status: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Disponible", style = MaterialTheme.typography.titleMedium)
            Text(MoneyText.format(availabilityMinor, accountingCurrency), style = MaterialTheme.typography.displaySmall)
            Text("Quedan $daysRemaining días", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Text(status, style = MaterialTheme.typography.bodyLarge)
            }
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun PocketProgressRow(
    summary: PocketPeriodSummary,
    accountingCurrency: SupportedCurrency,
    modifier: Modifier = Modifier,
) {
    val status = pocketStatus(summary)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    PocketArtwork(
                        summary.pocket.iconKey,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.pocket.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${MoneyText.format(summary.availabilityMinor, accountingCurrency)} disponibles",
                        fontFamily = FontFamily.Monospace,
                        color = status.color,
                    )
                    Text(
                        "Rollover: ${MoneyText.format(summary.rolloverMinor, accountingCurrency)}",
                        modifier = Modifier.testTag("rollover_${summary.pocket.name}"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(status.label, color = status.color, style = MaterialTheme.typography.labelMedium)
                }
            }
            LinearProgressIndicator(
                progress = { (summary.consumedPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = status.color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Text(
                "${summary.consumedPercent}% consumido · Presupuesto ${MoneyText.format(summary.budgetMinor, accountingCurrency)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun pocketStatus(summary: PocketPeriodSummary): PocketStatus = when {
    summary.exhausted -> PocketStatus(Icons.Default.Error, "Agotado", MaterialTheme.colorScheme.error)
    summary.atRisk -> PocketStatus(Icons.Default.Warning, "En riesgo", MaterialTheme.colorScheme.tertiary)
    else -> PocketStatus(Icons.Default.CheckCircle, "En buen ritmo", MaterialTheme.colorScheme.primary)
}

private data class PocketStatus(val icon: ImageVector, val label: String, val color: Color)

@Composable
private fun SupportingMetric(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace)
    }
}

private fun formatPeriod(start: java.time.LocalDate, end: java.time.LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))
    return "${start.format(formatter)} – ${end.format(formatter)}"
}
