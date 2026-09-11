# Codebase evaluation — 2026-09-06

This is a static evaluation of the canonical Windows checkout. It covers the
main Kotlin application, Room entities/queries/migrations, backup and restore,
resources and manifest security settings, release/debug notification code, the
test sources, and CI configuration. The domain terms and accepted recovery
behavior come from [`CONTEXT.md`](../../CONTEXT.md) and
[`ADR 0001`](../adr/0001-plaintext-portable-backup-and-replacement-restore.md).
Line links below were checked against this checkout on 2026-09-06.

The highest priority is financial correctness and recovery. “Confirmed” means
the behavior follows directly from source. “Runtime-unverified” means the code
path is clear but still needs an Android or concurrency test. “Planned gap”
means the accepted product documents already describe the work; it should not
be presented as a newly discovered exploit. No measured performance result or
security exploit is claimed.

## Scope matrix

| Area | Evidence reviewed | Result |
| --- | --- | --- |
| Financial domain and Room ledger | [`RoomPocketLedger.kt`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt), [`DomainRules.kt`](../../app/src/main/java/com/aif31/pocket/domain/DomainRules.kt), entities and host behavior tests | Three confirmed financial correctness findings: rollover release accounting, overflow, and amount input. |
| Backup, restore, CSV, and preferences | [`BackupCodec.kt`](../../app/src/main/java/com/aif31/pocket/data/BackupCodec.kt), [`PocketApp.kt`](../../app/src/main/java/com/aif31/pocket/PocketApp.kt), [`AppPreferences.kt`](../../app/src/main/java/com/aif31/pocket/settings/AppPreferences.kt) | One confirmed destructive preflight defect, plus accepted recovery-completeness work and a static snapshot risk. |
| Compose screens and lifecycle | [`ProductionExpense.kt`](../../app/src/main/java/com/aif31/pocket/ProductionExpense.kt), [`PocketsScreen.kt`](../../app/src/main/java/com/aif31/pocket/PocketsScreen.kt), [`MovementsScreen.kt`](../../app/src/main/java/com/aif31/pocket/MovementsScreen.kt), [`SettingsScreens.kt`](../../app/src/main/java/com/aif31/pocket/SettingsScreens.kt) | Duplicate-submit, archived-pocket, reminder truth, clock freshness, and feedback lifecycle findings. |
| Schema, resources, and notifications | [`FinanceDatabase.kt`](../../app/src/main/java/com/aif31/pocket/data/FinanceDatabase.kt), migration tests, manifest, backup rules, network security, debug/release notification providers | Migrations and resource exclusions are present. The real notification-listener/device experience remains unverified; no manifest backup leak was found. |
| Tests and delivery gates | [`android-ci.yml`](../../.github/workflows/android-ci.yml), Gradle managed-device configuration, host/device tests, planning docs | Host tests, lint, and builds are invoked by CI; the declared device suite is not executed there. This audit did not run Gradle or a physical device. |

## Prioritized findings

### P1 — Released rollover disappears from unallocated accounting

