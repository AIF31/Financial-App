package com.aif31.pocket.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.PeriodInsights
import com.aif31.pocket.data.PocketComparisonRow
import com.aif31.pocket.domain.SupportedCurrency
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val spanish = Locale.forLanguageTag("es")
private val shortDate = DateTimeFormatter.ofPattern("d MMM", spanish)
private val shortDateWithYear = DateTimeFormatter.ofPattern("d MMM yyyy", spanish)

/**
 * "25 feb – 24 mar 2026", or "25 dic 2026 – 24 ene 2027" across a year change. The year is always present so
 * the same dates in different years stay distinguishable in pickers and editors.
 */
internal fun formatPeriodRange(start: LocalDate, endExclusive: LocalDate): String {
    val end = endExclusive.minusDays(1)
    val startText = start.format(if (start.year == end.year) shortDate else shortDateWithYear)
    return "$startText – ${end.format(shortDateWithYear)}"
}

internal fun daysLeftText(days: Int): String = when (days) {
    0 -> "Último día del periodo"
    1 -> "Queda 1 día"
    else -> "Quedan $days días"
}

/** A Movement's merchant when recorded, otherwise its Pocket. */
internal val Movement.merchantOrPocket: String
    get() = merchant?.takeIf(String::isNotBlank) ?: pocketName

/**
 * Describes a daily-average change in words and arrows so meaning never depends on color. The deltas come
 * precomputed from [com.aif31.pocket.data.PeriodComparison].
 */
internal fun spendDeltaText(deltaMinor: Long, deltaPercent: Int?, currency: SupportedCurrency): String {
    if (deltaMinor == 0L) return "Sin cambio en el promedio diario"
    val arrow = if (deltaMinor > 0) "▲" else "▼"
    val direction = if (deltaMinor > 0) "más" else "menos"
    val percent = deltaPercent?.let { " (${if (it > 0) "+" else ""}$it%)" }.orEmpty()
    return "$arrow ${MoneyText.format(abs(deltaMinor), currency)} $direction al día$percent"
}

/** Label/value row used by the dashboard details, period cards, and the comparison screen. */
@Composable
internal fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    color: Color = Color.Unspecified,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = color, style = MaterialTheme.typography.bodyLarge)
            supporting?.let { Text(it, color = supportingColor, style = MaterialTheme.typography.bodySmall) }
        }
        Text(value, color = color, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
    }
}

/** Full-width expand/collapse control with a 48 dp target, chevron, and announced state. */
@Composable
internal fun DisclosureRow(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = expanded, role = Role.Button, onValueChange = onExpandedChange)
            .semantics { stateDescription = if (expanded) "Expandido" else "Contraído" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.weight(1f))
        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = color)
    }
}

/**
 * The spend-pace block shared by the dashboard details and the Pockets period view: average daily spend,
 * spend available per remaining day, projection, and budget used against time elapsed.
 */
@Composable
internal fun SpendPaceMetrics(
    insights: PeriodInsights,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    fun money(minor: Long) = MoneyText.format(minor, insights.accountingCurrency)
    Column(modifier) {
        MetricRow(
            label = "Gasto diario promedio",
            value = insights.averageDailySpendMinor?.let(::money) ?: "—",
            supporting = "${insights.elapsedDays} de ${insights.totalDays} días",
            color = color,
            supportingColor = supportingColor,
        )
        if (insights.inProgress) {
            MetricRow(
                label = "Puedes gastar al día",
                value = money(insights.safeDailySpendMinor ?: 0),
                supporting = if (insights.safeDailySpendMinor == null) "No queda disponible en tus Pockets" else "Hasta el final del periodo, incluido hoy",
                color = color,
                supportingColor = supportingColor,
            )
        }
        insights.projectedSpendMinor?.let {
            MetricRow("Proyección estimada", money(it), supporting = "Si mantienes el ritmo actual", color = color, supportingColor = supportingColor)
        }
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            insights.budgetUsedPercent?.let { LabeledBar("Presupuesto + rollover usado", it, barColor, trackColor, color) }
            LabeledBar("Periodo transcurrido", insights.periodElapsedPercent, barColor.copy(alpha = 0.55f), trackColor, color)
        }
    }
}

@Composable
private fun LabeledBar(label: String, percent: Int, color: Color, track: Color, textColor: Color) {
    val resolvedText = if (textColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else textColor
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = resolvedText, style = MaterialTheme.typography.labelMedium)
            Text("$percent%", color = resolvedText, style = MaterialTheme.typography.labelMedium)
        }
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = track,
            drawStopIndicator = {},
        )
    }
}

private enum class LineStyle { SOLID, DASHED, DOTTED }

private fun DrawScope.pathEffect(style: LineStyle): PathEffect? = when (style) {
    LineStyle.SOLID -> null
    LineStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    LineStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
}

/**
 * Cumulative net spending by day. The chosen period is a solid line, the comparison period is dashed, and
 * Pocket budgets plus rollover are a dotted guide, so the three remain distinguishable without color.
 */
