package com.aif31.pocket.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class Money(val minor: Long, val currencyCode: String) {
    init {
        require(currencyCode.matches(Regex("[A-Z]{3}"))) { "Currency must be an ISO 4217 code" }
    }

    fun toMajorString(fractionDigits: Int = 2): String =
        BigDecimal.valueOf(minor).movePointLeft(fractionDigits).setScale(fractionDigits).toPlainString()

    companion object {
        fun parse(value: String, currencyCode: String): Money =
            fromMajor(value, currencyCode, RoundingMode.UNNECESSARY)

        fun fromMajor(
            value: String,
            currencyCode: String,
            roundingMode: RoundingMode = RoundingMode.HALF_UP,
            fractionDigits: Int = 2,
        ): Money {
            val minor = BigDecimal(value.trim())
                .movePointRight(fractionDigits)
                .setScale(0, roundingMode)
                .longValueExact()
            return Money(minor, currencyCode.uppercase())
        }
    }
}

data class BudgetPeriodBounds(
    val start: LocalDate,
    val endExclusive: LocalDate,
    val zoneId: ZoneId,
) {
    init {
        require(start < endExclusive)
    }

    val totalDays: Int get() = (endExclusive.toEpochDay() - start.toEpochDay()).toInt()
}

class BudgetCalendar(val startDay: Int, val zoneId: ZoneId) {
    init {
        require(startDay in 1..31)
    }

    fun periodContaining(date: LocalDate): BudgetPeriodBounds {
        val currentMonth = YearMonth.from(date)
        val currentBoundary = boundary(currentMonth)
        val startMonth = if (date >= currentBoundary) currentMonth else currentMonth.minusMonths(1)
        return BudgetPeriodBounds(
            start = boundary(startMonth),
            endExclusive = boundary(startMonth.plusMonths(1)),
            zoneId = zoneId,
        )
    }

    private fun boundary(month: YearMonth): LocalDate = month.atDay(startDay.coerceAtMost(month.lengthOfMonth()))
}

data class PocketSummary(
    val budgetMinor: Long,
    val rolloverMinor: Long,
    val netSpendMinor: Long,
    val availabilityMinor: Long,
    val consumedPercent: Int,
    val atRisk: Boolean,
    val exhausted: Boolean,
)

data class SpendProjection(val amountMinor: Long, val estimated: Boolean = true)

object PocketMath {
    fun summary(
        budgetMinor: Long,
        rolloverMinor: Long,
        expensesMinor: Long,
        refundsMinor: Long,
    ): PocketSummary {
        require(budgetMinor >= 0 && rolloverMinor >= 0 && expensesMinor >= 0 && refundsMinor >= 0)
        val availableBudget = Math.addExact(budgetMinor, rolloverMinor)
        val netSpend = Math.subtractExact(expensesMinor, refundsMinor)
        val availability = Math.subtractExact(availableBudget, netSpend)
        val consumed = when {
            availableBudget == 0L && netSpend <= 0L -> 0
            availableBudget == 0L -> 100
            else -> BigInteger.valueOf(netSpend)
                .multiply(BigInteger.valueOf(100))
                .divide(BigInteger.valueOf(availableBudget))
                .toInt()
        }
        return PocketSummary(
            budgetMinor = budgetMinor,
            rolloverMinor = rolloverMinor,
            netSpendMinor = netSpend,
            availabilityMinor = availability,
            consumedPercent = consumed,
            atRisk = consumed >= 80,
            exhausted = consumed >= 100,
        )
    }

    fun rollover(allocatedMinor: Long, netSpendMinor: Long, enabled: Boolean): Long =
        if (enabled) Math.subtractExact(allocatedMinor, netSpendMinor).coerceAtLeast(0) else 0

    fun project(netSpendMinor: Long, elapsedDays: Int, totalDays: Int): SpendProjection {
        require(elapsedDays > 0 && totalDays >= elapsedDays)
        val projected = BigInteger.valueOf(netSpendMinor)
            .multiply(BigInteger.valueOf(totalDays.toLong()))
            .divide(BigInteger.valueOf(elapsedDays.toLong()))
        require(projected >= BigInteger.valueOf(Long.MIN_VALUE) && projected <= BigInteger.valueOf(Long.MAX_VALUE)) {
            "Projected spend exceeds supported range"
        }
        return SpendProjection(projected.toLong())
    }
}

data class ManualFx(
    val originalMinor: Long,
    val sarMinor: Long,
    val confirmed: Boolean,
    val rate: String?,
) {
    fun confirm(sarMinor: Long): ManualFx {
        require(sarMinor >= 0)
        return copy(sarMinor = sarMinor, confirmed = true)
    }

    companion object {
        fun estimate(originalMinor: Long, originalFractionDigits: Int, sarPerOriginal: String): ManualFx {
            require(originalMinor >= 0)
            val rate = BigDecimal(sarPerOriginal)
            require(rate > BigDecimal.ZERO)
            val sarMinor = BigDecimal.valueOf(originalMinor)
                .movePointLeft(originalFractionDigits)
                .multiply(rate)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
            return ManualFx(originalMinor, sarMinor, confirmed = false, rate = rate.stripTrailingZeros().toPlainString())
        }
    }
}
