package com.aif31.pocket.domain

import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainRulesTest {
    @Test
    fun `money preserves halalas and rounds half up without floating point`() {
        assertEquals(Money(2_550, "SAR"), Money.parse("25.50", "SAR"))
        assertEquals(Money(1, "SAR"), Money.fromMajor("0.005", "SAR", RoundingMode.HALF_UP))
        assertEquals("1234567890123.45", Money(123_456_789_012_345, "SAR").toMajorString())
    }

    @Test
    fun `period beginning on 25 includes the 24th only in the previous period`() {
        val calendar = BudgetCalendar(startDay = 25, zoneId = ZoneId.of("Asia/Riyadh"))

        assertEquals(LocalDate.of(2026, 1, 25), calendar.periodContaining(LocalDate.of(2026, 2, 24)).start)
        assertEquals(LocalDate.of(2026, 2, 25), calendar.periodContaining(LocalDate.of(2026, 2, 25)).start)
        assertEquals(LocalDate.of(2026, 2, 25), calendar.periodContaining(LocalDate.of(2026, 2, 24)).endExclusive)
    }

    @Test
    fun `calendar clamps configurable start days for short months without overlaps`() {
        val calendar = BudgetCalendar(startDay = 31, zoneId = ZoneId.of("Asia/Riyadh"))
        val februaryPeriod = calendar.periodContaining(LocalDate.of(2028, 2, 29))

        assertEquals(LocalDate.of(2028, 2, 29), februaryPeriod.start)
        assertEquals(LocalDate.of(2028, 3, 31), februaryPeriod.endExclusive)
    }

    @Test
    fun `Pocket availability permits overspending and refunds restore it`() {
        val summary = PocketMath.summary(
            budgetMinor = 10_000,
            rolloverMinor = 2_000,
            expensesMinor = 13_500,
            refundsMinor = 1_000,
        )

        assertEquals(-500, summary.availabilityMinor)
        assertEquals(12_500, summary.netSpendMinor)
        assertTrue(summary.atRisk)
        assertTrue(summary.exhausted)
    }

    @Test
    fun `rollover carries only positive availability for opted in Pockets`() {
        assertEquals(3_000, PocketMath.rollover(allocatedMinor = 10_000, netSpendMinor = 7_000, enabled = true))
        assertEquals(0, PocketMath.rollover(allocatedMinor = 10_000, netSpendMinor = 7_000, enabled = false))
        assertEquals(0, PocketMath.rollover(allocatedMinor = 10_000, netSpendMinor = 12_000, enabled = true))
    }

    @Test
    fun `rollover rejects values whose availability exceeds supported money range`() {
        assertThrows(ArithmeticException::class.java) {
            PocketMath.rollover(allocatedMinor = Long.MAX_VALUE, netSpendMinor = -1, enabled = true)
        }
    }

    @Test
    fun `projection is an explicitly estimated straight line pace`() {
        val projection = PocketMath.project(netSpendMinor = 9_000, elapsedDays = 9, totalDays = 30)

        assertEquals(30_000, projection.amountMinor)
        assertTrue(projection.estimated)
    }

    @Test
    fun `manual FX freezes confirmed SAR and can distinguish an estimate`() {
        val estimate = ManualFx.estimate(originalMinor = 2_000, originalFractionDigits = 2, sarPerOriginal = "3.75")
        val confirmed = estimate.confirm(sarMinor = 7_612)

        assertEquals(7_500, estimate.sarMinor)
        assertFalse(estimate.confirmed)
        assertEquals(7_612, confirmed.sarMinor)
        assertTrue(confirmed.confirmed)
        assertEquals(7_612, confirmed.confirm(sarMinor = 7_612).sarMinor)
    }
}
