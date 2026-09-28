package com.aif31.pocket.notifications

import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.Period
import com.aif31.pocket.data.PocketLedger
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/** Why a detected payment stayed in the review inbox instead of becoming a Movement. */
internal enum class ReviewReason { NO_MERCHANT, NEW_MERCHANT, POCKET_ARCHIVED, FOREIGN_CURRENCY, NO_PERIOD, REJECTED, UNAVAILABLE }

internal sealed interface AutoRecordOutcome {
    data class Recorded(val movementId: String, val pocketName: String) : AutoRecordOutcome
    data class NeedsReview(val reason: ReviewReason) : AutoRecordOutcome
}

/**
 * Turns a pending suggestion into a Movement when Pocket already knows where it belongs: the Pocket of the
 * most recent expense with the same merchant, in the period's accounting currency. Anything uncertain stays
 * a suggestion, so auto-recording never guesses a Pocket or an exchange rate.
 */
internal class NotificationAutoRecorder(
    private val ledger: PocketLedger,
    private val zoneId: ZoneId,
    private val catchUpPeriods: suspend () -> Unit = {},
) {
    suspend fun record(suggestionId: String): AutoRecordOutcome {
        val state = ledger.state.first()
        val suggestion = state.movementSuggestions.firstOrNull { it.id == suggestionId }
            ?: return AutoRecordOutcome.NeedsReview(ReviewReason.UNAVAILABLE)
        val localDate = Instant.ofEpochMilli(suggestion.effectiveAtUtcMillis).atZone(zoneId).toLocalDate()
        fun List<Period>.containing() = firstOrNull { !localDate.isBefore(it.start) && localDate.isBefore(it.endExclusive) }
        // A purchase can arrive before Pocket is opened in a new period; create that period as launch would.
        val period = state.periods.containing()
            ?: run { catchUpPeriods(); ledger.state.first().periods.containing() }
            ?: return AutoRecordOutcome.NeedsReview(ReviewReason.NO_PERIOD)
        // A conversion blocks recording even with a known Pocket, so it is the reason the person must see.
        if (suggestion.currency != period.accountingCurrency) {
            return AutoRecordOutcome.NeedsReview(ReviewReason.FOREIGN_CURRENCY)
        }
        val key = merchantKey(suggestion.merchant) ?: return AutoRecordOutcome.NeedsReview(ReviewReason.NO_MERCHANT)
        val latest = state.movements
            .filter { it.type == MovementType.EXPENSE && merchantKey(it.merchant) == key }
            .maxByOrNull { it.occurredAtUtcMillis }
            ?: return AutoRecordOutcome.NeedsReview(ReviewReason.NEW_MERCHANT)
        // Only the newest expense decides; falling back to an older Pocket would be a guess.
        val pocket = state.pocketCatalog.firstOrNull { it.id == latest.pocketId && !it.archived }
            ?: return AutoRecordOutcome.NeedsReview(ReviewReason.POCKET_ARCHIVED)
        val movementId = autoRecordedMovementId(suggestion.id)
        val result = ledger.execute(
            LedgerCommand.ConfirmSuggestion(
                suggestionId = suggestion.id,
                movement = LedgerCommand.AddMovement(
                    pocketId = pocket.id,
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = suggestion.amountMinor,
                    occurredAtUtcMillis = suggestion.effectiveAtUtcMillis,
                    localDate = localDate,
                    merchant = suggestion.merchant,
                    paymentMethodId = state.defaultPaymentMethodId
                        ?.takeIf { id -> state.paymentMethods.any { it.id == id && !it.archived } },
                    originalCurrencyCode = suggestion.currency.name,
                    accountingCurrency = period.accountingCurrency,
                ),
                submissionId = movementId,
                automatic = true,
            )
        )
        return if (result == LedgerResult.Success) {
            AutoRecordOutcome.Recorded(movementId, pocket.name)
        } else {
            AutoRecordOutcome.NeedsReview(ReviewReason.REJECTED)
        }
    }
}

private const val AUTO_RECORDED_PREFIX = "ntf-"

/** Auto-recorded Movements get a derived ID so the UI can label them and retries stay idempotent. */
internal fun autoRecordedMovementId(suggestionId: String) = AUTO_RECORDED_PREFIX + suggestionId

internal fun isAutoRecordedMovement(movementId: String) = movementId.startsWith(AUTO_RECORDED_PREFIX)
