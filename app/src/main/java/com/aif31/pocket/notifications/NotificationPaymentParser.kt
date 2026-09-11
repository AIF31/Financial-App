package com.aif31.pocket.notifications

import com.aif31.pocket.domain.SupportedCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlinx.coroutines.CancellationException

internal data class ParsedPayment(
    val amountMinor: Long,
    val currency: SupportedCurrency,
    val merchant: String?,
)

internal object NotificationPaymentParser {
    private val paymentWords = Regex("(?i)\\b(paid|payment|purchase|spent|charged|pago|pagaste|compra|cargo)\\b")
    private val excludedWords = Regex(
        "(?i)\\b(refund(?:ed)?|received|deposit|transfer|reembolso|recibido|dep[oó]sito|transferencia|" +
            "reversal|reversión|declined|failed|denied|rejected|rechazad[oa]|fallid[oa]|denegad[oa]|" +
            "otp|code|código|verification|verificación|pending|pendiente|due|promo(?:tional)?|promoción)\\b"
    )
    private const val NUMBER = "(?<![0-9.,])-?(?:[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]{1,3}(?:\\.[0-9]{3})+(?:,[0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)(?![0-9.,])"
    private const val CURRENCY = "SAR|USD|MXN|US\\$|MX\\$|\\u0631\\.?\\s?\\u0633\\.?"
    private val amount = Regex("(?i)($CURRENCY)\\s*($NUMBER)|($NUMBER)\\s*($CURRENCY)")
    private val merchant = Regex("(?i)\\b(?:at|en|comercio)\\s+([^,.;]{2,60})")

    fun parse(title: CharSequence?, text: CharSequence?): ParsedPayment? {
        val content = try {
            listOfNotNull(title?.toString(), text?.toString()).joinToString(" ").trim()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return null
        }
        if (content.isEmpty() || !paymentWords.containsMatchIn(content) || excludedWords.containsMatchIn(content)) return null
        val matches = amount.findAll(content).toList()
        if (matches.size != 1) return null
        val match = matches.single()
        val currencyToken = (match.groupValues[1].ifEmpty { match.groupValues[4] })
            .uppercase(Locale.ROOT)
            .replace(Regex("[\\s.]"), "")
        val currency = when (currencyToken) {
            "SAR", "رس" -> SupportedCurrency.SAR
            "USD", "US$" -> SupportedCurrency.USD
            "MXN", "MX$" -> SupportedCurrency.MXN
            else -> return null
        }
        val rawNumber = match.groupValues[2].ifEmpty { match.groupValues[3] }
        val decimal = parseNumber(rawNumber) ?: return null
        val minor = runCatching {
            decimal.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        }.getOrNull()?.takeIf { it > 0 } ?: return null
        val merchantName = merchant.find(content)?.groupValues?.get(1)?.trim()
            ?.takeIf { candidate -> candidate.none(Char::isDigit) }
        return ParsedPayment(minor, currency, merchantName)
    }

    private fun parseNumber(raw: String): BigDecimal? {
        val lastDot = raw.lastIndexOf('.')
        val lastComma = raw.lastIndexOf(',')
        val decimalSeparator = when {
            lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) '.' else ','
            lastDot >= 0 && raw.length - lastDot - 1 in 1..2 -> '.'
            lastComma >= 0 && raw.length - lastComma - 1 in 1..2 -> ','
            else -> null
        }
        val normalized = buildString {
            raw.forEach { char ->
                when {
                    char.isDigit() || char == '-' -> append(char)
                    char == decimalSeparator -> append('.')
                }
            }
        }
        return normalized.toBigDecimalOrNull()
    }
}
