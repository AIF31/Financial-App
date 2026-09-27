# Notification assistance beta

Status: experimental beta in the canonical Windows checkout. Automated verification is complete and listener delivery was verified on an emulator with synthetic SMS; delivery on a physical device with real bank messages remains unverified. This page describes the beta implementation and does not revise the historical planning record in [`FUTURE_UPDATES_DECISION_SPEC.md`](./FUTURE_UPDATES_DECISION_SPEC.md).

Notification assistance reads payment notifications from apps the person selects, such as a bank app or, for banks that send SMS, the Messages app. A payment for a known merchant becomes a Movement automatically; anything uncertain becomes a **Movement suggestion** to review. [ADR 0002](../adr/0002-auto-record-notification-payments-for-known-merchants.md) records why the beta moved from suggestion-only to auto-recording.

## Enable the beta

1. Open **Ajustes > Captura desde notificaciones**. A setup card shows three steps with their current state: notification access, selected apps, and Pocket's own alert permission.
2. Search for and select the source apps. Selected apps stay pinned at the top; banks, payment apps, and messaging apps are suggested first. Each row shows the app icon and name only. An unselected package is rejected before parsing.
3. Choose **Conceder acceso** (or **Administrar acceso**). Android opens its notification-listener settings, where the person must enable Pocket; Pocket cannot grant this itself. See the official [`NotificationListenerService` reference](https://developer.android.com/reference/android/service/notification/NotificationListenerService).
4. Choose **Permitir avisos** if Pocket's notifications are blocked. The screen refreshes all three states when it resumes.
5. Two switches control behavior, both on by default: **Registrar automáticamente** and **Aviso al detectar un gasto**.

## What happens when a payment is detected

Pocket reads each message on its own. Messaging apps keep one notification per conversation and append new messages, so an OTP beside a purchase cannot hide it, and a new SMS does not overwrite an earlier one. A message already stored is not parsed again when the conversation is re-posted. A bank app that updates its notification with the same text does not create a second payment; one that reuses the notification for a new transaction does.

The experimental parser accepts generic English and Spanish purchase messages in SAR, USD, and MXN, including multi-line bank SMS such as `Amount: SAR 243.18` / `At: MERCHANT` and single-line point-of-sale texts such as `was used at MERCHANT for SAR 23.05 on …`. A bare `$` amount is read as the default expense currency when that is USD or MXN; otherwise it is rejected as ambiguous. It requires exactly one supported amount. Refunds, reversals, credits, transfers, deposits, OTP and verification codes, declined, pending, due, and promotional messages are rejected.

With auto-recording on, the payment becomes a Movement when:

- it has a merchant, and the most recent expense for that merchant (ignoring case and punctuation) is in a Pocket that is not archived; and
- its currency equals the accounting currency of the period containing its date. If that period does not exist yet because Pocket has not been opened since it began, Pocket catches up periods first, as it does on launch. A foreign currency always needs review, and its hint asks for the conversion even when the merchant is new.

A sales channel after "en" or "at" (`en línea`, `online`, `internet`) is skipped, so `Compra en línea por … en OXXO` reads the merchant as `OXXO`.

The Movement uses the default payment method and is labelled **Detectado** in Movements. Every other payment stays in the **Por revisar** inbox at the top of Movements, with the source app name, date and time, and a hint: choose a Pocket, or confirm the conversion for a foreign currency. **Revisar** opens the normal expense form prefilled; **Descartar** removes it.

With the alert on, a newly detected payment from the last 15 minutes shows a heads-up notification such as `Gasto registrado · SAR 8.00` / `Tamimi-Market → Supermercado · Messages`, or `Gasto detectado · SAR 23.05` / `TAMIMI MARKET · Elige un Pocket para registrarlo`. Tapping it opens Movements. The lock screen shows only `Pocket leyó un movimiento`. Older messages found when access is first granted are captured silently.

No notification is treated as a complete transaction feed. The parser makes no bank-support claim and may miss valid notifications, so the person remains responsible for reviewing Movements.

## Stored data and retention

Raw notification text is parsed in memory and is not written to Room, logs, analytics, crash metadata, or backups. A pending suggestion stores only normalized data:

- amount in minor units and its currency;
- effective time;
- source package identifier;
- a one-way identity hash for deduplication; and
- an optional extracted merchant or description, plus lifecycle status and expiry metadata.

Pending suggestions are valid for 30 days. After expiry they cannot be confirmed and are hidden from the ledger state; database cleanup removes the expired rows on the next notification capture or app catch-up when Pocket launches or resumes. There is no exact-time background deletion while the app is inactive. Confirming, auto-recording, or discarding clears the normalized financial fields and retains only the one-way identity tombstone until its expiry. Notification suggestions and their identities are excluded from Pocket's portable backup; restoring a backup does not restore the suggestion inbox.

## Beta diagnostics and promotion limits

Debug-only beta diagnostics are limited to six aggregate counters: parser attempts, parser successes, confirmations, corrected confirmations, amount corrections, and currency corrections. Auto-recorded Movements are not counted as confirmations, and a re-posted message is not counted twice as a parser attempt. A message that fails to parse is not stored, so it is counted again each time its conversation is re-posted. They must not retain notification content or normalized transaction fields. Source inspection provides evidence that the debug provider persists only these counters, the release provider is a no-op, PocketApplication wires capture and confirmation metrics, MainActivity resets them after a successful restore, and the parser, capture, and listener paths contain no logging.

Stable promotion has not been claimed. It requires the English and Spanish synthetic suites, no parser crashes, at least 95% correct amount/currency extraction, and fewer than 10% user corrections over a predefined beta sample. The minimum sample size must be fixed before beta exit; that decision remains unresolved.

## Verification

2026-09-27 (auto-recording change):

- Host tests: `BankNotificationFormatsTest` (8), `NotificationAutoRecorderTest` (6), `ConversationCaptureTest` (4), `BankAppNotificationCaptureTest` (2), and the existing notification suites. A fresh full `:app:testDebugUnitTest --rerun-tasks` run passed 224 tests; `:app:compileReleaseKotlin` and `:app:compileDebugAndroidTestKotlin` compiled.
- Bugs found during review and emulator testing were each fixed test-first, with a failing test seen before the fix: counting re-posted messages twice, `en línea` read as the merchant, duplicate payments from updated bank-app notifications, no auto-recording before a new period was caught up, and a new-merchant hint shown instead of the conversion hint for foreign currency.
- The parser, auto-record, and capture tests were checked against deliberate regressions (the previous merchant pattern, a missing `credited` exclusion, raw merchant matching, and parsing every re-posted message); each regression made them fail.
- Pixel_10_Pro emulator: with Messages selected, `adb emu sms send` delivered a synthetic SAB-style point-of-sale SMS. It produced the `Gasto detectado` alert, and tapping it opened the **Por revisar** card. After it was saved into Supermercado, a second SMS for the same merchant, written with different punctuation, was auto-recorded with a `Gasto registrado … → Supermercado` alert and the **Detectado** label, without duplicating the first message.

Still unverified: delivery on the physical Pixel 10 Pro with real bank SMS or bank-app notifications, maximum font, and TalkBack. Stable promotion still needs the beta sample-size decision and the promotion gates above.
