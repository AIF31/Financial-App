# Pocket

Pocket is a local-first Android app for one person to budget declared funds across configurable Pockets and record expenses or refunds. It is Spanish-first and requires no account. Financial data stays on the device; network access is used only for exchange-rate lookup after the user enables online FX.

## Download and install

Pocket supports Android 8.0 (API 26) and newer.

**[Download Pocket 1.0.0 for Android](https://github.com/AIF31/Financial-App/releases/download/v1.0.0/Pocket-v1.0.0.apk)**

1. Open the download link on the Android device and save `Pocket-v1.0.0.apk`.
2. Open the downloaded APK from the browser notification or the device's **Downloads** folder.
3. If prompted, allow that browser or file manager to **Install unknown apps**, then return to the installer.
4. Tap **Install**, then **Open**.

Only install APKs from this repository's Releases page. For updates, install the newer APK over the existing app instead of uninstalling it. Uninstalling Pocket deletes its private data, so export and verify a `.pocketbackup` first.

See the [complete installation guide](Info/installing-pocket.md) for device-specific permission steps, troubleshooting, updates, and building from source. Other versions remain available on [GitHub Releases](https://github.com/AIF31/Financial-App/releases).

## Project information

Operational documentation is maintained outside the Android module under [`Info/`](Info/README.md). Start there for implementation decisions, Windows/Android Studio setup, physical-device testing, release signing, recovery, and retained verification evidence.

## Build and run

The project requires JDK 17 and an Android SDK with API 36. Configure `JAVA_HOME` and `ANDROID_HOME` for your environment, then run:

```bash
./gradlew :app:installDebug
```

Launch `Pocket` from the connected emulator or Android device. To run all host tests:

```bash
./gradlew :app:testDebugUnitTest
```

To build an installable debug APK:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Release APKs require the project's signing credentials and are built by maintainers for distribution.

## Release and device operations

Use [`Info/release-signing-and-recovery.md`](Info/release-signing-and-recovery.md) for signed builds, key backup, APK verification, and reinstall/restore procedures. Use [`Info/windows-android-studio-device-testing.md`](Info/windows-android-studio-device-testing.md) before running tests on Windows or a physical phone; device instrumentation can clear app data.

Device tests are configured on the `pixel6Api35` Gradle Managed Device:

```bash
./gradlew :app:pixel6Api35DebugAndroidTest
```
