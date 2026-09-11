# Code Review — Unmerged GitHub Issue Fixes

- **Date:** 2026-09-10
- **Fixed point:** `main` (`64d859339df02dcaaf8af958ed3285ed62624283`)
- **Compared branches:** `codex/p1-financial-integrity`, `codex/restore-recovery`, and `codex/ci-managed-device-gate`
- **Issue specifications:** GitHub issues [#9](https://github.com/AIF31/Financial-App/issues/9), [#10](https://github.com/AIF31/Financial-App/issues/10), [#11](https://github.com/AIF31/Financial-App/issues/11), [#13](https://github.com/AIF31/Financial-App/issues/13), [#19](https://github.com/AIF31/Financial-App/issues/19), and [#20](https://github.com/AIF31/Financial-App/issues/20)
- **Scope boundary:** committed branch objects only. The dirty working tree was not reviewed.
- **Method:** independent Standards and Spec reviews of each `git diff main...<branch>`. Findings shared by the stacked financial branches are listed once.

## Branch coverage

| Branch | Unique commits outside `main` | Referenced issues |
|---|---:|---|
| `codex/p1-financial-integrity` | 3 | #9, #10, #11 |
| `codex/restore-recovery` | 5, including the three commits above | #9, #10, #11, #13, #19 |
| `codex/ci-managed-device-gate` | 2 | #20 |

GitHub exposed only `main`; the reviewed feature branches are local. Open issues #12, #14–#18, #21, and #22 have no corresponding commit on these branches and therefore had no implementation diff to review.

## Standards

### S1. The CI gate includes a deliberately non-regression visual tour

**Severity:** Hard violation  
**Branch:** `codex/ci-managed-device-gate`  
**Locations:** `.github/workflows/android-ci.yml:72`; `app/src/androidTest/java/com/aif31/pocket/PocketUiUxReviewTourTest.kt:55`

The workflow runs the unfiltered `pixel6Api35DebugAndroidTest` task. That task includes `PocketUiUxReviewTourTest`, whose own contract says it is not part of the ordinary regression suite and should run through the recording script.

**Required correction:** exclude `@LargeTest` from the CI regression task or give the visual tour a dedicated task.

### S2. A composable duplicates the unassigned-funds domain calculation

**Severity:** Hard violation  
**Branches:** `codex/p1-financial-integrity`, inherited by `codex/restore-recovery`  
**Location:** `app/src/main/java/com/aif31/pocket/PocketsScreen.kt:55-60`

The composable calculates:

```kotlin
Math.addExact(
    Math.subtractExact(periodFundsMinor, allocatedMinor),
    releasedRolloverMinor,
)
```

This conflicts with `UI-UX-Design-Philosophy.md` §12: domain calculations do not belong in composables, and ledger rules should not be duplicated for UI convenience.

**Required correction:** expose the selected period's unassigned amount from ledger or domain state.

### S3. Runtime and backup validation duplicate monetary aggregation

**Severity:** Judgement call — Duplicated Code  
**Branches:** `codex/p1-financial-integrity`, inherited by `codex/restore-recovery`  
**Locations:** `RoomPocketLedger.kt:476-490`; `BackupCodec.kt:321-330`

Both paths independently partition expenses and refunds and calculate exact aggregates. Because they must enforce identical invariants, the copies can drift.

**Suggested correction:** use one domain aggregation/validation helper in both paths.

## Spec

### P1. Deleting a movement can commit an unrepresentable ledger state

**Severity:** High  
**Issue:** [#10](https://github.com/AIF31/Financial-App/issues/10)  
**Branches:** `codex/p1-financial-integrity`, inherited by `codex/restore-recovery`  
**Locations:** `RoomPocketLedger.kt:373-378`, `RoomPocketLedger.kt:510-513`

`DeleteMovement` deletes first and relies on `recalculateRolloverFrom` for validation. That helper returns immediately when the source is the last period. For example, budget `1`, expense `Long.MAX_VALUE`, and refund `Long.MAX_VALUE` is representable before deletion; deleting the expense leaves availability `1 - (-Long.MAX_VALUE)`, which overflows after the mutation commits.

This violates the requirement that an unrepresentable aggregate be rejected before mutation and leave the ledger unchanged.

### P2. Restore replacement and period catch-up are separate transactions

**Severity:** High  
**Issue:** [#13](https://github.com/AIF31/Financial-App/issues/13)  
**Branch:** `codex/restore-recovery`  
**Locations:** `BackupCodec.kt:105-144`; `PocketApp.kt:180-198`

The replacement transaction commits before the UI invokes `CatchUpPeriods`. Preflight does not simulate catch-up conversions. A stale backup whose next-period conversion overflows can replace the existing ledger and then fail catch-up, leaving no usable current period. Cancellation or recreation between the two operations has the same partial-completion risk.

This violates the requirement that replacement be one recoverable unit and that any validation or persistence failure preserve the previous ledger and current-period state.

### P3. An empty Pocket budget is silently saved as zero

**Severity:** Medium  
**Issue:** [#9](https://github.com/AIF31/Financial-App/issues/9)  
**Branches:** `codex/p1-financial-integrity`, inherited by `codex/restore-recovery`  
**Location:** `PocketsScreen.kt:556-564`

The editor maps blank input directly to `0L` and saves it without validation feedback. Issue #9 explicitly requires empty monetary input to produce validation feedback rather than silently becoming another value.

### P4. Restore persistence failures are classified as validation failures

**Severity:** Medium  
**Issue:** [#10](https://github.com/AIF31/Financial-App/issues/10)  
**Branches:** `codex/p1-financial-integrity`, inherited by `codex/restore-recovery`  
**Locations:** `BackupCodec.kt:142-143`; `Model.kt:228`

The restore catch block constructs `LedgerResult.Rejected` without `RejectionKind.PERSISTENCE`, so the default classification is validation. This violates the requirement to distinguish validation failures from persistence failures.

### P5. Document-operation UI coverage does not exercise the Android boundary

**Severity:** Medium  
**Issue:** [#19](https://github.com/AIF31/Financial-App/issues/19)  
**Branch:** `codex/restore-recovery`  
**Location:** `PocketAppHostFlowTest.kt:653-691`

The tests inject messages and retry callbacks directly into `PocketApp`; they do not drive `MainActivity` document results. There is no regression coverage for provider failure, cancellation, rotation, or exporting without a current period through the real document-operation boundary.

### P6. CI does not prove that the managed-device suite executed

**Severity:** Medium  
**Issue:** [#20](https://github.com/AIF31/Financial-App/issues/20)  
**Branch:** `codex/ci-managed-device-gate`  
**Locations:** `.github/workflows/android-ci.yml:72`, `.github/workflows/android-ci.yml:79`

The workflow trusts the Gradle exit code and only warns when report paths are absent. It does not assert a nonzero executed-test count, and the branch contains no evidence of the required passing run plus deliberate-failure propagation exercise. An empty or incomplete suite can therefore appear green.

## Confirmed coverage

- No Standards-only finding was identified in the restore-only commits beyond the shared findings above.
- No defect or scope creep was identified for issue #11.
- `git diff --check` passed for all three branch comparisons.
- Builds and tests were not executed as part of this read-only branch-object review.

## High-priority remediation status

- **P1 remediated in the working tree:** `DeleteMovement` validates the prospective movement set before deleting, including when the source is the last period. `deleting_a_refund_is_rejected_when_the_prospective_projection_overflows` verifies rejection and byte-for-byte preservation of the backup-visible ledger.
- **P2 remediated in the working tree:** backup preview and restore precompute the complete automatic period catch-up plan, including currency conversions and derived totals. Restore writes the replacement and catch-up plan in one Room transaction, rethrows cancellation for rollback, and serializes replacement attempts. `restore_preflight_rejects_catch_up_conversion_overflow_and_preserves_the_current_ledger` verifies preflight rejection and preservation of the existing current period.
- **Static verification:** `git diff --check` passes for the current working tree. Later remediation runs found the installed Python 3 launcher outside the sandbox and used the required compact-output Gradle wrapper successfully.

## Medium-priority remediation status

- **P3 remediated in the recovery working tree:** an empty Pocket budget now leaves the editor open with `Escribe un presupuesto válido`; it is no longer submitted as zero. The focused host regression passes.
- **P4 remediated in the recovery working tree:** restore commit exceptions return `RejectionKind.PERSISTENCE`, and the focused rollback/classification regression passes.
- **P5 remediated in the recovery working tree:** instrumented `MainActivity` coverage now drives the Android document-result boundary for onboarding provider failure, picker cancellation, no-current-period export failure, retry state, and rotation. All three focused tests pass on the Pixel 6 API 35 managed device.
- **P6 remediated in the isolated `codex/ci-managed-device-gate` worktree:** CI excludes the `@LargeTest` visual tour, fails when no non-skipped test was reported, and treats missing diagnostic artifacts as an error. The filtered suite passed with 15 executed tests; a temporary deliberately failing device test made `:app:pixel6Api35DebugAndroidTest` fail and was removed afterward.
- **Aggregate verification:** the complete `:app:testDebugUnitTest` suite passes after the medium-priority changes, both working-tree diffs pass `git diff --check`, and the edited CI workflow parses as valid YAML.

## Remediation order

1. Protect ledger integrity by fixing P1 and P2 with regression tests.
2. Correct P3 and P4 at their shared validation boundaries.
3. Add Android-boundary coverage for P5.
4. Make the CI suite selection and execution proof explicit for S1 and P6.
5. Move selected-period unassigned calculation out of Compose and consolidate duplicate validation logic.

## Summary

| Axis | Findings | Worst finding |
|---|---:|---|
| Standards | 3 | S1: CI executes a test explicitly excluded from the ordinary regression suite |
| Spec | 6 | P1/P2: ledger mutation or replacement can commit before all required validation succeeds |
