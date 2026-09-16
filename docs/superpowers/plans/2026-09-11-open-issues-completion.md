# All Open GitHub Issues Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete, verify, document, and close GitHub issues #9 through #22 without regressing Pocket's financial, recovery, accessibility, or release guarantees.

**Architecture:** Treat merged pull request #23 and the preserved working tree as an audited baseline, then deliver the repository's PR groups A–K in dependency order. Keep financial parsing and arithmetic in shared domain boundaries, use transactional Room snapshots for observations and exports, model lifecycle-sensitive work as explicit state machines, and prove device behavior on the configured Pixel 6 API 35 Gradle Managed Device.

**Tech Stack:** Kotlin, Jetpack Compose, Room 7, DataStore Preferences, WorkManager, Kotlin coroutines and Flow, JUnit 4, Robolectric, AndroidX instrumentation/Compose testing, Gradle Managed Devices, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-11-open-issues-completion-design.md`

## Global Constraints

- GitHub issues #9 through #22 are the acceptance authority; every checkbox must map to source and fresh evidence before closure.
- Use the canonical terms in `CONTEXT.md`; do not introduce account, balance, transaction, or income language for Pocket concepts.
- Preserve ADR 0001: portable backups are versioned plaintext JSON and restore replaces rather than merges after preview and confirmation.
- Pocket remains offline-first and supports SAR, USD, and MXN accounting boundaries.
- Validate complete prospective monetary state before mutation; validation rejection must leave the logical ledger unchanged.
- Composables render immutable state and do not duplicate ledger arithmetic.
- Use the existing Navigation 3 roots and Material 3 interaction/accessibility contract.
- Every new behavior starts with a focused failing test, is observed failing for the expected reason, and receives the minimum implementation needed to pass.
- Never discard or overwrite the pre-existing working-tree edits. Stage only reviewed paths or reviewed hunks.
- Run physical-device tests only after reading `docs/agents/physical-device-testing.md`; this plan requires the configured managed device, not a personal phone.

---

### Task 1: Reconcile the merged and preserved baseline

**Issues:** #10, #12, #20 partial remediation already present

**Files:**
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`
- Preserve and review: `app/src/main/java/com/aif31/pocket/data/BackupCodec.kt`
- Preserve and review: `app/src/main/java/com/aif31/pocket/data/FinanceDatabase.kt`
- Preserve and review: `app/src/main/java/com/aif31/pocket/data/PeriodLedgerRules.kt`
- Preserve and review: `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt`
- Preserve and review: `.github/workflows/android-ci.yml`
- Modify later, do not stage yet: `docs/reviews/2026-09-11-final-completeness-review.md`

**Interfaces:**
- Consumes: merged PR #23 at `a1ed2d3ff0ab4aa223fc187aa351ce1069bb5182` and the current uncommitted diff.
- Produces: a committed, test-backed baseline where every preserved hunk is assigned to #10, #12, or #20 and no unrelated user work is lost.

- [ ] **Step 1: Record the exact starting state**

Run:

```powershell
git status --short --branch
git diff --stat
git diff -- .github/workflows/android-ci.yml app/src/main/java/com/aif31/pocket/data app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt
```

Confirm that the only modified tracked paths are the six preserved files listed above and that the draft review remains untracked.

- [ ] **Step 2: Refine the stale-backup comparison regression**

In `PocketLedgerHostBehaviorTest.kt`, make the currency-boundary fixture isolate comparison overflow rather than another aggregate:

```kotlin
@Test
fun a_currency_boundary_rejects_previous_spend_that_cannot_be_compared() = runTest {
    val clock = Clock.fixed(Instant.parse("2026-03-24T09:00:00Z"), zone)
    val ledger = RoomPocketLedger(database, clock, zone)
    ledger.execute(LedgerCommand.Initialize(0))
    val state = ledger.state.first { !it.needsOnboarding }
    val period = state.currentPeriod!!
    ledger.execute(LedgerCommand.AddMovement(
        id = "comparison-overflow",
        pocketId = state.pockets.first().pocket.id,
        type = MovementType.EXPENSE,
        accountingAmountMinor = Long.MAX_VALUE / 64,
        occurredAtUtcMillis = clock.millis(),
        localDate = LocalDate.of(2026, 3, 24),
    ))
    ledger.execute(LedgerCommand.ScheduleCurrencyChange(
        targetCurrency = SupportedCurrency.USD,
        rate = "128",
        effectiveDate = period.endExclusive,
        source = "COMPARISON_OVERFLOW_TEST",
    ))
    val before = ledger.exportBackup()

    val result = ledger.execute(LedgerCommand.CreateNextPeriod())

    assertTrue(result is LedgerResult.Rejected)
    assertEquals(before.decodeToString(), ledger.exportBackup().decodeToString())
}
```

- [ ] **Step 3: Run the refined focused test and confirm RED or existing GREEN intentionally**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.PocketLedgerHostBehaviorTest.a_currency_boundary_rejects_previous_spend_that_cannot_be_compared" --no-daemon
```

If the preserved production hunk is temporarily excluded, the test must fail because comparison conversion overflows after mutation planning. Restore the preserved hunk and require the test to pass. Do not delete pre-existing code merely to manufacture RED for work that predates this plan.

- [ ] **Step 4: Verify the preserved host remediation**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.PocketLedgerHostBehaviorTest" --no-daemon
git diff --check
```

Expected: the host class passes and `git diff --check` reports no errors.

- [ ] **Step 5: Validate the CI workflow syntax**

Run:

```powershell
ruby -e "require 'yaml'; YAML.load_file('.github/workflows/android-ci.yml'); puts 'android-ci.yml parsed'"
```

Expected: `android-ci.yml parsed`. If Ruby is unavailable, use the repository's installed YAML parser and record the exact replacement command in the review evidence.

- [ ] **Step 6: Commit the reconciled baseline without the draft review**

```powershell
git add .github/workflows/android-ci.yml app/src/main/java/com/aif31/pocket/data/BackupCodec.kt app/src/main/java/com/aif31/pocket/data/FinanceDatabase.kt app/src/main/java/com/aif31/pocket/data/PeriodLedgerRules.kt app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt
git commit -m "fix: reconcile ledger snapshot and CI remediation (#10 #12 #20)"
```

---

### Task 2: Verify the shared decimal-entry policy

**Issue:** #9, group A

**Files:**
- Modify: `app/src/test/java/com/aif31/pocket/domain/DomainRulesTest.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt`
- Modify: `app/src/test/java/com/aif31/pocket/ExpenseWorkflowHostTest.kt`
- Modify only if a failing test proves a gap: `app/src/main/java/com/aif31/pocket/domain/DomainRules.kt`
- Modify only if a failing test proves a surface bypass: `app/src/main/java/com/aif31/pocket/PocketApp.kt`
- Modify only if a failing test proves a surface bypass: `app/src/main/java/com/aif31/pocket/PocketsScreen.kt`
- Modify only if a failing test proves a surface bypass: `app/src/main/java/com/aif31/pocket/ProductionExpense.kt`
- Modify only if a failing test proves a surface bypass: `app/src/main/java/com/aif31/pocket/SettingsScreens.kt`

**Interfaces:**
- Consumes: `Money.parse(value: String, currencyCode: String): Money`.
- Produces: one explicit two-decimal input policy used by new funds, Pocket budgets, expenses, refunds, templates, and manual accounting amounts.

- [ ] **Step 1: Extend the domain policy regression**

Add these assertions to `money uses one explicit decimal input policy without changing signs`:

```kotlin
assertEquals(Money(1_200, "SAR"), Money.parse("12.00", "SAR"))
assertEquals(Money(1_200, "SAR"), Money.parse("12,00", "SAR"))
listOf("+", "-", ".50", "12.", "12,", "12.500", "+-12.50", "1_000.00").forEach { value ->
    assertThrows(IllegalArgumentException::class.java) { Money.parse(value, "SAR") }
}
```

