package com.aif31.pocket.data

import com.aif31.pocket.domain.SupportedCurrency
import kotlin.math.abs

/** How far apart a recorded expense and a detected payment can be and still describe the same purchase. */
const val DUPLICATE_WINDOW_MILLIS = 12 * 60 * 60 * 1000L

/**
 * The expense that already records a detected payment, if any: same merchant, dated within
 * [DUPLICATE_WINDOW_MILLIS], and the same amount in the payment's currency, either as entered or as converted into
 * the Movement's accounting currency. It catches a purchase typed in by hand before its bank alert arrives, including
 * one entered in another currency, or one purchase alerted by two apps. Amount alone is never enough, so a payment
 * without a merchant has no duplicate.
 */
fun List<Movement>.likelyDuplicateOf(
    merchant: String?,
    amountMinor: Long,
    currency: SupportedCurrency,
    atUtcMillis: Long,
    accountingCurrencyOf: (periodId: String) -> SupportedCurrency?,
): Movement? {
    val key = merchantKey(merchant) ?: return null
    return firstOrNull { movement ->
        val sameAsEntered = movement.originalCurrencyCode == currency.name &&
            (movement.originalAmountMinor ?: movement.accountingAmountMinor) == amountMinor
        val sameAsConverted = accountingCurrencyOf(movement.periodId) == currency && movement.accountingAmountMinor == amountMinor
        movement.type == MovementType.EXPENSE &&
            (sameAsEntered || sameAsConverted) &&
            abs(movement.occurredAtUtcMillis - atUtcMillis) <= DUPLICATE_WINDOW_MILLIS &&
            merchantKey(movement.merchant) == key
    }
}
