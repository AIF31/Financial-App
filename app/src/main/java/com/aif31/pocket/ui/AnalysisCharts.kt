package com.aif31.pocket.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.medianMinor
import com.aif31.pocket.domain.SupportedCurrency
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private val spanish = Locale.forLanguageTag("es")

/** "lun", "mar", … in Spanish. */
internal fun DayOfWeek.shortSpanish(): String = getDisplayName(DateTextStyle.SHORT, spanish).trimEnd('.')

/** "los lunes", "los sábados": the weekday as a recurring day, in Spanish. */
internal fun DayOfWeek.recurringSpanish(): String {
    val name = getDisplayName(DateTextStyle.FULL, spanish)
    return "los " + if (name.endsWith("s")) name else name + "s"
}

/** One labeled bar in [TrendBars]. */
internal class TrendBar(val label: String, val valueMinor: Long?, val selected: Boolean)

/** A dashed horizontal reference across [TrendBars], such as the median of earlier periods, labeled at its left end. */
internal class TrendReference(val valueMinor: Long, val label: String)

/**
 * One bar per period, the selected period at full strength and the rest lighter. Every bar is the same series, so a
 * single hue carries it and the selected bar is also named in the caption above. With [valueLabel], each bar shows its
 * value above it when every value fits its bar's width; otherwise only the selected bar does. Tapping a bar selects its
 * period; screen readers get one action per bar instead.
 */
@Composable
internal fun TrendBars(
    bars: List<TrendBar>,
    description: String,
    onSelect: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: ((Long) -> String)? = null,
    reference: TrendReference? = null,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val referenceColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedValueColor = MaterialTheme.colorScheme.onSurface
    val valueStyle = MaterialTheme.typography.labelMedium
    val referenceStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val maxValue = maxOf(bars.maxOfOrNull { it.valueMinor ?: 0 } ?: 0, reference?.valueMinor ?: 0).coerceAtLeast(1)
    // The tap handler outlives recompositions with the same bar count, so it reads the latest selection mapping.
    val currentOnSelect by rememberUpdatedState(onSelect)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(148.dp)
                .testTag("trend_bars")
                .clearAndSetSemantics {
                    contentDescription = description
                    customActions = bars.mapIndexed { index, bar ->
                        CustomAccessibilityAction("Ver periodo ${bar.label}") { currentOnSelect(index); true }
                    }
                }
                .pointerInput(bars.size) {
                    detectTapGestures { offset ->
                        val index = (offset.x / (size.width / bars.size.toFloat())).toInt().coerceIn(0, bars.lastIndex)
                        currentOnSelect(index)
                    }
                },
        ) {
            val slot = size.width / bars.size
            val gap = 2.dp.toPx().coerceAtLeast(slot * 0.25f)
            val radius = 4.dp.toPx()
            val labelGap = 4.dp.toPx()
            val labels = bars.map { bar ->
                if (valueLabel == null || bar.valueMinor == null) return@map null
                val weight = if (bar.selected) FontWeight.Medium else null
                measurer.measure(valueLabel(bar.valueMinor), valueStyle.copy(fontWeight = weight), maxLines = 1, softWrap = false)
            }
            val everyLabelFits = labels.all { it == null || it.size.width <= slot - gap / 2 }
            // The tallest bar leaves room above it for its value.
            val labelSpace = labels.maxOfOrNull { it?.size?.height ?: 0 }?.takeIf { it > 0 }?.let { it + labelGap } ?: 0f
            val plotHeight = size.height - labelSpace
            fun topOf(valueMinor: Long) = size.height - plotHeight * valueMinor.coerceAtLeast(0) / maxValue
            bars.forEachIndexed { index, bar ->
                val top = topOf(bar.valueMinor ?: 0)
                if (top >= size.height) return@forEachIndexed
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = index * slot + gap / 2,
                            top = top,
                            right = (index + 1) * slot - gap / 2,
                            bottom = size.height,
                            topLeftCornerRadius = CornerRadius(radius),
                            topRightCornerRadius = CornerRadius(radius),
                        ),
                    )
                }
                drawPath(path, if (bar.selected) barColor else barColor.copy(alpha = UNSELECTED_BAR_ALPHA))
            }
            reference?.let {
                val y = topOf(it.valueMinor)
                drawLine(
                    referenceColor,
                    Offset(0f, y),
                    Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
                val text = measurer.measure(it.label, referenceStyle, maxLines = 1, softWrap = false)
                val textTop = (y - text.size.height - 2.dp.toPx()).coerceAtLeast(0f)
                // A patch of the card behind the label keeps it readable where it crosses a bar.
                drawRect(surfaceColor, Offset(0f, textTop), Size(text.size.width + 4.dp.toPx(), text.size.height.toFloat()))
                drawText(text, color = valueColor, topLeft = Offset(0f, textTop))
            }
            bars.forEachIndexed { index, bar ->
                val label = labels[index] ?: return@forEachIndexed
                if (!everyLabelFits && !bar.selected) return@forEachIndexed
                val x = (index * slot + slot / 2 - label.size.width / 2f).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f))
                val top = topOf(bar.valueMinor ?: 0)
                drawText(label, color = if (bar.selected) selectedValueColor else valueColor, topLeft = Offset(x, top - labelGap - label.size.height))
            }
            drawLine(baselineColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        PeriodLabels(bars.map { it.label }, selectedIndex = bars.indexOfFirst { it.selected })
    }
}

