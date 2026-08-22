# Release signing and recovery

## Permanent release identity

The private release uses a dedicated 4096-bit RSA key and APK Signature Scheme v2.

- Package: `com.aif31.pocket`
- Keystore: `%USERPROFILE%\PocketKeys\pocket-release.jks`
- Alias: `pocket`
- DPAPI credential: `%USERPROFILE%\PocketKeys\pocket-release-credential.xml`
- Certificate SHA-256: `d9d3555d1c0173e45141ff7d63c01a377c2cc7a68d0193a0aad24751eaef5fd4`

The keystore and credential must remain outside the repository. `.gitignore` blocks common `*.jks` and `*.keystore` files, but operators must still inspect staged files before every commit.

## Build a signed release

The preferred Windows command loads the DPAPI-protected password only for the Gradle process and restores the previous environment afterward:

```powershell
.\scripts\build-signed-release.ps1
```

Output:

```text
app\build\outputs\apk\release\app-release.apk
```

The Gradle build accepts these variables when manual configuration is necessary:

- `POCKET_RELEASE_STORE_FILE`
- `POCKET_RELEASE_STORE_PASSWORD`
- `POCKET_RELEASE_KEY_ALIAS`
- `POCKET_RELEASE_KEY_PASSWORD`

All four must be present. A partial configuration intentionally fails. With none present, `assembleRelease` creates an unsigned APK for non-distribution checks.

## Verify the APK

Use the newest installed Android SDK Build Tools directory:

```powershell
$buildTools = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Directory |
    Sort-Object Name -Descending |
    Select-Object -First 1
& "$($buildTools.FullName)\apksigner.bat" verify --verbose --print-certs .\app\build\outputs\apk\release\app-release.apk
```

Confirm that v2 verification succeeds and the certificate SHA-256 matches the value above. A release install should reject `run-as com.aif31.pocket` because it is non-debuggable.

## Password and migration recovery

DPAPI protects the credential for the current Windows account and machine. It is not a migration backup. Retrieve the password locally and place it in a trusted password manager; never paste it into chat, logs, an issue, or the repository:

```powershell
(Import-Clixml "$env:USERPROFILE\PocketKeys\pocket-release-credential.xml").GetNetworkCredential().Password
```

Back up both the keystore file and password independently. Losing either prevents future in-place updates to installations signed by this certificate.

## App-data backup and reinstall

Pocket backup uses the Storage Access Framework and a versioned `.pocketbackup` file. The 2026-08-22 device exercise saved:

```text
/sdcard/Download/pocket-s23-test-2026-08-22.pocketbackup
```

Recommended transition procedure:

1. Export a fresh backup from Pocket.
2. Open its restore preview and confirm expected period, Pocket, and movement counts.
3. Copy the backup off-device if the phone itself is being reset or replaced.
4. Uninstall the old package only after backup verification.
5. Install the APK with the intended signing identity.
6. Restore only into an empty installation and verify dashboard and movement totals.
7. Cold-start the app again to confirm persistence.

Restore intentionally rejects non-empty targets and applies valid backups transactionally. CSV is analytical export only and cannot restore the app.

## Security findings retained for future releases

- The packaged manifest declares no `android.permission.INTERNET`.
- `android:allowBackup="false"`; the explicit document flow is the supported backup path.
- MainActivity is the only app-owned exported component.
- WorkManager/ProfileInstaller exported components are protected by system permissions.
- Internal providers, services, and receivers are non-exported.
- Reminder navigation uses an explicit immutable `PendingIntent` with private lock-screen visibility.
- No explicit Android logging calls were found in main source, and release-process logs did not contain the known verification amounts.
