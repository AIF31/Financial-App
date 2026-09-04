package com.aif31.pocket.fx

import com.aif31.pocket.domain.SupportedCurrency
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

internal fun sameCurrencyQuote(date: LocalDate, currency: SupportedCurrency): FxQuote = FxQuote(
    requestedDate = date,
    effectiveDate = date,
    base = currency,
    quote = currency,
    rate = "1",
    source = "MISMA_MONEDA",
)

internal fun inverseQuote(value: FxQuote): FxQuote = FxQuote(
    requestedDate = value.requestedDate,
    effectiveDate = value.effectiveDate,
    base = value.quote,
    quote = value.base,
    rate = canonicalRate(BigDecimal.ONE.divide(BigDecimal(value.rate), MathContext.DECIMAL128)),
    source = "${value.source} · INVERSA",
)

internal fun composeQuotes(first: FxQuote, second: FxQuote, source: String): FxQuote {
    require(first.requestedDate == second.requestedDate) { "Las cotizaciones deben usar la misma fecha solicitada" }
    require(first.quote == second.base) { "Las cotizaciones no comparten la moneda puente" }
    return FxQuote(
        requestedDate = first.requestedDate,
        effectiveDate = minOf(first.effectiveDate, second.effectiveDate),
        base = first.base,
        quote = second.quote,
        rate = canonicalRate(BigDecimal(first.rate).multiply(BigDecimal(second.rate), MathContext.DECIMAL128)),
        source = source,
    )
}

internal fun canonicalRate(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
