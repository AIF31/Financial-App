package com.aif31.pocket.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

/**
 * One bar per period, the selected period at full strength and the rest lighter. Every bar is the same series, so a
 * single hue carries it and the selected bar is also named in the caption above. Tapping a bar selects its period;
 * screen readers get one action per bar instead.
 */
@Composable
internal fun TrendBars(
    bars: List<TrendBar>,
    description: String,
    onSelect: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val maxValue = bars.maxOfOrNull { it.valueMinor ?: 0 }?.coerceAtLeast(1) ?: 1
    // The tap handler outlives recompositions with the same bar count, so it reads the latest selection mapping.
    val currentOnSelect by rememberUpdatedState(onSelect)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
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
            bars.forEachIndexed { index, bar ->
                val value = (bar.valueMinor ?: 0).coerceAtLeast(0)
                val height = size.height * value / maxValue
                if (height <= 0f) return@forEachIndexed
                val top = size.height - height
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
                drawPath(path, if (bar.selected) barColor else barColor.copy(alpha = 0.4f))
            }
            drawLine(baselineColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            bars.forEach { bar ->
                Text(
                    bar.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bar.selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A small line of values from zero, with the latest point marked; gaps where a value is missing. [description] speaks
 * the whole series, since the surrounding text gives only the latest value.
 */
@Composable
internal fun Sparkline(
    values: List<Long?>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    description: String? = null,
) {
    val maxValue = values.maxOfOrNull { it ?: 0 }?.coerceAtLeast(1) ?: 1
    Canvas(modifier.width(72.dp).height(28.dp).clearAndSetSemantics { description?.let { contentDescription = it } }) {
        if (values.size < 2) return@Canvas
        val inset = 4.dp.toPx()
        val usableHeight = size.height - inset * 2
        fun x(index: Int) = inset + (size.width - inset * 2) * index / values.lastIndex.toFloat()
        fun y(value: Long) = inset + usableHeight - usableHeight * value.coerceAtLeast(0) / maxValue
        val path = Path()
        var drawing = false
        values.forEachIndexed { index, value ->
            if (value == null) {
                drawing = false
            } else if (drawing) {
                path.lineTo(x(index), y(value))
            } else {
                path.moveTo(x(index), y(value))
                drawing = true
            }
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        values.last()?.let { drawCircle(color, radius = 3.dp.toPx(), center = Offset(x(values.lastIndex), y(it))) }
    }
}

/** A labeled horizontal bar of [fraction] of the track, with its value text at the end. */
@Composable
internal fun ValueBar(label: String, fraction: Float, value: String, modifier: Modifier = Modifier, labelWidth: Int = 44) {
    val color = MaterialTheme.colorScheme.primary
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(labelWidth.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp))) {
            drawRect(color.copy(alpha = 0.14f))
            drawRect(color, size = size.copy(width = size.width * fraction.coerceIn(0f, 1f)))
        }
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
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
