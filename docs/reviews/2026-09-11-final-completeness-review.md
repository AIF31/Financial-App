# Final completeness review — draft pending validation

- **Review date:** 2026-09-11
- **Baseline:** `f1c2ecbd0b024814575db56a2a72cab84f232b6a` (`f1c2ecb`)
- **Branch:** `codex/restore-recovery`
- **Status:** Draft. Sol owns implementation and test execution in the shared checkout; the parent agent reviews and verifies. No implementation item is marked complete here until the parent supplies accepted source and test evidence.
- **Scope:** Current source at the baseline, `Skills-Guide.md`, `UI-UX-Design-Philosophy.md`, `CONTEXT.md`, ADR 0001, `docs/product/FUTURE_UPDATES_DECISION_SPEC.md`, the preceding 2026-09-10 review, and GitHub issues #9–#22.

This review uses the Pocket vocabulary in `CONTEXT.md`. It separates Standards concerns from product-spec acceptance. GitHub issue state is evidence of the requested contract, not evidence that the contract is implemented.

### Verification update — 2026-09-11

The current working tree now includes the CI remediation copied from the previously validated isolated CI branch. The parent reviewed the exact diff: it runs the Pixel 6 API 35 managed-device task, excludes the deliberate `@LargeTest` visual tour, requires at least one non-skipped test, uploads diagnostics with `if: always()`, and has no `continue-on-error`. The final new-checkout managed-device run is still pending. The deliberate failure run recorded in the preceding review remains historical evidence; this update does not claim a fresh GitHub workflow run.

Sol also added source-side prospective validation for both the source and destination periods when a Movement is edited or moved, plus shared historical-comparison conversion validation used by add/edit/delete/undo, manual period creation, catch-up, and restore preflight. The parent accepted that source change statically. Sol reported focused RED→GREEN coverage, but the #10 fixture still needs refinement to isolate the stale-backup comparison (`Long.MAX_VALUE / 64` with rate `128`); #10 therefore remains pending final refined GREEN evidence. No other issue status changes in this draft.

## Result at the baseline

The branch contains the recent financial-integrity and recovery work covered by the preceding review. Those remediations are not repeated as active findings below. The baseline still has unverified or incomplete requirements for coherent snapshots, single-flight expense saving, backup settings and recovery safeguards, truthful reminder status, archived Pocket catalog/history behavior, foreground date refresh, the managed-device CI gate, the populated migration chain, and the API 35 configuration matrix.

The following items are active baseline gaps or integrated changes pending final validation:

