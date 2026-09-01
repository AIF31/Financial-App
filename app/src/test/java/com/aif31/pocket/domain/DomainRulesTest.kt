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
    fun `supported currencies normalize known codes and reject unsupported codes`() {
        assertEquals(SupportedCurrency.SAR, SupportedCurrency.fromCode("SAR"))
        assertEquals(SupportedCurrency.MXN, SupportedCurrency.fromCode("mxn"))

        assertThrows(IllegalArgumentException::class.java) {
            SupportedCurrency.fromCode("EUR")
        }
    }

    @Test
    fun `frozen rate converts in its declared direction with half up rounding`() {
        val rate = FrozenRate(
            from = SupportedCurrency.SAR,
            to = SupportedCurrency.MXN,
            value = "4.525",
        )

        assertEquals(453L, rate.convertMinor(100L))
        assertEquals(Money(453L, "MXN"), rate.convert(Money(100L, "SAR")))
        assertThrows(IllegalArgumentException::class.java) {
            rate.convert(Money(100L, "USD"))
        }
    }

    @Test
    fun `frozen rate rejects zero negative and malformed values`() {
        listOf("0", "-1", "not-a-rate").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                FrozenRate(SupportedCurrency.SAR, SupportedCurrency.USD, value)
            }
        }
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
    fun `unchanged start day creates the next ordinary period`() {
        val calendar = BudgetCalendar(startDay = 25, zoneId = ZoneId.of("Asia/Riyadh"))
        val previous = PeriodSchedule(
            start = LocalDate.of(2026, 4, 25),
            endExclusive = LocalDate.of(2026, 5, 25),
            configuredStartDay = 25,
            isTransition = false,
        )

        assertEquals(
            PeriodSchedule(
                start = LocalDate.of(2026, 5, 25),
                endExclusive = LocalDate.of(2026, 6, 25),
                configuredStartDay = 25,
                isTransition = false,
            ),
            calendar.nextPeriodAfter(previous, preferredStartDay = 25),
        )
    }

    @Test
    fun `changed start day creates one long transition past the old next expected end`() {
        val calendar = BudgetCalendar(startDay = 25, zoneId = ZoneId.of("Asia/Riyadh"))
        val previous = PeriodSchedule(
            start = LocalDate.of(2026, 4, 25),
            endExclusive = LocalDate.of(2026, 5, 25),
            configuredStartDay = 25,
            isTransition = false,
        )

        assertEquals(
            PeriodSchedule(
                start = LocalDate.of(2026, 5, 25),
                endExclusive = LocalDate.of(2026, 7, 10),
                configuredStartDay = 10,
                isTransition = true,
            ),
            calendar.nextPeriodAfter(previous, preferredStartDay = 10),
        )
        assertEquals(
            PeriodSchedule(
                start = LocalDate.of(2026, 5, 25),
                endExclusive = LocalDate.of(2026, 6, 30),
                configuredStartDay = 30,
                isTransition = true,
            ),
            calendar.nextPeriodAfter(previous, preferredStartDay = 30),
        )
    }

    @Test
    fun `long transition crosses a year boundary and respects clamped old boundaries`() {
        val zone = ZoneId.of("Asia/Riyadh")
        val decemberBoundary = PeriodSchedule(
            start = LocalDate.of(2026, 11, 25),
            endExclusive = LocalDate.of(2026, 12, 25),
            configuredStartDay = 25,
            isTransition = false,
        )
        val clampedBoundary = PeriodSchedule(
            start = LocalDate.of(2028, 1, 31),
            endExclusive = LocalDate.of(2028, 2, 29),
            configuredStartDay = 31,
            isTransition = false,
        )

        assertEquals(
            PeriodSchedule(LocalDate.of(2026, 12, 25), LocalDate.of(2027, 2, 10), 10, true),
            BudgetCalendar(25, zone).nextPeriodAfter(decemberBoundary, preferredStartDay = 10),
        )
        assertEquals(
            PeriodSchedule(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 4, 30), 30, true),
            BudgetCalendar(31, zone).nextPeriodAfter(clampedBoundary, preferredStartDay = 30),
        )
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
    fun `manual FX freezes confirmed accounting amount and can distinguish an estimate`() {
        val estimate = ManualFx.estimate(originalMinor = 2_000, originalFractionDigits = 2, accountingPerOriginal = "3.75")
        val confirmed = estimate.confirm(accountingMinor = 7_612)

        assertEquals(7_500, estimate.accountingMinor)
        assertFalse(estimate.confirmed)
        assertEquals(7_612, confirmed.accountingMinor)
        assertTrue(confirmed.confirmed)
        assertEquals(7_612, confirmed.confirm(accountingMinor = 7_612).accountingMinor)
    }
}
