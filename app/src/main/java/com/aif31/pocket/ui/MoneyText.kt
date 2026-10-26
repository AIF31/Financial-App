package com.aif31.pocket.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.aif31.pocket.domain.SupportedCurrency
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal object MoneyText {
    fun format(minor: Long, currency: SupportedCurrency): String = "${currency.name} ${grouped(minor)}"

    fun sar(minor: Long): String = format(minor, SupportedCurrency.SAR)

    fun grouped(minor: Long): String =
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
            .format(BigDecimal.valueOf(minor).movePointLeft(2))

    fun editable(minor: Long): String =
        BigDecimal.valueOf(minor).movePointLeft(2).toPlainString()

    /**
     * "1 USD = 3.75 SAR": a [from]-to-[to] rate shown in whichever direction is at least 1, to six significant
     * digits, so a stored inverse such as 0.2666…67 reads as the rate people quote.
     */
    fun rate(from: SupportedCurrency, to: SupportedCurrency, rate: String): String {
        val value = rate.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return "1 ${from.name} = $rate ${to.name}"
        val (base, quote, shown) = if (value < BigDecimal.ONE) {
            Triple(to, from, BigDecimal.ONE.divide(value, MathContext.DECIMAL64))
        } else {
            Triple(from, to, value)
        }
        val digits = shown.round(MathContext(6, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()
        return "1 ${base.name} = $digits ${quote.name}"
    }
}

/** "SAR 1,234.00 disponibles" with the amount in a heavier weight than the words after it. */
internal fun availableText(minor: Long, currency: SupportedCurrency): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(MoneyText.format(minor, currency)) }
    append(" disponibles")
}
