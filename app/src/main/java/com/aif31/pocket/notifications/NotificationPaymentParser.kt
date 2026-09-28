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
    private val paymentWords = Regex(
        "(?i)\\b(paid|payment|purchases?|spent|charged|debited|pos|pago|pagaste|compras?|cargo|cobro)\\b"
    )
    private val excludedWords = Regex(
        "(?i)\\b(refund(?:ed)?|received|deposit|transfer|credited|reembolso|recibido|dep[oó]sito|transferencia|" +
            "abono|abonad[oa]|acreditad[oa]|reversal|reversión|declined|failed|denied|rejected|rechazad[oa]|" +
            "fallid[oa]|denegad[oa]|otp|code|código|verification|verificación|pending|pendiente|due|" +
            "promo(?:tional)?|promoción)\\b"
    )
    private const val NUMBER = "(?<![0-9.,])-?(?:[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]{1,3}(?:\\.[0-9]{3})+(?:,[0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)(?![0-9.,])"
    private const val CURRENCY = "SAR|USD|MXN|US\\$|MX\\$|\\$|\\u0631\\.?\\s?\\u0633\\.?"
    private val amount = Regex("(?i)($CURRENCY)\\s*($NUMBER)|($NUMBER)\\s*($CURRENCY)")

    // Bank texts put the merchant after "at"/"en" and end it with a connector ("for SAR…", "on 2026-…"),
    // a line break, or a sentence break. Both "used at SHOP for SAR 1.00" and "At: SHOP\nDate: …" occur.
    private val merchant = Regex(
        // "Compra en línea … en OXXO": the sales channel after "en" is not the merchant.
        "(?i)\\b(?:at|en|comercio|merchant)\\b(?![\\s:]*(?:l[ií]nea|internet|online)\\b)\\s*:?\\s*([^\\n;,]{2,60}?)" +
            "(?=\\s+(?:for|por|on|el|with|con)\\b|\\s*[\\n;,]|\\.(?:\\s|$)|\\s*$)"
    )
    private val merchantNoise = Regex("[^\\p{L}\\p{N}\\s&'.*/-]+")
    private val cardLikeDigits = Regex("[0-9]{4,}")

    /**
     * Parses one notification message. A bare `$` amount is accepted only when [dollarCurrency] says which
     * dollar it means; otherwise the message is ambiguous and rejected.
     */
    fun parse(
        title: CharSequence?,
        text: CharSequence?,
        dollarCurrency: SupportedCurrency? = null,
    ): ParsedPayment? {
        val content = try {
            listOfNotNull(title?.toString(), text?.toString()).joinToString("\n").trim()
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
            "$" -> dollarCurrency?.takeIf { it != SupportedCurrency.SAR } ?: return null
            else -> return null
        }
        val rawNumber = match.groupValues[2].ifEmpty { match.groupValues[3] }
        val decimal = parseNumber(rawNumber) ?: return null
        val minor = runCatching {
            decimal.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        }.getOrNull()?.takeIf { it > 0 } ?: return null
        return ParsedPayment(minor, currency, merchantName(content))
    }

    private fun merchantName(content: String): String? {
        val raw = merchant.find(content)?.groupValues?.get(1) ?: return null
        val cleaned = raw.replace(merchantNoise, " ").replace(Regex("\\s+"), " ").trim()
        return cleaned.takeIf { candidate ->
            candidate.length >= 2 &&
                candidate.any(Char::isLetter) &&
                !cardLikeDigits.containsMatchIn(candidate) &&
                !amount.containsMatchIn(candidate)
        }
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

/** Normalizes a merchant so "K.F.C", "KFC", and "Tamimi-Market" / "TAMIMI MARKET" share Pocket memory. */
internal fun merchantKey(merchant: String?): String? =
    merchant?.lowercase(Locale.ROOT)
        ?.replace(Regex("[^\\p{L}\\p{N}]+"), "")
        ?.takeIf { it.isNotEmpty() }
