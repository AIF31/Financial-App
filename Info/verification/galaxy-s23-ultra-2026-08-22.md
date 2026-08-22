# Galaxy S23 Ultra release verification — 2026-08-22

## Device and artifact

- Device: Samsung Galaxy S23 Ultra (`SM-S918B`)
- OS: Android 16 / API 36, One UI 8.5
- Package: `com.aif31.pocket`
- App version: `1.0` (`versionCode` 1)
- Release artifact: `app/build/outputs/apk/release/app-release.apk`
- Installed package rejected `run-as` as non-debuggable.
- APK Signature Scheme v2 verified with one 4096-bit RSA signer.
- Signing certificate SHA-256: `d9d3555d1c0173e45141ff7d63c01a377c2cc7a68d0193a0aad24751eaef5fd4`

The permanent keystore and its Windows DPAPI-protected credential are stored outside the repository under `%USERPROFILE%\PocketKeys`. An independent password-manager backup is still required so the key remains usable after migrating computers.

## Automated verification

- Host tests: 16 passed, 0 failures, 0 errors.
- Android lint: passed for debug and release-vital analysis.
- Physical-device instrumentation: 9 passed with no `INSTRUMENTATION_STATUS_CODE: -1` or `-2` frames.
- Instrumented coverage included onboarding, Pocket budgeting, expense capture, launcher shortcut, reminder settings, movement filters, delete/undo, backup round trip and rollback, rollover, refunds, FX confirmation, and historical recalculation.
- After review, the independent-filter and shortcut-submission host flows passed again, and the strengthened `ActivityScenario` shortcut-intent device test compiled successfully.
- Release signing fails closed when only a subset of `POCKET_RELEASE_*` variables is configured.
- `scripts/build-signed-release.ps1` rebuilt the signed artifact from the DPAPI-protected credential.

## Manual S23 scenarios

| Scenario | Result | Evidence |
| --- | --- | --- |
| Onboarding | Pass | New funds SAR 1,000.00; period start day 25 |
| Pocket budget | Pass | Supermercado budget SAR 300.00 |
| Launcher shortcut capture | Pass | Human-measured completion in 7.45 seconds |
| Movement totals | Pass | Intentional SAR 25.00 + SAR 30.00 expenses; net spend SAR 55.00; availability SAR 245.00 |
| Airplane mode | Pass | Dashboard and both movements remained available offline |
| Process death | Pass | Force-stop followed by a 692 ms cold launch preserved all values |
| Device reboot | Pass | Airplane mode remained enabled and all values survived reboot; cold launch 728 ms |
| Daily reminder privacy | Pass | Lock-screen notification observed as `Recordatorio diario` with no amount |
| Backup preview | Pass | Version 1: 1 budget period, 10 Pockets, 2 movements |
| Backup → uninstall → reinstall → restore | Pass | Dashboard, both movements, and Supermercado SAR 300.00 / SAR 245.00 restored exactly |
| Signed release install | Pass | Permanent release-signed APK installed and restored on the S23 |
| Signed release cold restart | Pass | 131 ms cold launch; restored values persisted |
| Dark theme | Pass | Visually inspected on the physical device |
| Maximum font size | Omitted | User explicitly chose to omit this manual check |
| TalkBack | Omitted | User explicitly chose to omit this manual check |

## Release security checks

- Packaged manifest declares no `android.permission.INTERNET`.
- `android:allowBackup="false"`; backup and restore use the app's explicit document flow.
- The launcher activity is the only app-owned exported component.
- Exported WorkManager/ProfileInstaller components are guarded by system-only `BIND_JOB_SERVICE` or `DUMP` permissions.
- Internal providers, services, and receivers are non-exported.
- The reminder notification uses an explicit immutable `PendingIntent` and private lock-screen visibility.
- Main source contains no explicit Android logging calls.
- No known financial values were found in 112 captured log lines scoped to Pocket's release process.

## Remaining coverage gaps

Maximum-font and TalkBack behavior are not physically verified because those checks were explicitly omitted. The S23 disconnected from Windows ADB before the strengthened shortcut-intent and independent-filter tests could receive a second physical run; their host equivalents pass, their device source compiles, and the corresponding shortcut and filter behavior passed earlier physical/manual checks. All other MVP physical-device exit checks exercised in this session passed.
