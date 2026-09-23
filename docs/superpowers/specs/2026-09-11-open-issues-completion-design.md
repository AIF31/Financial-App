# Open GitHub Issues Completion Design

**Date:** 2026-09-11

**Status:** Approved in chat; pending written-spec review

**Repository:** `AIF31/Financial-App`

**Scope:** GitHub issues #9 through #22

## Goal

Complete and verify every open `ready-for-agent` issue without weakening Pocket's financial invariants, recovery guarantees, or established Android experience. Work that already landed through pull request #23 is treated as implementation evidence to audit, not as proof that an issue is complete. An issue closes only after every acceptance criterion has fresh source and test evidence from the final integrated state.

## Authority and constraints

Implementation follows these sources in descending order:

1. The acceptance criteria in GitHub issues #9 through #22.
2. The canonical terms and invariants in `CONTEXT.md`.
3. ADR 0001, which requires versioned plaintext portable backups and previewed, confirmed, replacement restore rather than merge restore.
4. `docs/product/FUTURE_UPDATES_DECISION_SPEC.md`.
5. `UI-UX-Design-Philosophy.md`.
6. Existing tests that protect accepted behavior.

Pocket remains an offline-first personal spending tool. It does not introduce accounts, bank balances, cloud synchronization, backup encryption, or merge restore. Financial calculations use checked integer arithmetic and the supported accounting-currency rules. User-facing terminology continues to use Pocket, Pocket availability, budget period, new funds, Pocket budget, rollover, rollover release, Movement, payment method, and conversion status.

## Baseline and change ownership

Pull request #23, “Make recovery explicit and harden ledger and notification flows,” merged into `main` as `a1ed2d3ff0ab4aa223fc187aa351ce1069bb5182` on 2026-09-11. Its commits reference issues #9, #10, #11, #13, and #19 and also add migration and recovery coverage. GitHub reports no automatic closing issue references, so all fourteen issues remain open.

The canonical checkout currently contains uncommitted edits in the CI workflow, backup/ledger persistence code, monetary validation rules, and host tests, plus a draft completeness review. Those edits are preserved as pre-existing work. Before adding behavior, each hunk will be mapped to an issue and validated. Unrelated or unverifiable changes will not be discarded silently.

## Delivery structure

The approved ticket breakdown defines eleven independently reviewable groups. The implementation will preserve those boundaries even if work is performed on one integration branch. Commits, verification notes, and issue comments will identify their owning group.

| Group | Issues | Deliverable |
| --- | --- | --- |
| A | #9 | One shared decimal parsing and validation policy across every financial entry surface. |
| B | #10 | Checked monetary aggregates and projections validated before ledger mutation or restore replacement. |
| C | #11 | Explicit rollover releases included in unassigned funds without expanding the new-funds Pocket-budget cap. |
| D | #20 | Pixel 6 API 35 managed-device tests enforced by CI with execution proof and retained diagnostics. |
| E | #13, #19 | Atomic replacement restore and durable, accessible document-operation outcomes from onboarding through recovery. |
| F | #12, #18 | Transactional observation/export snapshots plus explicit foreground date invalidation and idempotent catch-up. |
| G | #14 | Stable expense-draft identity, single-flight persistence, and definitive lifecycle-aware outcomes. |
| H | #15, #16 | Complete portable settings recovery plus truthful reminder permission, channel, scheduling, and retry state. |
| I | #17 | Durable Pocket catalog identity for archive, restore, and historical filters. |
| J | #21 | Populated oldest-to-current Room migration journey with post-migration ledger usability. |
| K | #22 | API 35 configuration and lifecycle acceptance matrix for critical flows. |

The dependency chain is B → E → H for restore-related monetary validation and reminder recovery. Group D lands before K acceptance so the device matrix is enforced. Group F precedes final K lifecycle assertions because date refresh and coherent recovery observations affect those tests. Other groups may be developed independently but are integrated and verified in this order:

1. Reconcile merged and uncommitted work for A, B, C, D, and E.
2. Complete F, then G, H, and I.
3. Complete J and K.
4. Run full integrated verification and reconcile GitHub issue state.

## Architecture

### Financial input and arithmetic

All financial entry surfaces call one amount parser that accepts the explicitly supported dot and comma decimal forms and rejects negative, empty, ambiguous grouping, repeated separators, unsupported numerals, overflow, and malformed input without silently changing meaning. The parser returns either an exact minor-unit amount or a validation error suitable for field-level UI feedback.

All aggregate operations use shared checked helpers. Validation evaluates the complete prospective state—including source and destination periods, historical comparisons, rollover, currency-boundary conversion, percentage calculations, allocation caps, and backup-derived state—before any DAO mutation. Validation rejection and persistence failure remain distinct result kinds. A rejected operation leaves the backup-visible ledger byte-for-byte equivalent to its prior logical state.

Rollover releases remain a reclassification. Selected-period summaries receive their unassigned value from immutable ledger or UI state; composables do not duplicate the calculation. Pocket budgets remain bounded by new funds alone.

### Coherent observations and date invalidation

`RoomPocketLedger` establishes one database transaction as the snapshot boundary for every complete `LedgerState`. Room invalidation acts only as a signal to reread that snapshot. Portable CSV export reads metadata and Movements in one transaction and assembles output from the captured values after the read completes. Failed or canceled writes cannot emit a partially assembled observation.

The application supplies a testable `Clock` and a foreground date signal. While visible, the app detects a local-date change; on creation and resume it also reconciles the observed date. A new valid date triggers state recomputation and, when required, one transactional automatic period catch-up. Repeated signals for the same date do not create duplicate periods or unnecessary writes. A backward or invalid clock signal is ignored or reported without rewinding ledger state.

### Expense submission

