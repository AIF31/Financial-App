# Pocket future updates — decision specification

Status: planning approved; implementation not started

Decision session completed: 2026-08-29

Product code source of truth: `C:\Users\alan1\StudioProjects\Financial-App`

Reviewed baseline: local `main` at `61f5782fbf4aefad053c260c0e5e8d2610de0338`

## Purpose

This document converts the completed product grill into an implementation-ready direction for later work in the canonical Windows checkout. This WSL repository stores planning documentation only. It is not the source of truth for current application code.

The plan preserves Pocket as a private, single-user, offline-first personal spending tool. Reliability and financial correctness precede UX refinement and feature expansion.

## Verified baseline

The reviewed Windows baseline already includes:

- Production Material 3 dashboard, expense flow, settings hub, Pocket artwork, and compact/medium navigation behavior.
- Navigation 3 routes and a full-screen quick-expense flow.
- Room schema version 2 with automatic migration.
- Transactional replacement restore with a destructive-action warning.
- Host and Android test sources, a Pixel 6 API 35 `aosp-atd` Gradle Managed Device, and API 34+ Compose accessibility checks in device tests.
- A JDK 17 host-test baseline that passed `:app:testDebugUnitTest` during the review.

The following gaps remain relevant:

- Period catch-up is not automatically driven on launch or resume.
- Historical edits do not reliably cascade materialized rollover through every later period.
- Refund-only positive availability can be omitted when a Pocket has no allocation row.
- Start-day changes do not yet implement the accepted long-transition policy.
- Archive behavior does not resolve templates, current budgets, and positive rollover using the accepted rules.
- Full backup does not yet cover all selected DataStore preferences or offer a pre-restore safety export.
- Plaintext disclosure and first-use confirmation are incomplete.
- Movement validation is weaker at runtime than during backup validation.
- Reminder scheduling and permission state can diverge from what the UI reports.
- Automated production accessibility, large-font, navigation, CI, and recovery gates are incomplete.
- Notification-assisted movement suggestions do not exist in production.

## Accepted product decisions

### Product boundary and priorities

1. Preserve the Pocket-only financial model; do not introduce accounts or bank balances.
2. Optimize for a private personal tool and the owner's Galaxy S23 Ultra next.
3. Prioritize trust and reliability, then UX quality, then expansion.
4. Remain offline-first. A narrowly scoped opt-in network feature may be considered later.
5. Keep the approved production UI direction.
6. Limit financial currencies to SAR, USD, and MXN.
7. Use English and Spanish only for notification parsing and its review flow; full-app localization is not in this plan.

### Periods and rollover

8. Historical edits recalculate rollover through every later period while preserving later funds, Pocket budgets, and Movements.
9. Snapshot rollover eligibility per Pocket and source period so later preference changes do not rewrite old eligibility.
10. Refund-only positive availability may roll when the source Pocket was opted in.
11. Create every missing period sequentially, transactionally, and idempotently on app launch or resume.
12. Keep immediate expense capture available after catch-up and show a review banner for the current period only.
13. Changing the preferred start day creates one long transition period rather than a short nearby period.
14. The transition ends at the first new-schedule boundary after the old schedule's next expected end.
15. Mark the transition clearly and compare it using daily spending pace rather than absolute totals.

### Pocket archival

16. Active dependent recurring templates block archival; the blocking templates must be listed.
17. Archiving a currently funded Pocket returns its Pocket budget to unassigned funds.
18. Positive rollover is converted to unassigned funds through an explicit rollover-release adjustment.
19. An archived Pocket remains visible as a read-only “retired this period” row until the period ends.
20. Archived Pockets remain in history but are unavailable for new budgets, Movements, templates, or rollover.

### Backup and restore

21. Portable backups remain versioned plaintext JSON with a persistent confidentiality explanation and first-use confirmation.
22. A full backup includes all financial data, future period start day, and reminder time.
23. A restored reminder is disabled until the user explicitly reconfirms it.
24. Restore previews the incoming data, then may transactionally replace a nonempty ledger after explicit confirmation.
25. Before replacement, offer a safety backup but allow the user to explicitly continue without one.
26. Restore merging is out of scope.

### Reminders

27. Reminders are best effort around the selected time rather than promises of exact alarms.
28. The settings UI must accurately represent permission, enabled, scheduled, and failure states.

### Notification-assisted capture

