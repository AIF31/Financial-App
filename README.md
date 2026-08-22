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

## Sign a private release APK

Release signing is enabled only when all four `POCKET_RELEASE_*` environment variables are present. Keep the keystore and passwords outside the repository and back them up securely; losing the key prevents future in-place updates.

Create a dedicated key interactively on Windows (the command prompts for passwords):

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe' -genkeypair -v -keystore "$env:USERPROFILE\PocketKeys\pocket-release.jks" -alias pocket -keyalg RSA -keysize 4096 -validity 10000
```

Build the signed APK from PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:POCKET_RELEASE_STORE_FILE = "$env:USERPROFILE\PocketKeys\pocket-release.jks"
$env:POCKET_RELEASE_STORE_PASSWORD = '<store-password>'
$env:POCKET_RELEASE_KEY_ALIAS = 'pocket'
$env:POCKET_RELEASE_KEY_PASSWORD = '<key-password>'
.\gradlew.bat :app:assembleRelease
```

On Windows, the password can instead be stored for the current Windows user with DPAPI and loaded only for the Gradle process:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\PocketKeys"
[pscredential]::new('pocket', (Read-Host 'Keystore password' -AsSecureString)) |
    Export-Clixml "$env:USERPROFILE\PocketKeys\pocket-release-credential.xml"
.\scripts\build-signed-release.ps1
```

The DPAPI credential is tied to the current Windows account and machine. Keep an independent copy of the keystore password in a password manager so the key remains usable after migrating computers.

The signed artifact is `app/build/outputs/apk/release/app-release.apk`. If none of the signing variables are set, Gradle still produces `app-release-unsigned.apk` for non-distribution verification; setting only some variables fails configuration to prevent an ambiguous build.

Device tests are configured on the `pixel6Api35` Gradle Managed Device:

```bash
JAVA_HOME=/home/alan1/.local/share/financial-app-jdk17 ANDROID_HOME=/home/alan1/Android/Sdk ./gradlew :app:pixel6Api35DebugAndroidTest
```
