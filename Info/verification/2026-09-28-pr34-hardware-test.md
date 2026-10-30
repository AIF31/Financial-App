# Hardware test record — 2026-09-28 (PR #34)

Checkout: `9819740` (`claude/app-qol-improvements-72075b`, PR #34 head, `1.0.3` / code 4). Personal device details, settings, backup fingerprints, and ledger contents are omitted; layout measurements needed to reproduce findings are retained.

## Starting state

The physical device ran Android API 36 with a debug `1.0.3` build and the test APK already installed. The tested layout was 1080×2316 at density 450. Migration-test databases showed that destructive instrumentation had already run. Because app writes occurred after the previous backup, a fresh backup was required before proceeding.

A full destructive run was authorized, with a release-signed `1.0.3` installation required afterward.

## Backup

A fresh format-version-5 in-app backup was verified privately by SHA-256. A protected local copy was checked against the same digest, and plaintext working copies were deleted. Backup integrity was checked again immediately before the final install. Contents, record counts, storage locations, and fingerprints are omitted.

## Installation

The installed debug app was signed with a different debug key from this checkout's, so an in-place update failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. The debug and test packages were uninstalled after the backup was verified. Then the PR-head APKs were installed:

| Artifact | SHA-256 |
| --- | --- |
| Debug APK | `b9bf7ddb9f09f8dad184ba2fa9491bdb35f0ecb227ce5fcd45543303532b597b` |
| Instrumentation APK | `7cd163cda0f3da3ef715746a24bb280e61dec0b06eef8f18d36a3eaf49175a4b` |
| Release APK (signed, v2 verified) | `211e0fc2a57e4bc6b75416ae08a6b65da96b415e2f9ceeebd5dd42ac2e1b6569` |

All builds used `.agents/skills/gradle-run/scripts/gradle_run.py`. The installed `base.apk` hashes matched the local artifacts.

## Instrumentation

The full suite ran with `am instrument -w -r`: 31 tests, 29 passed, 2 failed. An isolated rerun of `PocketAppFlowTest` reproduced both failures.

- `onboarding_allocation_expense_dashboard_and_history_are_consistent` (line 127)
- `historical_pockets_are_snapshot_backed_and_read_only` (line 208)

Both fail in the accessibility check `SpeakableTextPresentCheck`. It reports a full-width clickable card clipped at the top edge of an edge-to-edge list, with bounds `[45,0][1035,16]` and `[45,27][1035,125]`. Pixel 6 CI passes both tests because its geometry does not leave a card clipped there.

A layout dump from the app shows the same issue in real use. When a clickable Pocket card is partly scrolled off the top, only its visible child text is exposed. The Pocket name is missing, and a card with only its padding visible has no label at all. The dashboard `PocketProgressRow` became clickable in this PR. The Pockets-list card's `clickable` already exists on `main`.

## Manual checks

Mutating checks ran on the debug build with throwaway data. Read-only checks ran on the signed release after restore; financial values are omitted.

| Check | Result |
| --- | --- |
| Tapping a Pocket card opens "Nuevo gasto" with that Pocket selected | Pass |
| The "Tus Pockets" header opens Pockets | Pass |
| The launcher "Nuevo gasto" shortcut over an unsaved Movement edit | **Fail**, see below |
| Movements swipe-to-delete, then "Deshacer" | Pass within the 5 s undo window. The fixed window ignores the accessibility "time to take action" setting. |
| Date picker (Spanish, year shown, dates before the period disabled) and 24-hour time picker | Pass |
| Reminder notification `ic_stat_pocket` | Pass. Posted on schedule on channel `daily-review` with `VISIBILITY_PRIVATE`. It renders as a monochrome silhouette in the status bar and the lock-screen icon row. The redacted-content view was not observed. |
| Inicio "Ver detalles" pace metrics | Pass |
| "Comparar periodos" chart, legend, "Hasta hoy" note, and summary table | Pass. The chart has a spoken text summary, and each table row reads as one label. The x-axis runs to "Día 31" (the longer comparison period) while the note says "de 30". |
| Dark theme | **Fail** on onboarding, see below. The other screens rendered correctly. |
| Font scale 1.5 | Usable, with issues: comparison-table amounts wrap mid-number, and the "Movimientos" navigation label truncates. |
| TalkBack spoken pass | Not exercised. The accessibility tree was inspected instead. |

### Shortcut over an unsaved edit

Static shortcuts always launch with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`, per the Android "Manage shortcuts" guide. `MainActivity` uses the standard launch mode.

- **Real launcher shortcut:** the task was cleared. Closing the new form showed Inicio, and the unsaved edit was lost.
- **`NEW_TASK` only:** a second `MainActivity` was created (task size 2) instead of calling `onNewIntent`. Closing its form showed that instance's Inicio. The edit came back only after a further back press.

The PR's `onNewIntent` path is therefore not reached on a device. The guide's remedy is a trampoline activity, with `MainActivity` handling the request in `onNewIntent()`.

### Dark-theme onboarding heading

`OnboardingScreen` (`PocketApp.kt`) is not inside a `Surface`, so its "Configura tu primer periodo" heading uses Compose's default black content color. On `main` the dark-mode window background was light, so the heading was readable. This PR adds a `values-night` window background of `#0E1616`, which leaves the heading near-invisible in dark mode on first launch and on restore-from-scratch.

## Final state

The signed release `1.0.3` / code 4 was installed from a clean state. The backup was restored through the app's restore flow; preview and result counts agreed in private checks. After a force-stop and cold start, Pocket opened past onboarding with all four tabs. `run-as` is rejected because the package is non-debuggable. Temporary device-setting changes were restored. Reinstall requires notification-listener access to be granted again; the debug reminder schedule was removed with the debug package.

## Decision on `9819740`

This run does not pass `9819740`. There are two functional regressions introduced by PR #34: the shortcut-over-edit behavior, and the dark-theme onboarding heading. The clipped-card accessibility failure reproduces deterministically on this phone.

## Fixes and re-verification

The findings were fixed test-first. Each fix has a Robolectric host test that failed before the change and passes after it:

- `NewExpenseShortcutActivityTest`: the shortcut trampoline forwards only the fixed action and closes itself.
- `PocketThemeTest`: text outside any surface uses dark `onBackground`.
- `PocketQualityOfLifeHostTest`:
  - both Pocket cards carry their own label;
  - screen readers hear each card once, and the Pockets-list "Gestionar" button stays reachable;
  - comparison amounts are not split at font scale 2.0 at the tested width, with native graphics. The legacy mode fakes glyph widths;
  - screen readers hear each comparison summary row once.

The full host suite passed (261 tests, 0 failures, 0 errors, 0 skips), and lint, the debug build, and the instrumentation APK build passed.

A release-signed build with the fixes (final SHA-256 `8615eca4b308756f254b71cd1928f686b5513e2f6173e53bb264524ff2f2a7a4`) was installed in place over the restored release, so no data was cleared. On the phone:

| Check | Result |
| --- | --- |
| Real launcher "Nuevo gasto" shortcut over an unsaved edit | Pass. A single `MainActivity` (task size 1) showed "Nuevo gasto". Closing it returned to "Editar movimiento" with the unsaved amount intact. The edit was then discarded, and the ledger was unchanged. |
| Clipped dashboard card at the top edge, and the Pockets-list cards | Pass. Each card exposes one label that starts with its Pocket name, the repeated texts are hidden, and "Gestionar …" stays exposed. |
| "Comparar periodos" summary at font scale 1.5 | Pass. Labels stack above the values, and amounts stay whole. |
| "Comparar periodos" summary rows for screen readers | Pass. Each row is exposed once as its spoken description; the cells are hidden. |
| Dark-theme onboarding heading | Covered by the host test only. Reaching onboarding on the phone would require clearing the ledger. |

The two `PocketAppFlowTest` instrumentation tests were not rerun on the phone, because that needs a debug install and therefore another uninstall. The layout dumps above check the same condition that `SpeakableTextPresentCheck` reported.

Final verification: signed `1.0.3` / code 4 contained the fixes, restored data was preserved, and temporary device-setting changes were restored. Notification-listener access requires a new grant after reinstall.
