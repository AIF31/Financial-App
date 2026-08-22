# Pocket

Pocket is a local-first Android app for one person to budget declared funds across configurable Pockets and record expenses or refunds. It is Spanish-first, requires no account, and declares no Internet permission.

## Project information

Operational documentation is maintained outside the Android module under [`Info/`](Info/README.md). Start there for implementation decisions, Windows/Android Studio setup, S23 testing, release signing, recovery, and retained verification evidence.

## Run

The project requires JDK 17 and an Android SDK with API 36. With the toolchain installed for this workspace:

```bash
JAVA_HOME=/home/alan1/.local/share/financial-app-jdk17 ANDROID_HOME=/home/alan1/Android/Sdk ./gradlew :app:installDebug
```

Launch `Pocket` from the connected emulator or Android device. To run all host tests:

```bash
JAVA_HOME=/home/alan1/.local/share/financial-app-jdk17 ANDROID_HOME=/home/alan1/Android/Sdk ./gradlew :app:testDebugUnitTest
```

To build installable debug and optimized release APKs:

```bash
JAVA_HOME=/home/alan1/.local/share/financial-app-jdk17 ANDROID_HOME=/home/alan1/Android/Sdk ./gradlew :app:assembleDebug :app:assembleRelease
```

## Release and device operations

Use [`Info/release-signing-and-recovery.md`](Info/release-signing-and-recovery.md) for signed builds, key backup, APK verification, and reinstall/restore procedures. Use [`Info/windows-android-studio-s23-testing.md`](Info/windows-android-studio-s23-testing.md) before running tests on Windows or a physical phone; device instrumentation can clear app data.

Device tests are configured on the `pixel6Api35` Gradle Managed Device:

```bash
JAVA_HOME=/home/alan1/.local/share/financial-app-jdk17 ANDROID_HOME=/home/alan1/Android/Sdk ./gradlew :app:pixel6Api35DebugAndroidTest
```
