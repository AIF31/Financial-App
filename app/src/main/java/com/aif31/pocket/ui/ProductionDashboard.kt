package com.aif31.pocket.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.PocketPeriodSummary
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun ActionableDashboardContent(
    state: LedgerState,
    contentPadding: PaddingValues,
    onRecordExpense: () -> Unit,
    onManagePockets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var supportingMetricsExpanded by rememberSaveable { mutableStateOf(false) }
    val activePockets = state.pockets
        .filterNot { it.pocket.archived }
        .sortedBy { summary ->
            when {
                summary.exhausted -> 0
                summary.atRisk -> 1
                else -> 2
            }
        }
    val period = state.currentPeriod
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
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Tu periodo", style = MaterialTheme.typography.titleLarge)
                Text(
                    period?.let { formatPeriod(it.start, it.endExclusive.minusDays(1)) } ?: "Sin periodo activo",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            AvailabilityHero(
                availabilityMinor = state.trackedAvailabilityMinor,
                daysRemaining = daysRemaining,
                status = spendingStatus,
                onRecordExpense = onRecordExpense,
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
                    value = formatSar(state.unallocatedMinor),
                    modifier = Modifier.weight(1f),
                )
                CompactMetric(
                    label = "Gastado",
                    value = formatSar(state.netSpendMinor),
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
                        formatSar(state.netSpendMinor / state.elapsedDays.coerceAtLeast(1)),
                    )
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                    SupportingMetric("Proyección estimada", formatSar(state.projectionMinor))
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                    SupportingMetric(
                        "Periodo anterior",
                        state.previousPeriodNetSpendMinor?.let(::formatSar)
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
    daysRemaining: Int,
    status: String,
    onRecordExpense: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Disponibilidad de Pockets", style = MaterialTheme.typography.labelLarge)
            Text(formatSar(availabilityMinor), style = MaterialTheme.typography.displaySmall)
            Text("Quedan $daysRemaining días")
            Text(status, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onRecordExpense,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
            ) {
                Text("Registrar gasto")
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
private fun PocketProgressRow(summary: PocketPeriodSummary, modifier: Modifier = Modifier) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.pocket.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatSar(summary.availabilityMinor)} disponibles",
                        fontFamily = FontFamily.Monospace,
                        color = status.color,
                    )
                    Text(
                        "Rollover: ${formatSar(summary.rolloverMinor)}",
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
                "${summary.consumedPercent}% consumido · Presupuesto ${formatSar(summary.budgetMinor)}",
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

private val sarFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

private fun formatSar(minor: Long): String =
    "SAR " + sarFormat.format(BigDecimal.valueOf(minor).movePointLeft(2))

private fun formatPeriod(start: java.time.LocalDate, end: java.time.LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))
    return "${start.format(formatter)} – ${end.format(formatter)}"
}
