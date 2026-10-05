# Reliability and performance review (2026-10-05)

## Scope

A whole-app review of `main` at `352e2ed` (1.0.7) covering the ledger math, loading time, Android practices, and the test suite. The branch advances the development version to 1.0.8/9. Changes are in five commits: ledger fixes, ledger performance, state off the main thread, cleanup, and test cleanup.

The new tests drive the same public seams as the existing tests: `PocketLedger` (`execute`, `state`, backup) and `PeriodInsights`.

## Findings and fixes

1. **Archiving destroyed funds.** `archivePocket` released `min(incoming rollover, positive availability)` and zeroed the Pocket's budget and rollover. When the Pocket had spent past its own budget, the difference vanished from the totals: budget 2,000 and rollover 10,000 with 5,000 spent released 7,000 instead of 10,000, so unassigned funds plus Pocket availability fell by 3,000. A later historical edit ran `rolloverProjection`, which releases the whole rollover, so the same ledger reported two different totals depending on history. `CONTEXT.md` defines a rollover release as reclassifying availability without creating funds, and the 2026-08-31 plan says archiving records the zeroed incoming rollover. The archive now releases the whole incoming rollover.
   - Test: `LedgerInvariantsHostTest.archiving_a_pocket_moves_its_whole_budget_and_rollover_to_unassigned_funds` failed with 7,000 before the fix.
2. **Archiving orphaned later-period Movements.** Archiving deletes the Pocket's snapshots and allocations in pre-created later periods, but its Movements there stayed. They were then counted in no period's totals, and the next export failed its own restore validation ("Movimiento sin estado de Pocket por periodo"). Archiving is now rejected while such Movements exist, as active templates already block it. `RestoreMovement` (undo after delete) skipped the snapshot check that `AddMovement` makes, and could recreate the same orphan; it now requires the Pocket's snapshot in that period. A retired snapshot is accepted, because retired Pockets still count their own Movements.
   - Tests: `PocketLedgerHostBehaviorTest.archiving_is_blocked_while_the_pocket_has_movements_in_a_later_period` and `undoing_a_delete_is_rejected_once_the_pocket_is_gone_from_that_period` failed before the fixes. The random-sequence test found the orphan first (seed 5, step 67).
3. **State built on the main thread.** `RoomPocketLedger.state` ran `buildState` in the collector's context. `PocketApp` collects on the main thread, so every write rebuilt all summaries there. State is now built on a background dispatcher (`computeDispatcher`, `Dispatchers.Default` by default) and conflated, so only the newest snapshot reaches the UI.
4. **Repeated work per command.** Each command validated a prospective rollover projection, discarded it, wrote, then re-read every table in `recalculateRolloverFrom` and projected again. It also rewrote every later allocation even when unchanged. Commands now write the projection they validated, and only the allocations that changed. Atomicity still comes from the Room transaction: the ten existing "rejected without mutating" tests and the random test's unchanged-state check after each rejection pass.
5. **Per-period refiltering.** `buildState`, `validateTotals`, `rolloverProjection`, and catch-up filtered all Movements for every period, and again for every Pocket. `MovementSpend` now groups them by period and Pocket once, with the same overflow-checked sums.
6. **Duplicated period creation.** `PeriodLedgerRules.catchUp` and `RoomPocketLedger.createPeriodAfter` each had a copy of next-period creation, and the copies differed on whether a retired source snapshot passes rollover. Both now call `PeriodLedgerRules.successor`, which uses the same eligibility rule as the rollover projection.
7. **Launch-time work.** Catch-up read the whole ledger on every launch even when no period was due, and always triggered a second state build. It now returns after reading `periods` when no period is due and no stale review flag needs clearing. A date change, rather than every catch-up call, triggers a rebuild without a ledger change.
8. **Dead and inconsistent code.** `LedgerState.projectionMinor` still extrapolated future-dated spending, the error PR #34 fixed in `PeriodInsights`; no screen read it. It was removed together with `rolloverTotalMinor`, `currentInstantMillis`, `ManualFx`, eleven unused `observe*` DAO queries, and `allocated()`.

## Android practices

- `PocketApp` calls `ReportDrawnWhen` once onboarding or the current period can be shown, so time to full display is measurable.
- Release builds shrink resources. `aapt2` confirmed that every drawable and XML resource the app references is kept. The APK goes from 2,664,389 to 2,448,493 bytes.
- Movements filtering and grouping, and "Comparar periodos", are remembered for unchanged inputs instead of being recomputed on each recomposition.
- Not kept: an app baseline profile with wildcard rules for `com.aif31.pocket`. It compiled into the APK, but after the profile was applied, the median time to full display was 591 ms against 599.5 ms without it, with overlapping middle-50% ranges (577–606 ms vs. 577–608 ms, n = 24 each). The library profiles that Compose and other AndroidX libraries ship are already in the APK.

