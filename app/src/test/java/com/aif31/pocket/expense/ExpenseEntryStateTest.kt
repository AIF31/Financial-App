package com.aif31.pocket.expense

import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.fx.ExchangeRateRepository
import com.aif31.pocket.fx.FxQuote
import com.aif31.pocket.fx.QuoteFailure
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExpenseEntryStateTest {
    private val date = LocalDate.of(2026, 2, 26)
    private val request = ExpenseRequest(date, SupportedCurrency.USD, SupportedCurrency.SAR, 100)
    private val saved = Movement(
        id = "saved", pocketId = "pocket", pocketName = "Pocket", periodId = "period",
        type = MovementType.EXPENSE, accountingAmountMinor = 401, occurredAtUtcMillis = 0,
        localDate = date, zoneId = "Asia/Riyadh", merchant = null, note = null,
        paymentMethodId = null, paymentMethodName = null, originalAmountMinor = 100,
        originalCurrencyCode = "USD", conversionStatus = ConversionStatus.ESTIMATED,
        rate = "3.75", conversionEffectiveDate = date.minusDays(1), conversionSource = "FROZEN",
    )

    @Test fun unchanged_edit_preserves_the_exact_accounting_amount_without_requoting() = runTest {
        val result = ExpenseEntryStateHolder(null, saved, SupportedCurrency.SAR).resolve(request, false)
        assertEquals(401L, result.accountingAmountMinor)
        assertEquals("3.75", result.rate)
        assertEquals("FROZEN", result.source)
        assertEquals(date.minusDays(1), result.effectiveDate)
        assertEquals(ConversionStatus.ESTIMATED, result.status)
    }

    @Test fun unchanged_legacy_edit_keeps_missing_provenance_and_stored_amount() = runTest {
        val legacy = saved.copy(rate = null, conversionEffectiveDate = null, conversionSource = null)
        val result = ExpenseEntryStateHolder(null, legacy, SupportedCurrency.SAR).resolve(request, false)
        assertEquals(401L, result.accountingAmountMinor)
        assertNull(result.rate)
        assertNull(result.source)
    }

    @Test fun same_currency_is_exact_and_offline() = runTest {
        val result = ExpenseEntryStateHolder(null).resolve(request.copy(inputCurrency = SupportedCurrency.SAR), false)
        assertEquals(100L, result.accountingAmountMinor)
        assertEquals(ConversionStatus.CONFIRMED, result.status)
    }

    @Test fun changed_amount_date_or_currency_requires_a_new_quote() = runTest {
        val repository = object : ExchangeRateRepository {
            override suspend fun quote(requestedDate: LocalDate, base: SupportedCurrency, quote: SupportedCurrency, forceRefresh: Boolean) =
                FxQuote(requestedDate, requestedDate, base, quote, "2", "NEW")
        }
        val holder = ExpenseEntryStateHolder(repository, saved, SupportedCurrency.SAR)
        for (changed in listOf(request.copy(inputAmountMinor = 200), request.copy(date = date.minusDays(1)), request.copy(inputCurrency = SupportedCurrency.MXN))) {
            val result = holder.resolve(changed, true)
            assertEquals(if (changed.inputAmountMinor == 200L) 400L else 200L, result.accountingAmountMinor)
            assertEquals("NEW", result.source)
            assertEquals(changed.date, result.effectiveDate)
        }
    }

    @Test fun foreign_entry_without_consent_is_blocked() = runTest {
        try {
            ExpenseEntryStateHolder(null).resolve(request, false)
            fail("Foreign entry must require consent")
        } catch (_: QuoteFailure.ConsentRequired) { }
    }

    @Test fun a_repository_quote_for_the_wrong_direction_is_rejected() = runTest {
        val repository = object : ExchangeRateRepository {
            override suspend fun quote(requestedDate: LocalDate, base: SupportedCurrency, quote: SupportedCurrency, forceRefresh: Boolean) =
                FxQuote(requestedDate, requestedDate, quote, base, "2", "WRONG")
        }
        try {
            ExpenseEntryStateHolder(repository).resolve(request, true)
            fail("Wrong-direction quote must not be saved")
        } catch (_: QuoteFailure.Unavailable) { }
    }
}
