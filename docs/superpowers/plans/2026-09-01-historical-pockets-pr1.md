# Historical Pockets and Budget Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make older budget periods an explicit read-only Pockets view backed only by their snapshots, while reducing allocation-entry friction in the current period.

**Architecture:** Keep durable ledger state and commands at the existing `PocketApp`/`PocketsScreen` boundary. Derive `isHistorical` from the selected and current period identities, classify historical rows only by their selected-period snapshot, and route historical row taps to a presentation-only dialog. Keep the current-period management dialog intact except for a `TextFieldValue`-backed amount field that can represent empty zero input and select an existing amount on first focus.

**Tech Stack:** Kotlin, Jetpack Compose with the repository's Compose BOM `2026.03.01`, Material 3, Room-backed `PocketLedger`, Robolectric Compose host tests, JDK 17.

**Spec:** the task handoff and locked PR 1 decisions recorded with the project work item

## Global Constraints

- Supported currencies are SAR, USD, and MXN; PR 1 displays SAR because persisted period currency is introduced in PR 2.
- Older periods are explicitly read-only and use only the selected period's `PeriodPocketEntity`, allocation, rollover-release, and Movement-derived summary.
- A Pocket's current global archive flag must not reclassify an older active snapshot.
- Current-period creation, editing, reordering, allocation, archive, restore, and rollover behavior must remain available and unchanged.
- Zero allocation renders as an empty value with `0.00` placeholder; first focus selects a nonzero value so typed text replaces it.
- Every Gradle command runs through `.agents/skills/gradle-run/scripts/gradle_run.py` with JDK 17.
- Preserve application and device data; never uninstall or clear the physical-device app.

---

### Task 1: Historical snapshot classification and read-only presentation

**Files:**
- Modify: `app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketsScreen.kt`

**Interfaces:**
- Consumes: `LedgerState.periods`, `LedgerState.currentPeriod`, `LedgerState.pocketSummariesByPeriod`, and `PocketPeriodSummary` snapshot-derived amounts.
- Produces: `PocketsScreen` behavior where `selectedPeriod.id != currentPeriod.id` renders `Vista histórica · Solo lectura`, `Moneda del periodo · SAR`, snapshot-classified rows, and a read-only detail dialog.

- [ ] **Step 1: Write the failing historical host UI test**

Create three sequential periods, give `Viajes` a nonzero copied budget and rollover, record an expense and refund in the middle period, then archive it in the newest period. Select the middle period through the public Pockets UI and assert:

```kotlin
compose.onNodeWithText("Vista histórica · Solo lectura").assertIsDisplayed()
compose.onNodeWithText("Moneda del periodo · SAR").assertIsDisplayed()
compose.onNodeWithTag("pocket_Viajes").assertIsDisplayed()
compose.onAllNodesWithText("Crear Pocket").assertCountEquals(0)
compose.onAllNodesWithText("Restaurar").assertCountEquals(0)

compose.onNodeWithTag("pocket_Viajes").performClick()
compose.onNodeWithText("Detalle histórico").assertIsDisplayed()
compose.onNodeWithText("Presupuesto: SAR 100.00").assertIsDisplayed()
compose.onNodeWithText("Rollover recibido: SAR 100.00").assertIsDisplayed()
compose.onNodeWithText("Gastos: SAR 20.00").assertIsDisplayed()
compose.onNodeWithText("Reembolsos: SAR 5.00").assertIsDisplayed()
compose.onNodeWithText("Rollover liberado: SAR 0.00").assertIsDisplayed()
compose.onNodeWithText("Disponibilidad final: SAR 185.00").assertIsDisplayed()
compose.onAllNodesWithText("Guardar presupuesto").assertCountEquals(0)
compose.onAllNodesWithText("Editar Pocket").assertCountEquals(0)
compose.onAllNodesWithText("Archivar Pocket").assertCountEquals(0)
```

- [ ] **Step 2: Run the exact new test through the Gradle wrapper and verify RED**

Run the repository wrapper with `:app:testDebugUnitTest --tests com.aif31.pocket.PocketAppHostFlowTest.historical_period_uses_its_snapshots_and_exposes_only_read_only_details`. Expected: FAIL because the mode label and historical detail dialog do not exist and the globally archived Pocket is misclassified.

- [ ] **Step 3: Implement the minimal historical mode**

In `PocketsScreen`, derive:

```kotlin
val isHistorical = selectedPeriod?.id != null && selectedPeriod.id != state.currentPeriod?.id
val activePockets = shownPockets.filterNot { summary ->
    summary.retiredThisPeriod || (!isHistorical && summary.pocket.archived)
}
```