/** Unselected bars stay visible against the card while the selected one stands out. */
private const val UNSELECTED_BAR_ALPHA = 0.55f

/** One label per period, centred under its slot; the selected period's label is emphasized. Decorative. */
@Composable
internal fun PeriodLabels(labels: List<String>, selectedIndex: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clearAndSetSemantics { }) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Medium else null,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * The range a relative line spans: from the lowest to the highest of [values], widened evenly to at least 10% of
 * the highest so a nearly flat series stays nearly flat instead of turning cents into swings.
 */
internal fun relativeRange(values: List<Long>): Pair<Double, Double> {
    val low = values.minOrNull()?.toDouble() ?: 0.0
    val high = values.maxOrNull()?.toDouble() ?: 0.0
    val floor = (high * 0.1).coerceAtLeast(1.0)
    if (high - low >= floor) return low to high
    val pad = (floor - (high - low)) / 2
    return low - pad to high + pad
}

/**
 * One Pocket's values per period as a line fitted to its own [relativeRange], with its median as a dashed reference
 * and the last point marked. Points sit at the centres of [PeriodLabels]' slots. A missing value breaks the line.
 * Decorative: the surrounding row speaks the values.
 */
@Composable
internal fun RelativeLine(values: List<Long?>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val referenceColor = MaterialTheme.colorScheme.outline
    val ringColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val present = values.filterNotNull()
    Canvas(modifier.height(44.dp).clearAndSetSemantics { }) {
        if (present.isEmpty()) return@Canvas
        val (low, high) = relativeRange(present)
        val inset = 6.dp.toPx()
        fun x(index: Int) = (index + 0.5f) * size.width / values.size
        fun y(value: Long) = (inset + (size.height - inset * 2) * (1 - (value - low) / (high - low))).toFloat()
        val medianY = y(medianMinor(present)!!)
        drawLine(
            referenceColor,
            Offset(0f, medianY),
            Offset(size.width, medianY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )
        val line = Path()
        val area = Path()
        var segmentStart: Int? = null
        fun closeArea(end: Int) {
            val start = segmentStart ?: return
            area.lineTo(x(end), medianY)
            area.lineTo(x(start), medianY)
            area.close()
        }
        values.forEachIndexed { index, value ->
            if (value == null) {
                closeArea(index - 1)
                segmentStart = null
            } else if (segmentStart == null) {
                line.moveTo(x(index), y(value))
                area.moveTo(x(index), y(value))
                segmentStart = index
            } else {
                line.lineTo(x(index), y(value))
                area.lineTo(x(index), y(value))
            }
        }
        closeArea(values.lastIndex)
        drawPath(area, lineColor.copy(alpha = 0.12f))
        drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val center = Offset(x(index), y(value))
            if (index == values.lastIndex) {
                drawCircle(ringColor, radius = 7.dp.toPx(), center = center)
                drawCircle(lineColor, radius = 5.dp.toPx(), center = center)
            } else {
                drawCircle(lineColor.copy(alpha = 0.7f), radius = 2.dp.toPx(), center = center)
            }
        }
    }
}

/**
 * A horizontal track filled to [fraction], with a tick at [markerFraction] for the value it is compared against. The
 * tick stands taller than the track, so it reads on the fill and on the empty track alike, and any value above zero
 * keeps a sliver of fill. Tracks in one list share a scale, so their lengths compare across rows. Decorative: the
 * surrounding row speaks the values.
 */
