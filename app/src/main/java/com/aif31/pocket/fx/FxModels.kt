package com.aif31.pocket.fx

import com.aif31.pocket.domain.SupportedCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class FxQuote(
    val requestedDate: LocalDate,
    val effectiveDate: LocalDate,
    val base: SupportedCurrency,
    val quote: SupportedCurrency,
    val rate: String,
    val source: String,
) {
    private val decimalRate = runCatching { BigDecimal(rate) }
        .getOrElse { throw IllegalArgumentException("Tipo de cambio no válido") }

    init {
        require(decimalRate > BigDecimal.ZERO) { "Tipo de cambio no válido" }
        require(source.isNotBlank()) { "Fuente no válida" }
        require(!effectiveDate.isAfter(requestedDate)) { "Fecha efectiva no válida" }
        require(!effectiveDate.isBefore(requestedDate.minusDays(MAX_QUOTE_AGE_DAYS))) {
            "Tipo de cambio fuera del límite de siete días"
        }
    }

    fun convertMinor(amountMinor: Long): Long = BigDecimal.valueOf(amountMinor)
        .multiply(decimalRate)
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()

    companion object {
        const val MAX_QUOTE_AGE_DAYS = 7L
    }
}

interface ExchangeRateRepository {
    suspend fun quote(
        requestedDate: LocalDate,
        base: SupportedCurrency,
        quote: SupportedCurrency,
        forceRefresh: Boolean = false,
    ): FxQuote
}

sealed class QuoteFailure(message: String) : Exception(message) {
    class ConsentRequired : QuoteFailure("Activa la conversión en línea")
    class ConfigurationUnavailable : QuoteFailure("Proveedor de tipo de cambio no configurado")
    class Unavailable : QuoteFailure("No hay un tipo de cambio disponible para esa fecha")
}
