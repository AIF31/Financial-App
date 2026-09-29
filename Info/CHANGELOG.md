# Changelog

Notable repository changes are recorded here for future maintainers.

## 2026-09-29

### Test reliability

- `PocketAppHostFlowTest.movement_search_survives_saved_state_restoration` no longer fails intermittently with `CalledFromWrongThreadException`. `createComposeRule()` runs composition coroutines on an unconfined test dispatcher. The ledger state collector therefore resumed on Room's `arch_disk_io` transaction thread and applied UI state there. The test now delivers ledger state on the main thread, as production's `AndroidUiDispatcher` already does. The app code is unchanged.

## 2026-09-28

### PR #34 physical-device test

- Recorded the PR #34 hardware run in `Info/verification/2026-09-28-pr34-hardware-test.md`: 29 of 31 device tests passed. Found: the launcher shortcut does not preserve an unsaved edit, the onboarding heading is unreadable in dark theme, and clipped Pocket cards lose their accessibility label. The phone was left on a release-signed `1.0.3` with the user's backup restored.
- The "Nuevo gasto" static shortcut now targets `NewExpenseShortcutActivity`, an invisible, non-exported activity in its own task. Launchers always start static shortcuts with `NEW_TASK | CLEAR_TASK`, which destroyed the running `MainActivity` and any unsaved form. The trampoline forwards only the fixed action, with `CLEAR_TOP | SINGLE_TOP`, so a running `MainActivity` receives `onNewIntent` and keeps the edit underneath.
- `PocketTheme` provides the palette's `onBackground` as the default content color. Screens drawn directly on the window, such as onboarding, were using Compose's black default, which was unreadable on the dark window background added in this PR.
- The dashboard and Pockets-list Pocket cards each carry a single accessibility label (name, availability, budget, status). The visible texts it repeats are hidden from accessibility services but stay available to tests. A card clipped at the list edge still announces its Pocket, and screen readers do not read each card twice. The Pockets list "Gestionar" button remains reachable.
- The "Comparar periodos" summary table measures its widest amount. When an amount cannot fit beside the label, as at large font scales, each label moves above its values, so numbers such as "7,500.00" are no longer split across lines. Each row is still announced once: its cells are hidden from accessibility services, because the row's spoken description already covers them.

### PR #34 review findings

Fixes from `docs/audits/2026-09-28-pr34-review.md`. Behavior that was new rather than wrong is now written into `Info/UI-UX-Design-Philosophy.md`.

- Saving a new expense opened by the launcher shortcut over an unsaved edit now returns to that edit with its draft. Previously only closing the new form did; saving reset navigation to a root screen and discarded the edit.
- Root navigation labels shrink to fit on one line instead of ending in an ellipsis, so "Movimientos" is shown in full at large font scales.
- Comparing periods in different accounting currencies with no frozen boundary rate between them now shows the compared period's daily average in its own currency, with no difference computed. The summary table gains a "Gasto diario promedio" row.
- The Movement form's Pocket choices show availability for the period that contains the entered date, in that period's currency, and name that period when it is not the current one. They previously showed the current period's availability for past dates.
- Future-dated Movements no longer inflate the pace of the current period. The daily average counts spending through today, and the projection extrapolates that pace and adds later-dated spending once instead of scaling it up. Period totals, availability, and budget used still include it. The implementation reference also now describes the shortcut trampoline Activity.
- Follow-up review: the "Gasto guardado" confirmation after saving over an edit now appears on that edit; the Movement form hosts the app's snackbar instead of leaving it queued until the form closes. Inicio's "Ver detalles" shows the previous period's daily average in its own currency when no frozen rate links the currencies, instead of only a warning.

### Time format and notification setup

- The time picker in the Movement form and the daily reminder follows the phone's 12- or 24-hour clock setting instead of always using a 24-hour dial. The stored time is unchanged. On a 12-hour phone, the Movement form's "Hora (HH:mm)" field also shows the time in that format, for example "10:16 p. m.".
- In "Captura desde notificaciones", the "Ninguna app seleccionada" setup step has an "Elegir apps" button. It scrolls to the "Buscar app" field and focuses it, so the keyboard opens ready to search.