- [ ] **Step 2: Run the domain test**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.domain.DomainRulesTest.money uses one explicit decimal input policy without changing signs" --no-daemon
```

Expected: PASS if PR #23 already implements the complete policy; otherwise the new assertion fails for the exact unsupported form.

- [ ] **Step 3: Extend the existing production-surface regressions**

In `PocketAppHostFlowTest.decimal_entry_accepts_typed_and_pasted_dot_or_comma_and_rejects_repeated_separators`, add:

```kotlin
compose.onNodeWithTag("period_funds").performTextReplacement("-30.75")
compose.onNodeWithText("Guardar fondos").performClick()
compose.waitUntilExactlyOneExists(hasText("Escribe fondos válidos"), 5_000)
assertEquals(3_075L, runBlocking { ledger.state.first().newFundsMinor })
```

In `ExpenseWorkflowHostTest.comma_decimal_is_saved_and_a_negative_sign_is_never_discarded`, add a successful save after restoring `12.50` and assert the persisted value:

```kotlin
compose.onNodeWithTag("movement_amount").performTextReplacement("12.50")
compose.onNodeWithTag("movement_save").assertIsEnabled().performClick()
compose.waitUntil(5_000) { runBlocking { ledger.state.first().movements.size == 1 } }
assertEquals(1_250L, runBlocking { ledger.state.first().movements.single().accountingAmountMinor })
```

In `PocketAppHostFlowTest.untouched_empty_allocation_shows_validation_without_saving`, retain the blank assertion, then enter `12,50`, save, and assert `budgetMinor == 1_250L`.

- [ ] **Step 4: Run the cross-surface test and observe RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.PocketAppHostFlowTest.decimal_entry_accepts_typed_and_pasted_dot_or_comma_and_rejects_repeated_separators" --tests "com.aif31.pocket.PocketAppHostFlowTest.untouched_empty_allocation_shows_validation_without_saving" --tests "com.aif31.pocket.ExpenseWorkflowHostTest.comma_decimal_is_saved_and_a_negative_sign_is_never_discarded" --no-daemon
```

Expected before any needed surface fix: FAIL at the bypassing form. If it passes against PR #23, record that the merged implementation already satisfies the additional coverage.

- [ ] **Step 5: Route any bypassing surface through `Money.parse`**

Use this form at each save boundary; never filter away sign or separator characters before validation:

```kotlin
val amountMinor = runCatching { Money.parse(input, currency.name).minor }.getOrNull() ?: run {
    error = "Escribe un importe válido"
    return@launch
}
```

Keep Pocket-budget copy as `Escribe un presupuesto válido` and require a positive amount only where the owning domain action forbids zero or negative values.

- [ ] **Step 6: Verify and commit group A**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.domain.DomainRulesTest" --tests "com.aif31.pocket.PocketAppHostFlowTest" --tests "com.aif31.pocket.ExpenseWorkflowHostTest" --no-daemon
git diff --check
git add app/src/main/java/com/aif31/pocket/domain/DomainRules.kt app/src/main/java/com/aif31/pocket/PocketApp.kt app/src/main/java/com/aif31/pocket/PocketsScreen.kt app/src/main/java/com/aif31/pocket/ProductionExpense.kt app/src/main/java/com/aif31/pocket/SettingsScreens.kt app/src/test/java/com/aif31/pocket/domain/DomainRulesTest.kt app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt app/src/test/java/com/aif31/pocket/ExpenseWorkflowHostTest.kt
git commit -m "test: prove shared decimal entry policy (#9)"
```

If production and tests already cover every criterion, update the evidence document instead of creating an empty commit.

---

### Task 3: Complete prospective monetary validation

**Issue:** #10, group B

**Files:**
- Modify: `app/src/main/java/com/aif31/pocket/data/PeriodLedgerRules.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/BackupCodec.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`

**Interfaces:**
- Consumes: `PeriodLedgerRules.validateTotals(...)`, `validateHistoricalComparisons(periods, movements)`, and `LedgerResult.Rejected(kind)`.
- Produces: the same prospective-state validation for add/edit/delete/undo, allocation, rollover, period creation/catch-up, conversion, percentage, backup preflight, and restore.

- [ ] **Step 1: Add the missing prospective-state matrix**

Add parameterized host cases that assert both rejection kind and unchanged backup:

```kotlin
private suspend fun assertRejectedWithoutLedgerMutation(operation: suspend (RoomPocketLedger) -> LedgerResult) {
    val ledger = RoomPocketLedger(database, clock, zone)
    val before = ledger.exportBackup()
    val result = operation(ledger)
    assertTrue(result is LedgerResult.Rejected)
    assertEquals(RejectionKind.VALIDATION, (result as LedgerResult.Rejected).kind)
    assertEquals(before.decodeToString(), ledger.exportBackup().decodeToString())
}
```

Use it for aggregate expense overflow, aggregate refund overflow, delete/undo overflow, moving/editing a Movement between budget periods, allocation-cap overflow, rollover conversion overflow, historical-comparison overflow, and percentage overflow.

- [ ] **Step 2: Run the new cases and observe RED for any uncovered path**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.PocketLedgerHostBehaviorTest" --no-daemon
```

Expected: any remaining post-mutation or unchecked path fails with changed backup or an uncaught arithmetic exception.

- [ ] **Step 3: Centralize complete-state validation before writes**

Keep the production boundary shaped as:

```kotlin
internal fun validateLedgerProjection(
    periods: List<PeriodEntity>,
    allocations: List<AllocationEntity>,
    movements: List<MovementEntity>,
    releases: List<RolloverReleaseEntity>,
) {
    periods.forEach { period -> validateTotals(period, allocations, movements, releases) }
    validateHistoricalComparisons(periods, movements)
}
```

Call it with the complete prospective lists before the first DAO insert, update, or delete in every affected transaction. `BackupCodec` must call the same helper on decoded entities before clearing current tables.

- [ ] **Step 4: Prove persistence failures remain distinguishable**

Retain `PocketLedgerHostBehaviorTest.restore_commit_failure_is_classified_as_persistence_and_rolls_back_replacement` with these assertions:

```kotlin
val result = target.restoreBackup(backup) as LedgerResult.Rejected
assertEquals(RejectionKind.PERSISTENCE, result.kind)
assertEquals(before.decodeToString(), target.exportBackup().decodeToString())
```

Keep arithmetic and malformed backup failures asserted as `VALIDATION`.

- [ ] **Step 5: Verify and commit group B**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.PocketLedgerHostBehaviorTest" --no-daemon
git diff --check
git add app/src/main/java/com/aif31/pocket/data/PeriodLedgerRules.kt app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt app/src/main/java/com/aif31/pocket/data/BackupCodec.kt app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt
git commit -m "fix: validate complete monetary projections before mutation (#10)"
```

---

### Task 4: Make rollover-release summaries domain-owned

**Issue:** #11, group C

**Files:**
- Modify: `app/src/main/java/com/aif31/pocket/data/Model.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketsScreen.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt`

**Interfaces:**
- Consumes: period summaries, allocations, and `RolloverReleaseEntity` rows.
- Produces: `LedgerState.unallocatedMinorByPeriod: Map<String, Long>` with current `unallocatedMinor` derived from the same map.

- [ ] **Step 1: Write the selected-period summary regression**

```kotlin
val stateAfterArchive = ledger.state.first {
    it.pockets.any { summary -> summary.pocket.id == pocket.id && summary.retiredThisPeriod }
}
assertEquals(35_000L, stateAfterArchive.unallocatedMinorByPeriod.getValue(current.id))
assertEquals(30_000L, stateAfterArchive.newFundsMinor)
assertTrue(
    ledger.execute(LedgerCommand.SetAllocation(current.id, otherPocket.id, 30_001))
        is LedgerResult.Rejected,
)
```

Add the assertions to the existing `archive_is_blocked_by_active_templates_then_releases_current_accounting_and_rejects_new_references` test after `otherPocket` is selected. Retain the existing negative/no-release and repeated-refresh cases.

- [ ] **Step 2: Run the regression and observe RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.PocketLedgerHostBehaviorTest.archive_is_blocked_by_active_templates_then_releases_current_accounting_and_rejects_new_references" --no-daemon
```

Expected: compile failure because `unallocatedMinorByPeriod` does not exist.

- [ ] **Step 3: Add period-owned unassigned state**

In `Model.kt`:

```kotlin
val unallocatedMinorByPeriod: Map<String, Long> = emptyMap()
```

In `RoomPocketLedger.buildState`, compute each period with checked arithmetic:

```kotlin
val unallocatedByPeriod = periods.associate { period ->
    val summaries = summariesByPeriod[period.id].orEmpty()
    period.id to Math.addExact(
        Math.subtractExact(period.newFundsMinor, summaries.map { it.budgetMinor }.sumMoneyExact()),
        summaries.map { it.rolloverReleasedMinor }.sumMoneyExact(),
    )
}
```

- [ ] **Step 4: Remove financial arithmetic from Compose**

Replace the allocated/released calculation in `PocketsScreen` with:

```kotlin
val unallocatedForPeriodMinor = selectedPeriod?.id
    ?.let(state.unallocatedMinorByPeriod::get)
    ?: state.unallocatedMinor
```

Keep display-only formatting in the composable.