29. Notification assistance is the first later expansion, but it remains suggestion-only.
30. Only apps explicitly selected by the user enter parsing.
31. The parser accepts English and Spanish and is labeled experimental; it makes no bank-support claim.
32. Tests use synthetic generic notifications rather than real-bank fixtures.
33. Parse in memory, discard raw notification text, and persist normalized data only.
34. A production suggestion stores only amount, currency, effective time, source-package identifier, a one-way deduplication identifier, and an optional extracted merchant or description.
35. Rich confidence, rule, and explanation data may exist only in beta/testing builds and must not become permanent production data.
36. Suggestions enter a review inbox. Confirming one creates a normal Movement; parsing never changes the ledger automatically.
37. An update with the same source package and notification identity updates the existing candidate. Amount alone is never a deduplication key.
38. Pending suggestions expire after 30 days.
39. Rejecting a suggestion deletes its normalized financial content but retains only its one-way deduplication identifier until expiry.
40. Stable promotion requires passing English/Spanish synthetic suites, no parser crashes, at least 95% correct amount/currency extraction, and fewer than 10% user corrections across a predefined beta sample. The minimum sample size must be fixed before beta exit.

### Quality and release policy

41. Automated host and device tests are release gates. A manual TalkBack/max-font pass is valuable milestone evidence but not required for every release.
42. GitHub Actions runs host tests, lint, and builds.
43. A fixed API-level Gradle Managed Device is the repeatable device gate; the physical S23 is used for milestone validation.
44. Production accessibility checks run on an API 34+ managed device, supplemented by large-font assertions for critical flows.
45. The delivery order is financial correctness, recovery/settings correctness, automated release gates, notification beta, then later network features.

## Delivery milestones

### M1 — financial-period integrity

Deliver automatic catch-up, cascading recalculation, source-period rollover eligibility, refund-only rollover, long-transition periods, and archive invariants.

Acceptance evidence:

- Unit tests cover zero, one, and many missing periods and repeated catch-up calls.
- Historical edit tests prove recalculation crosses multiple later periods without mutating later funds, budgets, or Movements.
- Refund-only and negative-availability rollover cases are explicit.
- Start-day changes cover nearby boundaries, year boundaries, and long transition reporting.
- Archive tests cover dependent templates, budget return, rollover release, spent/refunded Pockets, and the retired row.

### M2 — recovery and settings integrity

Complete full backup coverage, safety export, plaintext disclosure, restore preview, restored-reminder reconfirmation, reminder state truth, import limits, and CSV hardening.

Acceptance evidence:

- Round-trip tests include Room data and selected DataStore preferences.
- Restore failure at every phase leaves the original ledger intact.
- Replacement with and without a safety backup is tested.
- Unsupported versions, malformed values, oversized input, and partial writes fail safely.
- CSV cells that could be interpreted as formulas are neutralized.

### M3 — repeatable release gates

Add CI host tests/lint/build and a reproducible device suite on the declared ATD managed device.

Acceptance evidence:

- CI archives managed-device HTML reports and additional outputs on pass or failure.
- API 34+ accessibility validation exercises production flows.
- Critical screens run at compact, medium, expanded, compact-height, and 1.5x font configurations where applicable.
- Navigation covers back behavior, process restoration, shortcut launch, and warm/cold start.
- The S23 milestone checklist covers edge-to-edge, IME, TalkBack, maximum font size, reminder permission, backup, and restore.

### M4 — experimental notification beta

Add opt-in package selection, in-memory parsing, normalized suggestion persistence, review/confirm/reject/expiry behavior, and beta-only diagnostics.

Acceptance evidence:

- English and Spanish synthetic fixtures cover positive, negative, malformed, duplicated, updated, and unrelated notifications.
- Non-allowlisted packages are rejected before parsing or persistence.
- Raw text never reaches Room, logs, analytics, crash metadata, or backup.
- Confirming creates exactly one valid Movement through the normal ledger path.
- Revoking notification access leaves the ledger intact and gives an accurate UI state.
- Stable promotion metrics are computed locally without retaining notification content.

### M5 — optional later expansion

Consider network-backed capabilities only through a separate decision record. Accounts, cloud sync, bank APIs, automatic exchange rates, widgets, OCR, AI categorization, and automatic recurring Movements remain out of scope.

## Documentation handoff

Before implementation in the Windows checkout:

1. Copy or re-author this accepted specification and ADR in the canonical repository.
2. Convert each milestone into GitHub issues for `AIF31/Financial-App`, keeping acceptance evidence with the owning issue.
3. Resolve the beta sample-size threshold before a stable-parser ticket is marked ready.
4. Implement and verify one milestone at a time; do not mix notification ingestion into the financial-integrity milestone.