### PR #34 review follow-up

- Merged `main` after PR #33 and advanced the development build to `1.0.3` (`versionCode` 4), because PR #33 had already taken `1.0.2`. The notification settings screen keeps PR #33's redesign under the shared top app bar, and PR #33's "open Movements" alert action sits beside the new-expense request counter in `MainActivity`.
- Pace status counts rollover as period funds. A period funded only by rollover gets a real pace status instead of "Asigna fondos". Negative availability now always reports overspending, even with zero new funds.
- A "Nuevo gasto" shortcut delivered while an edit or suggestion form is open now opens a new-expense form above it. Closing that form returns to the edit with its unsaved changes. Each Movement form route carries its own instance id, and its draft is kept in the route state holder until the route closes.
- Period labels always include the year ("25 feb – 24 mar 2026"), so pickers and the period-funds editor distinguish the same dates in different years.
- The cumulative spend chart for the current period says it runs through today. When Movements are dated later in the period, it states the net amount that is in the totals but not yet on the curve (`PeriodInsights.netSpendAfterTodayMinor`).
- Each comparison-table row is announced with each value's period, for example "Gasto neto. 25 feb – 24 mar 2026: SAR 100.00. 25 ene – 24 feb 2026: SAR 80.00".
- The PR's CI failure in [`PocketAppFlowTest`](https://github.com/AIF31/Financial-App/actions/runs/36359066486) came from temporary overlays, not an undersized control. Compose reports only a control's uncovered area, so a partly covered control looks small to the touch-target check.
  - In CI, the check after the dashboard details expand ran while the "Gasto guardado" snackbar was still showing. The snackbar sits directly above the floating action button, and its lower edge at y = 2001 px covered the top 36 px of "Comparar periodos", leaving 34 dp. The same failure recurred on [CI after merging `main`](https://github.com/AIF31/Financial-App/actions/runs/36434465204), so PR #33 did not cause it.
  - The test now waits for that snackbar to clear before acting on the dashboard. The accessibility checks are unchanged.
  - Locally, the floating action button's top edge (the same 2001 px line) cut the per-Pocket "+" buttons.
  - The Inicio Pocket card is now itself the "Registrar gasto en …" target, with the "+" as a visual cue.
  - The whole "Tus Pockets" header row now opens Pockets. It is labelled "Ver todos los Pockets" and replaces the separate "Ver todos" text button.
  - The accessibility checks are unchanged. A suppression for results cut by the floating action button or navigation bar was tried and removed in review, because it could also have hidden a genuinely small control.
  - Two alternatives were rejected. Ending root lists at the bottom bar made hidden controls show as clipped slivers. Scrolling the card to a fixed position before expanding it only moved the overlap to another control.

## 2026-09-27

### Notification capture: auto-recording, alerts, and settings

- Detected bank payments for a known merchant are now recorded as Movements in that merchant's last Pocket; new merchants and foreign currencies stay in a **Por revisar** inbox. Accepted in [ADR 0002](../docs/adr/0002-auto-record-notification-payments-for-known-merchants.md); `CONTEXT.md` and the [beta spec](../docs/product/NOTIFICATION_ASSISTANCE_BETA.md) are updated.
- Fixed missed captures from SMS apps: each message in a conversation notification is now parsed and identified separately, so new bank SMS no longer overwrite earlier ones and OTPs no longer hide purchases. The parser now reads SAB-style multi-line and point-of-sale texts, extracts their merchants, rejects credits, and reads a bare `$` as the USD or MXN default currency.
- Added a heads-up alert for each newly detected payment, which opens Movements when tapped; the lock screen shows only a redacted version.
- Redesigned **Captura desde notificaciones**: setup checklist, auto-record and alert switches, app search, suggested finance and messaging apps, and selected apps pinned on top, each shown with icon and name. Movements shows a **Detectado** label on auto-recorded Movements.
- Advanced the development build to `1.0.2` (`versionCode` 3).
- Follow-up fixes, each covered by a test that failed first: re-posted conversations no longer inflate beta parser counts; `Compra en línea … en OXXO` reads `OXXO` as the merchant; a bank app updating its notification with the same text no longer duplicates the payment; a purchase in a new, not-yet-opened budget period catches up periods and still auto-records; and foreign-currency alerts always ask for the conversion. Review-card times now use the budget zone, matching Movements.
- Verification: a fresh full host unit run passed 224 tests, and the release and androidTest sources compiled. The flow and each fix except the bank-app update were exercised on the Pixel_10_Pro emulator with synthetic SMS. It was not tested on the physical phone or with real bank messages.
- PR #33 review fixes: merchant memory now uses only the newest matching expense, and sends the payment to review if that expense's Pocket is archived rather than falling back to an older Pocket. Merchant matching also drops punctuation (`K.F.C` matches `KFC`). Both were test-first. Review hints use the currency of the suggestion's own period. Each app row in settings is a single toggleable checkbox keyed by package; this was checked on the emulator.
- Known issue: `PocketAppHostFlowTest.restore_confirmation_disables_duplicate_submissions_until_the_result_arrives` failed once (dialog created on a background thread) and then passed three isolated reruns and a full rerun. It is unrelated to this change and is flagged for a separate fix.

### Period insights, comparison, and UX quality pass

- Added `PeriodInsights` and `PeriodComparison` (`data/PeriodInsights.kt`): read-only metrics derived from the ledger's Pocket summaries and Movements — average daily spend, spend available per remaining day, projection, funds used versus period elapsed, cumulative daily spend, largest expense, and per-Pocket averages. Comparisons use average daily spending; a different accounting currency is converted only through the adjacent frozen boundary rate, otherwise the periods are shown side by side without deltas.
- Inicio: the Disponible card gains a "Ver detalles" disclosure with spend pace and the previous-period daily average, plus a "Comparar periodos" entry. Status wording and icon now reflect overspending and pace; day counts are pluralized. Each Pocket row has a quick "Registrar gasto en …" action that opens the shared quick-entry route with that Pocket preselected.
- New subordinate route "Comparar periodos": any two periods, cumulative spend curve (solid, dashed, and dotted lines so meaning never depends on color), summary table, and per-Pocket paired bars. The four bottom-bar destinations are unchanged.
- Pockets: the period summary expands into the same metrics and chart for the selected period (open by default for historical periods) with a comparison shortcut; period chips expose tab semantics, show currency and "Actual", and start scrolled to the selection. Pocket status uses one shared badge with container colors instead of low-contrast tertiary text.
- Selection controls across onboarding, quick entry, Currency, Payment methods, and Templates now use Material segmented buttons and filter chips with real selected semantics instead of "✓" text prefixes. Quick entry adds date and time pickers, "Hoy"/"Ayer", Pocket cards that show availability, keyboard actions and capitalization, and haptic confirmation on save.
- Movements: explicit filter chips that highlight when active, readable period labels, search clear button, daily totals, full detail dialog, swipe-to-delete with undo plus TalkBack custom actions, and an empty state with a record action.
- Navigation and shell: root tabs keep their scroll, search, and filters; back from another tab returns to Inicio; short fade between tabs; settings detail screens use a top app bar; centered loading state.
- Artwork and theme: Pocket artwork and logo re-encoded as right-sized WebP (≈1.8 MB of PNG to ≈140 KB); adaptive launcher icon with a monochrome themed-icon layer; night theme window background to avoid a light flash on dark cold starts; a light artwork plate keeps illustrations legible in dark theme.
- Two-axis review follow-up:
  - The reminder notification now uses a monochrome `ic_stat_pocket` vector. An adaptive launcher icon as a notification small icon can crash System UI on API 26.
  - Budget use is measured against Pocket budgets plus rollover, the same base as availability.
  - The spend curve and Movement stats count only Pockets in the period snapshot, matching net spend.
  - Pace status, Pocket budget status, per-Pocket deltas, and Movement net spend moved into the data layer (`SpendPaceStatus`, `PocketBudgetStatus`, `netSpendMinor`). "Sin presupuesto" again depends on the Pocket budget only.
  - The ledger's previous-period total is shown again beside the daily-average comparison.
  - Artwork colors are theme roles.
  - Experimental Material APIs are isolated in `PocketTopAppBar`, the time picker, and the date picker, each with its reason documented.
- Intent handling (android-intent-security audit): `MainActivity` now counts "new expense" launches across `onCreate` (skipped when restoring state) and `onNewIntent`, reading only the explicit action. Quick entry no longer reopens after rotation. The existing PendingIntent (`FLAG_IMMUTABLE`, explicit), the non-exported `FileProvider` limited to `shared_backups/`, and the permission-guarded notification listener already followed the guidance.
- Verification: 226 host tests passed, including new `PeriodInsightsTest` and `PocketQualityOfLifeHostTest`; `lintDebug`, `assembleDebug`, and `compileDebugAndroidTestKotlin` passed. Screens were reviewed from Robolectric renders at phone width in light, dark, and 1.5× font; no emulator or physical-device run was performed for this change, and TalkBack remains an open manual check.

## 2026-09-25

### App version policy

- Advanced the development build to `1.0.1` (`versionCode` 2) after merged PR #26. Future pull requests changing app, test, build, or project script files must increase both values by exactly one patch/code step; documentation-only pull requests are exempt. The required Android checks job validates the change before merge. The published APK remains `1.0.0` until a new release is signed and published.
- The version rule passed its self-check and comparison against the previous `main` commit. The physical test record includes the focused visual-tour navigation fix and its isolated passing rerun; the full physical suite was not rerun after that fix. The local Graphify update command could not start because its configured Python interpreter is unavailable.

### Local Codex and Graphify files

- Ignored generated `graphify-out/` data and local `.codex/` additions while keeping `.codex/config.toml`, `Info/`, and Android CI versioned. Verified the ignore rules and that Codex CLI can read the local Graphify skill.
- Kept 36 Android and agent workflow skills versioned, removed 16 optional skill directories and the local Skills Guide from Git's index without deleting local copies, and reduced `skills-lock.json` to the retained set.
- Scanned the public `main` tree for credential files and key signatures; none were found. The `v1.0.0` APK predates the current `main` by 40 commits.

### Closed-issues implementation review

- Reviewed the current `e58b14f` checkout against closed GitHub issues #1 and #9–#22 along independent standards and spec axes; recorded findings in `docs/audits/2026-09-25-closed-issues-final-implementation-review.md`.
- Found two remaining #1 product gaps: new foreign expenses cannot use an offline manual SAR equivalent, and historical Pocket budgets cannot be edited. Found two documented UI-standard breaches and one lower-confidence code smell.
- Verified the exact committed head's Android CI run `36047162329` passed both jobs. This was a static review; no local Gradle or physical-device tests were run. The existing maximum-font and TalkBack physical-release gaps remain open.

### Latest merged PR review

- Reviewed merged PR #26 specifically against issues #14, #15, #16, #17, #21, and #22; recorded separate Standards and Spec findings in `docs/audits/2026-09-25-pr26-merged-review.md`.
- Found a test-only production database opener, a lower-confidence string-operation smell, an incomplete #17 restore path for pre-created later periods, and partial #22 large-font coverage. Earlier #1 findings were outside this PR diff.
- Confirmed GitHub `main` at merge commit `f35f8c3`, the PR diff, the passing CI run for `e58b14f`, and a clean `git diff --check` on the PR range. No local Gradle or physical-device tests were run for this review.

## 2026-09-24

### Remaining issues in [PR #26](https://github.com/AIF31/Financial-App/pull/26)

- #14: Expense drafts keep a stable submission ID across repeated taps, failure, retry, and lifecycle changes, preventing duplicate Movements.
- #15 and #16: Portable backup version 5 includes the future period start day and reminder time. Replacement restore has a safety-export step and persistent plaintext warning; restored reminders require confirmation on the new device. Settings reports permission, channel, and scheduler status with retry.
- #17: Archived Pockets remain in the durable catalog and can be restored; history filters keep Pocket identity across period advances.
- #21 and #22: Added populated migration fixtures from schema version 1 through 7 and API 35 managed-device tests for large fonts, compact/wide/landscape layouts, keyboard saves, restore confirmation, permission denial, and scheduler retry.
- Updated the release recovery procedure for version 5 backups. Corrected host-test feedback assertions and kept document feedback in its intended screen while ledger state loads.

### Remaining-issue verification

- The full local gate passed 205 host tests and 29 Pixel 6 API 35 managed-device tests with no failures or skips, plus lint and debug/release builds. No personal Android device was used.
- All checks passed on commit `95bd018`, including [Android CI](https://github.com/AIF31/Financial-App/actions/runs/36046233080) and [CodeQL](https://github.com/AIF31/Financial-App/actions/runs/36046227335). PR #26 awaits merge; its issue-closing references will close #14, #15, #16, #17, #21, and #22 when merged.

### Changed

- [PR #24](https://github.com/AIF31/Financial-App/pull/24) merged the fixes for foreground date refresh (#18), exact-file backup share retry (#19), and managed-device CI reliability (#20) into `main`.
- Foreground checks now continue at most 60 seconds apart while the app is visible and recover after invalid clock signals. Backup share retry keeps the prepared file across recreation and prepares a new one if it is missing.
- Activated the `main` ruleset requiring `Android checks` and `Pixel 6 API 35 device tests`. Issues #10, #12, #13, #18, #19, and #20 are closed.

### Verification

- The local Gradle gate passed 191 host tests and 19 Pixel 6 API 35 managed-device tests, plus lint and debug/release builds, with no failures or skips. No physical device was used.
- [PR #24 CI](https://github.com/AIF31/Financial-App/actions/runs/35919284476) passed both required jobs. A temporary [failing-test PR #25](https://github.com/AIF31/Financial-App/pull/25) proved that a device-test failure blocks merging and uploads diagnostics; it was then closed and its branch deleted.

## 2026-09-23

### Documentation

- Added a dated audit for the post-consolidation standards and issue-spec review, including the then-open #18, #19, and #20 findings.
- Updated the root README to require reading and updating this changelog and maintaining dated audits after code reviews and fixes.

### Verification

- Checked the reviewed commit range, source locations, and GitHub's `main` branch protection and ruleset status at the time. No Gradle or device tests were run for this documentation change.
- A consolidated-main CI run passed. At this review, a deliberate failing-device-test exercise was unverified, `main` had no active required-check rule, and the `Basic` ruleset was disabled. The required-check rule and failure exercise were completed on 2026-09-24.

## 2026-09-17

### Added

- Added deterministic host regression coverage for coherent Room snapshots in `SnapshotConsistencyHostTest`.
- Covered initial state loading, repeated invalidations, period catch-up, backup restore, failed and canceled writes, and CSV export during an in-flight write.
- Used separate reader and writer Room connections in WAL mode so tests control transaction interleavings without sleeps or production-only test hooks.

### Changed

- Updated the operational README to link the changelog and name Pixel 10 Pro as the physical release-test device while retaining the managed-device automation workflow.
- Replaced the non-canonical phrase “Movement transfer between periods” with “moving/editing a Movement between budget periods” in the open-issues implementation plan.
- Added a README workflow rule requiring future contributors and agents to keep this changelog accurate, including verification limits and unresolved blockers.

### Verification

- Documentation links and the Pixel 10 Pro testing guidance were reviewed against the repository's physical-device preservation procedure; no device test was run for this documentation-only change.
- Static diff checks and independent standards/spec reviews passed.
- Gradle host tests and the managed-device pass/failure exercise were not run because the required `python3` compact-wrapper prerequisite was unavailable.

### External work identified at the time (resolved 2026-09-24)

- GitHub `main` had no active required-check rule and the `Basic` ruleset was disabled. A rule requiring the `Pixel 6 API 35 device tests` check was needed before issue #20 could be considered complete.
- The managed-device workflow needed a recorded passing run and deliberate-failure propagation exercise.