The expense route owns a saveable draft identifier created once per logical draft. Submission state is `editing`, `saving`, `failed`, or `saved`. Only `editing` and `failed` may initiate a write; a retry reuses the same draft identifier. The save action is disabled and visibly in progress while persistence is pending. Success clears or exits once, while failure retains all draft fields and an actionable error. Leaving the route cancels UI collection deliberately but does not manufacture success or generate a second identity when the route resumes.

The ledger treats the stable Movement identifier idempotently. Repeating the same successful submission cannot append a second Movement with a new identifier.

### Portable recovery and reminder truth

The next backup format adds the future period start day and reminder time while preserving explicit decoding for every supported older version. Reminder-enabled device state is not restored as active. Restored reminder preferences enter an awaiting-reconfirmation state until the current device's notification permission, channel, and scheduler state are checked and the user confirms.

The restore preview states that current data will be replaced and offers a safety export. Continuing without it requires an explicit skip action with a visible result. A persistent plaintext warning remains present in data settings; the first export or share requires an acknowledgment stored as an app preference. Cancellation and provider failure are distinct outcomes.

Document operations retain the exact operation and artifact identity needed for retry. A share retry uses the exact successfully prepared backup; if preparation failed, retry repeats preparation rather than selecting an arbitrary cached file. Export and restore retries do not duplicate ledger mutation. Replacement plus required period catch-up commits in one Room transaction and remains serialized by a single-flight guard.

Reminder status is derived from preference intent, runtime notification permission, channel availability, and the latest scheduler result. The UI exposes four stable meanings: off, permission required, scheduled, and could not schedule. Failure includes a retry action. Settings reopening, app resume, permission results, and restore all trigger reconciliation. Copy describes reminder timing as best effort.

### Pocket catalog and history

The durable `pockets` table is the catalog source; current-period snapshots describe period eligibility and retirement but do not determine whether a Pocket exists. Management can show active and archived catalog entries after any number of period advances. Restoring an archived Pocket changes future eligibility without rewriting earlier allocations, releases, availability, or Movements.

Historical filters store a Pocket ID rather than a list position or current-summary reference. Filter labels resolve through the durable catalog. Reordering or adding Pockets cannot redirect an existing selection.

### CI, migrations, and device coverage

GitHub Actions retains host tests, lint, resource checks, and debug/release packaging and adds `:app:pixel6Api35DebugAndroidTest` as a required job. The ordinary CI suite excludes the deliberate visual-tour `@LargeTest`, fails on test failure, timeout, crash, or zero non-skipped executed tests, and uploads reports, logs, screenshots, and managed-device diagnostics under `if: always()`.

Migration coverage begins from schema version 1 with populated financial rows and applies every supported migration through the current schema. Assertions after each step preserve IDs, dates, amounts, Pocket state, history, rollover inputs, currency boundaries, and settings. Version-specific fixtures cover values that are safely defaulted or rejected. The final migrated database must open through the production ledger, calculate the current period, add a Movement, export a portable backup and CSV, and restore successfully.

The API 35 device matrix covers large fonts, compact and wider windows, relevant orientation/height constraints, keyboard-visible entry, process recreation, reminder permission/scheduler outcomes, and backup recovery. Tests assert reachability, semantics, state preservation, and non-duplication rather than screenshots alone. Failure diagnostics are collected through group D's CI path.

## Error and cancellation model

User-correctable validation failures do not mutate the ledger and keep the relevant draft or recovery input. Persistence and document-provider failures are identified separately and expose a scoped retry. Picker cancellation is neutral, not an error. Coroutine cancellation is rethrown through transactional boundaries so Room rolls back, while UI state records a cancellation only at an owning operation boundary. No operation reports success before persistence or document creation completes.

## Test strategy

Every behavior change follows red-green-refactor. A regression test is first observed failing for the expected missing behavior, then passing after the minimal implementation. Tests prefer real domain and Room behavior; fakes control clocks, delayed persistence, document boundaries, permission state, channels, and scheduler outcomes where Android or time must be deterministic.

Verification is layered:

1. Focused JVM/domain tests for parsing, arithmetic, snapshots, state machines, reminder reconciliation, archive identity, and clock behavior.
2. Room host or instrumented tests for atomic writes, interleaving, restore rollback, migrations, and post-migration use.
3. Compose/activity tests for validation, in-flight state, onboarding/recovery feedback, accessibility semantics, keyboard reachability, process recreation, and configuration changes.
4. Full `:app:testDebugUnitTest`, lint, resource checks, debug/release builds, and test APK assembly.
5. Pixel 6 API 35 managed-device suite from a clean checkout-equivalent state.
6. CI configuration validation plus a temporary deliberate failing device test to prove failure propagation; the temporary failure is removed and the passing suite is rerun.
7. `git diff --check` and an acceptance-criterion evidence table for all fourteen issues.

No physical Android device is used unless the repository's personal-data preservation procedure is read and followed. The required issue acceptance can be proven on the configured Gradle Managed Device; physical-device milestone testing is separate unless explicitly requested.

## GitHub reconciliation

Each issue receives a concise completion comment containing the implementing commits, acceptance evidence, exact verification commands, and any documented limitation. An issue is closed only when all its criteria are satisfied on the integrated source state. If an acceptance criterion cannot be met without a new product decision or contradicts an ADR, the issue remains open and the conflict is surfaced rather than bypassed.

## Completion definition

The work is complete when:

- all fourteen open issues have every acceptance criterion mapped to source and fresh verification evidence;
- the final integrated host, lint, build, and API 35 managed-device gates pass with no hidden skipped suite;
- ADR 0001, the product decision specification, canonical domain language, and UI philosophy remain satisfied;
- no pre-existing user change has been lost or silently overwritten;
- GitHub comments record the evidence and all fully satisfied issues are closed.