- [ ] **Step 5: Verify archive, restore, history, and refresh behavior**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.PocketLedgerHostBehaviorTest" --tests "com.aif31.pocket.PocketAppHostFlowTest.historical_period_uses_its_snapshots_and_exposes_only_read_only_details" --no-daemon
git diff --check
```

- [ ] **Step 6: Commit group C**

```powershell
git add app/src/main/java/com/aif31/pocket/data/Model.kt app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt app/src/main/java/com/aif31/pocket/PocketsScreen.kt app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt
git commit -m "fix: expose rollover releases through ledger summaries (#11)"
```

---

### Task 5: Enforce managed-device tests in CI

**Issue:** #20, group D

**Files:**
- Modify: `.github/workflows/android-ci.yml`
- Modify: `docs/reviews/2026-09-11-final-completeness-review.md`
- Temporary and remove before commit: `app/src/androidTest/java/com/aif31/pocket/DeliberateCiFailureTest.kt`

**Interfaces:**
- Consumes: Gradle task `:app:pixel6Api35DebugAndroidTest` and `@LargeTest` exclusion.
- Produces: a required CI job that proves at least one non-skipped test ran and always retains managed-device diagnostics.

- [ ] **Step 1: Run the filtered managed-device suite from the reconciled source**

```powershell
.\gradlew.bat :app:pixel6Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest --no-daemon
```

Record the executed, passed, failed, and skipped counts and the report directory.

- [ ] **Step 2: Add a temporary deliberate failure**

Create this file only for the failure-propagation exercise:

```kotlin
package com.aif31.pocket

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeliberateCiFailureTest {
    @Test fun deliberate_failure_reaches_gradle() = fail("deliberate CI propagation proof")
}
```

- [ ] **Step 3: Prove the managed-device task fails**

Run the same Gradle command and require a nonzero exit with `deliberate CI propagation proof` in the result. Delete `DeliberateCiFailureTest.kt` immediately after recording the failure.

- [ ] **Step 4: Rerun the passing managed-device suite**

```powershell
.\gradlew.bat :app:pixel6Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest --no-daemon
```

Expected: zero failures and at least one executed non-skipped test.

- [ ] **Step 5: Confirm diagnostics paths and workflow semantics**

Verify that `.github/workflows/android-ci.yml` has no `continue-on-error`, applies `timeout-minutes` to the job and Gradle step, checks XML executed counts, uses `if: always()`, and uploads:

```text
app/build/reports/androidTests/managedDevice/
app/build/outputs/androidTest-results/managedDevice/
app/build/outputs/managed_device_android_test_additional_output/
```

- [ ] **Step 6: Commit group D evidence**

```powershell
git add .github/workflows/android-ci.yml docs/reviews/2026-09-11-final-completeness-review.md
git commit -m "ci: enforce Pixel 6 API 35 device tests (#20)"
```

---

### Task 6: Finish atomic restore and durable document-operation outcomes

**Issues:** #13 and #19, group E

**Files:**
- Create: `app/src/main/java/com/aif31/pocket/DocumentOperationState.kt`
- Create: `app/src/test/java/com/aif31/pocket/DocumentOperationStateTest.kt`
- Create: `app/src/test/java/com/aif31/pocket/SingleFlightTest.kt`
- Modify: `app/src/main/java/com/aif31/pocket/RecoveryViewModel.kt`
- Modify: `app/src/main/java/com/aif31/pocket/MainActivity.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketApp.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/BackupCodec.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt`
- Modify: `app/src/test/java/com/aif31/pocket/RecoveryInfrastructureTest.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt`
- Modify: `app/src/androidTest/java/com/aif31/pocket/PocketAppFlowTest.kt`

**Interfaces:**
- Consumes: serialized `RoomPocketLedger.restoreBackup`, `RestoreCandidateStore`, Android document contracts, and `BackupShareLauncher`.
- Produces: `DocumentOperationState` that retains exact retry intent and prepared artifact identity; restore replacement and catch-up remain one transaction.

- [ ] **Step 1: Write the operation-state tests**

```kotlin
@Test
fun share_retry_uses_only_the_exact_prepared_file() {
    val state = DocumentOperationState.failed(
        operation = DocumentOperation.SHARE,
        phase = DocumentOperationPhase.LAUNCH,
        preparedPath = "shared_backups/pocket-2026-09-11.pocketbackup",
        message = "No se pudo abrir el selector.",
    )
    assertEquals("shared_backups/pocket-2026-09-11.pocketbackup", state.preparedPath)
}

@Test
fun preparation_failure_retries_preparation_not_a_stale_file() {
    val state = DocumentOperationState.failed(
        operation = DocumentOperation.SHARE,
        phase = DocumentOperationPhase.PREPARE,
        preparedPath = null,
        message = "No se pudo preparar el backup.",
    )
    assertEquals(DocumentRetry.PrepareShare, state.retry)
}
```

- [ ] **Step 2: Run the operation-state tests and observe RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.DocumentOperationStateTest" --no-daemon
```

Expected: compile failure because the state model does not exist.

- [ ] **Step 3: Implement the explicit document state**

```kotlin
internal enum class DocumentOperationPhase { PICKER, READ, PREPARE, WRITE, LAUNCH, COMPLETE }
internal sealed interface DocumentRetry {
    data object PrepareShare : DocumentRetry
    data class LaunchPreparedShare(val path: String) : DocumentRetry
    data class Relaunch(val operation: DocumentOperation) : DocumentRetry
}
internal data class DocumentOperationState(
    val operation: DocumentOperation,
    val phase: DocumentOperationPhase,
    val message: String,
    val preparedPath: String? = null,
    val retry: DocumentRetry? = null,
    val canceled: Boolean = false,
) {
    companion object {
        fun failed(
            operation: DocumentOperation,
            phase: DocumentOperationPhase,
            preparedPath: String?,
            message: String,
        ) = DocumentOperationState(
            operation = operation,
            phase = phase,
            message = message,
            preparedPath = preparedPath,
            retry = when {
                operation == DocumentOperation.SHARE && preparedPath == null -> DocumentRetry.PrepareShare
                operation == DocumentOperation.SHARE -> DocumentRetry.LaunchPreparedShare(requireNotNull(preparedPath))
                else -> DocumentRetry.Relaunch(operation)
            },
        )
    }
}

internal class SingleFlight {
    private val mutex = Mutex()
    suspend fun <T> run(block: suspend () -> T): T? {
        if (!mutex.tryLock()) return null
        return try { block() } finally { mutex.unlock() }
    }
}
```

Persist the state's enum names, message, path, and cancellation flag as scalar `SavedStateHandle` entries, then reconstruct `DocumentOperationState`; do not place the data class itself in the Bundle. Clear the prepared path only after a new preparation starts or the user acknowledges completion.

- [ ] **Step 4: Split share preparation from chooser launch**

In `MainActivity`, use two operations:

```kotlin
private suspend fun prepareShareBackup(): File
private fun launchPreparedShare(file: File)
```

`prepareShareBackup` writes one `AtomicFile` and records its canonical path after `finishWrite`. `launchPreparedShare` accepts that exact file. Retry dispatches `PrepareShare` when no file exists and `LaunchPreparedShare(path)` only when that recorded file still exists. Remove newest-file selection.

- [ ] **Step 5: Add atomic restore/catch-up and single-flight tests**

Retain the existing `PocketLedgerHostBehaviorTest.restore_preflight_rejects_catch_up_conversion_overflow_and_preserves_the_current_ledger` and `restore_commit_failure_is_classified_as_persistence_and_rolls_back_replacement` tests. Add this focused gate test:

```kotlin
@Test
fun a_second_operation_is_rejected_while_the_first_is_in_flight() = runTest {
    val gate = SingleFlight()
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val first = backgroundScope.async {
        gate.run {
            entered.complete(Unit)
            release.await()
            "committed"
        }
    }
    entered.await()

    val second = gate.run { "duplicate" }

    assertNull(second)
    release.complete(Unit)
    assertEquals("committed", first.await())
}
```

Use `SingleFlight` in `RoomPocketLedger.restoreBackup`; map a null result to `LedgerResult.Rejected("Ya hay una restauración en curso")` without entering `BackupCodec.restore`.

- [ ] **Step 6: Drive the real Android document boundary**

Extend `PocketAppFlowTest` to launch `MainActivity` and cover provider read failure, picker cancellation, no-current-period export failure, exact share retry, rotation, and successful recovery. Assert visible messages with Compose semantics and use Espresso Intents for chooser/document contracts.

