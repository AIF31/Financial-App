# M1 Financial-Period Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Pocket's period creation, rollover propagation, start-day transitions, and Pocket archival financially consistent across time.

**Architecture:** Keep `PocketLedger.execute(...)` as the external financial seam and deepen `RoomPocketLedger` so each command completes its own Room transaction plus downstream rollover recalculation. Add period-scoped Pocket state and explicit rollover-release rows rather than deriving historical eligibility or archival effects from the current global Pocket row. Keep lifecycle and Compose code as thin adapters over the ledger interface.

**Tech Stack:** Kotlin 2.x, Room 2.x with exported schemas, kotlinx-coroutines `runTest`, Robolectric host tests, AndroidJUnit4 migration/device tests, Jetpack Compose, JDK 17, repository Gradle wrapper.

**Spec:** `docs/product/FUTURE_UPDATES_DECISION_SPEC.md` (M1), with coverage requirements from `docs/testing/FUTURE_TEST_STRATEGY.md` and vocabulary from `CONTEXT.md`.

## Global Constraints

- Preserve the Pocket-only, private, single-user, offline-first model.
- Use `Asia/Riyadh` for budget-period dates and integer minor units for SAR.
- Periods are contiguous; historical edits must not mutate later new funds, Pocket budgets, or Movements.
- Snapshot rollover eligibility by Pocket and source period; preference changes affect the current/future snapshot only.
- A changed preferred start day creates one long transition ending at the first new-schedule boundary after the old schedule's next expected end.
- Archived Pockets retain history but cannot receive new budgets, Movements, templates, or rollover.
- Do not add notification ingestion or any M2-M5 feature.
- Run every Gradle command through `.agents/skills/gradle-run/scripts/gradle_run.py` with JDK 17 and the repository wrapper.
- Every behavior follows red, verified red, minimal green, verified green, then refactor.

---

### Task 1: Period construction rules

**Files:**
- Modify: `app/src/test/java/com/aif31/pocket/domain/DomainRulesTest.kt`
- Modify: `app/src/main/java/com/aif31/pocket/domain/DomainRules.kt`

**Interfaces:**
- Consumes: existing `BudgetCalendar.periodContaining(LocalDate)`.
- Produces: `BudgetCalendar.nextPeriodAfter(PeriodSchedule, preferredStartDay): PeriodSchedule` and `PeriodSchedule(start, endExclusive, configuredStartDay, isTransition)`.

- [x] **Step 1: Write failing table-driven calendar tests**

Add literal expectations for unchanged day 25, nearby changes 25→10 and 25→30, clamped month ends, and a December/January transition. The core assertions are:

```kotlin
assertEquals(
    PeriodSchedule(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 7, 10), 10, true),
    calendar.nextPeriodAfter(previous, preferredStartDay = 10),
)
assertEquals(
    PeriodSchedule(LocalDate.of(2026, 12, 25), LocalDate.of(2027, 2, 10), 10, true),
    calendar.nextPeriodAfter(yearBoundary, preferredStartDay = 10),
)
```

- [x] **Step 2: Verify red**

Run the wrapper-owned `:app:testDebugUnitTest --tests com.aif31.pocket.domain.DomainRulesTest`. Expected: compilation/test failure because `PeriodSchedule` and `nextPeriodAfter` do not exist.

- [x] **Step 3: Implement the minimal pure calendar rule**

Use the prior period's configured calendar to find its next expected end. If the preferred day is unchanged, end there. Otherwise find the first preferred-day boundary strictly after that old expected end and mark the new period as a transition.

```kotlin
data class PeriodSchedule(
    val start: LocalDate,
    val endExclusive: LocalDate,
    val configuredStartDay: Int,
    val isTransition: Boolean,
)

fun BudgetCalendar.nextPeriodAfter(previous: PeriodSchedule, preferredStartDay: Int): PeriodSchedule
```

- [x] **Step 4: Verify green and refactor**

Run the same targeted test, then `:app:testDebugUnitTest --tests com.aif31.pocket.domain.*`. Keep boundary calculation private to `BudgetCalendar`.

- [x] **Step 5: Commit**

Commit `test/domain` and `main/domain` as `feat: model long transition periods`.

### Task 2: Persist period-scoped Pocket state and explicit rollover releases

