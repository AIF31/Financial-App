# Future update test strategy

Status: planned; no test implementation performed

Execution target: the repository checkout containing this project.

## Existing baseline

The reviewed Windows project uses JUnit 4, coroutine test utilities, Room testing, Robolectric, Compose UI testing, Espresso, WorkManager testing, and a Pixel 6 API 35 `aosp-atd` Gradle Managed Device. It is a Compose-only application without a dependency-injection framework. API 34+ accessibility validation already appears in an Android test.

Host unit tests passed with JDK 17 during the planning review. That result establishes only the existing baseline; none of the future behaviors in the decision specification have been implemented or validated.

## Test layers

### Host unit tests

Own deterministic financial rules, period construction, cascading rollover, archive accounting, parser normalization, validation, and serialization boundaries. Prefer fakes over framework mocks.

### Robolectric behavior tests

Own Compose state behavior, navigation state restoration, validation messages, review-inbox interactions, and layout assertions that do not require Android accessibility services. A Robolectric accessibility-check pass is inconclusive and must never satisfy the accessibility gate.

### Instrumented managed-device tests

Own Room/SQLite behavior, Storage Access Framework flows that can be reliably automated, notification-listener integration boundaries, WorkManager/reminder state, edge-to-edge/IME behavior, and Compose accessibility checks. Use the version-controlled API 35 ATD managed device; do not replace it with hand-scripted AVD startup.

### Physical-device milestone tests

Own OEM-specific permission flows, notification access, actual reminder delivery, system picker behavior, TalkBack, maximum-font usability, reinstall/restore, and device-transfer exclusions. These are milestone checks rather than every-release gates.

## Release gates

1. `:app:testDebugUnitTest` passes on JDK 17.
2. Debug lint passes without newly accepted unexplained findings.
3. Debug and release compilation succeed; signing verification remains a separate controlled release operation.
4. The declared managed-device suite passes and its reports are archived even on infrastructure failure.
5. API 34+ production accessibility checks and critical large-font assertions pass.
6. Recovery fixtures from current and supported older backup versions restore correctly.

## Beta parser matrix

Synthetic fixtures must cover both English and Spanish across:

- Amount placement, grouping symbols, decimal separators, supported currencies, and SAR symbol variants.
- Purchases, refunds, reversals, pending/posted updates, missing fields, multiple amounts, and unrelated messages.
- Same amount from different notifications, repeated notification identity, updated identity, and expiry after 30 days.
- Allowed and disallowed source packages.
- Parser exceptions, cancellation, process restart, notification-access revocation, and database failure.

The test oracle asserts normalized fields only. Raw fixture text may exist in test source but must never be written to production storage or emitted to logs.

## Accessibility matrix

Exercise setup, dashboard, quick expense/refund, history filters, Pocket editing/archive, backup preview/restore, settings, and suggestion review. Validate labels, roles, state, error recovery, spoken monetary meaning, touch targets, focus order, scrolling, keyboard visibility, and destructive confirmations.

Run device accessibility validation on API 34 or newer. Add explicit checks after focus requests or multimodal input because those actions do not automatically invoke the Compose accessibility validator.

## Evidence handling

- Archive bounded test summaries and managed-device HTML reports.
- Keep screenshots and physical-device verification records free of personal financial data, usernames, device serials, and signing secrets.
- Never commit raw real-bank notification examples.
- Treat infrastructure failure separately from assertion failure.
