# Notification assistance beta

Status: experimental beta in the canonical Windows checkout. Automated verification is complete; real Android notification-listener delivery remains unverified. This page describes the beta implementation and does not revise the historical planning record in [`FUTURE_UPDATES_DECISION_SPEC.md`](./FUTURE_UPDATES_DECISION_SPEC.md).

Notification assistance turns an eligible notification into a temporary **Movement suggestion**. It is suggestion-only: parsing never writes to the ledger or creates a Movement without the user reviewing and confirming it.

## Enable the beta

1. Open **Ajustes > Captura desde notificaciones**.
2. Select the source apps with the checkboxes. Pocket stores the selected package identifiers; an unselected package is rejected before parsing.
3. Choose **Conceder acceso** or, after it is enabled, **Administrar acceso**. Android opens its notification-listener settings, where the user must enable Pocket. This is a system-controlled permission; Pocket cannot grant it for the user. See the official [`NotificationListenerService` reference](https://developer.android.com/reference/android/service/notification/NotificationListenerService).
4. Return to Pocket. The settings page refreshes the access state when it resumes. Capture requires both system access and an explicitly selected source package. Revoking system access leaves the financial ledger intact and stops listener delivery.

## Review a suggestion

Pocket reads the notification title and text in memory after the package check. The experimental parser accepts synthetic, generic purchase or payment messages in English and Spanish for SAR, USD, and MXN. It requires a single unambiguous supported amount. Malformed, unsupported, unrelated, ambiguous, refund, and reversal messages are conservatively rejected.

Accepted candidates appear in the **Movements** suggestion inbox. Opening one uses the normal expense form with the detected amount, currency, effective date/time, and optional merchant prefilled. The user chooses a Pocket, edits any field, and confirms through the ordinary Movement path. The user can also discard a suggestion. A repeated notification identity updates its pending candidate; amount alone is never a deduplication key.

No notification is treated as a complete transaction feed. The parser makes no bank-support claim and may miss or reject valid notifications. The user remains responsible for reviewing every suggestion and recording movements that do not produce one.

## Stored data and retention

Raw notification text is parsed in memory and is not written to Room, logs, analytics, crash metadata, or backups. A pending suggestion stores only normalized data:

- amount in minor units and its currency;
- effective time;
- source package identifier;
- a one-way identity hash for deduplication; and
- an optional extracted merchant or description, plus lifecycle status and expiry metadata.

Pending suggestions are valid for 30 days. After expiry they cannot be confirmed and are hidden from the ledger state; database cleanup removes the expired rows on the next notification capture or app catch-up when Pocket launches or resumes. There is no exact-time background deletion while the app is inactive. Confirming or discarding clears the normalized financial fields and retains only the one-way identity tombstone until its expiry. Notification suggestions and their identities are excluded from Pocket's portable backup; restoring a backup does not restore the suggestion inbox.

## Beta diagnostics and promotion limits

Debug-only beta diagnostics are limited to six aggregate counters: parser attempts, parser successes, confirmations, corrected confirmations, amount corrections, and currency corrections. They must not retain notification content or normalized transaction fields. Source inspection provides evidence that the debug provider persists only these counters, the release provider is a no-op, PocketApplication wires capture and confirmation metrics, MainActivity resets them after a successful restore, and the parser, capture, and listener paths contain no logging.

Stable promotion has not been claimed. It requires the English and Spanish synthetic suites, no parser crashes, at least 95% correct amount/currency extraction, and fewer than 10% user corrections over a predefined beta sample. The minimum sample size must be fixed before beta exit; that decision remains unresolved.

## Verification

The current verification evidence is:

- Wrapper compilation passed for `:app:compileDebugKotlin`, `:app:compileReleaseKotlin`, and `:app:compileDebugAndroidTestKotlin`.
- Focused host tests passed: `NotificationBetaMetricsTest` (2 tests), `NotificationPaymentParserTest` (5), `NotificationSuggestionLedgerTest` (12), and `PocketAppHostFlowTest` (31).
- Focused reruns passed: `AppPreferencesTest` (4 tests) and `NotificationAssistanceSettingsTest` (1). Their test storage uses the existing Okio NIO adapter, preserving real DataStore serialization; no production change was made.
- The Pixel 6 API 35 Gradle Managed Device migration execution passed all five migration tests, including the 6-to-7 migration. The generated [report](../../app/build/reports/androidTests/managedDevice/debug/pixel6Api35/index.html) records that run.
- A forced fresh `:app:testDebugUnitTest --rerun-tasks` execution passed all 149 host tests across 17 suites with zero failures or errors.

Automated verification is complete for the implemented beta scope. Host coverage is synthetic and does not establish the real Android system/device notification-listener permission experience or listener delivery. Stable promotion remains unresolved until the sample-size decision is fixed and the promotion gates are met.