| Issue | Baseline evidence | Status for this draft |
|---|---|---|
| [#12](https://github.com/AIF31/Financial-App/issues/12) | `BackupCodec.csv` reads Pocket, period, payment-method, and movement tables separately at `app/src/main/java/com/aif31/pocket/data/BackupCodec.kt:177-205`; `RoomPocketLedger.state` combines independent Room flows at `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt:35-71`. | Open gap; source fix and interleaving evidence pending. |
| [#14](https://github.com/AIF31/Financial-App/issues/14) | `ProductionMovementScreen.saveMovement` launches a new coroutine on every tap and gives a new movement a null ID at `app/src/main/java/com/aif31/pocket/ProductionExpense.kt:187-239`; entity creation generates an ID when the command ID is null at `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt:952-974`. | Open gap; single-flight behavior and lifecycle tests pending. |
| [#15](https://github.com/AIF31/Financial-App/issues/15) | Version 4 backup encoding contains ledger data and `ledgerPreferences`, but no DataStore future-start-day or reminder-time fields at `app/src/main/java/com/aif31/pocket/data/BackupCodec.kt:15-95,394-407`. Restore infers only the latest period’s start day at `app/src/main/java/com/aif31/pocket/PocketApp.kt:185-204`. | Open gap; compatible settings round-trip and recovery safeguards pending. |
| [#16](https://github.com/AIF31/Financial-App/issues/16) | `ReminderScheduler.apply` returns `Unit` and only enqueues/cancels WorkManager work at `app/src/main/java/com/aif31/pocket/settings/Reminder.kt:26-47`. Settings renders `reminderEnabled` as “Activado” and ignores scheduler outcome at `app/src/main/java/com/aif31/pocket/SettingsScreens.kt:314-350`; the permission callback is empty at `app/src/main/java/com/aif31/pocket/MainActivity.kt:70`. | Open gap; permission/channel/scheduler truth and retry evidence pending. |
| [#17](https://github.com/AIF31/Financial-App/issues/17) | Pocket lists are period snapshots at `app/src/main/java/com/aif31/pocket/PocketsScreen.kt:47-55`; archiving deletes future snapshots at `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt:278-284`; history filter options use current `state.pockets` at `app/src/main/java/com/aif31/pocket/MovementsScreen.kt:90-104`. | Open gap; stable catalog and archived-history evidence pending. |
| [#18](https://github.com/AIF31/Financial-App/issues/18) | Catch-up runs from `onCreate`/`onResume` at `app/src/main/java/com/aif31/pocket/MainActivity.kt:72-118`. There is no visible-session date signal; the ledger clock is read while rebuilding state after database invalidation at `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt:51-71,826`. | Open gap; injected-clock midnight and repeated-resume evidence pending. |
| [#20](https://github.com/AIF31/Financial-App/issues/20) | At the `f1c2ecb` baseline, `.github/workflows/android-ci.yml:30-39` ran host tests, lint, and APK tasks without the managed-device suite. The current working tree adds a Pixel 6 API 35 task, `@LargeTest` filter, non-skipped execution guard, and always-on diagnostics upload. | Integrated and parent-reviewed; final new-checkout GMD run pending. Historical deliberate-failure evidence is not a fresh workflow result. |
| [#21](https://github.com/AIF31/Financial-App/issues/21) | The database is version 7 with an automatic 1→2 migration and manual 2→3 through 6→7 migrations at `app/src/main/java/com/aif31/pocket/data/FinanceDatabase.kt:293-319`. `FinanceDatabaseMigrationTest` creates separate fixtures for 2→3 through 6→7 at `app/src/androidTest/java/com/aif31/pocket/data/FinanceDatabaseMigrationTest.kt:20-285`; it does not exercise one populated oldest-to-current journey. | Open gap; populated full-chain and post-migration usability evidence pending. |
| [#22](https://github.com/AIF31/Financial-App/issues/22) | Existing device tests cover normal flows and accessibility checks, but the repository has no systematic API 35 matrix for font scale, narrow/wide windows, keyboard-visible layouts, process recreation, and reminder/recovery states. | Open gap; configuration coverage and diagnostics pending. |

## Standards

### S1. Domain arithmetic remains in a Compose screen

**Severity:** Hard violation

`PocketsScreen` derives allocated, released, and unassigned amounts inside the composable at `app/src/main/java/com/aif31/pocket/PocketsScreen.kt:53-60`. The ledger already exposes the current unassigned amount through `LedgerState.unallocatedMinor` (`app/src/main/java/com/aif31/pocket/data/Model.kt:135-160`, populated at `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt:804-807`). This conflicts with `UI-UX-Design-Philosophy.md:265-288`, which places domain calculations outside composables and prohibits duplicated ledger rules.

The correction should expose any selected-period value through immutable domain/UI state and make the composable render that value. Parent verification is required to show that historical and current-period summaries remain correct.

### S2. The release workflow omitted the declared device gate at the fixed baseline

**Severity:** Hard violation of the repository’s release/testing contract

At the fixed baseline, the workflow stopped after host tests, lint, and build/package tasks (`.github/workflows/android-ci.yml:30-39`). The product specification requires a fixed API-level Gradle Managed Device gate, API 34+ accessibility validation, and diagnostic artifacts (`docs/product/FUTURE_UPDATES_DECISION_SPEC.md:136-146`). The current working tree contains the reviewed remediation, but issue [#20](https://github.com/AIF31/Financial-App/issues/20) remains pending the final new-checkout GMD run and execution evidence.

No additional duplicated-validation finding is carried forward from the prior review: the current backup path calls `PeriodLedgerRules.validateTotals` at `app/src/main/java/com/aif31/pocket/data/BackupCodec.kt:343-349`. That observation still needs normal test validation.

## Spec

### P1. CSV and derived observations do not have one documented snapshot boundary

**Issue:** [#12](https://github.com/AIF31/Financial-App/issues/12)

`BackupCodec.encode` is transactional, but `BackupCodec.csv` performs independent reads outside `withTransaction` (`app/src/main/java/com/aif31/pocket/data/BackupCodec.kt:177-205`). A restore, edit, or catch-up between those reads can pair a movement with metadata from another logical version. The state flow has the same shape at `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt:35-71`: combining flows does not by itself establish one read transaction for the complete observation.

The implementation must define the coherent snapshot boundary and test a write interleaved with export and observation. A failed or canceled operation must not expose a partially assembled result.

### P2. New expense saves have no durable single-flight identity

**Issue:** [#14](https://github.com/AIF31/Financial-App/issues/14)

`saveMovement` has no in-flight guard or visible saving state and can submit the same draft repeatedly (`ProductionExpense.kt:187-239`). New commands carry `id = null`, after which `toEntity` creates a fresh UUID (`RoomPocketLedger.kt:952-974`). Recomposition, background/resume, or a delayed ledger response can therefore produce duplicate Movements. The required fix must keep one draft identity through the relevant lifecycle, report definitive success or failure, and preserve a failed draft for correction or one retry.

### P3. Backup recovery omits selected DataStore settings and safeguards

**Issue:** [#15](https://github.com/AIF31/Financial-App/issues/15)

`AppPreferences` owns `futurePeriodStartDay`, `reminderEnabled`, and `reminderTime` (`app/src/main/java/com/aif31/pocket/settings/AppPreferences.kt:17-24`), while the version 4 `BackupPayload` has no corresponding fields (`BackupCodec.kt:394-407`). On success, the restore UI writes only a preferred start day inferred from a restored period (`PocketApp.kt:185-204`), leaving reminder state outside the portable recovery contract.

The settings data is coupled with two missing user safeguards. The restore preview contains a replacement warning but no safety-export offer or explicit skip outcome (`PocketApp.kt:153-223`), and the data settings screen has only a plaintext note, with no first-export acknowledgment before writing/sharing (`SettingsScreens.kt:505-513`). Restored reminders must be disabled or awaiting reconfirmation until the current device’s permission, channel, and scheduler state are verified.

### P4. Reminder UI reports preference state as device truth

**Issue:** [#16](https://github.com/AIF31/Financial-App/issues/16)

The settings text is based solely on `preferences.reminderEnabled` (`SettingsScreens.kt:314-350`). `WorkReminderScheduler.apply` does not return an enqueue result or expose current WorkManager state (`Reminder.kt:26-47`), and the notification permission result is ignored (`MainActivity.kt:70`). Permission denial, channel failure, and worker failure cannot be represented as distinct visible states, and resume/reopen/restore cannot reliably recheck them. The product decision requires best-effort timing and truthful `off`, `permission required`, `scheduled`, and failure states (`docs/product/FUTURE_UPDATES_DECISION_SPEC.md:82-85`).

### P5. Archived Pockets are not a durable catalog for later restore/history

**Issue:** [#17](https://github.com/AIF31/Financial-App/issues/17)

The current-period UI takes its Pocket set from `pocketSummariesByPeriod` (`PocketsScreen.kt:47-55`). Archiving removes future period rows (`RoomPocketLedger.kt:278-284`), and restoring only repairs the current period snapshot (`RoomPocketLedger.kt:215-234`). History’s Pocket selector is built from current summaries (`MovementsScreen.kt:90-104`). After advancing periods, an archived Pocket can therefore disappear from management and cannot be selected by stable ID in history. The implementation must retain the catalog identity while preserving historical budgets, availability, releases, and Movements.

### P6. A visible app does not refresh at foreground midnight

**Issue:** [#18](https://github.com/AIF31/Financial-App/issues/18)

The activity calls catch-up on creation and resume, but no event runs while the activity remains visible across midnight (`MainActivity.kt:72-118`). `RoomPocketLedger` computes `today()` from its injected clock while building state, yet its state flow is invalidated by database flows rather than by a date signal (`RoomPocketLedger.kt:51-71,826`). A long-running foreground session can keep stale elapsed days, projections, and period selection until another write or lifecycle transition occurs.

### P7. Managed-device execution and failure propagation were absent at the fixed baseline

**Issue:** [#20](https://github.com/AIF31/Financial-App/issues/20)

The fixed baseline workflow did not invoke `:app:pixel6Api35DebugAndroidTest`, assert that a non-skipped test executed, or upload managed-device reports/logs/screenshots on failure (`.github/workflows/android-ci.yml:30-39`). The current working tree contains the exact reviewed remediation: the filtered managed-device task, the execution-count guard, and always-on diagnostics upload. The final new-checkout GMD run is pending, and the deliberate failure evidence remains the prior review’s historical run rather than a fresh GitHub workflow result.

### P8. Migration coverage does not prove a populated oldest-to-current upgrade

**Issue:** [#21](https://github.com/AIF31/Financial-App/issues/21)

The schema supports version 1 through version 7, but the test suite starts separate databases at version 2, 3, 4, 5, and 6. It does not start from the oldest supported schema and apply the complete chain while checking the same populated ledger after every step (`FinanceDatabaseMigrationTest.kt:20-285`). The fixtures also do not cover the full requested set of populated rows and app-owned settings in one upgrade journey. The acceptance flow must end with opening the migrated ledger, calculating the current period, adding a Movement, exporting, and restoring.

### P9. API 35 configuration coverage is not yet a release acceptance matrix

**Issue:** [#22](https://github.com/AIF31/Financial-App/issues/22)

The API 35 device tests and the large visual tour exercise normal flows (`app/src/androidTest/java/com/aif31/pocket/PocketAppFlowTest.kt:59-72`, `PocketUiUxReviewTourTest.kt:58-80`), but static inspection found no systematic parameterization for large font scale, narrow/wide windows, keyboard-visible save controls, process recreation, or reminder/recovery states. This leaves the critical flows without the configuration evidence required by the product contract and UI philosophy (`docs/product/FUTURE_UPDATES_DECISION_SPEC.md:140-146`, `UI-UX-Design-Philosophy.md:200-223,246-263`).

### P10. Share retry is not tied to the failed prepared backup

**Issue:** [#19](https://github.com/AIF31/Financial-App/issues/19)

`shareBackup` combines backup preparation, cache-file writing, and chooser launch in one try/catch and records only `DocumentOperation.SHARE` on failure (`app/src/main/java/com/aif31/pocket/MainActivity.kt:140-165`). `shareExistingBackup` then selects whichever file in `cache/shared_backups` has the newest timestamp (`MainActivity.kt:169-181`). If preparation fails before a new file is safely written, retry can repeatedly select a stale backup or find no file. The UI needs to retain the exact successfully prepared file for a chooser retry, or retry preparation when preparation failed.

The document writer also rethrows coroutine cancellation from `writeExport` (`MainActivity.kt:184-203`) without an explicit operation outcome or provider-partial-write cleanup. This requires parent validation against the issue’s cancellation, rotation, and partial-write acceptance cases before #19 can be considered complete.

## Security review

The static intent/component review found no confirmed intent-redirection vulnerability. `MainActivity` only checks the explicit `NEW_EXPENSE` action and does not forward a nested Intent. The launcher activity is intentionally exported, the notification listener is protected by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`, and the `FileProvider` is `exported="false"` with URI grants confined by `res/xml/file_paths.xml`. Reminder notifications use an explicit immutable `PendingIntent` (`app/src/main/java/com/aif31/pocket/settings/Reminder.kt:56-62`).

This is a scoped static pass, not a whole-app security guarantee. The component assumptions align with the Android [`FileProvider`](https://developer.android.com/reference/androidx/core/content/FileProvider) and [`NotificationListenerService`](https://developer.android.com/reference/android/service/notification/NotificationListenerService) contracts. No signature permission should be added to the public launcher without a real authorization requirement, and no Intent sanitizer is warranted without an actual nested-Intent flow.

## Previously reviewed work and non-active findings

The preceding review (`docs/reviews/2026-09-10-unmerged-github-issue-fixes.md`) records fixes for the earlier deletion-overflow, restore transaction, empty-budget, persistence-classification, document-boundary, and isolated CI findings. They remain acceptance items until the parent repeats the relevant checks on the final shared checkout, but they are not restated as active baseline defects here. Issue #11’s released-rollover behavior is also treated as a carried-forward remediation, subject to regression evidence. The current working tree includes the reviewed CI remediation described above; its final GMD run is pending. The new #10/#13 source validation slice is statically accepted, while #10 remains pending the refined GREEN fixture. The current Standards finding S1 is separate: it concerns the remaining Compose duplication at `PocketsScreen.kt:53-60`.

## Validation status and required follow-up

This documentation draft performed static inspection and read the issue specifications. It did not execute Gradle, managed-device, physical-device, or manual TalkBack tests. Sol owns implementation and test execution; the parent agent reviews and verifies those checks and must update this file with:

- the final source/configuration diff and exact commit or working-tree state;
- focused host and device test commands with executed/passed counts;
- migration, reminder, recovery, snapshot, single-flight, archive/history, midnight, and API 35 configuration evidence mapped to the acceptance rows above;
- CI YAML validation, a passing managed-device run, a deliberate failing-test propagation run, and preserved diagnostics;
- `git diff --check` and the complete appropriate test suite result;
- any remaining limitations, with stale findings removed only when the new evidence proves them resolved.

Until that evidence is supplied, this report remains a draft and the listed gaps are pending verification rather than completion claims.