@Composable
internal fun ComparisonTrack(
    fraction: Float,
    markerFraction: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    markerColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier.height(18.dp).clearAndSetSemantics { }) {
        val trackHeight = 12.dp.toPx()
        val top = (size.height - trackHeight) / 2
        val corner = CornerRadius(trackHeight / 2)
        drawRoundRect(color.copy(alpha = TRACK_ALPHA), topLeft = Offset(0f, top), size = Size(size.width, trackHeight), cornerRadius = corner)
        if (fraction > 0f) {
            val fill = (size.width * fraction.coerceAtMost(1f)).coerceAtLeast(MIN_FILL.toPx())
            drawRoundRect(color, topLeft = Offset(0f, top), size = Size(fill, trackHeight), cornerRadius = corner)
        }
        markerFraction?.let {
            val markerWidth = 2.dp.toPx()
            val x = (size.width * it.coerceIn(0f, 1f)).coerceIn(markerWidth / 2, size.width - markerWidth / 2)
            drawRoundRect(
                markerColor,
                topLeft = Offset(x - markerWidth / 2, 0f),
                size = Size(markerWidth, size.height),
                cornerRadius = CornerRadius(markerWidth / 2),
            )
        }
    }
}

private const val TRACK_ALPHA = 0.16f
private val MIN_FILL = 3.dp

/** Names the fill and the tick of a list of [ComparisonTrack]s. Hidden from screen readers, which hear each row's values. */
@Composable
internal fun ComparisonLegend(fillLabel: String, markerLabel: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.onSurface
    FlowRow(
        modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendKey(fillLabel) {
            Canvas(Modifier.width(22.dp).height(14.dp)) {
                val height = 8.dp.toPx()
                drawRoundRect(color, Offset(0f, (size.height - height) / 2), Size(size.width, height), CornerRadius(height / 2))
            }
        }
        // A piece of empty track crossed by the tick, so the key reads as a marker on a track rather than a divider.
        LegendKey(markerLabel) {
            Canvas(Modifier.width(22.dp).height(14.dp)) {
                val height = 8.dp.toPx()
                drawRoundRect(color.copy(alpha = TRACK_ALPHA), Offset(0f, (size.height - height) / 2), Size(size.width, height), CornerRadius(height / 2))
                drawRect(markerColor, Offset(size.width / 2 - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
            }
        }
    }
}

@Composable
private fun LegendKey(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        swatch()
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A Pocket's rollover as one bar on a shared [scaleMinor]: what it held when the period began ([startMinor]) in a
 * lighter tone, what it added since in full colour, or, when it ends with less, the part used up as an outline.
 * Decorative: the row's text states the same amounts.
 */
@Composable
internal fun RolloverBar(startMinor: Long, nowMinor: Long, scaleMinor: Long, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Canvas(
        modifier
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .clearAndSetSemantics { },
    ) {
        val scale = scaleMinor.coerceAtLeast(1).toFloat()
        fun width(minor: Long) = size.width * minor.coerceAtLeast(0) / scale
        val gap = 2.dp.toPx()
        val radius = CornerRadius(size.height / 2)
        drawRect(color.copy(alpha = TRACK_ALPHA))
        val kept = width(minOf(startMinor, nowMinor))
        if (kept > 0f) drawRect(color.copy(alpha = KEPT_ALPHA), size = Size(kept, size.height))
        val added = width(nowMinor - startMinor)
        if (added > 0f) drawRect(color, topLeft = Offset(kept + gap, 0f), size = Size(added, size.height))
        val used = width(startMinor - nowMinor)
        if (used > 0f) {
            val stroke = 1.5.dp.toPx()
            val outlinePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = kept + gap + stroke / 2,
                        top = stroke / 2,
                        right = kept + gap + used - stroke / 2,
                        bottom = size.height - stroke / 2,
                        topRightCornerRadius = radius,
                        bottomRightCornerRadius = radius,
                    ),
                )
            }
            drawPath(outlinePath, outline, style = Stroke(width = stroke))
        }
    }
}

private const val KEPT_ALPHA = 0.5f

