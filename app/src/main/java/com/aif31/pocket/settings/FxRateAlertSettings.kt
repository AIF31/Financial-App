package com.aif31.pocket.settings

import com.aif31.pocket.domain.SupportedCurrency
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

/** How often the rate alert is sent; every option checks once a day at the chosen time. */
enum class FxRateAlertFrequency {
    /** Every day with the latest FIX, which repeats on days Banxico publishes none. */
    DAILY,

    /** Only when Banxico has published a FIX not yet alerted, so usually on business days. */
    NEW_FIX,

    /** Once a week on the chosen day, with the latest FIX. */
    WEEKLY,
}

/** The person's rate alert: which pair, how often, at what Asia/Riyadh time, and from which rate. */
data class FxRateAlertSettings(
    val enabled: Boolean = false,
    val time: LocalTime = LocalTime.of(9, 0),
    val base: SupportedCurrency = SupportedCurrency.SAR,
    val quote: SupportedCurrency = SupportedCurrency.MXN,
    val frequency: FxRateAlertFrequency = FxRateAlertFrequency.DAILY,
    val weekday: DayOfWeek = DayOfWeek.MONDAY,
    /** Alerts are sent only when 1 [base] buys at least this many [quote]; null sends them at any rate. */
    val minimumRate: BigDecimal? = null,
) {
    init {
        require(base to quote in PAIRS) { "Par de monedas no compatible" }
        require(minimumRate == null || minimumRate > BigDecimal.ZERO) { "Tipo de cambio mínimo no válido" }
    }

    companion object {
        /** Pairs whose rate moves: MXN against the dollar or the dollar-pegged riyal. */
        val PAIRS: List<Pair<SupportedCurrency, SupportedCurrency>> = listOf(
            SupportedCurrency.SAR to SupportedCurrency.MXN,
            SupportedCurrency.MXN to SupportedCurrency.SAR,
            SupportedCurrency.USD to SupportedCurrency.MXN,
            SupportedCurrency.MXN to SupportedCurrency.USD,
        )
    }
}
