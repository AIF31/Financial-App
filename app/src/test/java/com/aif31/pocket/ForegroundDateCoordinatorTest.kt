package com.aif31.pocket

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundDateCoordinatorTest {
    @Test
    fun new_forward_date_refreshes_once_while_same_or_backward_dates_do_nothing() = runTest {
        val zone = ZoneId.of("Asia/Riyadh")
        val clock = MutableClock(Instant.parse("2026-03-24T09:00:00Z"), zone)
        var calls = 0
        val coordinator = ForegroundDateCoordinator(LocalDate.of(2026, 3, 24), clock, zone) {
            calls++
            true
        }

        coordinator.refresh()
        clock.value = Instant.parse("2026-03-25T09:00:00Z")
        coordinator.refresh()
        coordinator.refresh()
        clock.value = Instant.parse("2026-03-24T09:00:00Z")
        coordinator.refresh()

        assertEquals(1, calls)
    }

    @Test
    fun failed_refresh_is_retried_for_the_same_date() = runTest {
        val zone = ZoneId.of("Asia/Riyadh")
        val clock = MutableClock(Instant.parse("2026-03-25T09:00:00Z"), zone)
        var succeeds = false
        var calls = 0
        val coordinator = ForegroundDateCoordinator(LocalDate.of(2026, 3, 24), clock, zone) {
            calls++
            succeeds
        }

        assertEquals(60_000L, coordinator.refresh())
        succeeds = true
        coordinator.refresh()

        assertEquals(2, calls)
    }

    @Test
    fun refresh_uses_the_injected_clock_and_rechecks_during_a_long_foreground_session() = runTest {
        val zone = ZoneId.of("Asia/Riyadh")
        val clock = MutableClock(Instant.parse("2026-03-24T20:59:00Z"), zone)
        var calls = 0
        val coordinator = ForegroundDateCoordinator(
            initialDate = LocalDate.of(2026, 3, 23),
            clock = clock,
            zoneId = zone,
        ) {
            calls++
            true
        }

        assertEquals(60_000L, coordinator.refresh())
        clock.value = Instant.parse("2026-03-24T21:01:00Z")
        coordinator.refresh()
        coordinator.refresh()
        clock.value = Instant.parse("2026-03-23T21:01:00Z")
        coordinator.refresh()

        assertEquals(2, calls)
    }

    @Test
    fun a_forward_clock_change_is_detected_at_the_next_foreground_check() = runTest {
        val zone = ZoneId.of("Asia/Riyadh")
        val clock = MutableClock(Instant.parse("2026-03-24T09:00:00Z"), zone)
        var calls = 0
        val coordinator = ForegroundDateCoordinator(LocalDate.of(2026, 3, 24), clock, zone) {
            calls++
            true
        }

        assertEquals(60_000L, coordinator.refresh())
        clock.value = Instant.parse("2026-03-25T09:00:00Z")
        coordinator.refresh()

        assertEquals(1, calls)
    }

    private class MutableClock(
        var value: Instant,
        private val zoneId: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = MutableClock(value, zone)
        override fun instant(): Instant = value
    }
}
