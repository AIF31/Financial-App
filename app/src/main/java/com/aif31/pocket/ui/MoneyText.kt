package com.aif31.pocket.ui

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal object MoneyText {
    fun sar(minor: Long): String = "SAR ${grouped(minor)}"

    fun grouped(minor: Long): String =
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
            .format(BigDecimal.valueOf(minor).movePointLeft(2))

    fun editable(minor: Long): String =
        BigDecimal.valueOf(minor).movePointLeft(2).toPlainString()
}
