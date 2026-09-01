package com.aif31.pocket.ui

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
