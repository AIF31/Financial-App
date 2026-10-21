package com.aif31.pocket.data

import com.aif31.pocket.domain.SupportedCurrency
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringChargesTest {
    private val current = Period("current", date("2026-03-01"), date("2026-04-01"), 100_000, 1)
    private val history = current.copy(id = "history", start = date("2025-12-01"), endExclusive = current.start)

    @Test
    fun monthly_charges_use_normalized_merchants_latest_amount_and_latest_pocket() {
        val charges = monthly().mapIndexed { index, movement ->
            movement.copy(merchant = listOf("Stream Co", "STREAM-CO", "Stream.Co")[index])
        }.toMutableList()
        charges[2] = charges[2].copy(pocketId = "leisure", pocketName = "Ocio", accountingAmountMinor = 1_050)

        val result = RecurringCharges.of(state(charges), current.id)

        val item = result.items.single()
        assertEquals("streamco", item.merchantKey)
        assertEquals("Stream.Co", item.merchantName)
        assertEquals("leisure", item.pocketId)
        assertEquals("Ocio", item.pocketName)
        assertEquals(date("2026-03-15"), item.expectedDate)
        assertEquals(1_050L, item.expectedAmountMinor)
        assertEquals(RecurringCadence.MONTHLY, item.cadence)
        assertEquals(3, item.chargeCount)
        assertFalse(item.overdue)
        assertEquals(1_050L, result.totalExpectedMinor)
    }

    @Test
    fun weekly_charges_list_every_remaining_occurrence_before_the_exclusive_period_end() {
        val result = RecurringCharges.of(state(weekly(), today = "2026-03-01"), current.id)

        assertEquals(listOf("2026-03-01", "2026-03-08", "2026-03-15", "2026-03-22", "2026-03-29").map(::date), result.items.map { it.expectedDate })
        assertTrue(result.items.all { it.cadence == RecurringCadence.WEEKLY && it.chargeCount == 3 && !it.overdue })
        assertEquals(5_000L, result.totalExpectedMinor)
    }

    @Test
    fun irregular_gaps_and_mixed_cadences_are_not_recurring() {
        listOf(
            listOf("2026-01-01", "2026-01-10", "2026-02-13"),
            listOf("2026-01-07", "2026-02-06", "2026-02-13"),
            listOf("2026-02-13", "2026-02-13", "2026-02-20"),
        ).forEach { days -> assertTrue(RecurringCharges.of(state(days.map { charge(it) }), current.id).items.isEmpty()) }
    }

    @Test
    fun each_of_the_last_three_amounts_must_be_within_ten_percent_of_their_median() {
        val unstable = monthly().mapIndexed { i, charge -> charge.copy(accountingAmountMinor = listOf(899L, 1_000L, 1_100L)[i]) }
        assertTrue(RecurringCharges.of(state(unstable), current.id).items.isEmpty())
        val stable = unstable.mapIndexed { i, charge -> if (i == 0) charge.copy(accountingAmountMinor = 900) else charge }
        assertEquals(1_100L, RecurringCharges.of(state(stable), current.id).totalExpectedMinor)
    }

    @Test
    fun older_amounts_do_not_override_the_last_three_stable_charges() {
        val charges = listOf(charge("2025-11-15", 8_000)) + monthly()
        assertEquals(4, RecurringCharges.of(state(charges), current.id).items.single().chargeCount)
    }

    @Test
    fun fewer_than_three_expenses_or_an_unnamed_merchant_are_not_detected() {
        assertTrue(RecurringCharges.of(state(monthly().takeLast(2)), current.id).items.isEmpty())
        assertTrue(RecurringCharges.of(state(monthly().map { it.copy(merchant = "---") }), current.id).items.isEmpty())
    }

    @Test
    fun charges_in_periods_with_another_accounting_currency_are_excluded_without_conversion() {
        val foreign = history.copy(id = "foreign", accountingCurrency = SupportedCurrency.USD)
        val charges = monthly().mapIndexed { i, charge -> if (i == 0) charge.copy(periodId = foreign.id) else charge }
        val ledger = state(charges).copy(periods = listOf(foreign, history, current))
        assertTrue(RecurringCharges.of(ledger, current.id).items.isEmpty())
    }

    @Test
    fun existing_accounting_amounts_are_used_even_when_the_original_input_currency_differs() {
        val charges = monthly().map { it.copy(originalAmountMinor = 267, originalCurrencyCode = "USD") }
        assertEquals(1_000L, RecurringCharges.of(state(charges), current.id).totalExpectedMinor)
    }

    @Test
    fun a_charge_already_recorded_this_cycle_is_not_listed_again() {
        val charges = monthly() + charge("2026-03-15")
        assertTrue(RecurringCharges.of(state(charges, "2026-03-16"), current.id).items.isEmpty())
    }

    @Test
    fun weekly_charges_already_recorded_leave_only_the_remaining_cycles() {
        val charges = weekly() + charge("2026-03-01") + charge("2026-03-08")
        val result = RecurringCharges.of(state(charges, "2026-03-10"), current.id)
        assertEquals(listOf("2026-03-15", "2026-03-22", "2026-03-29").map(::date), result.items.map { it.expectedDate })
    }

    @Test
    fun a_monthly_occurrence_expires_after_half_a_cadence() {
        assertTrue(RecurringCharges.of(state(monthly(), "2026-03-30"), current.id).items.single().overdue)
        assertTrue(RecurringCharges.of(state(monthly(), "2026-03-31"), current.id).items.isEmpty())
    }

    @Test
    fun a_recently_missed_weekly_occurrence_is_marked_overdue() {
        val weeklyResult = RecurringCharges.of(state(weekly(), "2026-03-04"), current.id)
        assertTrue(weeklyResult.items.first().overdue)
        assertFalse(weeklyResult.items[1].overdue)
    }

    @Test
    fun one_expired_weekly_occurrence_does_not_drop_later_occurrences() {
        val period = current.copy(start = date("2026-02-25"))
        val ledger = state(weekly(), "2026-03-05").copy(
            periods = listOf(history.copy(endExclusive = period.start), period), currentPeriod = period,
        )

        val result = RecurringCharges.of(ledger, period.id)

        assertEquals(listOf("2026-03-08", "2026-03-15", "2026-03-22", "2026-03-29").map(::date), result.items.map { it.expectedDate })
        assertTrue(result.items.none { it.overdue })
        assertEquals(4_000L, result.totalExpectedMinor)
    }

    @Test
    fun two_consecutive_expired_occurrences_end_the_merchant_forecast() {
        val stillExpected = RecurringCharges.of(state(weekly(), "2026-03-11"), current.id)
        assertEquals(date("2026-03-08"), stillExpected.items.first().expectedDate)
        assertTrue(stillExpected.items.first().overdue)

        assertTrue(RecurringCharges.of(state(weekly(), "2026-03-12"), current.id).items.isEmpty())
        assertTrue(RecurringCharges.of(state(weekly(), "2026-03-20"), current.id).items.isEmpty())
    }

    @Test
    fun refunds_do_not_count_or_change_the_latest_charge() {
        val refund = charge("2026-03-10", 50_000).copy(type = MovementType.REFUND)
        assertEquals(1_000L, RecurringCharges.of(state(monthly() + refund), current.id).totalExpectedMinor)
        assertTrue(RecurringCharges.of(state(monthly().takeLast(2) + refund), current.id).items.isEmpty())
    }

    @Test
    fun the_total_is_exact_across_merchants_and_occurrences() {
        val charges = weekly().map { it.copy(accountingAmountMinor = 333) } + monthly().map { it.copy(merchant = "Other", accountingAmountMinor = 1_099) }
        assertEquals(2_764L, RecurringCharges.of(state(charges, "2026-03-01"), current.id).totalExpectedMinor)
    }

    @Test
    fun an_unrepresentable_total_is_absent_without_losing_individual_expectations() {
        val result = RecurringCharges.of(state(monthly().map { it.copy(accountingAmountMinor = Long.MAX_VALUE) } +
            monthly().map { it.copy(merchant = "Other", accountingAmountMinor = 1) }), current.id)
        assertNull(result.totalExpectedMinor)
        assertEquals(2, result.items.size)

        val weeklyResult = RecurringCharges.of(state(weekly().map { it.copy(accountingAmountMinor = Long.MAX_VALUE) }), current.id)
        assertNull(weeklyResult.totalExpectedMinor)
        assertEquals(4, weeklyResult.items.size)
        assertTrue(weeklyResult.items.all { it.expectedAmountMinor == Long.MAX_VALUE })
    }

    @Test
    fun median_gap_is_used_and_dates_outside_the_period_are_excluded() {
        val charges = listOf("2025-12-15", "2026-01-13", "2026-02-13").map { charge(it) }
        assertEquals(date("2026-03-15"), RecurringCharges.of(state(charges), current.id).items.single().expectedDate)
        assertTrue(RecurringCharges.of(state(monthly()), "missing").items.isEmpty())
        val ledger = state(monthly()).copy(periods = listOf(history, current.copy(endExclusive = date("2026-03-15"))))
        assertTrue(RecurringCharges.of(ledger, current.id).items.isEmpty())
    }

    @Test
    fun merchant_history_drill_shows_past_expenses_in_the_selected_accounting_currency() {
        val foreign = history.copy(id = "foreign", accountingCurrency = SupportedCurrency.USD)
        val charges = monthly() + listOf(
            charge("2026-03-09").copy(type = MovementType.REFUND),
            charge("2026-01-01").copy(periodId = foreign.id),
            charge("2026-03-20"),
            charge("2026-03-02", merchant = "Other"),
        )
        val ledger = state(charges).copy(periods = listOf(foreign, history, current))
        assertEquals(monthly(), MovementDrill.merchantHistory(current.id, "stream").movements(ledger))
    }

    private fun monthly() = listOf("2025-12-15", "2026-01-14", "2026-02-13").map { charge(it) }
    private fun weekly() = listOf("2026-02-08", "2026-02-15", "2026-02-22").map { charge(it) }

    private fun state(charges: List<Movement>, today: String = "2026-03-10") = LedgerState(
        periods = listOf(history, current), currentPeriod = current, movements = charges, currentLocalDate = date(today),
    )

    private fun charge(day: String, amount: Long = 1_000, merchant: String? = "Stream") = Movement(
        id = "$merchant-$day", pocketId = "services", pocketName = "Servicios",
        periodId = if (date(day) < current.start) history.id else current.id,
        type = MovementType.EXPENSE, accountingAmountMinor = amount,
        occurredAtUtcMillis = date(day).toEpochDay() * 86_400_000, localDate = date(day), zoneId = "Asia/Riyadh",
        merchant = merchant, note = null, paymentMethodId = null, paymentMethodName = null,
        originalAmountMinor = null, originalCurrencyCode = SupportedCurrency.SAR.name,
        conversionStatus = ConversionStatus.CONFIRMED, rate = null, conversionEffectiveDate = null, conversionSource = null,
    )

    private fun date(value: String) = LocalDate.parse(value)
}
