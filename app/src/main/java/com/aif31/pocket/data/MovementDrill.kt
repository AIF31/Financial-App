package com.aif31.pocket.data

import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * One slice of a period's Movements picked from the period analysis: a merchant, a weekday, a day, or a payment
 * method. Recurring-charge history instead selects past expenses in the selected period's accounting currency.
 */
@Serializable
data class MovementDrill(val periodId: String, val kind: Kind, val value: String?) {
    enum class Kind { MERCHANT, WEEKDAY, DAY, PAYMENT_METHOD, MERCHANT_HISTORY }

    fun movements(state: LedgerState): List<Movement> {
        if (kind == Kind.MERCHANT_HISTORY) {
            val currency = state.periods.firstOrNull { it.id == periodId }?.accountingCurrency ?: return emptyList()
            val eligiblePeriods = state.periods.filter { it.accountingCurrency == currency }.map { it.id }.toSet()
            return state.movements.filter {
                it.periodId in eligiblePeriods && it.type == MovementType.EXPENSE &&
                    merchantKey(it.merchant) == value && it.localDate <= state.currentLocalDate
            }
        }
        val inProgress = periodId == state.currentPeriod?.id
        return state.periodMovements(periodId).filter { movement ->
            when (kind) {
                Kind.MERCHANT -> merchantKey(movement.merchant) == value
                // The weekday average counts only days that have passed, so the list does too.
                Kind.WEEKDAY -> movement.localDate.dayOfWeek.value.toString() == value &&
                    (!inProgress || movement.localDate <= state.currentLocalDate)
                Kind.DAY -> movement.localDate.toString() == value
                Kind.PAYMENT_METHOD -> movement.paymentMethodId == value
                Kind.MERCHANT_HISTORY -> false
            }
        }
    }

    /**
     * For a weekday drill, how many of that weekday the period has had through today: the divisor of the weekday
     * average the list was opened from. Null for other drills.
     */
    fun weekdaysElapsed(state: LedgerState): Int? {
        if (kind != Kind.WEEKDAY) return null
        val insights = PeriodInsights.of(state, periodId) ?: return null
        return (0 until insights.elapsedDays).count { insights.period.start.plusDays(it.toLong()).dayOfWeek.value.toString() == value }
    }

    companion object {
        fun merchant(periodId: String, key: String) = MovementDrill(periodId, Kind.MERCHANT, key)
        fun merchantHistory(periodId: String, key: String) = MovementDrill(periodId, Kind.MERCHANT_HISTORY, key)
        fun weekday(periodId: String, day: DayOfWeek) = MovementDrill(periodId, Kind.WEEKDAY, day.value.toString())
        fun day(periodId: String, date: LocalDate) = MovementDrill(periodId, Kind.DAY, date.toString())

        /** A null [id] selects Movements recorded without a payment method. */
        fun paymentMethod(periodId: String, id: String?) = MovementDrill(periodId, Kind.PAYMENT_METHOD, id)
    }
}
