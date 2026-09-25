# Changelog

Notable repository changes are recorded here for future maintainers.

## 2026-09-25

### App version policy

- Advanced the development build to `1.0.1` (`versionCode` 2) after merged PR #26. Future pull requests changing app, test, build, or project script files must increase both values by exactly one patch/code step; documentation-only pull requests are exempt. The required Android checks job validates the change before merge. The published APK remains `1.0.0` until a new release is signed and published.
- The version rule passed its self-check and comparison against the previous `main` commit. The physical test record includes the focused visual-tour navigation fix and its isolated passing rerun; the full physical suite was not rerun after that fix. The local Graphify update command could not start because its configured Python interpreter is unavailable.

### Local Codex and Graphify files

- Ignored generated `graphify-out/` data and local `.codex/` additions while keeping `.codex/config.toml`, `Info/`, and Android CI versioned. Verified the ignore rules and that Codex CLI can read the local Graphify skill.
- Kept 36 Android and agent workflow skills versioned, removed 16 optional skill directories and the local Skills Guide from Git's index without deleting local copies, and reduced `skills-lock.json` to the retained set.
- Scanned the public `main` tree for credential files and key signatures; none were found. The `v1.0.0` APK predates the current `main` by 40 commits.

### Closed-issues implementation review

- Reviewed the current `e58b14f` checkout against closed GitHub issues #1 and #9–#22 along independent standards and spec axes; recorded findings in `docs/audits/2026-09-25-closed-issues-final-implementation-review.md`.
- Found two remaining #1 product gaps: new foreign expenses cannot use an offline manual SAR equivalent, and historical Pocket budgets cannot be edited. Found two documented UI-standard breaches and one lower-confidence code smell.
- Verified the exact committed head's Android CI run `36047162329` passed both jobs. This was a static review; no local Gradle or physical-device tests were run. The existing maximum-font and TalkBack physical-release gaps remain open.

### Latest merged PR review

- Reviewed merged PR #26 specifically against issues #14, #15, #16, #17, #21, and #22; recorded separate Standards and Spec findings in `docs/audits/2026-09-25-pr26-merged-review.md`.
- Found a test-only production database opener, a lower-confidence string-operation smell, an incomplete #17 restore path for pre-created later periods, and partial #22 large-font coverage. Earlier #1 findings were outside this PR diff.
- Confirmed GitHub `main` at merge commit `f35f8c3`, the PR diff, the passing CI run for `e58b14f`, and a clean `git diff --check` on the PR range. No local Gradle or physical-device tests were run for this review.

## 2026-09-24

### Remaining issues in [PR #26](https://github.com/AIF31/Financial-App/pull/26)

- #14: Expense drafts keep a stable submission ID across repeated taps, failure, retry, and lifecycle changes, preventing duplicate Movements.
- #15 and #16: Portable backup version 5 includes the future period start day and reminder time. Replacement restore has a safety-export step and persistent plaintext warning; restored reminders require confirmation on the new device. Settings reports permission, channel, and scheduler status with retry.
- #17: Archived Pockets remain in the durable catalog and can be restored; history filters keep Pocket identity across period advances.
- #21 and #22: Added populated migration fixtures from schema version 1 through 7 and API 35 managed-device tests for large fonts, compact/wide/landscape layouts, keyboard saves, restore confirmation, permission denial, and scheduler retry.
- Updated the release recovery procedure for version 5 backups. Corrected host-test feedback assertions and kept document feedback in its intended screen while ledger state loads.

### Remaining-issue verification

- The full local gate passed 205 host tests and 29 Pixel 6 API 35 managed-device tests with no failures or skips, plus lint and debug/release builds. No personal Android device was used.
- All checks passed on commit `95bd018`, including [Android CI](https://github.com/AIF31/Financial-App/actions/runs/36046233080) and [CodeQL](https://github.com/AIF31/Financial-App/actions/runs/36046227335). PR #26 awaits merge; its issue-closing references will close #14, #15, #16, #17, #21, and #22 when merged.

### Changed

- [PR #24](https://github.com/AIF31/Financial-App/pull/24) merged the fixes for foreground date refresh (#18), exact-file backup share retry (#19), and managed-device CI reliability (#20) into `main`.
- Foreground checks now continue at most 60 seconds apart while the app is visible and recover after invalid clock signals. Backup share retry keeps the prepared file across recreation and prepares a new one if it is missing.
- Activated the `main` ruleset requiring `Android checks` and `Pixel 6 API 35 device tests`. Issues #10, #12, #13, #18, #19, and #20 are closed.

### Verification

- The local Gradle gate passed 191 host tests and 19 Pixel 6 API 35 managed-device tests, plus lint and debug/release builds, with no failures or skips. No physical device was used.
- [PR #24 CI](https://github.com/AIF31/Financial-App/actions/runs/35919284476) passed both required jobs. A temporary [failing-test PR #25](https://github.com/AIF31/Financial-App/pull/25) proved that a device-test failure blocks merging and uploads diagnostics; it was then closed and its branch deleted.

## 2026-09-23

### Documentation

- Added a dated audit for the post-consolidation standards and issue-spec review, including the then-open #18, #19, and #20 findings.
- Updated the root README to require reading and updating this changelog and maintaining dated audits after code reviews and fixes.

### Verification

- Checked the reviewed commit range, source locations, and GitHub's `main` branch protection and ruleset status at the time. No Gradle or device tests were run for this documentation change.
- A consolidated-main CI run passed. At this review, a deliberate failing-device-test exercise was unverified, `main` had no active required-check rule, and the `Basic` ruleset was disabled. The required-check rule and failure exercise were completed on 2026-09-24.

## 2026-09-17

### Added

- Added deterministic host regression coverage for coherent Room snapshots in `SnapshotConsistencyHostTest`.
- Covered initial state loading, repeated invalidations, period catch-up, backup restore, failed and canceled writes, and CSV export during an in-flight write.
- Used separate reader and writer Room connections in WAL mode so tests control transaction interleavings without sleeps or production-only test hooks.

### Changed

- Updated the operational README to link the changelog and name Pixel 10 Pro as the physical release-test device while retaining the managed-device automation workflow.
- Replaced the non-canonical phrase “Movement transfer between periods” with “moving/editing a Movement between budget periods” in the open-issues implementation plan.
- Added a README workflow rule requiring future contributors and agents to keep this changelog accurate, including verification limits and unresolved blockers.

### Verification

- Documentation links and the Pixel 10 Pro testing guidance were reviewed against the repository's physical-device preservation procedure; no device test was run for this documentation-only change.
- Static diff checks and independent standards/spec reviews passed.
- Gradle host tests and the managed-device pass/failure exercise were not run because the required `python3` compact-wrapper prerequisite was unavailable.

### External work identified at the time (resolved 2026-09-24)

- GitHub `main` had no active required-check rule and the `Basic` ruleset was disabled. A rule requiring the `Pixel 6 API 35 device tests` check was needed before issue #20 could be considered complete.
- The managed-device workflow needed a recorded passing run and deliberate-failure propagation exercise.