## Measurements

Host measurements used a synthetic ledger of 60 periods, 10 Pockets, and 7,200 Movements, run in Robolectric on JDK 17. The probe was temporary and is not committed.

| Operation | Before | After |
| --- | --- | --- |
| Build ledger state | 130 ms | 75 ms (about 52 ms is SQLite reads) |
| Save an expense in the current period | 130–150 ms | 67 ms |
| Save an expense in the oldest period | 230 ms | 76 ms |
| Catch-up with no period due | 45 ms | 0.7 ms |

Cold start used R8-minified, non-debuggable builds signed with a debug key. The emulator was a throwaway API 35 ATD AVD (4 cores) with the same ledger copied into app storage. Each row is 15 cold starts; times are medians, with the middle 50% in parentheses.

| Build and compile state | First frame | Fully drawn |
| --- | --- | --- |
| `main`, just installed (`verify`) | 346 ms | 678 ms (656–760) |
| Branch, just installed (`verify`) | 349 ms | 658 ms (647–677) |
| `main`, profiles applied (`speed-profile`) | 357 ms | 608 ms (588–679) |
| Branch, profiles applied (`speed-profile`) | 369 ms | 598 ms (582–611) |

The cold-start gain is small on this host, but the slow tail is shorter. The larger change is that saves and rebuilds no longer run on the main thread. A release-like build was smoke-tested on the same emulator: all four tabs, the expense form, the "Nuevo gasto" action, and saving an expense, with no crash in logcat.

## Test suite

- Added `LedgerInvariantsHostTest`. Six seeded sequences of 120 random commands check the following after every command:
  - Each Pocket's expenses, refunds, net spend, and availability against its Movements.
  - That budgets stay within new funds.
  - Unassigned funds.
  - Rollover between adjacent periods, converted at frozen boundaries.
  - That every Movement is counted in its period.
  - The daily curve against net spend.
  - That archiving conserves funds.
  - That rejected commands change nothing.

  At the end, it checks that catch-up is idempotent and that a backup round trip changes nothing. A one-off sweep of 60 seeds × 200 steps found nothing beyond findings 1 and 2.
- Removed `androidTest/.../PocketLedgerBehaviorTest`. It ran only ledger checks on the emulator, and four of its five tests duplicated host tests: three were 87–88% identical text, and its backup test is a subset of `backup_restore_rejects_bad_relationships_atomically_and_csv_is_observable`. Its unique test moved to `PocketLedgerHostBehaviorTest`. `FinanceDatabaseMigrationTest` still covers device SQLite.
- Kept: the `PocketAppFlowTest` device copies of host UI tests. They run with `enableAccessibilityChecks()`, which is the accessibility gate in `docs/testing/FUTURE_TEST_STRATEGY.md`; a Robolectric pass cannot satisfy it. Also kept: the ten overflow-rejection tests. Each one covers a different command's prospective state.
- Removed the `ManualFx` test together with that unused class. The `projectionMinor` assertion now checks `PeriodInsights.projectedSpendMinor`.
- `SnapshotConsistencyHostTest.repeated_invalidation_never_emits_a_mixed_snapshot` paused the collector to make a rebuild start during an in-flight write. State is now read on the compute dispatcher, so the test pauses that dispatcher instead. With the read transaction removed from state building, it and three other tests in the class fail.
- Host tests run in up to four JVMs: 63 s to 33 s for the test task on this machine.

## Verification and limits

- Each regression test above failed before its fix and passed after it.
- Final runs went through `.agents/skills/gradle-run/scripts/gradle_run.py`:
  - `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest` passed: 286 host tests with no failures or skips, and no warning fingerprints.
  - `:app:pixel6Api35DebugAndroidTest` with CI's arguments passed 25 tests, none failed or skipped. The baseline on `main` was 30; five were the removed duplicates.
- Intermediate development runs called `./gradlew` directly.
- `scripts/check_version_increment.py` against `352e2ed` passed.
- The work was done in a native Linux worktree, not the Windows checkout named in `AGENTS.md`, because no Windows checkout is available on this machine.
- No physical-device test was run. Emulator timings depend on the host and indicate relative change only.
- Graphify is not installed here, so `graphify update .` was not run.
