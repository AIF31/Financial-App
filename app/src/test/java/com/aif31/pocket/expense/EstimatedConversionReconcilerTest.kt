package com.aif31.pocket.expense

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.RoomPocketLedger
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.fx.ExchangeRateRepository
import com.aif31.pocket.fx.FxQuote
import com.aif31.pocket.fx.QuoteFailure
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EstimatedConversionReconcilerTest {
    private lateinit var database: FinanceDatabase
    private lateinit var ledger: RoomPocketLedger
    private val date = LocalDate.of(2026, 2, 26)
    private val finalRate = "0.1973684210526315789473684210526316"

    @Before fun setup(): Unit = runBlocking {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        val zone = ZoneId.of("Asia/Riyadh")
        ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        ledger.execute(LedgerCommand.Initialize(100_000))
    }

    @After fun close() { database.close() }

    @Test fun final_purchase_day_fix_replaces_the_provider_estimate(): Unit = runBlocking {
        val estimate = addEstimate()

        EstimatedConversionReconciler(ledger, rates(isFinal = true)).reconcile()

        val confirmed = movement(estimate.id)
        assertEquals(ConversionStatus.CONFIRMED, confirmed.conversionStatus)
        assertEquals(1_974L, confirmed.accountingAmountMinor)
        assertEquals(finalRate, confirmed.rate)
        assertEquals(date, confirmed.conversionEffectiveDate)
        assertEquals(10_000L, confirmed.originalAmountMinor)
        assertEquals(estimate.merchant, confirmed.merchant)
    }

    @Test fun provisional_or_failed_quotes_keep_the_estimate(): Unit = runBlocking {
        val estimate = addEstimate()

        EstimatedConversionReconciler(ledger, rates(isFinal = false)).reconcile()
        EstimatedConversionReconciler(ledger, rates(failure = QuoteFailure.Unavailable())).reconcile()

        assertEquals(estimate, movement(estimate.id))
    }

    @Test fun legacy_manual_estimates_are_left_for_the_person(): Unit = runBlocking {
        val estimate = addEstimate(rate = null, effectiveDate = null, source = null)

        EstimatedConversionReconciler(ledger, rates(isFinal = true)).reconcile()

        assertEquals(estimate, movement(estimate.id))
    }

    @Test fun an_edit_after_the_estimate_was_read_wins(): Unit = runBlocking {
        val stale = addEstimate()
        val edited = addEstimate(id = stale.id, originalMinor = 20_000, accountingMinor = 4_167)

        val result = ledger.execute(LedgerCommand.ConfirmConversion(stale, 1_974, finalRate, date, "BANXICO"))

        assertEquals(LedgerResult.Success, result)
        assertEquals(edited, movement(stale.id))
    }

    private suspend fun addEstimate(
        id: String = "estimate",
        originalMinor: Long = 10_000,
        accountingMinor: Long = 2_083,
        rate: String? = "0.2083333333333333333333333333333333",
        effectiveDate: LocalDate? = date.minusDays(1),
        source: String? = "BANXICO_FIX_SF43718 · INVERSA + SAMA_PARITY",
    ): Movement {
        val pocketId = ledger.state.first().pockets.first().pocket.id
        val result = ledger.execute(
            LedgerCommand.AddMovement(
                id = id,
                pocketId = pocketId,
                type = MovementType.EXPENSE,
                accountingAmountMinor = accountingMinor,
                occurredAtUtcMillis = Instant.parse("2026-02-26T07:00:00Z").toEpochMilli(),
                localDate = date,
                merchant = "Tienda",
                originalAmountMinor = originalMinor,
                originalCurrencyCode = "MXN",
                conversionStatus = ConversionStatus.ESTIMATED,
                rate = rate,
                conversionEffectiveDate = effectiveDate,
                conversionSource = source,
            )
        )
        assertEquals(LedgerResult.Success, result)
        return movement(id)
    }

    private suspend fun movement(id: String) = ledger.state.first().movements.single { it.id == id }

    private fun rates(isFinal: Boolean = true, failure: Exception? = null) = object : ExchangeRateRepository {
        override suspend fun quote(requestedDate: LocalDate, base: SupportedCurrency, quote: SupportedCurrency, forceRefresh: Boolean): FxQuote {
            failure?.let { throw it }
            val effective = if (isFinal) requestedDate else requestedDate.minusDays(1)
            return FxQuote(requestedDate, effective, base, quote, finalRate, "BANXICO_FIX_SF43718 · INVERSA + SAMA_PARITY", isFinal)
        }
    }
}
