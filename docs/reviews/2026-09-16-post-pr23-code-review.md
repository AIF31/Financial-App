# Code Review — Post-PR #23 Issue Fixes

- **Date:** 2026-09-16
- **Fixed point:** `main` at `a1ed2d3ff0ab4aa223fc187aa351ce1069bb5182`, merge commit for [PR #23](https://github.com/AIF31/Financial-App/pull/23)
- **Reviewed range:** `git diff main...HEAD`
- **Additional scope:** staged `.github/workflows/android-ci.yml` GPU setting
- **Issue specifications:** [#9](https://github.com/AIF31/Financial-App/issues/9), [#10](https://github.com/AIF31/Financial-App/issues/10), [#11](https://github.com/AIF31/Financial-App/issues/11), [#12](https://github.com/AIF31/Financial-App/issues/12), and [#20](https://github.com/AIF31/Financial-App/issues/20)
- **Conclusion:** #9, #10, and #11 look correct by static review. #12 and #20 remain incomplete against their acceptance criteria.

## Scope

The committed review range contains six post-merge commits and 13 changed files. The staged CI change adds:

```text
-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Generated `.gradle-user-home/` content and the untracked draft `docs/reviews/2026-09-11-final-completeness-review.md` were treated as evidence, not implementation changes.

## Standards

### S1. The implementation plan uses non-canonical domain wording

**Severity:** P3, documented-standard violation

**Location:** `docs/superpowers/plans/2026-09-11-open-issues-completion.md:255`

The phrase `Movement transfer between periods` conflicts with `CONTEXT.md`, where a Movement is an expense or refund and `transfer` is explicitly rejected terminology.

**Correction:** use `moving/editing a Movement between budget periods`.

### S2. Commit `9ddda44` combines unrelated behavior slices

**Severity:** P3, documented-standard violation

**Locations:** `.github/workflows/android-ci.yml:46`; `app/src/main/java/com/aif31/pocket/data/BackupCodec.kt:177`; `app/src/main/java/com/aif31/pocket/data/PeriodLedgerRules.kt:72`

The commit combines managed-device CI, transactional observation/export snapshots, and cross-currency projection validation. This conflicts with `UI-UX-Design-Philosophy.md:342`: commits should represent coherent behavior slices.

No runtime correction is required. Keep future CI, snapshot, and monetary-integrity changes in separate commits.

### S3. Prospective ledger collections form a data clump

**Severity:** P3, judgement call

**Location:** `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt:577`

`rolloverProjection` receives periods, period-Pocket snapshots, movements, allocations, and releases as parallel collections at seven call sites. A caller can accidentally combine collections from different snapshots.

**Correction:** introduce one prospective-ledger value only when the next change touches this interface; no standalone refactor is required.

### S4. Net-spend aggregation is duplicated

**Severity:** P3, judgement call

**Locations:** `app/src/main/java/com/aif31/pocket/data/PeriodLedgerRules.kt:80`; `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt:605`

Both paths independently filter expenses and refunds, exact-sum each group, and subtract the totals. The copies can drift between validation and rollover calculation.

**Correction:** reuse one domain calculation when either path next changes.

## Spec

### P1. Managed-device tests are not a required merge gate

**Severity:** P1

**Issue:** [#20](https://github.com/AIF31/Financial-App/issues/20)
**Location:** `.github/workflows/android-ci.yml:46`

The workflow defines `managed-device-tests`, but GitHub reported `main` as unprotected on 2026-09-16 and the repository's only ruleset, `Basic`, as disabled. A failing device job therefore does not block merging.

This misses the requirement that the API 35 Pixel 6 suite run as a required PR or protected-branch gate.

**Completion criterion:** enable a branch rule or ruleset that requires the managed-device job, then confirm a failing run blocks merge.

### P2. Issue #12's concurrency regression matrix is absent

**Severity:** P2

**Issue:** [#12](https://github.com/AIF31/Financial-App/issues/12)
**Locations:** `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt:35`; `app/src/main/java/com/aif31/pocket/data/BackupCodec.kt:178`; `docs/superpowers/plans/2026-09-11-open-issues-completion.md:645`

The implementation adds transaction boundaries for ledger observations and CSV export, but no test deliberately interleaves a write with either read path. Coverage is also absent for the required initial-load, repeated-invalidation, period-catch-up, restore, and export-during-write cases.

The implementation is plausible, but the acceptance criteria remain partial and unproven.

**Completion criterion:** deterministic tests pause a write inside a transaction and prove that every observed state and CSV document comes entirely from the pre-write or post-write snapshot, never a mixture.

### P3. The final issue #20 pass/failure exercise is unverified

**Severity:** P2

**Issue:** [#20](https://github.com/AIF31/Financial-App/issues/20)
**Locations:** `docs/superpowers/plans/2026-09-11-open-issues-completion.md:406`; `.github/workflows/android-ci.yml:76`

The plan's passing-suite and deliberate-failure steps remain unchecked. The exact managed-device command also contains a staged GPU change, so no GitHub workflow run validates the final configuration.

**Completion criterion:** commit the final workflow, record one passing managed-device run, temporarily introduce a failing device test, confirm the job fails and retains diagnostics, then remove the deliberate failure.

## Confirmed coverage

- No high-confidence incorrect behavior or scope creep was found for issues #9, #10, or #11.
- Prospective monetary validation is applied before the reviewed ledger mutations.
- Rollover releases are exposed through domain-owned per-period summaries rather than recomputed in Compose.
- Transactional observation and CSV boundaries are present, subject to the missing concurrency evidence above.
- `git diff --check main...HEAD` and `git diff --check HEAD` passed.

## Verification limits

Gradle tests were not rerun during this review. The repository's `gradle-run` procedure requires a `python3` executable for its compact-output wrapper, and that prerequisite was unavailable. Direct Gradle execution was intentionally not substituted.

The successful Android CI run on PR #23's head predates the post-merge managed-device workflow and does not validate the reviewed CI changes.

## Summary

| Axis | Findings | Worst finding |
| --- | ---: | --- |
| Standards | 4 | S1/S2: documented terminology and coherent-commit violations |
| Spec | 3 | P1: the managed-device job does not currently block merges |
