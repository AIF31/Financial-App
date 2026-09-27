package com.aif31.pocket.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import com.aif31.pocket.domain.SupportedCurrency
import java.math.BigDecimal
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
}

/** "SAR 1,234.00 disponibles" with only the amount in the monospace face, so rows wrap less. */
internal fun availableText(minor: Long, currency: SupportedCurrency): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(MoneyText.format(minor, currency)) }
    append(" disponibles")
}
