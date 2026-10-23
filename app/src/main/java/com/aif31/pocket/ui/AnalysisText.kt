package com.aif31.pocket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.PeriodInsights
import com.aif31.pocket.domain.PocketMath
import com.aif31.pocket.domain.SupportedCurrency
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private const val NO_BREAK_SPACE = '\u00A0'
private val monthLabel = DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es"))

/** "SAR 1,234.00" with a no-break space, so a line never ends between the currency and its amount. */
internal fun MoneyText.unbroken(minor: Long, currency: SupportedCurrency): String =
    format(minor, currency).replace(' ', NO_BREAK_SPACE)

/** The month most of a period falls in: a period from 25 Feb to 24 Mar reads as "mar". */
internal fun PeriodInsights.shortLabel(): String =
    period.start.plusDays(totalDays / 2L).format(monthLabel).trimEnd('.')

/** The change from [baseMinor] as a whole percentage, or null when there is no base or the value fell to zero. */
private fun changePercent(deltaMinor: Long, baseMinor: Long): Long? =
    if (baseMinor > 0 && baseMinor + deltaMinor > 0) (deltaMinor * 100.0 / baseMinor).roundToLong() else null

/**
 * "▲ SAR 6.17 más al día (+15%)": the change from [baseMinor], with its direction in the arrow and in the words, so it
 * never depends on the arrow's tint. [unit] follows the direction word ("al día"); [than] names what the change is
 * against ("que sept", "de lo habitual"); [unchanged] is shown when there is no change. Screen readers should hear
 * [changeSpoken] instead of the arrow.
 */
@Composable
internal fun changeText(
    deltaMinor: Long,
    baseMinor: Long,
    currency: SupportedCurrency,
    unit: String? = null,
    than: String? = null,
    unchanged: String = "Sin cambio",
): AnnotatedString {
    if (deltaMinor == 0L) return AnnotatedString(unchanged)
    val more = deltaMinor > 0
    val arrowColor = if (more) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        withStyle(SpanStyle(color = arrowColor)) { append(if (more) "▲" else "▼") }
        append(NO_BREAK_SPACE)
        append(changeWords(deltaMinor, currency, unit, than))
        changePercent(deltaMinor, baseMinor)?.let { append("$NO_BREAK_SPACE(${if (it > 0) "+" else ""}$it%)") }
    }
}

/** [changeText] in words, without the arrow: "SAR 6.17 más al día, un 15% más". */
internal fun changeSpoken(
    deltaMinor: Long,
    baseMinor: Long,
    currency: SupportedCurrency,
    unit: String? = null,
    than: String? = null,
    unchanged: String = "Sin cambio",
): String {
    if (deltaMinor == 0L) return unchanged
    val percent = changePercent(deltaMinor, baseMinor)?.let { ", un ${abs(it)}% ${if (it > 0) "más" else "menos"}" }.orEmpty()
    return changeWords(deltaMinor, currency, unit, than) + percent
}

private fun changeWords(deltaMinor: Long, currency: SupportedCurrency, unit: String?, than: String?): String =
    buildString {
        append(MoneyText.unbroken(abs(deltaMinor), currency))
        append(if (deltaMinor > 0) " más" else " menos")
        unit?.let { append(" $it") }
        than?.let { append(" $it") }
    }

/**
 * A name with its amount at the end, or the amount under the name once a large font scale would squeeze the name
 * into a narrow column.
 */
@Composable
internal fun NameValueRow(
    name: String,
    value: String,
    modifier: Modifier = Modifier,
    nameStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    valueStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) {
        Column(modifier) {
            Text(name, style = nameStyle)
            Text(value, style = valueStyle)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = nameStyle, modifier = Modifier.weight(1f))
            Text(value, style = valueStyle, softWrap = false)
        }
    }
}

/** From this font scale up, a name and its amount no longer fit side by side on a phone, so they stack. */
internal const val LARGE_FONT_SCALE = 1.3f

/**
 * [partMinor] as a whole percentage of [wholeMinor], rounded toward zero like every other share in the app; null when
 * there is no positive whole to divide by.
 */
internal fun sharePercent(partMinor: Long, wholeMinor: Long): Int? =
    wholeMinor.takeIf { it > 0 }?.let { PocketMath.percent(partMinor, it) }