- [ ] **Step 7: Verify group E**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.DocumentOperationStateTest" --tests "com.aif31.pocket.SingleFlightTest" --tests "com.aif31.pocket.RecoveryInfrastructureTest" --tests "com.aif31.pocket.PocketLedgerHostBehaviorTest" --tests "com.aif31.pocket.PocketAppHostFlowTest" --no-daemon
.\gradlew.bat :app:pixel6Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aif31.pocket.PocketAppFlowTest --no-daemon
git diff --check
```

- [ ] **Step 8: Commit group E**

```powershell
git add app/src/main/java/com/aif31/pocket/DocumentOperationState.kt app/src/main/java/com/aif31/pocket/RecoveryViewModel.kt app/src/main/java/com/aif31/pocket/MainActivity.kt app/src/main/java/com/aif31/pocket/PocketApp.kt app/src/main/java/com/aif31/pocket/data/BackupCodec.kt app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt app/src/test/java/com/aif31/pocket/DocumentOperationStateTest.kt app/src/test/java/com/aif31/pocket/SingleFlightTest.kt app/src/test/java/com/aif31/pocket/RecoveryInfrastructureTest.kt app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt app/src/androidTest/java/com/aif31/pocket/PocketAppFlowTest.kt
git commit -m "fix: complete atomic recovery and document retry flows (#13 #19)"
```

---

### Task 7: Make observations coherent and foreground dates explicit

**Issues:** #12 and #18, group F

**Files:**
- Create: `app/src/main/java/com/aif31/pocket/ForegroundDateCoordinator.kt`
- Create: `app/src/test/java/com/aif31/pocket/ForegroundDateCoordinatorTest.kt`
- Modify: `app/src/main/java/com/aif31/pocket/MainActivity.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/Model.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/BackupCodec.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt`

**Interfaces:**
- Consumes: Room invalidation signals, `database.withTransaction`, injected `Clock`, and `CatchUpPeriods`.
- Produces: coherent `LedgerState` and CSV snapshots plus `ForegroundDateCoordinator.observe(date)` idempotency.

- [ ] **Step 1: Write deterministic snapshot interleaving tests**

```kotlin
@Test
fun state_and_csv_are_each_one_complete_room_snapshot() = runTest {
    val ledger = RoomPocketLedger(database, clock, zone)
    ledger.execute(LedgerCommand.Initialize(10_000))
    val initial = ledger.state.first { !it.needsOnboarding }
    val pocketId = initial.pockets.first().pocket.id
    ledger.execute(LedgerCommand.AddMovement(
        id = "interleaved",
        pocketId = pocketId,
        type = MovementType.EXPENSE,
        accountingAmountMinor = 1_000,
        occurredAtUtcMillis = clock.millis(),
        localDate = LocalDate.of(2026, 2, 26),
    ))
    val writerStarted = CompletableDeferred<Unit>()
    val allowCommit = CompletableDeferred<Unit>()
    val pocket = database.financeDao().pockets().single { it.id == pocketId }
    val writer = backgroundScope.launch(Dispatchers.IO) {
        database.withTransaction {
            database.financeDao().putPocket(pocket.copy(name = "Renamed Pocket"))
            writerStarted.complete(Unit)
            allowCommit.await()
        }
    }
    writerStarted.await()
    val csv = async { ledger.exportCsv().decodeToString() }
    allowCommit.complete(Unit)
    writer.join()

    val document = csv.await()
    assertTrue(document.contains("Renamed Pocket"))
    assertFalse(document.contains(pocket.name))
    val state = ledger.state.first { it.movements.any { movement -> movement.id == "interleaved" } }
    assertEquals("Renamed Pocket", state.movements.first { it.id == "interleaved" }.pocketName)
}
```

Add matching initial-load, repeated-invalidation, failed-write, catch-up, and restore cases. Each assertion permits the complete before or complete after version, never a mixed version.

- [ ] **Step 2: Run snapshot tests and observe RED against any independent read path**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.PocketLedgerHostBehaviorTest.state_and_csv_are_each_one_complete_room_snapshot" --no-daemon
```

- [ ] **Step 3: Use transactions as the only snapshot boundary**

Keep state construction shaped as:

```kotlin
database.invalidationTracker.createFlow(
    "periods", "pockets", "allocations", "period_pockets", "rollover_releases",
    "payment_methods", "movements", "recurring_templates", "pending_currency_change",
    "ledger_preferences", "movement_suggestions", emitInitialState = true,
).map {
    val snapshot = database.withTransaction {
        LedgerSnapshot(
            dao.periods(), dao.pockets(), dao.allocations(), dao.periodPockets(), dao.rolloverReleases(),
            dao.paymentMethods(), dao.movements(), dao.templates(), dao.pendingCurrencyChange(),
            dao.ledgerPreferences(), dao.pendingMovementSuggestions(),
        )
    }
    buildState(
        snapshot.periods, snapshot.pockets, snapshot.allocations, snapshot.periodPockets,
        snapshot.rolloverReleases, snapshot.paymentMethods, snapshot.movements, snapshot.templates,
        snapshot.pendingCurrencyChange, snapshot.ledgerPreferences, snapshot.suggestions,
    )
}
```

Keep the current `BackupCodec.csv` body inside `database.withTransaction`: obtain `pockets`, `periods`, `paymentMethods`, and `movements` from the same DAO transaction, build every CSV row from those captured collections, leave formula neutralization in `csvCell`, and return the completed string as UTF-8 only after the transaction closes. No DAO read may remain before or after that block.

- [ ] **Step 4: Write the foreground-date coordinator tests**

```kotlin
@Test
fun a_new_forward_date_refreshes_once_and_same_or_backward_dates_do_not_mutate() = runTest {
    val calls = mutableListOf<LocalDate>()
    val coordinator = ForegroundDateCoordinator(LocalDate.of(2026, 3, 24)) { calls += it }
    coordinator.observe(LocalDate.of(2026, 3, 24))
    coordinator.observe(LocalDate.of(2026, 3, 25))
    coordinator.observe(LocalDate.of(2026, 3, 25))
    coordinator.observe(LocalDate.of(2026, 3, 23))
    assertEquals(listOf(LocalDate.of(2026, 3, 25)), calls)
}
```

- [ ] **Step 5: Run the coordinator test and observe RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.ForegroundDateCoordinatorTest" --no-daemon
```

- [ ] **Step 6: Implement explicit refresh signaling**

Implement the coordinator as:

```kotlin
internal class ForegroundDateCoordinator(
    initialDate: LocalDate,
    private val onForwardDate: suspend (LocalDate) -> Unit,
) {
    private val mutex = Mutex()
    private var latestDate = initialDate

    suspend fun observe(date: LocalDate) = mutex.withLock {
        if (!date.isAfter(latestDate)) return
        latestDate = date
        onForwardDate(date)
    }
}
```

`MainActivity` supplies `Clock` through `PocketApplication`, checks on resume, and starts a lifecycle-bound foreground coroutine that waits until the next `Asia/Riyadh` midnight. Its callback executes `CatchUpPeriods(preferredStartDay)` even when no period is created.

Add a private `MutableSharedFlow<Unit>(extraBufferCapacity = 1)` to `RoomPocketLedger`; merge it with Room invalidation signals so a successful no-write catch-up still rebuilds elapsed days, projection, current period, and Pocket availability from the injected clock.

- [ ] **Step 7: Add clock-boundary ledger tests**

Use a mutable `Clock` to cover ordinary midnight, a start-day boundary, multiple missed periods, repeated resume, and backward time. Assert period count, current period, `elapsedDays`, `projectionMinor`, and unchanged exported backup on repeated/backward signals.

- [ ] **Step 8: Verify and commit group F**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.ForegroundDateCoordinatorTest" --tests "com.aif31.pocket.PocketLedgerHostBehaviorTest" --tests "com.aif31.pocket.PocketAppHostFlowTest" --no-daemon
git diff --check
git add app/src/main/java/com/aif31/pocket/ForegroundDateCoordinator.kt app/src/main/java/com/aif31/pocket/MainActivity.kt app/src/main/java/com/aif31/pocket/data/Model.kt app/src/main/java/com/aif31/pocket/data/BackupCodec.kt app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt app/src/test/java/com/aif31/pocket/ForegroundDateCoordinatorTest.kt app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt
git commit -m "fix: emit coherent snapshots across date boundaries (#12 #18)"
```

---

### Task 8: Make expense saving single-flight and idempotent

**Issue:** #14, group G

**Files:**
- Create: `app/src/main/java/com/aif31/pocket/expense/MovementSubmissionViewModel.kt`
- Create: `app/src/test/java/com/aif31/pocket/expense/MovementSubmissionViewModelTest.kt`
- Modify: `app/src/main/java/com/aif31/pocket/ProductionExpense.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketApp.kt`
- Modify: `app/src/test/java/com/aif31/pocket/ExpenseWorkflowHostTest.kt`
- Modify: `app/src/androidTest/java/com/aif31/pocket/PocketAppFlowTest.kt`

**Interfaces:**
- Consumes: a fully formed `LedgerCommand.AddMovement` and `PocketLedger.execute`.
- Produces: a saveable stable draft ID and `SubmissionState` (`Editing`, `Saving`, `Failed`, `Saved`) owned by an activity-retained ViewModel.

- [ ] **Step 1: Write delayed single-flight tests**

```kotlin
@Test
fun repeated_submit_and_recreated_viewmodel_use_one_draft_identity() = runTest {
    val savedState = SavedStateHandle()
    val persisted = mutableListOf<LedgerCommand.AddMovement>()
    val release = CompletableDeferred<LedgerResult>()
    val execute: suspend (LedgerCommand.AddMovement) -> LedgerResult = { command ->
        persisted += command
        release.await()
    }
    val command = LedgerCommand.AddMovement(
        pocketId = "pocket-1",
        type = MovementType.EXPENSE,
        accountingAmountMinor = 1_250,
        occurredAtUtcMillis = 1L,
        localDate = LocalDate.of(2026, 2, 26),
    )
    val first = MovementSubmissionViewModel(savedState, execute)
    first.submit(command)
    first.submit(command)
    assertEquals(SubmissionState.Saving, first.state.value)
    release.complete(LedgerResult.Success)
    advanceUntilIdle()

    val recreated = MovementSubmissionViewModel(savedState, execute)
    recreated.submit(command)

    assertEquals(1, persisted.size)
    assertEquals(persisted.single().id, recreated.draftId)
}
```

