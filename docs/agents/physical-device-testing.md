# Physical Android device testing

Use the declared Gradle Managed Device for repeatable instrumentation. Treat a
persistent or personal phone as production data, not as a Gradle test target.

## Before a physical-device run

1. Confirm that physical hardware is required for the acceptance claim. Prefer
   a disposable managed device whenever it can answer the same question.
2. Audit every selected instrumentation method and every helper it reaches.
   A personal-device allowlist may contain only tests that cannot clear Room,
   delete a database, clear preferences, uninstall a package, replace app-owned
   files, or otherwise reset persistent state.
3. Confirm the exact device serial with `adb devices -l`, then pass that serial
   to every ADB command with `adb -s <serial> ...`.
4. Confirm the production and test application IDs from the built artifacts or
   installed-package metadata before installing or launching them.
5. Stop and use a disposable managed device if the selected test's storage
   behavior or target package cannot be proven safe.

## Build, install, and run

1. Build APKs only through `.agents/skills/gradle-run/scripts/gradle_run.py`.
2. Update the production package in place with
   `adb -s <serial> install -r <app-apk>`.
3. Install the test package separately with
   `adb -s <serial> install -r -t <test-apk>`.
4. Run only the audited allowlist directly with
   `adb -s <serial> shell am instrument -w -r ...`.
5. Determine test results from instrumentation status: `-1` is success and
   `-2` is failure. The ADB shell process exit code alone is not a test result.

Gradle connected-device tasks, Gradle device providers targeting a personal
phone, production-package uninstall, and package-data clearing are outside the
personal-device procedure. In particular, do not run `connectedAndroidTest`,
`connectedDebugAndroidTest`, `installDebug` workflows with cleanup,
`adb uninstall <production-package>`, or `pm clear <production-package>` on a
personal phone.

## Preservation checks

Non-reversible database, WAL, SHM, and preference fingerprints can detect an
unexpected mutation without reading personal values. They are evidence only,
not backups, and never make a destructive test safe. Compare the same protected
file set before and after the run, restore every animation setting that was
changed, and leave the production application installed.

If preservation cannot be guaranteed at any point, stop physical testing and
continue on the disposable managed device.