**Files:**
- Modify: `app/src/androidTest/java/com/aif31/pocket/data/FinanceDatabaseMigrationTest.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/FinanceDatabase.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/Model.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/BackupCodec.kt`
- Create through Room schema export: `app/schemas/com.aif31.pocket.data.FinanceDatabase/4.json`

**Interfaces:**
- Produces `PeriodPocketEntity(periodId, pocketId, rolloverEligible, retired)` and `RolloverReleaseEntity(periodId, pocketId, amountMinor)`.
- Extends `PeriodEntity`/`Period` with `isTransition` and `needsReview`.
- Extends backup version 3 with period Pocket rows and rollover releases while retaining restore support for versions 1 and 2 through defaulted DTO fields.

- [x] **Step 1: Write the failing 3→4 migration test**

Create a version-3 database containing two periods, active/archived Pockets, allocations, and Movements. Use reflection for the initial red to request `MIGRATION_3_4`, run the migration, and assert literal rows/columns:

```kotlin
assertEquals(4, database.version)
assertEquals(setOf("period-1:pocket-1", "period-2:pocket-1"), periodPocketKeys)
assertEquals(1, rolloverEligibleForPocket1)
assertEquals(0, rolloverReleaseCount)
assertEquals(0, transitionFlag)
```

- [x] **Step 2: Verify red**

Run the wrapper-owned `:app:pixel6Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aif31.pocket.data.FinanceDatabaseMigrationTest`. Expected: the new migration test fails at runtime because database version 4 / `MIGRATION_3_4` does not exist.

- [x] **Step 3: Add the version-4 schema and migration**

Migration SQL must:

```sql
ALTER TABLE periods ADD COLUMN is_transition INTEGER NOT NULL DEFAULT 0;
ALTER TABLE periods ADD COLUMN needs_review INTEGER NOT NULL DEFAULT 0;
CREATE TABLE period_pockets (... PRIMARY KEY(period_id, pocket_id), ...);
INSERT INTO period_pockets (...) SELECT periods.id, pockets.id, pockets.rollover_enabled, 0 FROM periods CROSS JOIN pockets;
CREATE TABLE rollover_releases (... PRIMARY KEY(period_id, pocket_id), amount_minor INTEGER NOT NULL, ...);
```

Add DAO observation/query/upsert methods for both new tables and clear them in foreign-key-safe restore order.

- [x] **Step 4: Update backup round-trip behavior**

Encode version 3 with `periodPockets` and `rolloverReleases`. For older backups, derive period Pocket snapshots from each Pocket's stored rollover preference and use an empty release list. Validate nonnegative releases and valid period/Pocket foreign keys.

- [x] **Step 5: Verify green and schema export**

Run `:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest`, and the managed-device migration class. Confirm `4.json` is generated and tracked.

- [x] **Step 6: Commit**

Commit schema, migration, models, codec, and migration tests as `feat: persist period Pocket accounting state`.

### Task 3: Transactional catch-up and lifecycle integration