Add failure and retry assertions: failure retains the command fields, state becomes `Failed(message)`, retry uses the same ID, and success is consumable once.

- [ ] **Step 2: Run the submission tests and observe RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.expense.MovementSubmissionViewModelTest" --no-daemon
```

- [ ] **Step 3: Implement the submission ViewModel**

```kotlin
internal sealed interface SubmissionState {
    data object Editing : SubmissionState
    data object Saving : SubmissionState
    data class Failed(val message: String) : SubmissionState
    data object Saved : SubmissionState
}

internal class MovementSubmissionViewModel(
    private val savedState: SavedStateHandle,
    private val execute: suspend (LedgerCommand.AddMovement) -> LedgerResult,
) : ViewModel() {
    val draftId: String = savedState.get<String>(DRAFT_ID) ?: UUID.randomUUID().toString().also {
        savedState[DRAFT_ID] = it
    }
    private val mutableState = MutableStateFlow<SubmissionState>(
        when (savedState.get<String>(STATUS)) {
            "SAVED" -> SubmissionState.Saved
            else -> SubmissionState.Editing
        },
    )
    val state: StateFlow<SubmissionState> = mutableState.asStateFlow()

    fun submit(command: LedgerCommand.AddMovement) {
        if (mutableState.value is SubmissionState.Saving || mutableState.value is SubmissionState.Saved) return
        mutableState.value = SubmissionState.Saving
        viewModelScope.launch {
            mutableState.value = when (val result = execute(command.copy(id = draftId))) {
                LedgerResult.Success -> SubmissionState.Saved.also { savedState[STATUS] = "SAVED" }
                is LedgerResult.Rejected -> SubmissionState.Failed(result.message)
                is LedgerResult.Deleted -> SubmissionState.Failed("No se pudo guardar el movimiento")
            }
        }
    }

    private companion object {
        const val DRAFT_ID = "movement_draft_id"
        const val STATUS = "movement_submission_status"
    }

    class Factory(private val ledger: PocketLedger) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return MovementSubmissionViewModel(extras.createSavedStateHandle()) { command ->
                ledger.execute(command)
            } as T
        }
    }
}
```

The implementation copies `id = draftId` into new Movement commands, uses `viewModelScope`, and changes to `Saved` only after `LedgerResult.Success`.

- [ ] **Step 4: Render submission state in the production screen**

Extend the serializable route to `MovementRoute(val draftKey: String, val movementId: String? = null, val suggestionId: String? = null)` and create every new route with a fresh `UUID.randomUUID().toString()`; Navigation 3 restores that key across recreation. Obtain the ViewModel with `viewModel(key = movementRoute.draftKey, factory = MovementSubmissionViewModel.Factory(ledger))`. Disable `movement_save` while saving, label it `Guardando…`, retain field values and show the rejection on failure, and navigate only after observing `Saved`. Removing the route removes that logical draft; opening another route gets a new key. Back/dismiss cancels the UI route but never reports success.

- [ ] **Step 5: Add Compose/activity lifecycle coverage**

Drive double tap, `scenario.recreate()`, background/resume, delayed success, delayed failure, and retry. Assert exactly one Movement ID and all submitted Pocket, period, amount, date, payment method, and conversion fields.

- [ ] **Step 6: Verify and commit group G**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.expense.MovementSubmissionViewModelTest" --tests "com.aif31.pocket.ExpenseWorkflowHostTest" --no-daemon
.\gradlew.bat :app:pixel6Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aif31.pocket.PocketAppFlowTest --no-daemon
git diff --check
git add app/src/main/java/com/aif31/pocket/expense/MovementSubmissionViewModel.kt app/src/main/java/com/aif31/pocket/ProductionExpense.kt app/src/main/java/com/aif31/pocket/PocketApp.kt app/src/test/java/com/aif31/pocket/expense/MovementSubmissionViewModelTest.kt app/src/test/java/com/aif31/pocket/ExpenseWorkflowHostTest.kt app/src/androidTest/java/com/aif31/pocket/PocketAppFlowTest.kt
git commit -m "fix: save each expense draft exactly once (#14)"
```

---

### Task 9: Complete portable settings recovery and truthful reminders

**Issues:** #15 and #16, group H

**Files:**
- Create: `app/src/main/java/com/aif31/pocket/PortableBackupCoordinator.kt`
- Create: `app/src/main/java/com/aif31/pocket/settings/ReminderStatus.kt`
- Create: `app/src/test/java/com/aif31/pocket/settings/ReminderStatusTest.kt`
- Modify: `app/src/main/java/com/aif31/pocket/settings/AppPreferences.kt`
- Modify: `app/src/main/java/com/aif31/pocket/settings/Reminder.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/Model.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/BackupCodec.kt`
- Modify: `app/src/main/java/com/aif31/pocket/MainActivity.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketApp.kt`
- Modify: `app/src/main/java/com/aif31/pocket/SettingsScreens.kt`
- Modify: `app/src/test/java/com/aif31/pocket/settings/AppPreferencesTest.kt`
- Modify: `app/src/test/java/com/aif31/pocket/settings/ReminderSchedulerTest.kt`
- Modify: `app/src/test/java/com/aif31/pocket/RecoveryInfrastructureTest.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt`
- Modify: `app/src/androidTest/java/com/aif31/pocket/PocketAppFlowTest.kt`

**Interfaces:**
- Consumes: DataStore `PreferencesStore`, backup codec versions 1–4, WorkManager, notification permission, and channel availability.
- Produces: backup version 5 with `PortableSettings`; restored reminders are disabled/awaiting confirmation; `ReminderStatus` expresses device truth.

Add these recovery interfaces:

```kotlin
data class PortableSettings(
    val futurePeriodStartDay: Int = 25,
    val reminderTime: LocalTime = LocalTime.of(21, 0),
)

class PortableBackupCoordinator(
    private val ledger: PocketLedger,
    private val preferences: PreferencesStore,
) {
    suspend fun export(): ByteArray = ledger.exportBackup(preferences.state.first().toPortableSettings())
    suspend fun preview(bytes: ByteArray): BackupPreview = ledger.previewBackup(bytes)
    suspend fun restore(bytes: ByteArray): LedgerResult {
        val preview = ledger.previewBackup(bytes)
        if (!preview.valid) return LedgerResult.Rejected(preview.message ?: "Backup inválido")
        val result = ledger.restoreBackup(bytes)
        if (result is LedgerResult.Success) {
            preferences.applyRestoredPortableSettings(preview.portableSettings ?: PortableSettings())
        }
        return result
    }
}

private fun AppPreferences.toPortableSettings() = PortableSettings(
    futurePeriodStartDay = futurePeriodStartDay,
    reminderTime = reminderTime,
)
```

Extend `PocketLedger.exportBackup` as `suspend fun exportBackup(settings: PortableSettings = PortableSettings()): ByteArray`, and extend `BackupPreview` with `portableSettings: PortableSettings?`. The default preserves existing test and internal call sites; production create/share flows use `PortableBackupCoordinator.export()` so they capture current DataStore values.

- [ ] **Step 1: Write backup version 5 round-trip and legacy tests**

Add the following to `PocketLedgerHostBehaviorTest.kt`:

```kotlin
@Test
fun backup_round_trips_future_start_day_and_reminder_time_but_disables_restored_reminder() = runTest {
    val sourcePreferences = MemoryPreferencesStore(
        AppPreferences(futurePeriodStartDay = 10, reminderEnabled = true, reminderTime = LocalTime.of(22, 30)),
    )
    val sourceLedger = RoomPocketLedger(database, clock, zone)
    sourceLedger.execute(LedgerCommand.Initialize(10_000))
    val source = PortableBackupCoordinator(sourceLedger, sourcePreferences)
    val bytes = source.export()
    val preview = source.preview(bytes)
    assertEquals(10, preview.portableSettings?.futurePeriodStartDay)
    assertEquals(LocalTime.of(22, 30), preview.portableSettings?.reminderTime)

    val restoredPreferences = MemoryPreferencesStore()
    val restoredDatabase = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext())
    try {
        val restoredLedger = RoomPocketLedger(restoredDatabase, clock, zone)
        PortableBackupCoordinator(restoredLedger, restoredPreferences).restore(bytes)
        val restored = restoredPreferences.state.first()
        assertEquals(10, restored.futurePeriodStartDay)
        assertEquals(LocalTime.of(22, 30), restored.reminderTime)
        assertFalse(restored.reminderEnabled)
        assertTrue(restored.reminderAwaitingConfirmation)
    } finally {
        restoredDatabase.close()
    }
}
```