/** Names the three parts of a [RolloverBar]. Hidden from screen readers, which hear each row's amounts. */
@Composable
internal fun RolloverLegend(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    FlowRow(
        modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendKey("Al inicio") { Swatch { drawRoundRect(color.copy(alpha = KEPT_ALPHA), cornerRadius = CornerRadius(3.dp.toPx())) } }
        LegendKey("Añadido") { Swatch { drawRoundRect(color, cornerRadius = CornerRadius(3.dp.toPx())) } }
        LegendKey("Usado") {
            Swatch {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    outline,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}

@Composable
private fun Swatch(draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Canvas(Modifier.width(14.dp).height(10.dp), onDraw = draw)
}

/** The width of the widest of [texts] in [style]: a column of values this wide ends at one edge in every row. */
@Composable
internal fun widestTextWidth(texts: List<String>, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(texts, style, density) {
        val widest = texts.maxOfOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width } ?: 0
        with(density) { widest.toDp() }
    }
}

/**
 * A labeled horizontal bar of [fraction] of the track, with its value text at the end. Give every bar in a list the
 * same [valueWidth] so their tracks are the same length and the bars compare.
 */
@Composable
internal fun ValueBar(
    label: String,
    fraction: Float,
    value: String,
    modifier: Modifier = Modifier,
    labelWidth: Int = 44,
    valueWidth: Dp = Dp.Unspecified,
) {
    val color = MaterialTheme.colorScheme.primary
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(labelWidth.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp))) {
            drawRect(color.copy(alpha = TRACK_ALPHA))
            drawRoundRect(color, size = size.copy(width = size.width * fraction.coerceIn(0f, 1f)), cornerRadius = CornerRadius(size.height / 2))
        }
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            softWrap = false,
            modifier = Modifier.padding(start = 4.dp).widthIn(min = valueWidth),
        )
    }
}

/**
 * The period as a calendar, Monday first: each elapsed day is shaded by its net spending in one hue, light to dark;
 * days still to come are outlined. Day numbers stay readable on every shade.
 */
@Composable
internal fun SpendCalendar(
    start: LocalDate,
    totalDays: Int,
    dailyNetSpendMinor: List<Long>,
    currency: SupportedCurrency,
    modifier: Modifier = Modifier,
    /** Called for an elapsed day with spending. */
    onDayClick: (LocalDate) -> Unit = {},
) {
    val hue = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outlineVariant
    val maxSpend = dailyNetSpendMinor.maxOrNull()?.coerceAtLeast(1) ?: 1
    val leading = start.dayOfWeek.value - 1
    val cells = leading + totalDays
    val weeks = (cells + 6) / 7
    val busiest = dailyNetSpendMinor.withIndex().filter { it.value > 0 }.maxByOrNull { it.value }
    val description = buildString {
        append("Calendario de gasto de ${counted(dailyNetSpendMinor.size, "día transcurrido", "días transcurridos")}. ")
        val spendingDays = dailyNetSpendMinor.count { it > 0 }
        append("Hubo gasto en ${counted(spendingDays, "día", "días")}. ")
        busiest?.let {
            append("El día de más gasto fue el ${start.plusDays(it.index.toLong()).dayOfMonth}, con ${MoneyText.format(it.value, currency)}.")
        }
    }
    fun shade(spend: Long): Float = if (spend <= 0) 0f else 0.25f + 0.75f * spend / maxSpend
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // The weekday header carries the calendar's spoken summary; each day with spending is its own button.
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.entries.forEach {
                Text(
                    it.shortSpanish().take(2),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        for (week in 0 until weeks) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (column in 0 until 7) {
                    val day = week * 7 + column - leading
                    if (day !in 0 until totalDays) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                        continue
                    }
                    val date = start.plusDays(day.toLong())
                    val spend = dailyNetSpendMinor.getOrNull(day)
                    val intensity = spend?.let(::shade) ?: 0f
                    val cellShape = RoundedCornerShape(4.dp)
                    val tappable = spend != null && spend != 0L
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(cellShape)
                            .then(
                                if (tappable) {
                                    Modifier
                                        .clickable(onClickLabel = "Ver movimientos", role = Role.Button) { onDayClick(date) }
                                        .clearAndSetSemantics {
                                            contentDescription = "${date.dayOfMonth}: ${MoneyText.format(spend, currency)}"
                                            role = Role.Button
                                            onClick("Ver movimientos") { onDayClick(date); true }
                                        }
                                } else {
                                    Modifier.clearAndSetSemantics { }
                                },
                            )
                            .then(
                                when {
                                    spend == null -> Modifier.border(1.dp, outline, cellShape)
                                    intensity == 0f -> Modifier.background(emptyColor)
                                    else -> Modifier.background(hue.copy(alpha = intensity))
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (intensity > 0.6f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Row(
            Modifier.padding(top = 4.dp).clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Menos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { step ->
                Canvas(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp))) {
                    drawRect(if (step == 0f) emptyColor else hue.copy(alpha = step), size = Size(size.width, size.height))
                }
            }
            Text("Más", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