**Evidence.** Archiving a funded Pocket records a `RolloverReleaseEntity` and
zeros the Pocket allocation in [`RoomPocketLedger.kt#L239`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L239). The derived ledger value at
[`RoomPocketLedger.kt#L734`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L734)
subtracts only Pocket budgets from new funds. The screen repeats that formula at
[`PocketsScreen.kt#L52`](../../app/src/main/java/com/aif31/pocket/PocketsScreen.kt#L52),
while the release is shown only in a retired row. The host test explicitly
asserts `30_000` unallocated after a `5_000` release at
[`PocketLedgerHostBehaviorTest.kt#L275`](../../app/src/test/java/com/aif31/pocket/PocketLedgerHostBehaviorTest.kt#L275).

**Trigger.** Archive a current-period Pocket with positive rollover.

**Impact.** A reclassification of existing availability is absent from the
unassigned view, so the user cannot distinguish remaining new-funds capacity
from released-unassigned value. This conflicts with the glossary’s definition
of a rollover release and with accepted archive decisions 17–18.

**Smallest fix.** Add a separate released-unassigned amount to the ledger state
and display it beside the new-funds unallocated amount. Keep the allocation cap
based on `newFundsMinor`; adding releases to new funds would change the budget
rule and be incorrect.

**Regression test.** Archive a Pocket with a known budget and rollover, then
assert the release amount, the remaining new-funds capacity, and the allocation
cap independently. Include a second Pocket to prove released value is not
silently absorbed into its budget cap.

### P1 — A future-only backup replaces the ledger before restore rejects it

**Evidence.** [`BackupCodec.kt#L103`](../../app/src/main/java/com/aif31/pocket/data/BackupCodec.kt#L103) validates structure and then clears and rewrites the database in a transaction. It does not reject a payload whose earliest period is after today. [`PocketApp.kt#L150`](../../app/src/main/java/com/aif31/pocket/PocketApp.kt#L150) calls restore first and checks the future-only condition only after `Success` at [`PocketApp.kt#L160`](../../app/src/main/java/com/aif31/pocket/PocketApp.kt#L160). If no current period remains, the app returns the “updating period” screen at [`PocketApp.kt#L195`](../../app/src/main/java/com/aif31/pocket/PocketApp.kt#L195).

**Trigger.** Select a structurally valid backup whose earliest period starts
after the device’s current local date.

**Impact.** Existing data is committed away before the UI reports the error.
The replacement can leave the app with no current period and no completed
catch-up path. This violates the accepted transactional replacement experience
because the destructive precondition was checked too late.

**Smallest fix.** Perform date coverage and derived-state/catch-up feasibility
checks during preview or before entering the replacement transaction. Commit
only after those checks pass. Preserve the existing ledger if any preflight or
restore phase fails.

**Regression test.** Seed a nonempty ledger, restore a future-only payload, and
assert a rejection while every original row and the current state remain. Add
failure injection after each restore phase to prove replacement is atomic.

### P1 — Monetary `Long` arithmetic can wrap or terminate state derivation

**Evidence.** Allocation-cap arithmetic is unchecked at
[`RoomPocketLedger.kt#L155`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L155).
Rollover recalculation uses unchecked `sumOf` and addition at
[`RoomPocketLedger.kt#L445`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L445)
and [`RoomPocketLedger.kt#L450`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L450).
State derivation sums expenses, refunds, budgets, and availability at
[`RoomPocketLedger.kt#L665`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L665)
and [`RoomPocketLedger.kt#L734`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L734).
`PocketMath.summary` uses exact addition/subtraction at
[`DomainRules.kt#L154`](../../app/src/main/java/com/aif31/pocket/domain/DomainRules.kt#L154),
but its percentage conversion can wrap through `toInt` at
[`DomainRules.kt#L160`](../../app/src/main/java/com/aif31/pocket/domain/DomainRules.kt#L160).
The command boundary catches `IllegalArgumentException` and SQLite constraint
errors only at [`RoomPocketLedger.kt#L100`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L100).
Backup validation exact-sums budgets but does not aggregate movements or
availability at [`BackupCodec.kt#L273`](../../app/src/main/java/com/aif31/pocket/data/BackupCodec.kt#L273).

**Trigger.** A valid per-row amount near the `Long` limit, two large expenses,
or enough ordinary rows to overflow an aggregate.

**Impact.** A wrapped allocation total can pass the budget cap. A summary can
throw `ArithmeticException` or expose an invalid consumed percentage, and a
backup can contain individually valid movements whose aggregate is unsafe.
These are source-confirmed arithmetic paths; no exploit or runtime crash is
claimed.

**Smallest fix.** Define a supported money range, use checked aggregation at
every domain boundary, reject `ArithmeticException` as invalid input, and run
the same aggregate validation during backup decode. Bound or explicitly handle
the percentage result instead of narrowing an unbounded `BigInteger` value to
`Int`.

**Regression test.** Cover two `5_000_000_000_000_000_000` expenses, an
allocation near `Long.MAX_VALUE`, rollover plus budget addition, and a backup
with an overflowing movement aggregate. Assert rejection and preservation of
the prior state.

### P1 — Amount input silently changes the user’s value

**Evidence.** The amount field filters input to digits and `.` at
[`ProductionExpense.kt#L323`](../../app/src/main/java/com/aif31/pocket/ProductionExpense.kt#L323); the remaining text is passed to `Money.parse` at [`ProductionExpense.kt#L145`](../../app/src/main/java/com/aif31/pocket/ProductionExpense.kt#L145).

**Trigger.** A user pastes Spanish-style `12,50`, or types `-12.50`.

**Impact.** `12,50` becomes `1250` before parsing, turning 12.50 into
1250.00 in a two-decimal currency. The minus sign is removed, turning a
negative entry into a positive one. This is a silent financial misentry, not
just a formatting issue.

**Smallest fix.** Use a locale-aware normalizer with explicit validation. If a
separator or sign is unsupported, preserve the visible text and show an error;
never discard characters that change the amount. Keep expense/refund choice as
the sign model and reject negative text rather than converting it.

**Regression test.** Test typing and pasting with comma decimals, dot grouping,
malformed mixed separators, and a negative value. Add Arabic separators or
digits only if the supported input contract includes them.

### P2 — New movement saves have no in-flight or idempotent guard

`saveMovement` launches a coroutine on every tap at
[`ProductionExpense.kt#L185`](../../app/src/main/java/com/aif31/pocket/ProductionExpense.kt#L185),
and the button is enabled from field readiness only at
[`ProductionExpense.kt#L261`](../../app/src/main/java/com/aif31/pocket/ProductionExpense.kt#L261).
New commands receive a random ID in [`RoomPocketLedger.kt#L880`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L880).
Two taps against a delayed ledger can therefore create two movements. Give the
entry flow a durable `Saving/Saved/Failed` operation state and intent at the
screen boundary, disable repeat submission immediately, and generate one
stable draft ID for retries. A delayed fake-ledger test should prove exactly one
row and one success callback.

The Compose boundary should stay narrow: durable data and intents belong in the
screen-level holder; `FocusRequester` and `SnackbarHostState` remain in
composition or a plain UI state holder; and a previewable content composable
should receive immutable state and event callbacks. This follows the lifecycle
guidance in the [Compose side-effects documentation](https://developer.android.com/develop/ui/compose/side-effects)
without requiring a blanket ViewModel conversion.

### P2 — Archived Pockets lose their management and restore route after period creation

Archiving deletes future period snapshots at
[`RoomPocketLedger.kt#L245`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L245),
and new periods are built from active catalog entries only at
[`RoomPocketLedger.kt#L526`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L526).
The current screen exposes an archive restore row only when the current
`shownPockets` contains it at [`PocketsScreen.kt#L306`](../../app/src/main/java/com/aif31/pocket/PocketsScreen.kt#L306).
After the next period, the archived Pocket is absent from that collection, so
the user cannot restore it. Historical data remains read-only, but the history
filter also derives its Pocket options from current summaries at
[`MovementsScreen.kt#L90`](../../app/src/main/java/com/aif31/pocket/MovementsScreen.kt#L90).

Expose the full Pocket catalog through a dedicated archive route while keeping
new-period budgets and movements blocked. Add an archive → next period →
restore test, plus a historical movement filter test. Treat filter-index
resetting as a secondary UX issue, not as ledger loss.

### P2 — Reminder status can say “Activated” when delivery is impossible

Settings persist the preference and call a scheduler with no result at
[`SettingsScreens.kt#L306`](../../app/src/main/java/com/aif31/pocket/SettingsScreens.kt#L306). The UI label is driven by the preference, while the Android permission callback is empty at [`MainActivity.kt#L61`](../../app/src/main/java/com/aif31/pocket/MainActivity.kt#L61). The worker returns success without posting when notification permission is denied at [`Reminder.kt#L49`](../../app/src/main/java/com/aif31/pocket/settings/Reminder.kt#L49). WorkManager’s periodic request is intentionally best effort, as documented by [`PeriodicWorkRequest`](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest), so timing drift alone is not a defect.

Expose permission, channel, scheduled, and failure states; reconcile them on
resume and after the permission callback; and make scheduler failure visible or
roll back the enabled state. Exercise denied permission, disabled channel, and
enqueue failure with WorkManager tests.

### P2 — CI builds the device APK but does not execute the managed-device suite

[`android-ci.yml#L36`](../../.github/workflows/android-ci.yml#L36) runs host tests, lint, and assemble tasks, including the instrumentation APK. The Pixel 6 API 35 managed device is declared at [`app/build.gradle.kts#L89`](../../app/build.gradle.kts#L89), and migration/device tests live under `app/src/androidTest`. No device task appears in the workflow. Add the declared managed-device test task and archive reports on pass or infrastructure failure. Populate an oldest-supported migration fixture from schema 1 through 7, then add large-font, window-size, navigation restoration, and process-death coverage. The Android migration guidance is in the [Room migration documentation](https://developer.android.com/training/data-storage/room/migrating-db-versions).

### P2 — Restore omits accepted app-owned settings and recovery safeguards

The payload contains ledger data and a default payment method but no future
period start day, reminder time, or enabled state at
[`BackupCodec.kt#L351`](../../app/src/main/java/com/aif31/pocket/data/BackupCodec.kt#L351).
After restore, Pocket derives the future start day from the latest restored
period at [`PocketApp.kt#L156`](../../app/src/main/java/com/aif31/pocket/PocketApp.kt#L156).
The accepted ADR explicitly marks full preference export, disabled-until-
reconfirmation reminders, a pre-restore safety export, and persistent plaintext
disclosure as future work. Track this as planned M2 recovery work, with
round-trip and failure-preservation tests; do not describe plaintext as an
unapproved security regression.

### P2 — Continuous foreground use can leave date-derived state stale

`buildState` calculates today only when its Room/preferences flow emits at
[`RoomPocketLedger.kt#L649`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L649).
`MainActivity` invokes catch-up at lifecycle entry points at
[`MainActivity.kt#L94`](../../app/src/main/java/com/aif31/pocket/MainActivity.kt#L94),
and lifecycle-aware collection refreshes after a stop/start. The remaining
case is an app kept continuously in the foreground across midnight with no
database emission: elapsed days, projection, current local date, and period
selection can stay stale. Add an explicit date invalidation or midnight-aware
signal while active, and test with an injected clock without mutating the DB.

### P2 — Flow and CSV snapshots can combine different revisions

Budget and activity data are collected as independent groups at
[`RoomPocketLedger.kt#L30`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L30) and then combined at [`RoomPocketLedger.kt#L46`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L46). Kotlin `combine` uses the latest value from each input, so this is a static consistency risk during closely spaced invalidations, not an observed crash. CSV reads the Pocket, period, method, and movement tables independently at [`BackupCodec.kt#L145`](../../app/src/main/java/com/aif31/pocket/data/BackupCodec.kt#L145). Use one repository-level transactional snapshot for derived state and CSV export, or an equivalent coherent DAO read. A controlled invalidation test should assert that IDs, labels, period currency, and movements come from one revision. See [Room observable query guidance](https://developer.android.com/training/data-storage/room/async-queries) and the [Kotlin `combine` contract](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/combine.html).

Restore also catches `Exception` around the operation at
[`BackupCodec.kt#L140`](../../app/src/main/java/com/aif31/pocket/data/BackupCodec.kt#L140),
which can turn coroutine cancellation into a rejection while the dialog’s
composition-scoped job is being cancelled. Re-throw cancellation, keep a
durable restore operation state, and disable repeat/dismiss actions during the
commit. Keep snackbar runtime objects near the wiring boundary and render a
plain immutable content state so onboarding and restore failures remain
visible. Add cancellation and failed-document-read tests.

## Lower-priority observations and product boundaries

State derivation eagerly builds summaries for every period and repeatedly scans
lists in [`RoomPocketLedger.kt#L685`](../../app/src/main/java/com/aif31/pocket/data/RoomPocketLedger.kt#L685). This is a performance risk for large histories, not measured jank. Profile realistic multi-year data first; only then consider grouped maps, a dispatcher boundary for pure derivation, or separate current/history queries.

Potential simplifications should follow caller checks: `ManualFx` in
[`DomainRules.kt#L191`](../../app/src/main/java/com/aif31/pocket/domain/DomainRules.kt#L191)
has no production callers in this checkout, `MoneyText.sar` is test-only, and
`forceRefresh` is accepted by the FX interface but ignored by the default
repository at [`DefaultExchangeRateRepository.kt#L12`](../../app/src/main/java/com/aif31/pocket/fx/DefaultExchangeRateRepository.kt#L12).
These are optional cleanup candidates, not reasons to remove useful test seams.

The next product priorities remain a recovery center with last-backup/shareable
export, archived/date/type history filters, and explicit unsaved-draft handling.
The online FX path intentionally requires user consent; an offline/manual FX
fallback is a product decision and is not reported as a missing implementation.
Accounts, cloud sync, bank auto-import, OCR, and AI categorization remain out of
scope.

## Verification limits

This report is a source audit. No Gradle build or instrumented test was run in
this pass because the documented native-tool prerequisite was unavailable, and
no physical Android device was used. Existing planning and beta documents record
historical successful runs; those records are not presented as current audit
execution. The findings above therefore distinguish source-confirmed behavior
from runtime-unverified concurrency, scheduler, device-permission, and
performance risks. The report is thorough within the listed areas but is not an
exhaustive dynamic audit.
