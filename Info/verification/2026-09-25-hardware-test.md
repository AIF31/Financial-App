# Hardware test record — 2026-09-25

Checkout: `f35f8c3` (`main`). This record contains no phone serial, personal values, or backup contents.

## Automated gate

| Check | Result |
| --- | --- |
| Host tests | Pass: 205 tests, 0 failures, 0 errors, 0 skips |
| Android lint | Pass |
| Debug and release APK builds | Pass; the release artifact from this gate was unsigned |
| Instrumentation APK build | Pass |
| Pixel 6 API 35 managed-device tests | Pass: 28 tests, 0 failures, 0 errors, 0 skips |
| Pixel 6 API 35 visual tour | Pass separately: 1 test, 0 failures, 0 errors, 0 skips |

The checks ran through `.agents/skills/gradle-run/scripts/gradle_run.py`. Test counts came from JUnit XML under `app/build/test-results/testDebugUnitTest/` and `app/build/outputs/androidTest-results/managedDevice/` immediately after each run. The tour run replaced the managed-device XML for the preceding 28-test run; the counts above are retained here. The first managed-device run excluded the `LargeTest` visual tour, matching CI, and a second run selected that test alone. Gradle reported only Java native-access and experimental managed-device GPU option warnings.

## Personal phone

The connected Samsung SM-S918B was running Android API 36 with Pocket `1.0.0` (version code `1`). Android Studio Quail 4 and Android CLI `1.0.15985488` were available. The installed release launched and its main activity resumed. `run-as` rejected the package as non-debuggable. Package metadata still showed `1.0.0` afterward. The user reported making a fresh in-app backup immediately before this run; its contents were not inspected.

The phone was confirmed to be personal, so no debug or test APK was installed, no database-clearing instrumentation ran, and the release package was not removed. Existing device tests call `clearAllTables()` and change app preferences. A debug APK also cannot replace the installed release without a signing mismatch. The in-place launch check did not establish preservation of every app-owned file.

## Continued data-preserving physical checks

The four main tabs opened on the installed release. At font scale 1.5, all four navigation items remained reachable and the dashboard rendered; the original 1.0 scale was restored. The installed system theme was dark and Pocket rendered in dark colors. With airplane mode temporarily enabled, Pocket opened and its main navigation remained available; airplane mode was restored off. After force-stop and relaunch, visible dashboard text matched the pre-stop screen without recording its values. The launcher shortcut action opened the expense form, and the form was dismissed without saving. The reminder settings screen opened, but a lock-screen reminder was not generated or observed.

The supplied `Download/pocket-2026-09-25.pocketbackup` opened a valid replacement preview. The preview identified backup format version 4, showed period/Pocket/movement counts, and warned that restore could replace current data. It was cancelled without restoring. Temporary screenshots used for visual inspection were deleted and are not part of this record.

After reboot, the same device reconnected, reported boot complete, and launched Pocket `1.0.0` with its main navigation present. This verifies restart and launch, but does not independently compare every stored value with its pre-reboot value. Samsung TalkBack was temporarily enabled; Pocket remained foreground with all four navigation nodes exposed, then accessibility settings were restored to disabled with no enabled services. Spoken feedback and TalkBack gestures were not independently verified. Financial create/edit/refund/delete flows, onboarding, lock-screen reminder privacy, and replacement restore were not exercised on the personal ledger.

The shortcut and incoming-intent path were inspected using the Android intent security guidance. The exported launcher activity accepts a fixed `NEW_EXPENSE` action to open a form, with no incoming nested Intent or extra forwarded to another component. The backup picker uses a system-selected document URI; the app's `FileProvider` is non-exported and grants read access only for explicit sharing. No security change was indicated by this focused check.

## Authorized physical instrumentation and final installation

The user subsequently authorized the full physical run despite its app-state changes. The phone backup was verified by SHA-256 before testing; a separate copy was protected locally with Windows DPAPI. The release package was replaced with the debug and test APKs for instrumentation. The physical suite reported 28 passes and one failure in `PocketUiUxReviewTourTest`: its Settings navigation click did not reach recurring templates because the target card was below the viewport. An isolated rerun reproduced the failure. The test now scrolls the Settings hub to the card and invokes its click semantics; the requested isolated rerun passed (1/1). The full suite was not rerun, following the user's instruction to run only the failing test. Logs are under `app/build/outputs/physicalTest/`.

The signed release build was restored first, and the verified phone backup was applied successfully. GitHub's latest published release was checked as `v1.0.0`. Its APK SHA-256 matched GitHub's asset digest (`5aa0e4cbb2527c8d7674b5a469d91d901ebdc03418008d23d7fd26b107ca7e5a`), and its signing certificate matched the local release build. Because the current checkout uses database schema 7 while the published APK uses schema 6, the published APK was installed fresh and the original backup was restored in that version. The installed `base.apk` hash matched the published asset. The app resumed with all four main tabs present and no onboarding screen. Package metadata confirmed version `1.0.0`, code `1`. The original backup remains in phone Downloads with its original SHA-256; the encrypted local copy remains available.

## Release decision

The phone now runs the latest published release, `1.0.0` / code `1`, with the backup restored. The project development version was subsequently advanced to `1.0.1` / code `2`; no newer release has been published or installed on the phone. The initial full physical suite was not a clean single-run pass, and lock-screen reminder privacy and full TalkBack interaction remain unverified, so this run does not establish a new-release acceptance gate.
