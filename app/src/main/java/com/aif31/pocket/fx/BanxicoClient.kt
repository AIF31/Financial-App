package com.aif31.pocket.fx

import com.aif31.pocket.domain.SupportedCurrency
import java.io.IOException
import java.math.BigDecimal
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface BanxicoRateSource {
    suspend fun fetchUsdToMxn(requestedDate: LocalDate): FxQuote
}

fun interface BanxicoTransport {
    suspend fun get(startDate: LocalDate, endDate: LocalDate, token: String): String
}

class HttpsBanxicoClient(
    private val token: String,
    private val transport: BanxicoTransport = UrlConnectionBanxicoTransport(),
) : BanxicoRateSource {
    override suspend fun fetchUsdToMxn(requestedDate: LocalDate): FxQuote {
        if (token.isBlank()) throw QuoteFailure.ConfigurationUnavailable()
        return try {
            val startDate = requestedDate.minusDays(FxQuote.MAX_QUOTE_AGE_DAYS)
            parseBanxicoUsdToMxn(transport.get(startDate, requestedDate, token), requestedDate)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: QuoteFailure) {
            throw known
        } catch (_: Exception) {
            throw QuoteFailure.Unavailable()
        }
    }
}

class UrlConnectionBanxicoTransport(
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 10_000,
) : BanxicoTransport {
    override suspend fun get(startDate: LocalDate, endDate: LocalDate, token: String): String =
        runInterruptible(Dispatchers.IO) {
            val url = URL(
                "$BASE_URL/${startDate.format(ISO_DATE)}/${endDate.format(ISO_DATE)}"
            )
            val connection = url.openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = connectTimeoutMillis
                connection.readTimeout = readTimeoutMillis
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Bmx-Token", token)
                if (connection.responseCode != HttpsURLConnection.HTTP_OK) {
                    throw IOException("Proveedor no disponible")
                }
                val bytes = connection.inputStream.use { input -> input.readNBytes(MAX_RESPONSE_BYTES + 1) }
                if (bytes.size > MAX_RESPONSE_BYTES) throw IOException("Respuesta demasiado grande")
                String(bytes, StandardCharsets.UTF_8)
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val BASE_URL = "https://www.banxico.org.mx/SieAPIRest/service/v1/series/SF43718/datos"
        const val MAX_RESPONSE_BYTES = 256 * 1024
        val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}

internal fun parseBanxicoUsdToMxn(payload: String, requestedDate: LocalDate): FxQuote {
    val response = try {
        BANXICO_JSON.decodeFromString<BanxicoResponse>(payload)
    } catch (_: Exception) {
        throw QuoteFailure.Unavailable()
    }
    val lowerBound = requestedDate.minusDays(FxQuote.MAX_QUOTE_AGE_DAYS)
    val observation = response.bmx.series
        .firstOrNull { it.id == SERIES_ID }
        ?.data
        .orEmpty()
        .mapNotNull { value ->
            val date = runCatching { LocalDate.parse(value.date, BANXICO_DATE) }.getOrNull() ?: return@mapNotNull null
            if (date.isAfter(requestedDate) || date.isBefore(lowerBound)) return@mapNotNull null
            val rate = runCatching { BigDecimal(value.value.replace(",", "")) }.getOrNull()
                ?.takeIf { it > BigDecimal.ZERO }
                ?: return@mapNotNull null
            date to rate
        }
        .maxByOrNull { it.first }
        ?: throw QuoteFailure.Unavailable()
    return FxQuote(
        requestedDate = requestedDate,
        effectiveDate = observation.first,
        base = SupportedCurrency.USD,
        quote = SupportedCurrency.MXN,
        rate = canonicalRate(observation.second),
        source = "BANXICO_FIX_SF43718",
    )
}

private val BANXICO_JSON = Json { ignoreUnknownKeys = true }
private val BANXICO_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
private const val SERIES_ID = "SF43718"

@Serializable
private data class BanxicoResponse(val bmx: BanxicoEnvelope)

@Serializable
private data class BanxicoEnvelope(val series: List<BanxicoSeries> = emptyList())

@Serializable
private data class BanxicoSeries(
    @SerialName("idSerie") val id: String,
    @SerialName("datos") val data: List<BanxicoObservation> = emptyList(),
)

@Serializable
private data class BanxicoObservation(
    @SerialName("fecha") val date: String,
    @SerialName("dato") val value: String,
)
