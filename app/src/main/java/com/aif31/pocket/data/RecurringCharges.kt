package com.aif31.pocket.data

import com.aif31.pocket.domain.sumMoneyExact
import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class RecurringCadence { MONTHLY, WEEKLY }

data class RecurringCharge(
    val merchantKey: String,
    val merchantName: String,
    val pocketId: String,
    val pocketName: String,
    val expectedDate: LocalDate,
    val expectedAmountMinor: Long,
    val cadence: RecurringCadence,
    val chargeCount: Int,
    val overdue: Boolean,
)

/** Derived expectations only: this module never records a Movement or a suggestion. */
data class RecurringCharges(val items: List<RecurringCharge>) {
    val totalExpectedMinor: Long? = runCatching { items.map { it.expectedAmountMinor }.sumMoneyExact() }.getOrNull()

    companion object {
        fun of(state: LedgerState, periodId: String): RecurringCharges {
            val period = state.periods.firstOrNull { it.id == periodId } ?: return RecurringCharges(emptyList())
            val currencyByPeriod = state.periods.associate { it.id to it.accountingCurrency }
            val items = state.movements.filter {
                it.type == MovementType.EXPENSE && merchantKey(it.merchant) != null &&
                    currencyByPeriod[it.periodId] == period.accountingCurrency
            }.groupBy { merchantKey(it.merchant)!! }.flatMap { (key, charges) ->
                if (charges.size < 3) return@flatMap emptyList()
                val ordered = charges.sortedWith(compareBy<Movement> { it.localDate }.thenBy { it.occurredAtUtcMillis }.thenBy { it.id })
                val gaps = ordered.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.localDate, b.localDate) }
                val cadence = when {
                    gaps.all { it in 26L..35L } -> RecurringCadence.MONTHLY
                    gaps.all { it in 6L..8L } -> RecurringCadence.WEEKLY
                    else -> return@flatMap emptyList()
                }
                val amounts = ordered.takeLast(3).map { BigInteger.valueOf(it.accountingAmountMinor) }
                val medianAmount = amounts.sorted()[1]
                if (amounts.any { (it - medianAmount).abs() * BigInteger.TEN > medianAmount }) return@flatMap emptyList()
                val gap = medianMinor(gaps)!!
                val latest = ordered.last()
                val monthDay = ordered.maxOf { it.localDate.dayOfMonth }
                fun calendarDate(index: Long): LocalDate {
                    val month = ordered.first().localDate.plusMonths(index)
                    return month.withDayOfMonth(minOf(monthDay, month.lengthOfMonth()))
                }
                // shortcut: shifted monthly dates use median-day estimates; add tolerance when supporting posting-date drift.
                val calendarMonthly = cadence == RecurringCadence.MONTHLY && ordered.withIndex().all { (index, charge) ->
                    charge.localDate == calendarDate(index.toLong())
                }
                fun expectedDate(occurrence: Long): LocalDate =
                    if (calendarMonthly) calendarDate(ordered.lastIndex + occurrence) else latest.localDate.plusDays(gap * occurrence)
                var occurrence = 1L
                var expected = expectedDate(occurrence)
                // Two consecutive expired expectations indicate the merchant has ended.
                if (ChronoUnit.DAYS.between(expectedDate(2), state.currentLocalDate) > gap / 2) return@flatMap emptyList()
                buildList {
                    while (expected < period.endExclusive) {
                        if (expected >= period.start && ChronoUnit.DAYS.between(expected, state.currentLocalDate) <= gap / 2) {
                            add(RecurringCharge(
                                key, latest.merchant!!, latest.pocketId, latest.pocketName, expected, latest.accountingAmountMinor,
                                cadence, charges.size, expected < state.currentLocalDate,
                            ))
                        }
                        expected = expectedDate(++occurrence)
                    }
                }
            }
            return RecurringCharges(items.sortedWith(compareBy<RecurringCharge> { it.expectedDate }.thenBy { it.merchantKey }))
        }
    }
}