Add this test helper in the same test class:

```kotlin
private class MemoryPreferencesStore(
    initial: AppPreferences = AppPreferences(),
) : PreferencesStore {
    private val mutable = MutableStateFlow(initial)
    override val state: Flow<AppPreferences> = mutable
    override suspend fun setFuturePeriodStartDay(day: Int) { mutable.value = mutable.value.copy(futurePeriodStartDay = day) }
    override suspend fun setReminder(enabled: Boolean, time: LocalTime) {
        mutable.value = mutable.value.copy(reminderEnabled = enabled, reminderTime = time)
    }
    override suspend fun applyRestoredPortableSettings(settings: PortableSettings) {
        mutable.value = mutable.value.copy(
            futurePeriodStartDay = settings.futurePeriodStartDay,
            reminderTime = settings.reminderTime,
            reminderEnabled = false,
            reminderAwaitingConfirmation = true,
        )
    }
    override suspend fun setReminderAwaitingConfirmation(value: Boolean) {
        mutable.value = mutable.value.copy(reminderAwaitingConfirmation = value)
    }
    override suspend fun acknowledgePlaintextBackup() {
        mutable.value = mutable.value.copy(plaintextBackupAcknowledged = true)
    }
    override suspend fun setOnlineFxEnabled(enabled: Boolean) = Unit
    override suspend fun setDefaultExpenseCurrency(currency: SupportedCurrency) = Unit
    override suspend fun setNotificationSourcePackages(packages: Set<String>) = Unit
}
```

Decode representative v1, v2, v3, and v4 payloads with default settings and no enabled reminder.

- [ ] **Step 2: Run recovery tests and observe RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.RecoveryInfrastructureTest" --no-daemon
```

- [ ] **Step 3: Add portable settings and disclosure preferences**

Append these properties to the existing `AppPreferences` constructor:

```kotlin
val reminderAwaitingConfirmation: Boolean = false,
val plaintextBackupAcknowledged: Boolean = false,
```

Add atomic DataStore methods `applyRestoredPortableSettings(settings)`, `setReminderAwaitingConfirmation(value)`, and `acknowledgePlaintextBackup()`.

- [ ] **Step 4: Upgrade the backup codec explicitly**

Encode version 5 with:

```kotlin
@Serializable
private data class PortableSettingsPayload(
    val futurePeriodStartDay: Int,
    val reminderHour: Int,
    val reminderMinute: Int,
)
```

Keep older version decoding branches unchanged except for validated defaults. Validate day in `1..31`, hour in `0..23`, and minute in `0..59` during preview.

- [ ] **Step 5: Write reminder truth-table tests**

```kotlin
@Test
fun reminder_status_combines_intent_permission_channel_and_scheduler() {
    assertEquals(ReminderStatus.Off, resolveReminderStatus(false, false, false, ScheduleState.Absent))
    assertEquals(ReminderStatus.PermissionRequired, resolveReminderStatus(true, false, true, ScheduleState.Absent))
    assertEquals(ReminderStatus.Failed, resolveReminderStatus(true, true, false, ScheduleState.Absent))
    assertEquals(ReminderStatus.Scheduled, resolveReminderStatus(true, true, true, ScheduleState.Enqueued))
    assertEquals(ReminderStatus.Failed, resolveReminderStatus(true, true, true, ScheduleState.Failed))
}
```

- [ ] **Step 6: Implement scheduler reconciliation**

```kotlin
sealed interface ReminderStatus {
    data object Off : ReminderStatus
    data object PermissionRequired : ReminderStatus
    data object Scheduled : ReminderStatus
    data class Failed(val message: String) : ReminderStatus
}

internal enum class ScheduleState { Absent, Enqueued, Failed }

internal fun resolveReminderStatus(
    enabled: Boolean,
    permissionGranted: Boolean,
    channelAvailable: Boolean,
    schedule: ScheduleState,
): ReminderStatus = when {
    !enabled -> ReminderStatus.Off
    !permissionGranted -> ReminderStatus.PermissionRequired
    !channelAvailable -> ReminderStatus.Failed("No se pudo preparar el canal de notificaciones")
    schedule == ScheduleState.Enqueued -> ReminderStatus.Scheduled
    else -> ReminderStatus.Failed("No se pudo programar el recordatorio")
}

interface ReminderScheduler {
    suspend fun apply(enabled: Boolean, time: LocalTime): ReminderStatus
    suspend fun reconcile(enabled: Boolean, time: LocalTime): ReminderStatus
}
```

`WorkReminderScheduler` creates/checks the channel, checks runtime permission, enqueues or cancels unique work, and queries WorkManager state. It returns `Scheduled` only for enqueued/running periodic work. Permission denial and enqueue/channel failure remain visible and retryable.

- [ ] **Step 7: Implement recovery safeguards in UI**

Before the first backup create/share, show the persistent plaintext explanation and require `plaintextBackupAcknowledged`. In restore preview, offer `Crear backup de seguridad` and `Continuar sin backup`; record success, skip, cancellation, or failure before enabling replacement. After restore, write portable settings with reminders disabled and awaiting confirmation.

- [ ] **Step 8: Render and reconcile truthful reminder state**

Display exactly `Desactivado`, `Permiso necesario`, `Programado`, or `No se pudo programar`. Include concise next action and `Reintentar` for failure. Permission callback, settings reopen, app resume, and restore invoke `reconcile`. Copy states that delivery is best effort near the selected time.

- [ ] **Step 9: Verify group H**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.settings.AppPreferencesTest" --tests "com.aif31.pocket.settings.ReminderStatusTest" --tests "com.aif31.pocket.settings.ReminderSchedulerTest" --tests "com.aif31.pocket.RecoveryInfrastructureTest" --tests "com.aif31.pocket.PocketAppHostFlowTest" --no-daemon
.\gradlew.bat :app:pixel6Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aif31.pocket.PocketAppFlowTest --no-daemon
git diff --check
```

- [ ] **Step 10: Commit group H**

```powershell
git add app/src/main/java/com/aif31/pocket/PortableBackupCoordinator.kt app/src/main/java/com/aif31/pocket/settings/ReminderStatus.kt app/src/main/java/com/aif31/pocket/settings/AppPreferences.kt app/src/main/java/com/aif31/pocket/settings/Reminder.kt app/src/main/java/com/aif31/pocket/data/Model.kt app/src/main/java/com/aif31/pocket/data/BackupCodec.kt app/src/main/java/com/aif31/pocket/MainActivity.kt app/src/main/java/com/aif31/pocket/PocketApp.kt app/src/main/java/com/aif31/pocket/SettingsScreens.kt app/src/test/java/com/aif31/pocket/settings/ReminderStatusTest.kt app/src/test/java/com/aif31/pocket/settings/AppPreferencesTest.kt app/src/test/java/com/aif31/pocket/settings/ReminderSchedulerTest.kt app/src/test/java/com/aif31/pocket/RecoveryInfrastructureTest.kt app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt app/src/androidTest/java/com/aif31/pocket/PocketAppFlowTest.kt
git commit -m "feat: complete portable recovery and reminder truth (#15 #16)"
```

---

### Task 10: Preserve archived Pocket identity across periods and history

**Issue:** #17, group I

**Files:**
- Modify: `app/src/main/java/com/aif31/pocket/data/Model.kt`
- Modify: `app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketsScreen.kt`
- Modify: `app/src/main/java/com/aif31/pocket/MovementsScreen.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt`
- Modify: `app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt`
- Modify: `app/src/androidTest/java/com/aif31/pocket/PocketAppFlowTest.kt`

**Interfaces:**
- Consumes: durable `PocketEntity` rows and period-scoped `PeriodPocketEntity` snapshots.
- Produces: `LedgerState.pocketCatalog: List<Pocket>` and history selection keyed by Pocket ID.

- [ ] **Step 1: Write archive-to-next-period-to-restore tests**

