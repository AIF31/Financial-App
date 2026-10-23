package com.aif31.pocket.fx

import com.aif31.pocket.domain.SupportedCurrency
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
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

/** Every Banxico FIX published from [startDate] through [endDate], oldest first. */
fun interface BanxicoHistorySource {
    suspend fun fetchUsdToMxnHistory(startDate: LocalDate, endDate: LocalDate): List<FxObservation>
}

fun interface BanxicoTransport {
    suspend fun get(startDate: LocalDate, endDate: LocalDate, token: String): String
}

class HttpsBanxicoClient(
    private val token: String,
    private val transport: BanxicoTransport = UrlConnectionBanxicoTransport(),
) : BanxicoRateSource, BanxicoHistorySource {
    override suspend fun fetchUsdToMxn(requestedDate: LocalDate): FxQuote = guarded {
        val startDate = requestedDate.minusDays(FxQuote.MAX_QUOTE_AGE_DAYS)
        parseBanxicoUsdToMxn(transport.get(startDate, requestedDate, token), requestedDate)
    }

    override suspend fun fetchUsdToMxnHistory(startDate: LocalDate, endDate: LocalDate): List<FxObservation> {
        require(!startDate.isAfter(endDate)) { "Rango de fechas no válido" }
        return guarded { history(startDate, endDate) }
    }

    private suspend fun history(startDate: LocalDate, endDate: LocalDate): List<FxObservation> =
        parseBanxicoUsdToMxnObservations(transport.get(startDate, endDate, token))
            .filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }

    private suspend fun <T> guarded(block: suspend () -> T): T {
        if (token.isBlank()) throw QuoteFailure.ConfigurationUnavailable()
        return try {
            block()
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
                connection.inputStream.use { input -> readBounded(input, MAX_RESPONSE_BYTES) }
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

internal fun readBounded(input: InputStream, maximumBytes: Int): String {
    require(maximumBytes >= 0)
    val output = ByteArrayOutputStream(minOf(maximumBytes, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (total <= maximumBytes) {
        val count = input.read(buffer, 0, minOf(buffer.size, maximumBytes + 1 - total))
        if (count < 0) break
        output.write(buffer, 0, count)
        total += count
    }
    if (total > maximumBytes) throw IOException("Respuesta demasiado grande")
    return output.toString(StandardCharsets.UTF_8.name())
}

internal fun parseBanxicoUsdToMxn(payload: String, requestedDate: LocalDate): FxQuote {
    val lowerBound = requestedDate.minusDays(FxQuote.MAX_QUOTE_AGE_DAYS)
    val observation = parseBanxicoUsdToMxnObservations(payload)
        .lastOrNull { !it.date.isAfter(requestedDate) && !it.date.isBefore(lowerBound) }
        ?: throw QuoteFailure.Unavailable()
    return FxQuote(
        requestedDate = requestedDate,
        effectiveDate = observation.date,
        base = SupportedCurrency.USD,
        quote = SupportedCurrency.MXN,
        rate = canonicalRate(observation.usdToMxn),
        source = "BANXICO_FIX_SF43718",
    )
}

/** The payload's valid FIX observations, oldest first; non-numeric values such as "N/E" are skipped. */
internal fun parseBanxicoUsdToMxnObservations(payload: String): List<FxObservation> {
    val response = try {
        BANXICO_JSON.decodeFromString<BanxicoResponse>(payload)
    } catch (_: Exception) {
        throw QuoteFailure.Unavailable()
    }
    return response.bmx.series
        .firstOrNull { it.id == SERIES_ID }
        ?.data
        .orEmpty()
        .mapNotNull { value ->
            val date = runCatching { LocalDate.parse(value.date, BANXICO_DATE) }.getOrNull() ?: return@mapNotNull null
            val rate = runCatching { BigDecimal(value.value) }.getOrNull()
                ?.takeIf { it > BigDecimal.ZERO }
                ?: return@mapNotNull null
            FxObservation(date, rate)
        }
        .distinctBy { it.date }
        .sortedBy { it.date }
}

private val BANXICO_JSON = Json { ignoreUnknownKeys = true }
private val BANXICO_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
    .withResolverStyle(ResolverStyle.STRICT)
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
