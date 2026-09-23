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
        val current = mutex.withLock {
            val date = now.toLocalDate()
            if (!date.isAfter(latestDate)) return@withLock true
            if (!onForwardDate()) return@withLock false
            latestDate = date
            true
        }
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
        val untilMidnight = Duration.between(now.toInstant(), nextMidnight).toMillis().coerceAtLeast(1)
        return if (current) untilMidnight else minOf(untilMidnight, RETRY_DELAY_MILLIS)
    }

    private companion object {
        const val RETRY_DELAY_MILLIS = 60_000L
    }
}