```kotlin
@Test
fun archived_pocket_remains_in_catalog_and_history_after_period_advance() = runTest {
    val firstLedger = RoomPocketLedger(database, clock, zone)
    firstLedger.execute(LedgerCommand.Initialize(30_000))
    val firstState = firstLedger.state.first { !it.needsOnboarding }
    val pocketId = firstState.pockets.first().pocket.id
    val firstPeriodId = firstState.currentPeriod!!.id
    firstLedger.execute(LedgerCommand.AddMovement(
        id = "historical-pocket-movement",
        pocketId = pocketId,
        type = MovementType.EXPENSE,
        accountingAmountMinor = 1_000,
        occurredAtUtcMillis = clock.millis(),
        localDate = LocalDate.of(2026, 2, 26),
    ))
    val historicalBefore = firstLedger.state.first().pocketSummariesByPeriod.getValue(firstPeriodId)
        .single { it.pocket.id == pocketId }
    firstLedger.execute(LedgerCommand.ArchivePocket(pocketId))
    firstLedger.execute(LedgerCommand.CreateNextPeriod())
    val archived = firstLedger.state.first().pocketCatalog.single { it.id == pocketId }
    assertTrue(archived.archived)
    assertTrue(firstLedger.state.first().movements.any { it.pocketId == pocketId })

    firstLedger.execute(LedgerCommand.ArchivePocket(pocketId, archived = false))
    val restoredState = firstLedger.state.first()
    val restored = restoredState.pocketCatalog.single { it.id == pocketId }
    assertFalse(restored.archived)
    assertEquals(
        historicalBefore.copy(pocket = historicalBefore.pocket.copy(archived = false)),
        restoredState.pocketSummariesByPeriod.getValue(firstPeriodId).single { it.pocket.id == pocketId },
    )
    assertEquals(
        "historical-pocket-movement",
        restoredState.movements.single { it.pocketId == pocketId }.id,
    )
}
```

Add multiple archived Pockets, zero-current-budget, repeated refresh, and catalog reorder cases.

- [ ] **Step 2: Run the catalog tests and observe RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.PocketLedgerHostBehaviorTest.archived_pocket_remains_in_catalog_and_history_after_period_advance" --no-daemon
```

Expected: compile failure because `pocketCatalog` does not exist or assertion failure because management/history uses current summaries.

- [ ] **Step 3: Expose the durable catalog**

Add to `LedgerState`:

```kotlin
val pocketCatalog: List<Pocket> = emptyList()
```

Populate it directly from all `PocketEntity` rows ordered by `sortOrder, name`. Keep `pocketSummariesByPeriod` as the immutable historical/current snapshot source.

- [ ] **Step 4: Use stable IDs in management and history**

Build management archived rows from `state.pocketCatalog.filter(Pocket::archived)`. Restore with `LedgerCommand.ArchivePocket(id, archived = false)` and only add eligibility to the current/future period. In `MovementsScreen`, replace `pocketIndex` with:

```kotlin
var selectedPocketId by rememberSaveable { mutableStateOf<String?>(null) }
val pocketOptions = state.pocketCatalog
val filtered = state.movements.filter { selectedPocketId == null || it.pocketId == selectedPocketId }
```

Resolve labels from the catalog by ID; never store list positions.

- [ ] **Step 5: Verify and commit group I**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aif31.pocket.PocketLedgerHostBehaviorTest" --tests "com.aif31.pocket.PocketAppHostFlowTest" --no-daemon
.\gradlew.bat :app:pixel6Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aif31.pocket.PocketAppFlowTest --no-daemon
git diff --check
git add app/src/main/java/com/aif31/pocket/data/Model.kt app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt app/src/main/java/com/aif31/pocket/PocketsScreen.kt app/src/main/java/com/aif31/pocket/MovementsScreen.kt app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt app/src/androidTest/java/com/aif31/pocket/PocketAppFlowTest.kt
git commit -m "fix: preserve archived Pocket catalog identity (#17)"
```

---

### Task 11: Verify the populated schema 1-to-current upgrade journey

**Issue:** #21, group J

**Files:**
- Modify: `app/src/main/java/com/aif31/pocket/data/FinanceDatabase.kt`
- Modify: `app/src/androidTest/java/com/aif31/pocket/data/FinanceDatabaseMigrationTest.kt`

**Interfaces:**
- Consumes: Room schema versions 1–7, auto migration 1→2, and manual migrations 2→3 through 6→7.
- Produces: one named-database opening seam and a populated oldest-to-current journey that ends in production ledger use, export, and restore.

- [ ] **Step 1: Add a named database opening seam**

In `FinanceDatabase`:

```kotlin
internal fun open(context: Context, name: String): FinanceDatabase =
    Room.databaseBuilder(context.applicationContext, FinanceDatabase::class.java, name)
        .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        .build()
```

Make the public `open(context)` delegate to `open(context, "pocket.db")`.

- [ ] **Step 2: Seed a populated version 1 database**

Use the exact version 1 schema columns from `app/schemas/com.aif31.pocket.data.FinanceDatabase/1.json`:

```kotlin
helper.createDatabase("migration-1-current-test", 1).apply {
    execSQL(
        "INSERT INTO periods (id, start_epoch_day, end_exclusive_epoch_day, new_funds_minor, configured_start_day) VALUES " +
            "('period-1', 20478, 20506, 100000, 25), ('period-2', 20506, 20537, 110000, 25)"
    )
    execSQL(
        "INSERT INTO pockets (id, name, sort_order, archived, rollover_enabled) VALUES " +
            "('pocket-1', 'Viajes', 0, 0, 1), ('pocket-2', 'Comida', 1, 0, 0)"
    )
    execSQL("INSERT INTO payment_methods (id, name, archived) VALUES ('card-1', 'Tarjeta', 0)")
    execSQL(
        "INSERT INTO allocations (period_id, pocket_id, budget_minor, rollover_minor) VALUES " +
            "('period-1', 'pocket-1', 25000, 5000), ('period-2', 'pocket-1', 30000, 7000)"
    )
    execSQL(
        "INSERT INTO movements (id, period_id, pocket_id, type, sar_amount_minor, occurred_at_utc_millis, " +
            "local_epoch_day, zone_id, merchant, note, payment_method_id, original_amount_minor, " +
            "original_currency_code, conversion_status, rate) VALUES " +
            "('expense-1', 'period-1', 'pocket-1', 'EXPENSE', 1000, 1, 20479, 'Asia/Riyadh', " +
            "'Merchant', NULL, 'card-1', NULL, 'SAR', 'CONFIRMED', NULL), " +
            "('refund-1', 'period-2', 'pocket-1', 'REFUND', 250, 2, 20507, 'Asia/Riyadh', " +
            "NULL, 'Legacy refund', NULL, NULL, 'SAR', 'CONFIRMED', NULL)"
    )
    execSQL(
        "INSERT INTO recurring_templates (id, name, amount_minor, pocket_id, payment_method_id, archived) " +
            "VALUES ('template-1', 'Viaje', 5000, 'pocket-1', 'card-1', 0)"
    )
    close()
}
```

- [ ] **Step 3: Run the full-chain test and observe RED**

```powershell
.\gradlew.bat :app:pixel6Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aif31.pocket.data.FinanceDatabaseMigrationTest#populated_version_1_upgrades_to_current_and_remains_usable --no-daemon
```

Expected before the new fixture/opening seam: missing test or compile failure.

- [ ] **Step 4: Open through the production Room builder and assert final semantics**

```kotlin
val context = InstrumentationRegistry.getInstrumentation().targetContext
val zone = ZoneId.of("Asia/Riyadh")
val fixedClock = Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone)
val migrated = FinanceDatabase.open(context, "migration-1-current-test")
val ledger = RoomPocketLedger(migrated, fixedClock, zone)
val state = ledger.state.first { it.periods.isNotEmpty() }
assertEquals(setOf("period-1", "period-2"), state.periods.mapTo(mutableSetOf()) { it.id })
assertEquals(setOf("expense-1", "refund-1"), state.movements.mapTo(mutableSetOf()) { it.id })
assertNotNull(state.currentPeriod)
assertEquals(
    LedgerResult.Success,
    ledger.execute(LedgerCommand.AddMovement(
        id = "post-migration",
        pocketId = state.pockets.first().pocket.id,
        type = MovementType.EXPENSE,
        accountingAmountMinor = 100,
        occurredAtUtcMillis = fixedClock.millis(),
        localDate = LocalDate.of(2026, 2, 26),
    )),
)
val backup = ledger.exportBackup()
assertTrue(ledger.exportCsv().isNotEmpty())
assertEquals(LedgerResult.Success, ledger.restoreBackup(backup))
```

Retain the existing step-specific 2→3 through 6→7 tests so failures identify the exact migration that broke.

- [ ] **Step 5: Assert safe legacy defaults and rejection**

Keep the existing step-specific assertions for icon backfill, rollover eligibility snapshots, SAR accounting defaults, payment default, null FX provenance, empty FX cache, and empty suggestion inbox. Add a separate version 1 database with two expense rows of `Long.MAX_VALUE` and `1` in the same period, migrate it through the production builder, and assert state construction rejects the aggregate:

```kotlin
val boundary = FinanceDatabase.open(context, "migration-1-boundary-test")
try {
    val boundaryLedger = RoomPocketLedger(boundary, fixedClock, zone)
    assertFailsWith<ArithmeticException> { boundaryLedger.state.first() }
    assertEquals(2, boundary.financeDao().movements().size)
} finally {
    boundary.close()
}
```

The raw row-count assertion proves rejection did not silently drop either legacy Movement.

- [ ] **Step 6: Verify and commit group J**

