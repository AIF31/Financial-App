# Android future-update facts — 2026-08-27

Official Android platform research checked on 2026-08-27. Facts and Pocket implications are separated below; implications are planning judgments, not committed scope.

## Subsequent product decisions

The decision session completed on 2026-08-29 supersedes two planning implications below without changing the cited platform facts:

- Portable backups remain plaintext with persistent disclosure and first-use confirmation; password-based encryption is not planned.
- Notification parsing begins as an opt-in, English/Spanish, generic beta tested with synthetic fixtures. It stores no raw notification text and makes no claim of supporting a particular bank; real-bank samples are not a prerequisite for the beta.

See `docs/product/FUTURE_UPDATES_DECISION_SPEC.md` and ADR 0001 for the accepted direction.

## Repository baseline

- Pocket targets API 36 (`app/build.gradle.kts:22-29`).
- `MainActivity` calls `enableEdgeToEdge()` (`MainActivity.kt:51-54`), the manifest uses `adjustResize` (`AndroidManifest.xml:13-16`), and the production shell passes `Scaffold` padding to screens (`PocketApp.kt:150-185`).
- Platform backup is disabled and legacy and Android 12+ rules exclude databases, files, and preferences (`AndroidManifest.xml:4-8`, `backup_rules.xml:2-6`, `data_extraction_rules.xml:2-13`). Pocket uses user-selected documents (`MainActivity.kt:23-30`).
- Pocket declares one `Nuevo gasto` launcher shortcut (`shortcuts.xml:2-14`).

## Adaptive layout and window size

### Platform facts

