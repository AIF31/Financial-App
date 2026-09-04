# Release verification

This record is a public-safe template for release evidence. Add only reproducible results and synthetic data; omit usernames, device serials, personal financial values, signing secrets, and other environment-specific identifiers.

## Artifact and environment

- Device class: representative physical Android device or the declared managed device
- OS/API: record the tested Android API level only when it affects the result
- Package: `com.aif31.pocket`
- App version: record the tested version
- Release artifact: `app/build/outputs/apk/release/app-release.apk`
- Release package rejected `run-as` as non-debuggable.
- APK Signature Scheme v2 verification succeeded.
- Certificate SHA-256: compare with the independently stored release fingerprint; do not commit the fingerprint here.

The permanent keystore and its protected credential are stored outside the repository under `%USERPROFILE%\PocketKeys`. Keep independent recovery material in an approved password manager.

## Automated verification

- Host tests passed with no failures or errors.
- Android lint passed for debug and release-vital analysis.
- Physical-device instrumentation passed with no `INSTRUMENTATION_STATUS_CODE: -1` or `-2` frames.
- Instrumented coverage included onboarding, Pocket budgeting, expense capture, launcher shortcut, reminder settings, movement filters, delete/undo, backup round trip and rollback, rollover, refunds, FX confirmation, and historical recalculation.
- The independent-filter and shortcut-submission host flows passed again, and the strengthened `ActivityScenario` shortcut-intent device test compiled successfully.
- Release signing fails closed when only a subset of `POCKET_RELEASE_*` variables is configured.
- `scripts/build-signed-release.ps1` rebuilt the signed artifact from the protected credential.

## Manual physical-device scenarios

Use synthetic values and generic labels in all manual checks:

| Scenario | Result | Evidence |
| --- | --- | --- |
| Onboarding | Pass | Synthetic funds and period-start fixture |
| Pocket budget | Pass | Synthetic Pocket budget fixture |
| Launcher shortcut capture | Pass | Completed within the agreed interaction target |
| Movement totals | Pass | Synthetic expense fixture produced expected totals |
| Airplane mode | Pass | Dashboard and movements remained available offline |
| Process death | Pass | Force-stop preserved all synthetic values |
| Device reboot | Pass | All synthetic values survived reboot |
| Daily reminder privacy | Pass | Lock-screen notification displayed without an amount |
| Backup preview | Pass | Synthetic fixture previewed successfully |
| Backup → uninstall → reinstall → restore | Pass | Synthetic dashboard, movements, and budget data restored exactly |
| Signed release install | Pass | Permanent release-signed APK installed and restored |
| Signed release cold restart | Pass | Restored synthetic values persisted |
| Dark theme | Pass | Visually inspected on the physical device |
| Maximum font size | Omitted | Not included in this verification run |
| TalkBack | Omitted | Not included in this verification run |

## Release security checks

- Packaged manifest declares no `android.permission.INTERNET`.
- `android:allowBackup="false"`; backup and restore use the app's explicit document flow.
- The launcher activity is the only app-owned exported component.
- Exported WorkManager/ProfileInstaller components are guarded by system-only `BIND_JOB_SERVICE` or `DUMP` permissions.
- Internal providers, services, and receivers are non-exported.
- The reminder notification uses an explicit immutable `PendingIntent` and private lock-screen visibility.
- Main source contains no explicit Android logging calls.
- No known financial values were found in captured logs scoped to the release process.

## Remaining coverage gaps

Maximum-font and TalkBack behavior are not physically verified because those checks were omitted. Repeat those checks, plus any scenario marked incomplete, on a representative physical device before using this record as release evidence. Keep all future evidence synthetic and device-agnostic.
