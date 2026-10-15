# Ubuntu development and verification

Pocket supports source development, host tests, lint, and APK assembly on Ubuntu, including Ubuntu on WSL. Use the current checkout and Linux tools. The existing [Android CI](../.github/workflows/android-ci.yml) runs on Ubuntu; no app or Gradle configuration change is needed for this host.

## Set up the build environment

Install Git, Python 3, JDK 17, and unzip:

```bash
sudo apt-get update
sudo apt-get install git python3 openjdk-17-jdk unzip
```

Install the Linux Android SDK through [Android Studio's SDK Manager](https://developer.android.com/studio/intro/update#sdk-manager) or the [official command-line tools](https://developer.android.com/tools/sdkmanager). Include Android SDK Platform 36, Android SDK Build-Tools 36.0.0, and Platform-Tools, and accept the SDK licenses. Install the Android Emulator only when running device tests.

From the repository root, configure the installed paths. These defaults use Ubuntu's x86_64 OpenJDK package and Android Studio's usual SDK location; adjust them for your installation or CPU architecture:

```bash
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
java -version
```

Confirm Java 17 and the presence of `$ANDROID_HOME/platforms/android-36/android.jar` and `$ANDROID_HOME/build-tools/36.0.0`. Use Linux SDK binaries on Ubuntu and WSL. Keep each host's SDK location in its environment or ignored `local.properties`; that file must not be committed. Gradle uses its checked-in wrapper, so a system Gradle installation is unnecessary.

The first build needs access to Google's Maven repository, Maven Central, and the Gradle distribution and toolchain download services. Gradle also needs a writable cache, normally `$HOME/.gradle`. In a sandbox, report cache or network restrictions separately from a source or test failure.

## Run host checks and assemble APKs

Host tests use Robolectric and require neither a connected Android device nor KVM. Agents must follow the [Gradle-run skill](../.agents/skills/gradle-run/SKILL.md), including its diagnostic-owner procedure and bounded summaries.

Create a compact-output workflow:

```bash
python3 .agents/skills/gradle-run/scripts/gradle_run.py create
```

Copy the returned `workflow` value into `gradle_workflow_id`, then run the same non-device gate as CI:

```bash
gradle_workflow_id="<workflow value returned by create>"
python3 .agents/skills/gradle-run/scripts/gradle_run.py run \
  --workflow "$gradle_workflow_id" \
  --scope broad \
  --question "Do Ubuntu host tests, lint, and debug, release, and test APK builds pass?" -- \
  ./gradlew :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest \
  --no-daemon
```

Read the wrapper's bounded summary. If a check fails, inspect the reported source or test result and run the narrowest relevant follow-up through the same workflow. Summarize the results and finish once validation or diagnosis is complete:

```bash
python3 .agents/skills/gradle-run/scripts/gradle_run.py finish \
  --workflow "$gradle_workflow_id"
```

Host-test XML results are under `app/build/test-results/testDebugUnitTest/`; the HTML report is under `app/build/reports/tests/testDebugUnitTest/`. APKs are under `app/build/outputs/apk/`. Assembling the instrumentation APK verifies compilation, not execution of device tests.

## Run managed-device tests when acceleration is available

The project declares `pixel6Api35`, a Pixel 6 API 35 `aosp-atd` managed device. Linux emulator VM acceleration uses KVM; confirm access before starting the device gate. See the [official acceleration requirements](https://developer.android.com/studio/run/emulator-acceleration#vm-linux).

```bash
"$ANDROID_HOME/emulator/emulator" -accel-check
```

On a Linux host with usable acceleration and accepted system-image licenses, run the declared managed-device gate through the same compact wrapper workflow:

```bash
python3 .agents/skills/gradle-run/scripts/gradle_run.py run \
  --workflow "$gradle_workflow_id" \
  --scope targeted \
  --question "Do the Pixel 6 API 35 managed-device tests execute and pass on Ubuntu?" -- \
  ./gradlew :app:pixel6Api35DebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  --no-daemon
```

Run this before finishing an active workflow, or create a new workflow and update `gradle_workflow_id` if the host checks are already finished. The task and properties match the repository's current CI gate. Gradle provisions and tears down the disposable device; retain its reports under `app/build/outputs/androidTest-results/managedDevice/` and confirm that tests actually executed.

If KVM is unavailable or inaccessible, continue host validation and use the Ubuntu CI managed-device job for device evidence. Containers and WSL may need host virtualization configuration; their lack of acceleration is an infrastructure limitation, not an Ubuntu app-build incompatibility. Record that device tests were not locally executed.

## Windows release tooling and personal devices

The protected signing-password workflow in `scripts/build-signed-release.ps1` uses Windows DPAPI. Continue to use the [Windows release procedure](release-signing-and-recovery.md) for that protected credential. Ubuntu can assemble a release APK without it; release assembly alone does not produce a verified, permanently signed release.

Before installing or testing on a physical Android device from either host, follow [the personal-data preservation procedure](../docs/agents/physical-device-testing.md).
