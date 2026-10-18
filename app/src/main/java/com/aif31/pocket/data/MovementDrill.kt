package com.aif31.pocket.data

import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * One slice of a period's Movements picked from the period analysis: a merchant, a weekday, a day, or a payment
 * method. It selects from the same Movements the analysis counted, so the list adds up to the figure tapped.
 */
@Serializable
data class MovementDrill(val periodId: String, val kind: Kind, val value: String?) {
    enum class Kind { MERCHANT, WEEKDAY, DAY, PAYMENT_METHOD }

    fun movements(state: LedgerState): List<Movement> {
        val inProgress = periodId == state.currentPeriod?.id
        return state.periodMovements(periodId).filter { movement ->
            when (kind) {
                Kind.MERCHANT -> merchantKey(movement.merchant) == value
                // The weekday average counts only days that have passed, so the list does too.
                Kind.WEEKDAY -> movement.localDate.dayOfWeek.value.toString() == value &&
                    (!inProgress || movement.localDate <= state.currentLocalDate)
                Kind.DAY -> movement.localDate.toString() == value
                Kind.PAYMENT_METHOD -> movement.paymentMethodId == value
            }
        }
    }

    companion object {
        fun merchant(periodId: String, key: String) = MovementDrill(periodId, Kind.MERCHANT, key)
        fun weekday(periodId: String, day: DayOfWeek) = MovementDrill(periodId, Kind.WEEKDAY, day.value.toString())
        fun day(periodId: String, date: LocalDate) = MovementDrill(periodId, Kind.DAY, date.toString())

        /** A null [id] selects Movements recorded without a payment method. */
        fun paymentMethod(periodId: String, id: String?) = MovementDrill(periodId, Kind.PAYMENT_METHOD, id)
    }
}