@Composable
internal fun CumulativeSpendChart(
    current: List<Long>,
    baseline: List<Long>?,
    totalDays: Int,
    availableBudgetMinor: Long?,
    currency: SupportedCurrency,
    modifier: Modifier = Modifier,
    currentLabel: String = "Este periodo",
    baselineLabel: String = "Periodo anterior",
    footnote: String? = null,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outline
    val budgetColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val budgetLabel = "Presupuesto + rollover"
    val days = maxOf(totalDays, baseline?.size ?: 0, current.size, 1)
    val budget = availableBudgetMinor?.takeIf { it > 0 }
    val maxValue = listOfNotNull(current.maxOrNull(), baseline?.maxOrNull(), budget).maxOrNull()?.coerceAtLeast(1) ?: 1
    val minValue = minOf(0L, current.minOrNull() ?: 0L, baseline?.minOrNull() ?: 0L)
    val summary = buildString {
        append("Gráfica de gasto acumulado. ")
        current.lastOrNull()?.let { append("$currentLabel: ${MoneyText.format(it, currency)} en ${current.size} días. ") }
        baseline?.let { curve ->
            val sameDay = curve.getOrNull(current.size - 1)
            if (sameDay != null && current.isNotEmpty()) {
                append("$baselineLabel al mismo día: ${MoneyText.format(sameDay, currency)}. ")
            }
            curve.lastOrNull()?.let { append("$baselineLabel completo: ${MoneyText.format(it, currency)}. ") }
        }
        budget?.let { append("$budgetLabel: ${MoneyText.format(it, currency)}.") }
    }
    val axisStyle = MaterialTheme.typography.labelSmall
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(MoneyText.format(maxValue, currency), style = axisStyle, color = axisColor, modifier = Modifier.clearAndSetSemantics { })
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clearAndSetSemantics { contentDescription = summary },
        ) {
            val range = (maxValue - minValue).toFloat().coerceAtLeast(1f)
            fun x(day: Int) = size.width * day / days.toFloat()
            fun y(value: Long) = size.height - size.height * (value - minValue) / range
            for (step in 0..4) {
                val gy = size.height * step / 4f
                drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            }
            budget?.let {
                drawLine(
                    budgetColor,
                    Offset(0f, y(it)),
                    Offset(size.width, y(it)),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = pathEffect(LineStyle.DOTTED),
                    cap = StrokeCap.Round,
                )
            }
            fun curvePath(values: List<Long>): Path = Path().apply {
                moveTo(0f, y(0))
                values.forEachIndexed { index, value -> lineTo(x(index + 1), y(value)) }
            }
            baseline?.takeIf { it.isNotEmpty() }?.let {
                drawPath(curvePath(it), baselineColor, style = Stroke(width = 2.dp.toPx(), pathEffect = pathEffect(LineStyle.DASHED)))
            }
            if (current.isNotEmpty()) {
                drawPath(curvePath(current), lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x(current.size), y(current.last())))
            }
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Día 1", style = axisStyle, color = axisColor)
            Text("Día $days", style = axisStyle, color = axisColor)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics { },
        ) {
            LegendItem(currentLabel, lineColor, LineStyle.SOLID)
            if (baseline != null) LegendItem(baselineLabel, baselineColor, LineStyle.DASHED)
            if (budget != null) LegendItem(budgetLabel, budgetColor, LineStyle.DOTTED)
        }
        footnote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = axisColor) }
    }
}

/**
 * Explains where the current period's curve stops: it runs through today, so Movements dated later in the
 * period are in the totals but not yet on the curve. Null for closed periods.
 */
internal fun PeriodInsights.chartCutoffNote(): String? {
    if (!inProgress) return null
    val throughToday = "Hasta hoy, día $elapsedDays de $totalDays."
    if (netSpendAfterTodayMinor == 0L) return throughToday
    return "$throughToday No incluye ${MoneyText.format(netSpendAfterTodayMinor, accountingCurrency)} " +
        "netos con fecha posterior a hoy, que sí cuentan en el gasto neto."
}

@Composable
private fun LegendItem(label: String, color: Color, style: LineStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.width(20.dp).height(8.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 3.dp.toPx(), pathEffect = pathEffect(style))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Paired horizontal bars per Pocket: the chosen period (filled) against the comparison period (outlined tone). */
@Composable
internal fun PocketComparisonBars(
    rows: List<PocketComparisonRow>,
    currency: SupportedCurrency,
    modifier: Modifier = Modifier,
) {
    val maxValue = rows.maxOfOrNull { maxOf(it.currentAverageDailyMinor ?: 0, it.baselineAverageDailyMinor ?: 0) }?.coerceAtLeast(1) ?: 1
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { row ->
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PocketArtworkPlate(row.pocket.iconKey, plateSize = 32.dp)
                    Text(row.pocket.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        row.averageDailyDeltaMinor?.let { spendDeltaText(it, row.averageDailyDeltaPercent, currency) } ?: "Sin comparación",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ComparisonBar("Elegido", row.currentAverageDailyMinor ?: 0, maxValue, MaterialTheme.colorScheme.primary, currency)
                row.baselineAverageDailyMinor?.let { ComparisonBar("Comparado", it, maxValue, MaterialTheme.colorScheme.outline, currency) }
            }
        }
    }
}

@Composable
private fun ComparisonBar(label: String, value: Long, maxValue: Long, color: Color, currency: SupportedCurrency) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(72.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(
            Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            drawRect(color.copy(alpha = 0.14f))
            val fraction = (value.coerceAtLeast(0).toFloat() / maxValue).coerceIn(0f, 1f)
            drawRect(color, size = size.copy(width = size.width * fraction))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "${MoneyText.format(value, currency)}/día",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