- Window size classes describe the current app window, not a device category, and can change during rotation, resizing, split screen, or fold/unfold. Android defines compact, medium, expanded, large, and extra-large width classes; 600 dp and 840 dp are the compact/medium/expanded boundaries. Height also matters when a wide window is short. [Use window size classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)
- Android recommends computing adaptive information centrally and passing layout decisions down as state. For apps targeting API 36, the system ignores orientation, aspect-ratio, and resizability restrictions on displays whose smallest width is at least 600 dp. [Support different display sizes](https://developer.android.com/develop/adaptive-apps/guides/support-different-display-sizes)

### Pocket implications

- Do not add tablet detection. If larger-window support becomes a goal, use one app-shell window policy and test compact, medium, expanded, and compact-height configurations.
- Prefer a purposeful two-pane history/detail or Pocket-list/detail workflow over stretching the phone column. Add adaptive dependencies only with accepted product scope.

## Edge-to-edge and IME

### Platform facts

- Edge-to-edge is enforced on Android 15/API 35+ for apps targeting SDK 35+. Important content and controls must stay clear of system bars, cutouts, and gesture regions. [About window insets](https://developer.android.com/develop/ui/compose/system/insets)
- Compose guidance pairs `enableEdgeToEdge()` with `adjustResize`, which lets the app receive IME insets. [Set up edge-to-edge](https://developer.android.com/develop/ui/compose/system/setup-e2e)
- Material 3 `Scaffold` padding generally covers managed bars; independent inset padding on the same path can double-apply space. Compose provides `imePadding()` and inset-aware spacers for scrolling forms. [Material 3 insets](https://developer.android.com/develop/ui/compose/system/material-insets), [Compose insets](https://developer.android.com/develop/ui/compose/system/insets-ui)

### Pocket implications

- The activity prerequisites exist. Gate future UI with device tests that open the keyboard on every editable form and verify the final field, save action, snackbar, bottom navigation, and gesture area at large font sizes.
- Keep inset ownership explicit; do not layer generic safe-area padding over Material defaults without a failing case.

## Accessibility and TalkBack

### Platform facts

- Android recommends combining manual assistive-technology testing, analysis tools, automated tests, and user testing. TalkBack is the built-in screen reader. [Test accessibility](https://developer.android.com/guide/topics/ui/accessibility/testing)
- Compose accessibility and tests share the semantics tree. Material/Foundation components provide many semantics automatically, but custom components still need explicit meaning, state, and actions. [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)
- `ComposeTestRule.enableAccessibilityChecks()` requires API 34+ and currently does not work on Robolectric, so it is a device-test gate. [ComposeTestRule accessibility API](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/junit4/ComposeTestRule)
- Interactive targets should be at least 48 dp. [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

### Pocket implications

- Automated checks do not close the retained physical-device gaps. Repeat expense, refund, filtering, Pocket-budget, backup, and restore with TalkBack and maximum font size on a representative physical device.
- Treat spoken amounts, conversion status, warning state, error recovery, and delete/undo feedback as acceptance criteria, not only content descriptions.

## Local data, backup, and recovery

### Platform facts

- Auto Backup otherwise includes internal files, databases, preferences, and app-specific external files. `android:allowBackup="false"` is appropriate for sensitive data, but on some Android 12+ manufacturer devices it may not disable device-to-device transfer by itself. [Back up user data](https://developer.android.com/identity/data/autobackup)
- Apps targeting API 31+ use `data-extraction-rules` for cloud and device-transfer behavior; legacy rules remain relevant to Android 11 and lower. [Back up user data](https://developer.android.com/identity/data/autobackup)
- The Storage Access Framework lets users choose where to create or open a document without broad storage access. `ACTION_CREATE_DOCUMENT` creates a numbered name rather than overwriting a same-named file. [Access documents and files](https://developer.android.com/training/data-storage/shared/documents-files)

### Pocket implications

- Keep the current opt-out and explicit exclusions covered by manifest tests and a transfer/reinstall exercise across at least one representative physical device after meaningful storage changes.
- The picker controls location and access, not exported-payload confidentiality. Decide whether `.pocketbackup` requires password-based authenticated encryption; if so, specify format versioning, wrong-password behavior, tamper rejection, recovery-key handling, and migration tests first.

## Notification-assisted capture

### Platform facts

- A `NotificationListenerService` needs the system binding permission and service action. The user separately grants or denies access in Settings. [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService), [listener settings](https://developer.android.com/reference/android/provider/Settings#ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
- Manifest metadata filters broad notification types, not bank package names. Listener callbacks run on the main thread, and posted notifications identify their source package. [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- The API reports newly posted notifications and can query currently outstanding notifications; it is not a complete historical feed. A disconnected listener receives no events until rebound. [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- Android 15 redacts detected one-time passcodes from notification content exposed to untrusted listeners. [Android 15 behavior changes](https://developer.android.com/about/versions/15/behavior-changes-all#otp-redaction)

### Pocket implications

- Keep notification capture behind an evidence gate: named bank packages, anonymized examples per bank/language/event type, measured coverage, and explicit consent. Do not promise completeness or treat missing notifications as no spending.
- Reject non-allowlisted packages before parsing or persistence, move work off the callback thread, store normalized candidates rather than raw text, deduplicate updates, and require confirmation before changing a Pocket.
- OTP redaction is a privacy protection, not a parser failure to work around. Diagnostics must contain no financial or raw notification content.

## Widgets and shortcuts

### Platform facts

- Static shortcuts suit routine actions whose meaning remains stable across an app version. Pocket's `Nuevo gasto` fits that model. [Create shortcuts](https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts)
- Widgets are glanceable home-screen surfaces and can expose frequent task entry points, but gestures are limited and layouts must respond to launcher grids and resizing. [App widgets overview](https://developer.android.com/develop/ui/views/appwidgets/overview)

### Pocket implications

- Harden the shortcut first: verify warm-start intent handling, process-death launch, locale changes, and no sensitive preview content.
- Add a widget only if usage evidence shows the shortcut is insufficient. The safest first widget is action-only (`Nuevo gasto`, optionally `Nueva devolución`) with no amounts, availability, merchants, or notes.

## Suggested planning gates

1. Close release-confidence gaps first: reproducible build/CI, TalkBack and maximum-font checks, and repeatable recovery evidence.
2. Tie adaptive work to accepted large-window workflows, not dependency upgrades alone.
3. Decide portable-backup confidentiality before expanding data import/export.
4. Prototype notification capture only after real bank-notification evidence exists; keep it suggestion-only until measured accuracy and lifecycle coverage justify more.
5. Prefer the existing shortcut over a widget until observed friction supports another home-screen surface.