Render the explicit mode and SAR period-currency labels only in historical mode. Hide current-only creation, management copy, and archived restore actions. Pass `readOnly = isHistorical` to the detail dialog, where the read-only branch displays the seven requested snapshot values and only a Close action.

- [ ] **Step 4: Run the exact historical test through the Gradle wrapper and verify GREEN**

Expected: PASS with snapshot classification, historical labels, absence of write actions, and all requested detail values observable through semantics.

### Task 2: Allocation input replacement behavior

**Files:**
- Modify: `app/src/test/java/com/aif31/pocket/PocketAppHostFlowTest.kt`
- Modify: `app/src/main/java/com/aif31/pocket/PocketsScreen.kt`

**Interfaces:**
- Consumes: current `PocketPeriodSummary.budgetMinor` and `LedgerCommand.SetAllocation`.
- Produces: `allocation_amount` as a current-period decimal input whose zero is visually empty with placeholder `0.00`, and whose first focus selects an existing nonzero value.

- [ ] **Step 1: Write the failing zero-allocation host UI test**

Open a zero-budget Pocket and assert the field has no entered value while its merged semantics contains the `0.00` placeholder; type `25.00`, save, and assert the ledger budget becomes `2_500` minor units.

- [ ] **Step 2: Run the exact zero-allocation test through the Gradle wrapper and verify RED**

Expected: FAIL because the field currently contains `0.00` as its value rather than an empty value with placeholder content.

- [ ] **Step 3: Implement empty-zero state**

Initialize the editor with:

```kotlin
val initialAmount = if (summary.budgetMinor == 0L) "" else minorNumber(summary.budgetMinor)
var amount by rememberSaveable(summary.pocket.id, periodId, stateSaver = TextFieldValue.Saver) {
    mutableStateOf(TextFieldValue(initialAmount))
}
```

Add `placeholder = { Text("0.00") }` and parse `amount.text` when saving.

- [ ] **Step 4: Run the zero-allocation test and verify GREEN**

Expected: PASS and the public ledger state records `2_500`.

- [ ] **Step 5: Write the failing first-focus replacement test**

Start with a `SAR 100.00` allocation, open the current-period dialog, call `performClick()` followed by `performTextInput("250.00")`, assert the field contains `250.00` and not `100.00250.00`, save, and assert the budget is `25_000`.

- [ ] **Step 6: Run the exact replacement test through the Gradle wrapper and verify RED**

Expected: FAIL because the current field places the cursor without selecting the old nonzero value.

- [ ] **Step 7: Implement select-all on first focus**

Use state keyed by Pocket and period plus `Modifier.onFocusChanged`. On the first focused transition only, set:

```kotlin
amount = amount.copy(selection = TextRange(0, amount.text.length))
```

Do this only for nonempty text; later focus changes preserve user cursor/selection behavior.

- [ ] **Step 8: Run the replacement test and the full `PocketAppHostFlowTest` class and verify GREEN**

Expected: both focused tests and all existing current-period host flows pass.

### Task 3: PR 1 verification and review

**Files:**
- Modify if required by validated findings: files already listed above.

**Interfaces:**
- Consumes: the completed PR 1 diff and approved handoff requirements.
- Produces: a reviewed PR branch with fresh focused, unit, lint, and assembly evidence.

- [ ] **Step 1: Run focused host tests**

Through the Gradle wrapper, run `:app:testDebugUnitTest --tests com.aif31.pocket.PocketAppHostFlowTest` and require an exit-zero bounded summary.

- [ ] **Step 2: Run PR-level broad verification**

Through the same workflow, run `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease` as a broad gate. Do not inspect or paste full logs.

- [ ] **Step 3: Self-review the requirements and diff**

Confirm every PR 1 requirement maps to an assertion or visible branch, current-only command paths are gated by `isHistorical`, no schema/data changes occurred, and no credential-related file or value entered the diff.

- [ ] **Step 4: Request parallel read-only reviews**

Request a Standards reviewer for Compose state/focus/accessibility/testing correctness and a Spec reviewer for locked PR 1 acceptance coverage. Fix every validated Critical or Important finding in the primary agent and rerun the narrowest owning tests.

- [ ] **Step 5: Finish verification and create the pull request**

Finish the Gradle wrapper workflow after summarizing its bounded ledger, commit the reviewed diff, push `codex/historical-pockets`, and open a PR against `main` with test evidence and no secret values.
