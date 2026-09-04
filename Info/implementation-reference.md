# Implementation reference

## Product boundary

Pocket is a Spanish-first, single-user, local-first Android application for manually budgeting declared funds and recording expenses or refunds. It does not model bank accounts, import transactions, authenticate users, or synchronize to a backend. Internet access is limited to exchange-rate lookup after explicit user consent.

The authoritative requirements are in GitHub Issue [AIF31/Financial-App#1](https://github.com/AIF31/Financial-App/issues/1). If an older research plan differs from that issue, the issue wins.

## Technology and architecture

- Kotlin, Jetpack Compose, Material 3, and a single Activity.
- Room is the source of truth for financial records.
- DataStore is limited to small preferences such as the future period start day and reminder configuration.
- State flows toward Compose; user actions flow through the ledger/application interface.
- Money is stored in integer minor units; `Float` and `Double` are not used for financial amounts.
- Budget-period assignment uses `Asia/Riyadh`, with day 25 as the default future-period start.
- SAR is the reporting currency. USD and MXN movements retain their original amount and a manually entered SAR equivalent with estimated or confirmed status.
- The app has no backend, login, third-party analytics, or advertising SDK. Its Internet permission supports opt-in HTTPS exchange-rate lookup.

## Canonical language

Use the glossary in `CONTEXT.md`. In particular:

- A **Pocket** is a spending classification with a budget, not a category, account, or envelope.
- **Pocket availability** is budget plus eligible rollover, less expenses, plus refunds. It may be negative.
- A **Pocket budget** is the portion of a period's new funds assigned to a Pocket.
- **New funds** are declared for a budget period; they are not income or a deposit.
- A **movement** is an expense or refund assigned to one Pocket and one budget period.

## Implemented MVP capabilities

- Initial setup with editable new funds and a configurable period start day.
- Editable, reorderable, archivable Pockets and payment-method labels.
- Pocket budgets, unassigned funds, selective rollover, and period creation.
- Manual expenses and refunds, including editing, delete/undo, negative availability, and 80%/100% warning states.
- Manual SAR, USD, and MXN capture with estimated/confirmed SAR equivalents.
- Opt-in online USD/MXN exchange-rate lookup with local caching; same-currency and SAR/USD conversion remain available offline.
- Dashboard totals, daily pace, projection, Pocket availability, previous-period comparison, and historical recalculation.
- History search and filters for period, Pocket, original currency, and payment method.
- Manual recurring templates that prefill rather than automatically create movements.
- Launcher shortcut for `Nuevo gasto`.
- Optional daily reminder with user-selected time and no financial amount in lock-screen content.
- Versioned full backup/restore and analytical CSV export through the Storage Access Framework.
- Offline core operation and app-private primary storage.

## Explicitly excluded from the MVP

Bank integrations, notification ingestion, SMS reading, cloud sync, authentication, receipt OCR, AI categorization, widgets, app-specific biometrics, automatic recurring expenses, backup merging, and Google Play publication are outside Issue #1.

## Important implementation history

| Commit | Purpose |
| --- | --- |
| `6e36227` | Accepted repository documentation/configuration baseline for Issue #1 |
| `5abb97c` | Initial local-first spending MVP implementation |
| `4e17dd3` | Material implementation-review fixes |
| `bfe56b5` | Retained host/device flow verification tests |
| `2b01539` | Permanent signed-release workflow and strengthened tests |

## Review findings resolved

- Replaced machine-specific usernames in reusable instructions with `%USERPROFILE%` or `$env:USERPROFILE`.
- Removed device-specific identifiers from committed evidence.
- Standardized documentation on the canonical term **Pocket budget**.
- Strengthened history tests so period, Pocket, currency, and payment-method filters each have positive and negative observable assertions.
- Strengthened shortcut coverage to submit a movement; the Android test now launches `MainActivity` with the real shortcut action via `ActivityScenario`.
- Keystores are excluded by `.gitignore`, and partial release-signing configuration fails closed.
