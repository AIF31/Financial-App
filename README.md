# Pocket

Pocket is a local-first Android app for one person to budget declared funds across configurable Pockets and record expenses or refunds. It is Spanish-first, requires no account, and declares no Internet permission.

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

Device tests are configured on the `pixel6Api35` Gradle Managed Device:

```bash
JAVA_HOME=/home/alan1/.local/share/financial-app-jdk17 ANDROID_HOME=/home/alan1/Android/Sdk ./gradlew :app:pixel6Api35DebugAndroidTest
```