**Files:**
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/Model.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt`
- Modify: `app/src/main/java/com/aif31/pocket/MainActivity.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketApp.kt`
- Modify: `app/src/main/java/com/aif31/pocket/ui/ProductionDashboard.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketsScreen.kt`

**Interfaces:**
- Adds `LedgerCommand.CatchUpPeriods(preferredStartDay: Int)` and `LedgerCommand.MarkPeriodReviewed(periodId: String)`.
- `LedgerState.currentPeriod` is null while a nonempty ledger is behind today's date; UI displays an updating state until catch-up completes.
- Only the final catch-up-created current period has `needsReview = true`.

- [x] **Step 1: Write failing zero/one/many/idempotence tests**

Use a mutable `Clock` and a real in-memory Room database. Assert zero missing periods leaves IDs unchanged; one adds one period; many adds every contiguous period; a repeated call leaves the full period list unchanged.

```kotlin
assertEquals(listOf(
    LocalDate.of(2026, 1, 25),
    LocalDate.of(2026, 2, 25),
    LocalDate.of(2026, 3, 25),
    LocalDate.of(2026, 4, 25),
), state.periods.map { it.start })
assertEquals(listOf(false, false, false, true), state.periods.map { it.needsReview })
```

- [x] **Step 2: Verify red**

Run the targeted host ledger test. Expected: unresolved `CatchUpPeriods` or assertion failure because only one manually requested period can be created.

- [x] **Step 3: Implement catch-up inside one Room transaction**

Loop from the latest period while `today >= endExclusive`, constructing each successor sequentially. Copy new funds and budgets, create period Pocket snapshots only for non-archived Pockets, calculate rollover from the immediately previous source period, and set review only on the resulting current period. Repeated and concurrent calls must observe the already inserted unique start and make no duplicate.

- [x] **Step 4: Add launch/resume adapter and review UI**

In `MainActivity`, launch catch-up during `onCreate` and `onResume` using `preferences.state.first().futurePeriodStartDay`. In Compose, wait when onboarding is complete but `currentPeriod == null`. Show a current-period review banner with a route to Pockets and an explicit `MarkPeriodReviewed` action; do not block quick Movement capture after catch-up.

- [x] **Step 5: Verify green**

Run the targeted host tests, then `:app:testDebugUnitTest`. Add a Robolectric host-flow assertion that the shortcut Movement flow opens after catch-up rather than against a stale period.

- [x] **Step 6: Commit**

Commit as `feat: catch up missing budget periods`.

### Task 4: Cascade rollover through all later periods

**Files:**
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt`

**Interfaces:**
- Adds one private transaction-local implementation function: `recalculateRolloverFrom(sourcePeriodId: String)`.
- Keeps later `newFundsMinor`, `budgetMinor`, and Movement rows byte-for-byte unchanged while updating only materialized incoming `rolloverMinor`.

- [x] **Step 1: Write failing cascade tests**

Create three later periods with distinct new-fund values, Pocket budgets, and Movement IDs. Edit a first-period Movement and assert rollover changes in every later period while snapshots of later funds, budgets, and Movements remain equal. Add separate literal tests for refund-only availability with no allocation row and negative availability.

```kotlin
assertEquals(listOf(12_000L, 17_000L, 22_000L), laterRollover)
assertEquals(laterFundsBefore, laterFundsAfter)
assertEquals(laterBudgetsBefore, laterBudgetsAfter)
assertEquals(laterMovementsBefore, laterMovementsAfter)
assertEquals(4_000L, refundOnlyRollover)
assertEquals(0L, negativeAvailabilityRollover)
```

- [x] **Step 2: Verify red**

Run only the new host tests. Expected: current code leaves later materialized rollover stale and omits the refund-only Pocket.

- [x] **Step 3: Implement minimal recalculation**

For each adjacent source/target period pair, calculate source availability from budget + incoming rollover − expenses + refunds. Apply the source period's `rolloverEligible` snapshot, clamp negative availability to zero, and update only the target allocation's rollover column. Invoke from allocation changes, Movement insert/update/move/delete/restore, current-period eligibility changes, and archival changes; for a moved Movement start at the earlier old/new period.

- [x] **Step 4: Verify green and mutation cases**

Run the targeted host class and ensure tests fail if eligibility is read from `PocketEntity`, if refunds are ignored, or if recalculation stops after one successor.

- [x] **Step 5: Commit**

Commit as `feat: cascade historical rollover corrections`.

### Task 5: Enforce archival accounting invariants

