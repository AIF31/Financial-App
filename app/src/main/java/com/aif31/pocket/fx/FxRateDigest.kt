package com.aif31.pocket.fx

import com.aif31.pocket.domain.SupportedCurrency
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/**
 * The figures in a rate alert for [base] → [quote], derived from Banxico FIX observations and, for SAR, the SAMA peg.
 * [changePercent] compares the latest FIX with the one published before it; [weekAverage] averages every FIX
 * dated in the seven calendar days ending on the latest one, so it usually covers five business days.
 */
internal data class FxRateDigest(
    val base: SupportedCurrency,
    val quote: SupportedCurrency,
    val date: LocalDate,
    val rate: BigDecimal,
    val previousDate: LocalDate?,
    val changePercent: BigDecimal?,
    val weekAverage: BigDecimal,
    val weekObservations: Int,
) {
    /** How far [rate] sits above (positive) or below the seven-day average, in percent. */
    val versusAveragePercent: BigDecimal get() = percentChange(from = weekAverage, to = rate)

    companion object {
        const val WEEK_DAYS = 7L

        fun of(observations: List<FxObservation>, base: SupportedCurrency, quote: SupportedCurrency): FxRateDigest? {
            val sorted = observations.distinctBy { it.date }.sortedBy { it.date }
            val latest = sorted.lastOrNull() ?: return null
            val previous = sorted.getOrNull(sorted.lastIndex - 1)
            fun rate(observation: FxObservation) = mxnPer(base, observation).divide(mxnPer(quote, observation), MathContext.DECIMAL128)
            val weekStart = latest.date.minusDays(WEEK_DAYS - 1)
            val week = sorted.filter { !it.date.isBefore(weekStart) }.map(::rate)
            val rate = rate(latest)
            return FxRateDigest(
                base = base,
                quote = quote,
                date = latest.date,
                rate = rate,
                previousDate = previous?.date,
                changePercent = previous?.let { percentChange(from = rate(it), to = rate) },
                weekAverage = week.fold(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal(week.size), MathContext.DECIMAL128),
                weekObservations = week.size,
            )
        }

        /** Mexican pesos per unit of [currency] on the observation's FIX. */
        private fun mxnPer(currency: SupportedCurrency, observation: FxObservation): BigDecimal = when (currency) {
            SupportedCurrency.MXN -> BigDecimal.ONE
            SupportedCurrency.USD -> observation.usdToMxn
            SupportedCurrency.SAR -> observation.usdToMxn.divide(SAMA_SAR_PER_USD, MathContext.DECIMAL128)
        }

        private fun percentChange(from: BigDecimal, to: BigDecimal): BigDecimal =
            to.subtract(from).multiply(BigDecimal(100)).divide(from, 2, RoundingMode.HALF_UP)
    }
}
