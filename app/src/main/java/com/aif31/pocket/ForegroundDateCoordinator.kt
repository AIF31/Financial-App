package com.aif31.pocket

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ForegroundDateCoordinator(
    initialDate: LocalDate,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val onForwardDate: suspend () -> Boolean,
) {
    private val mutex = Mutex()
    private var latestDate = initialDate

    suspend fun refresh(): Long {
        val now = clock.instant().atZone(zoneId)
        mutex.withLock {
            val date = now.toLocalDate()
            if (!date.isAfter(latestDate)) return@withLock
            if (!onForwardDate()) return@withLock
            latestDate = date
        }
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
        val untilMidnight = Duration.between(now.toInstant(), nextMidnight).toMillis().coerceAtLeast(1)
        return minOf(untilMidnight, FOREGROUND_CHECK_INTERVAL_MILLIS)
    }

    private companion object {
        const val FOREGROUND_CHECK_INTERVAL_MILLIS = 60_000L
    }
}