**Files:**
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/Model.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketsScreen.kt`
- Modify: `app/src/main/java/com/aif31/pocket/ProductionExpense.kt`
- Modify: `app/src/main/java/com/aif31/pocket/SettingsScreens.kt`

**Interfaces:**
- `PocketPeriodSummary` exposes `rolloverEligible`, `retiredThisPeriod`, and `rolloverReleasedMinor`.
- `ArchivePocket` rejects with a message listing every active dependent recurring template.
- Current-period archive zeroes that Pocket's budget and incoming rollover, records the released rollover amount, and marks its period row retired.

- [x] **Step 1: Write failing archive behavior tests**

Cover: two named active templates block archival; archived templates do not block; budget returns to unassigned; positive incoming rollover becomes one explicit release; expense/refund rows remain; the current retired summary is read-only; next period has no row for the archived Pocket; and new allocations, Movements, templates, and rollover are rejected/absent.

```kotlin
assertTrue((blocked as LedgerResult.Rejected).message.contains("Teléfono"))
assertTrue(blocked.message.contains("Suscripción"))
assertEquals(30_000L, state.unallocatedMinor)
assertEquals(5_000L, retired.rolloverReleasedMinor)
assertTrue(retired.retiredThisPeriod)
assertFalse(nextSummaries.any { it.pocket.id == pocketId })
```

- [x] **Step 2: Verify red**

Run the archive tests. Expected: current implementation archives blindly, retains the allocation, and permits new financial references.

- [x] **Step 3: Implement archive transaction and validation**

List active templates before any write. For a valid archive, insert/update one rollover release, zero budget/rollover, mark the current period Pocket retired, archive the global Pocket, and cascade later materialized rollover. Reject archived Pocket IDs in `SetAllocation`, `AddMovement`, and `UpsertTemplate`.

- [x] **Step 4: Implement read-only retired UI**

Show a `Retirado este periodo` section only for the current period, retaining spending/refund values but no edit, allocation, Movement, template, reorder, or rollover controls. Keep historical visibility through period selection.

- [x] **Step 5: Verify green**

Run archive host tests and the complete host suite. Add a Robolectric semantics assertion for the retired label and absence of edit actions.

- [x] **Step 6: Commit**

Commit as `feat: enforce Pocket archival accounting`.

### Task 6: Transition reporting and milestone verification

**Files:**
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/Model.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt`
- Modify: `app/src/main/java/com/aif31/pocket/ui/ProductionDashboard.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketsScreen.kt`
- Modify: `docs/superpowers/plans/2026-08-31-m1-financial-period-integrity.md`

**Interfaces:**
- `LedgerState.comparisonMode` is `TOTAL_SPEND` for ordinary periods and `DAILY_PACE` for a transition.
- `LedgerState.previousPeriodComparisonMinor` contains the previous total or previous daily pace according to that mode.

- [x] **Step 1: Write failing reporting tests**

Assert an ordinary period compares the prior total, while a transition compares `previousNetSpend / previous.totalDays`. Add Compose assertions for `Periodo de transición` and `Ritmo diario del periodo anterior`.

- [x] **Step 2: Verify red**

Run the targeted ledger and host-flow tests. Expected: absolute prior spend is displayed for every period and no transition marker exists.

- [x] **Step 3: Implement minimal reporting state and UI**

Derive comparison mode from `currentPeriod.isTransition`; use integer minor-unit division by the prior period's actual day count. Mark transition chips and dashboard copy without changing ordinary-period labels.

- [x] **Step 4: Run full fresh verification**

Through one compact Gradle workflow under JDK 17, run:

```text
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleRelease
:app:pixel6Api35DebugAndroidTest
```

Also run `git diff --check`, verify no `Thread.sleep` in `app/src/test`, inspect the version-4 schema, and compare every M1 acceptance item against concrete tests.

- [x] **Step 5: Request code review and address findings**

Dispatch the required read-only reviewer against base `68ae616` and the feature head. Fix every Critical/Important finding with a new red/green cycle and rerun the owning verification.

- [x] **Step 6: Record evidence and commit**

Check completed plan boxes, comment bounded verification summaries on the owning GitHub issue once external publication is explicitly approved, and commit as `test: verify M1 financial-period integrity`.

Post-review evidence: separate Standards and Spec reviews covered `68ae616...d56f5ab`. Validated findings were addressed with red/green coverage for retired rollover-release recalculation, successive catch-up review flags, current-period restoration of archived Pockets, backup relationship validation, catch-up after restore, and historical retired-Pocket visibility with expense/refund values. Backup export capture was made transactional as a behavior-preserving refactor. The suggestion to add rollover releases to Pocket-budget capacity was rejected because it conflicts with the Task 5 literal `unallocatedMinor == 30_000` acceptance value and the domain rule that Pocket budgets are portions of new funds. Compact Gradle workflow `105ce974a0b71fdb70f0dbee90ffb909` run `0021` passed the full unit, lint, debug/release assembly, and 12-test managed-device gate with no warning fingerprints. Schema v4 parsed, `git diff --check` passed, and `app/src/test` contains no `Thread.sleep`. No GitHub issue was created or commented because external publication has not been explicitly approved.