```powershell
.\gradlew.bat :app:pixel6Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aif31.pocket.data.FinanceDatabaseMigrationTest --no-daemon
git diff --check
git add app/src/main/java/com/aif31/pocket/data/FinanceDatabase.kt app/src/androidTest/java/com/aif31/pocket/data/FinanceDatabaseMigrationTest.kt
git commit -m "test: verify populated Room upgrade chain (#21)"
```

---

### Task 12: Add the API 35 critical-configuration matrix

**Issue:** #22, group K

**Files:**
- Create: `app/src/androidTest/java/com/aif31/pocket/ManagedDeviceConfigurationRule.kt`
- Create: `app/src/androidTest/java/com/aif31/pocket/PocketCriticalConfigurationTest.kt`
- Modify: `app/src/androidTest/java/com/aif31/pocket/PocketAppFlowTest.kt`
- Modify: `.github/workflows/android-ci.yml` only if diagnostics from a failing matrix case are missing.

**Interfaces:**
- Consumes: Pixel 6 API 35 `aosp-atd`, Compose semantics, `ActivityScenario.recreate`, and instrumentation shell commands.
- Produces: deterministic font/window/IME/recreation tests that restore emulator configuration after each test.

- [ ] **Step 1: Write the configuration restoration rule**

```kotlin
class ManagedDeviceConfigurationRule : TestWatcher() {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    fun fontScale(value: Float) = shell("settings put system font_scale $value")
    fun displaySize(value: String) = shell("wm size $value")
    private fun shell(command: String) {
        automation.executeShellCommand(command).use { it.readBytes() }
    }
    override fun finished(description: Description) {
        shell("settings put system font_scale 1.0")
        shell("wm size reset")
    }
}
```

Run this rule only on disposable managed devices. Pair each configuration change with activity recreation before assertions.

- [ ] **Step 2: Write large-font and narrow/wide tests**

At 1.5 font scale and representative compact/wide `wm size` values, navigate through onboarding, Pocket edit, Movement entry, reminder settings, and restore confirmation. Start with these concrete cases:

```kotlin
@Test
fun onboarding_and_validation_are_reachable_at_large_font_and_compact_width() {
    configuration.fontScale(1.5f)
    configuration.displaySize("720x1280")
    compose.activityRule.scenario.recreate()
    compose.onNodeWithTag("new_funds").performTextInput("12..50")
    compose.onNodeWithText("Comenzar").performScrollTo().performClick()
    compose.onNodeWithText("Escribe fondos válidos").assertIsDisplayed()
    compose.onNodeWithText("Restaurar backup").performScrollTo().assertIsDisplayed()
}

@Test
fun dashboard_and_Pocket_actions_remain_reachable_at_wide_size() {
    seedLedger()
    configuration.displaySize("1440x1080")
    compose.activityRule.scenario.recreate()
    compose.onNodeWithText("Pockets").performClick()
    compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("pocket_Supermercado"))
    compose.onNodeWithTag("pocket_Supermercado").assertIsDisplayed().performClick()
    compose.onNodeWithText("Guardar presupuesto").assertIsDisplayed()
}
```

Define the class helper with the real application ledger:

```kotlin
private fun seedLedger() = runBlocking {
    val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PocketApplication
    if (app.ledger.state.first().needsOnboarding) {
        app.ledger.execute(LedgerCommand.Initialize(100_000))
    }
    compose.activityRule.scenario.recreate()
}
```

- [ ] **Step 3: Write keyboard-visible entry coverage**

Focus `movement_amount`, enter a valid amount, keep the IME open, and assert `movement_save` remains reachable:

```kotlin
@Test
fun keyboard_visible_movement_keeps_draft_and_save_reachable() {
    seedLedger()
    compose.onNodeWithTag("contextual_add").performClick()
    compose.onNodeWithTag("movement_amount").performTextInput("12.50")
    compose.onNodeWithTag("movement_pocket_Supermercado").performClick()
    compose.onNodeWithTag("movement_save").assertIsDisplayed().assertIsEnabled()
    compose.activityRule.scenario.recreate()
    compose.onNodeWithTag("movement_amount").assertTextContains("12.50")
    compose.onNodeWithTag("movement_save").assertIsDisplayed().assertIsEnabled()
}
```

- [ ] **Step 4: Write process/lifecycle recovery coverage**

Add this lifecycle assertion after the delayed-save instrumentation seam from Task 8 is available:

```kotlin
compose.onNodeWithTag("movement_save").performClick()
compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
compose.activityRule.scenario.recreate()
compose.waitUntil(10_000) {
    runBlocking { application.ledger.state.first().movements.size == 1 }
}
assertEquals(1, runBlocking { application.ledger.state.first().movements.size })
```

In the test, bind `application` from `compose.activity.application as PocketApplication` before clicking save. Reuse the existing recovery document-result helpers in `PocketAppFlowTest` to recreate the activity while the restore confirmation is visible, then assert `Confirmar restauración` is still displayed and no ledger replacement occurred. Navigate to reminders after permission denial, recreate/resume, and assert `Permiso necesario` plus the recovery action remain visible.

- [ ] **Step 5: Run the focused matrix and observe RED**

```powershell
.\gradlew.bat :app:pixel6Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aif31.pocket.PocketCriticalConfigurationTest --no-daemon
```

Expected: the first uncovered reachability or state-preservation assertion fails before its targeted UI/lifecycle fix.

- [ ] **Step 6: Apply only evidence-driven UI fixes**

Use `verticalScroll`, `LazyColumn`, `imePadding`, `navigationBarsPadding`, stable semantics, and saveable state at the failing screen. Do not change navigation roots, financial behavior, or screen copy beyond what is required for accurate accessible feedback.

- [ ] **Step 7: Verify and commit group K**

```powershell
.\gradlew.bat :app:pixel6Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aif31.pocket.PocketCriticalConfigurationTest --no-daemon
.\gradlew.bat :app:pixel6Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest --no-daemon
git diff --check
git add app/src/androidTest/java/com/aif31/pocket/ManagedDeviceConfigurationRule.kt app/src/androidTest/java/com/aif31/pocket/PocketCriticalConfigurationTest.kt app/src/androidTest/java/com/aif31/pocket/PocketAppFlowTest.kt .github/workflows/android-ci.yml app/src/main/java/com/aif31/pocket/ProductionExpense.kt app/src/main/java/com/aif31/pocket/PocketApp.kt app/src/main/java/com/aif31/pocket/PocketsScreen.kt app/src/main/java/com/aif31/pocket/SettingsScreens.kt
git commit -m "test: cover critical API 35 configurations (#22)"
```

---

### Task 13: Run the integrated release gate and reconcile GitHub

**Issues:** #9 through #22

**Files:**
- Modify: `docs/reviews/2026-09-11-final-completeness-review.md`

**Interfaces:**
- Consumes: all group commits and every issue acceptance criterion.
- Produces: one final evidence table, GitHub issue comments, and closure only for fully proven issues.

- [ ] **Step 1: Run the complete host, lint, and package gate**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --no-daemon
```

Record exit code, host-test count, lint errors/warnings, and produced APK/test-APK paths.

- [ ] **Step 2: Run the complete ordinary managed-device gate**

```powershell
.\gradlew.bat :app:pixel6Api35DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest --no-daemon
```

Record executed, passed, failed, and skipped counts plus report paths.

- [ ] **Step 3: Run repository integrity checks**

```powershell
git diff --check
git status --short --branch
git log --oneline --decorate origin/main..HEAD
```

Confirm the deliberate-failure test is absent, no build output is tracked, and every remaining working-tree path is understood.

- [ ] **Step 4: Replace the draft review status with verified evidence**

For each issue #9–#22, add a six-row acceptance table to `docs/reviews/2026-09-11-final-completeness-review.md` containing the exact source path, test name, command, and observed result for each criterion. Remove stale baseline-gap text only where final source and fresh output prove it false. Retain any limitation with its owning issue left open.

- [ ] **Step 5: Commit the final evidence**

```powershell
git add docs/reviews/2026-09-11-final-completeness-review.md
git commit -m "docs: record open issue completion evidence (#9-#22)"
```

- [ ] **Step 6: Publish one evidence comment per satisfied issue**

After all fourteen tables are satisfied, publish the final review and close the exact issue range:

```powershell
9..22 | ForEach-Object {
    gh issue comment $_ --repo AIF31/Financial-App --body "Implemented and verified against every acceptance criterion. Criterion-by-criterion source and test evidence is committed in docs/reviews/2026-09-11-final-completeness-review.md."
    gh issue close $_ --repo AIF31/Financial-App --reason completed
}
```

If any acceptance table contains a gap, do not run the range. Comment and close only the individually satisfied issue numbers with the same two commands.

- [ ] **Step 7: Confirm tracker state**

```powershell
gh issue list --repo AIF31/Financial-App --state open --limit 100 --json number,title,labels,url
```

Expected: no issue from #9 through #22 remains open if and only if all fourteen evidence tables are complete. Report any intentionally open issue with its unmet criterion and blocking decision.
