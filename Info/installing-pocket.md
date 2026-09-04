# Install Pocket on an Android device

Pocket supports Android 8.0 (API 26) and newer. It requires no account, and financial data stays on the device. Network access is used only for exchange-rate lookup after the user enables online FX.

## Install an official release

Download **[Pocket 1.0.0](https://github.com/AIF31/Financial-App/releases/download/v1.0.0/Pocket-v1.0.0.apk)** directly, or choose another version from [GitHub Releases](https://github.com/AIF31/Financial-App/releases).

Before installing:

- Confirm the device runs Android 8.0 or newer in **Settings > About phone > Android version**.
- If Pocket is already installed, export and verify a `.pocketbackup` before changing between a debug build and an official release.
- Download APKs only from `github.com/AIF31/Financial-App`.

1. Open the Pocket 1.0.0 link on the Android device and download `Pocket-v1.0.0.apk`. If the browser warns that APK files can be harmful, continue only after confirming the address is this repository.
2. Tap the completed-download notification. If it is gone, open the device's **Files** app, select **Downloads**, and tap `Pocket-v1.0.0.apk`.
3. If Android blocks the install, tap **Settings** on the prompt and enable **Allow from this source** for the browser or Files app that opened the APK.
4. Return to Android's package installer and tap **Install**.
5. Tap **Open**, or launch **Pocket** from the app drawer.
6. For tighter security, return to **Settings > Apps > Special app access > Install unknown apps** and disable **Allow from this source** after installation.

Menu names vary by manufacturer. Searching Settings for **Install unknown apps** reaches the same permission on most devices.

Google Play Protect may scan the sideloaded APK before installation. Cancel if the APK came from any location other than this repository.

## Confirm the installation

Open **Settings > Apps > Pocket > App details** and confirm the installed version is `1.0.0`. Pocket should open to its Spanish setup flow and should not require an account.

## Troubleshooting

- **The download will not open:** Open the system **Files** app, go to **Downloads**, and tap the APK there.
- **Installation is blocked:** Allow installs from the specific browser or Files app that opened the APK, then return to the installer.
- **App not installed:** A Pocket build signed with a different certificate may already be installed. Do not uninstall it until you have exported and verified a `.pocketbackup`; uninstall it only after the backup is safe, then install the official APK and restore the backup.
- **Package appears invalid or incompatible:** Confirm Android 8.0 or newer is installed, delete the incomplete download, and download the APK again from GitHub Releases.

## Build and install from source

You need Git, JDK 17, and the Android SDK Platform 36. Android Studio includes a suitable JDK and can install the required SDK components.

Clone the repository and enter it:

```bash
git clone https://github.com/AIF31/Financial-App.git
cd Financial-App
```

Connect an Android device with USB debugging enabled, accept its RSA prompt, then build and install the debug APK.

Windows PowerShell:

```powershell
$env:JAVA_HOME = "$env:ProgramFiles\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:installDebug
```

macOS or Linux:

```bash
./gradlew :app:installDebug
```

You can also build the APK without installing it:

```bash
./gradlew :app:assembleDebug
```

The result is `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the device and open it to install. Debug builds are for personal testing; official updates use release APKs from GitHub Releases.

## Updates and data safety

To update an official installation, download the newer official APK and install it over the existing app. Do not uninstall first. Android accepts an in-place update only when both APKs use the same signing certificate; a source-built debug APK and an official release APK therefore cannot update one another.

Switching between debug and official builds requires uninstalling Pocket, which deletes app-private data. Before uninstalling or replacing data:

1. Export a `.pocketbackup` from Pocket.
2. Open its restore preview and verify the expected counts.
3. Keep a copy outside the device if the device will be reset or replaced.

CSV exports cannot restore the app. See [Release signing and recovery](release-signing-and-recovery.md) for the full backup and restore procedure.
