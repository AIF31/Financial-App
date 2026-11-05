package com.aif31.pocket.expense

import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.fx.DefaultExchangeRateRepository
import com.aif31.pocket.fx.FxQuote
import com.aif31.pocket.fx.FxQuoteCache
import com.aif31.pocket.fx.HttpsBanxicoClient
import com.aif31.pocket.fx.QuoteFailure
import com.aif31.pocket.notifications.NotificationPaymentParser
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** From a typed amount or a bank's alert to the accounting amount, through the real client with a fake transport. */
class BankAndExchangeConversionTest {
    private val date = LocalDate.of(2026, 11, 3)
    private val noon = Instant.parse("2026-11-03T20:00:00Z")

    @Test
    fun `typed amounts convert between every currency pair at the FIX and the SAMA peg`() = runTest {
        val expected = mapOf(
            Triple(SupportedCurrency.MXN, SupportedCurrency.SAR, 2_000L) to 400L,
            Triple(SupportedCurrency.SAR, SupportedCurrency.MXN, 400L) to 2_000L,
            Triple(SupportedCurrency.USD, SupportedCurrency.MXN, 100L) to 1_875L,
            Triple(SupportedCurrency.MXN, SupportedCurrency.USD, 1_875L) to 100L,
            Triple(SupportedCurrency.USD, SupportedCurrency.SAR, 2_000L) to 7_500L,
            Triple(SupportedCurrency.SAR, SupportedCurrency.USD, 7_500L) to 2_000L,
        )
        expected.forEach { (request, accountingMinor) ->
            val (input, accounting, inputMinor) = request
            val conversion = holder().resolve(ExpenseRequest(date, input, accounting, inputMinor), onlineFxEnabled = true)
            assertEquals("$input to $accounting", accountingMinor, conversion.accountingAmountMinor)
        }
    }

    @Test
    fun `a bank alert in a foreign currency converts into the Pocket's currency`() = runTest {
        val alerts = mapOf(
            "Compra por MXN 20.00 en OXXO" to 400L,
            "Purchase of USD 20.00 at STARBUCKS" to 7_500L,
        )
        alerts.forEach { (alert, sarMinor) ->
            val payment = requireNotNull(NotificationPaymentParser.parse("Banco", alert)) { alert }
            val conversion = holder().resolve(
                ExpenseRequest(date, payment.currency, SupportedCurrency.SAR, payment.amountMinor),
                onlineFxEnabled = true,
            )
            assertEquals(alert, sarMinor, conversion.accountingAmountMinor)
        }
    }

    @Test
    fun `without a token only conversions that need Banxico fail`() = runTest {
        val missingToken = holder(token = "")
        val peso = runCatching {
            missingToken.resolve(ExpenseRequest(date, SupportedCurrency.MXN, SupportedCurrency.SAR, 2_000), onlineFxEnabled = true)
        }.exceptionOrNull()
        assertTrue("Expected a missing-provider failure, got $peso", peso is QuoteFailure.ConfigurationUnavailable)
        assertEquals("Proveedor de tipo de cambio no configurado", peso?.message)

        val dollar = missingToken.resolve(ExpenseRequest(date, SupportedCurrency.USD, SupportedCurrency.SAR, 2_000), onlineFxEnabled = true)
        assertEquals(7_500L, dollar.accountingAmountMinor)
    }

    @Test
    fun `turning conversion off asks for consent before reporting the provider`() {
        assertThrows(QuoteFailure.ConsentRequired::class.java) {
            kotlinx.coroutines.runBlocking {
                holder(token = "").resolve(
                    ExpenseRequest(date, SupportedCurrency.MXN, SupportedCurrency.SAR, 2_000),
                    onlineFxEnabled = false,
                )
            }
        }
    }

    private fun holder(token: String = "test-token") = ExpenseEntryStateHolder(
        DefaultExchangeRateRepository(
            HttpsBanxicoClient(token, { _, _, _ -> FIX_PAYLOAD }),
            NoCache,
            onlineFxEnabled = { true },
            Clock.fixed(noon, ZoneOffset.UTC),
        ),
    )

    private object NoCache : FxQuoteCache {
        override suspend fun put(quote: FxQuote) = Unit
        override suspend fun latestEligible(requestedDate: LocalDate, base: SupportedCurrency, quote: SupportedCurrency): FxQuote? = null
    }

    private companion object {
        /** 18.75 MXN per USD against 3.75 SAR per USD makes exactly 5 MXN per SAR. */
        const val FIX_PAYLOAD = """{"bmx":{"series":[{"idSerie":"SF43718","datos":[{"fecha":"03/11/2026","dato":"18.75"}]}]}}"""
    }
}
