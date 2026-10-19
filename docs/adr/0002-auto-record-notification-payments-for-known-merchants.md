---
status: accepted
date: 2026-09-27
---

# Auto-record notification payments for known merchants

## Context

The notification assistance beta was suggestion-only: every detected payment waited in an inbox until the person opened the expense form and confirmed it. In daily use this meant bank purchases were "not added automatically". Real bank SMS also showed that detection itself was unreliable. Messaging apps append every new message to one conversation notification, so a new purchase overwrote the previous suggestion or was ignored after an earlier confirmation. An OTP in the same conversation could also hide a purchase.

## Decision

A detected payment becomes a Movement without review when Pocket already knows where it belongs:

- the payment has a merchant, and the most recent expense with the same normalized merchant sits in a Pocket that is not archived;
- the payment currency equals the accounting currency of the budget period containing its date; and
- the ledger accepts the Movement under its normal validation.

Otherwise the payment stays a Movement suggestion for review. Pocket never guesses a Pocket for a new merchant and never invents an exchange rate.

Auto-recorded Movements use the ID `ntf-` plus the suggestion identity. That makes retries idempotent and lets the UI label them "Detectado". The suggestion is tombstoned as `AUTO_RECORDED`, and auto-recording does not count toward the beta correction metrics, which measure human corrections.

Each message in a notification is parsed and identified separately by conversation, message time, and text. A message already stored, including as a tombstone, is not parsed again.

When a payment is newly detected, Pocket posts a short heads-up notification saying whether it was recorded (amount, merchant → Pocket, source app) or needs review. The lock screen shows only a redacted public version.

Auto-recording and the detection alert are separate settings, both on by default. Turning auto-recording off restores suggestion-only behavior.

## Consequences

- The Movement definition in `CONTEXT.md` now includes Movements recorded from a known merchant's notification.
- A wrong merchant memory records a wrong Pocket until the person edits the Movement. The alert and the "Detectado" label keep these records visible.
- Deleting an auto-recorded Movement does not re-create it; the tombstone blocks the same message until it expires after 30 days.
- The raw notification text is still never stored. Only a one-way identity hash and normalized fields are kept.

## Amendment (2026-10-18): possible duplicates stay in review

A purchase typed in by hand gives its merchant a known Pocket, so the bank alert that follows met every condition above and was recorded a second time. A detected payment is now also kept for review when an expense for the same normalized merchant, with the same amount in the payment's currency, is dated within 12 hours of it. The amount matches either as the expense was entered or as converted into its period's accounting currency, so a purchase typed in USD still matches the bank's SAR charge. The ledger repeats the check inside the transaction that records the payment, so an expense saved while the alert is being processed still wins. This covers manual entries and the same purchase alerted by two apps. The review hint reads "Posible duplicado de un gasto ya registrado". A merchant is still required, so amount alone never decides. Two genuine identical purchases within 12 hours now need one confirmation instead of none.
