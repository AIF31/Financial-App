# Windows, Android Studio, and S23 testing

## Environment finding

The WSL environment did not provide usable x86_64 hardware acceleration for the Android emulator. The reliable paths are:

1. Run Android Studio and its emulator directly on Windows, where Hyper-V/WHPX acceleration can be available.
2. Prefer the physical Samsung S23 Ultra through Windows ADB for final acceptance testing.

The repository may be opened from a Windows worktree such as `%USERPROFILE%\StudioProjects\Financial-App`. Keep that worktree on `main` and preserve unrelated Android Studio or Gradle edits when pulling updates.

## Windows build environment

From PowerShell at the repository root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:GRADLE_USER_HOME = "$env:USERPROFILE\.gradle"
```

Useful verification commands:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:assembleDebug
```

Combined local gate:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

The project also defines a `pixel6Api35` Gradle Managed Device. It requires working Windows virtualization; it is not expected to run in an unaccelerated WSL environment.

## Connect the S23 through Windows ADB

Enable Developer options and USB debugging on the phone, connect it by USB, accept the phone's RSA prompt, and run:

```powershell
$env:ANDROID_USER_HOME = "$env:USERPROFILE\.android"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
```

The device must appear with state `device`. `offline` normally requires reconnecting the cable, restarting USB debugging, or rebooting the phone. An empty device list means Windows ADB cannot currently see the phone; Android Studio cannot compensate for that state.

## Instrumented tests

Build the debug app and test APKs, install them, and invoke the runner directly when Gradle device orchestration is unavailable:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
& $adb install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb install -r .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& $adb shell am instrument -w -r com.aif31.pocket.test/androidx.test.runner.AndroidJUnitRunner
```

A healthy raw run finishes with `OK` and no `INSTRUMENTATION_STATUS_CODE: -1` or `-2` frames.

### Data safety warning

Treat device instrumentation as destructive to Pocket app data:

- The debug APK and permanent release APK use different signing certificates and cannot update each other in place.
- Switching certificates requires uninstalling the existing package, which erases app-private data.
- The shortcut-intent instrumentation test deliberately clears the target application's database before and after its isolated scenario.

Always create and verify a Pocket backup before uninstalling the release app or installing test builds. Never run the device suite against irreplaceable data.

## Manual physical-device checklist

For a release candidate, verify at minimum:

- Onboarding, new funds, period start day, and Pocket budgets.
- Expense capture through both the app and launcher shortcut; ordinary capture should remain below ten seconds.
- Dashboard/history consistency, search, filters, editing, refunds, delete/undo, and negative availability.
- Airplane mode, process death, and reboot persistence.
- Daily reminder enable/disable, selected time, and lock-screen privacy.
- Backup preview, uninstall/reinstall, empty-install restore, and rejection/rollback behavior.
- Dark theme, maximum font size, and TalkBack.
- Release package is non-debuggable and does not expose financial data through logs.

The dated S23 report records which of these checks passed and which were omitted.

## Android Studio notes

- Import the repository as a Gradle project and use Android Studio's bundled JDK 17.
- Wait for Gradle sync before selecting the physical device and `app` run configuration.
- Build Analyzer output is useful for future build-performance regressions, but no release-blocking analyzer finding was recorded during the 2026-08-22 verification.
- If Android Studio's worktree is behind remote `main`, use `git pull --ff-only` after checking `git status`; do not discard local Gradle edits automatically.
