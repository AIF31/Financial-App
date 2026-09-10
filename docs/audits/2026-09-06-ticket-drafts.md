# Proposed ticket drafts — 2026-09-06

This file records the approved `/to-tickets` breakdown from the 2026-09-06
codebase evaluation. The 14 audit tickets were published to GitHub on
2026-09-07. The identifiers 01–14 below are stable audit ticket IDs; use the
mapping table for their published issue references. All published tickets carry
the `ready-for-agent` triage label.

## Published issue mapping

| Audit ticket | GitHub issue | Phase | PR |
| --- | --- | --- | --- |
| 01 | [Issue #9](https://github.com/AIF31/Financial-App/issues/9) | 1 | A |
| 02 | [Issue #10](https://github.com/AIF31/Financial-App/issues/10) | 1 | B |
| 03 | [Issue #11](https://github.com/AIF31/Financial-App/issues/11) | 1 | C |
| 04 | [Issue #12](https://github.com/AIF31/Financial-App/issues/12) | 2 | F |
| 05 | [Issue #13](https://github.com/AIF31/Financial-App/issues/13) | 2 | E |
| 06 | [Issue #14](https://github.com/AIF31/Financial-App/issues/14) | 2 | G |
| 07 | [Issue #15](https://github.com/AIF31/Financial-App/issues/15) | 2 | H |
| 08 | [Issue #16](https://github.com/AIF31/Financial-App/issues/16) | 2 | H |
| 09 | [Issue #17](https://github.com/AIF31/Financial-App/issues/17) | 2 | I |
| 10 | [Issue #18](https://github.com/AIF31/Financial-App/issues/18) | 2 | F |
| 11 | [Issue #19](https://github.com/AIF31/Financial-App/issues/19) | 2 | E |
| 12 | [Issue #20](https://github.com/AIF31/Financial-App/issues/20) | 1 | D |
| 13 | [Issue #21](https://github.com/AIF31/Financial-App/issues/21) | 3 | J |
| 14 | [Issue #22](https://github.com/AIF31/Financial-App/issues/22) | 3 | K |

## Phased PR grouping

The 14 tickets group into 11 independently reviewable PRs. A `+` means the
tickets share a user-facing flow or state boundary and should be reviewed
together; their acceptance criteria remain separate. Phases describe priority
and review order, not strict gates. The dependencies column lists genuine
implementation blockers; other sequencing notes are prioritization guidance.

| Phase | PR | Tickets | Shared solution or review boundary | Genuine dependencies |
| --- | --- | --- | --- | --- |
| 1 — Safety foundation | A | 01 | One amount parsing and validation policy across every financial entry surface. | None |
| 1 — Safety foundation | B | 02 | One checked monetary arithmetic and aggregate-validation boundary for writes and backups. | None |
| 1 — Safety foundation | C | 03 | Released-rollover accounting kept separate from the new-funds allocation cap. | None |
| 1 — Safety foundation | D | 12 | Managed-device execution and failure reporting become a usable CI gate for later device coverage. | None; can land early. |
| 2 — Consistency and recovery | E | 05 + 11 | Restore preflight, replacement outcomes, and onboarding/document-operation feedback share the recovery operation state in the app shell. Review the restore safety path and its visible failure/retry states together. | 02 gates 05; 11 has no hard blocker. |
| 2 — Consistency and recovery | F | 04 + 10 | Coherent ledger observations, CSV reads, and date-boundary invalidation share the repository observation path. | None. |
| 2 — Consistency and recovery | G | 06 | Stable movement submission identity and in-flight handling remain a narrow, independently testable flow. | None. |
| 2 — Consistency and recovery | H | 07 + 08 | Restored reminder preferences and truthful permission/scheduler status share reminder reconciliation and settings state. | 05 (through PR E) gates 07 restore integration; 08 has no hard blocker. |
| 2 — Consistency and recovery | I | 09 | The full Pocket catalog and stable historical filters share archive/restore identity handling. | None. |
| 3 — Broad regression | J | 13 | Populated migration fixtures verify the complete upgrade journey and can run independently of feature fixes. | None; may start during Phase 1 or 2. |
| 3 — Broad regression | K | 14 | Device configuration coverage exercises the stabilized critical flows across fonts, windows, keyboard, and recreation. | None for authoring or local execution; PR D is required for CI enforcement. Prioritize after affected behavior PRs. |

Suggested order is PRs A–D in parallel, then E, F, G, and I in parallel;
H follows E only for the #07 restore wiring, while its #08 scheduling work can
start earlier. PR J may begin as soon as migration fixtures are ready. PR K can
be authored earlier, but its final acceptance should follow the relevant
behavior PRs and PR D's device execution path. The only hard ticket blockers
are the existing 02 → 05 → 07 chain; grouping 05 with 11 and 07 with 08 does
not add new logical blockers.

## 01: Preserve amounts during decimal entry

**Priority:** P1

**What to build:** Financial forms preserve the amount the user intended when they type or paste a decimal value. Supported decimal separators are normalized by one shared policy, and input that cannot be interpreted safely is rejected with a clear validation message. A sign must never disappear and turn a negative amount into a positive one.

**Acceptance criteria:**

- `12,50` and `12.50` produce the same accounting amount when both separators are supported; the displayed value and saved value retain two decimal places of meaning.
- Grouping separators, Arabic numerals, and other locale-specific forms are either handled explicitly by the supported policy or rejected without changing the numeric value silently.
- Negative, empty, repeated-separator, and otherwise malformed values produce validation feedback and cannot be saved as a different positive amount.
- The same parsing and validation policy is used for new funds, Pocket budgets, expenses, refunds, and manually entered accounting amounts.
- Automated domain and UI regression coverage exercises pasted and typed comma-decimal and dot-decimal values, malformed values, and sign handling.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent — [GitHub issue #9](https://github.com/AIF31/Financial-App/issues/9).

## 02: Reject unsafe monetary totals before ledger mutation

**Priority:** P1

**What to build:** The app validates every monetary aggregate and derived projection before it changes the ledger. Per-row values that fit the accounting amount type must not be allowed to overflow when expenses, refunds, budgets, rollover, conversion results, or percentages are combined. The new-funds cap remains enforced during all allocation paths.

**Acceptance criteria:**

- Checked arithmetic or an equivalent bounded strategy rejects overflowing sums, differences, conversions, and projections with actionable feedback.
- A set of individually valid movements whose aggregate cannot be represented is rejected before any ledger mutation; the logical ledger remains unchanged after the failed operation.
- Pocket budgets cannot exceed new funds through ordinary allocation, rollover, conversion, or percentage-based calculations, including values near numeric limits.
- Backup validation applies the same aggregate and derived-state bounds before restore can proceed.
- Failure handling distinguishes validation failure from persistence failure and leaves the user able to correct the input.
- Regression tests cover expense/refund aggregate overflow, allocation-cap overflow, conversion/percentage overflow, and unchanged state after rejection.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent — [GitHub issue #10](https://github.com/AIF31/Financial-App/issues/10).

## 03: Account for released rollover in unassigned funds

**Priority:** P1

**What to build:** When an archived Pocket releases positive rollover, the released amount is shown and counted as unassigned funds in the receiving budget period. It remains a reclassification of existing availability, so it must not increase the period’s new-funds amount or permit Pocket budgets to exceed that new-funds cap.

**Acceptance criteria:**

- Archiving a Pocket with positive availability creates one explicit rollover-release amount for the receiving period and exposes it in the unassigned-funds summary.
- The receiving period’s available unassigned amount includes released rollover, while its new-funds amount and total Pocket-budget cap remain unchanged.
- Negative availability produces no rollover release, and archiving a Pocket with no eligible rollover leaves the release amount at zero.
- Release accounting is conserved across period creation, archive/restore actions, summaries, and historical views; the same amount is not counted twice.
- Existing historical periods retain their prior availability and movements when a later release occurs.
- Host and UI regression coverage verifies a release alongside a new-funds budget cap, period rollover, repeated refresh, and archive/restore.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent — [GitHub issue #11](https://github.com/AIF31/Financial-App/issues/11).

## 04: Read coherent ledger and CSV snapshots

**Priority:** P2

**What to build:** Derived ledger observations and portable CSV exports are each built from one coherent snapshot of the financial data. A concurrent restore, edit, or period catch-up must not let a caller combine rows from different logical versions, such as movements from one period with Pocket labels or currency metadata from another.

**Acceptance criteria:**

- Each aggregate observation is calculated from a consistent snapshot covering periods, Pockets, payment methods, and movements needed for that observation.
- Each CSV export captures its related metadata and rows from one consistent snapshot, so labels, IDs, currencies, and amounts describe the same ledger version.
- Concurrent restore/edit tests deliberately interleave reads and writes and verify that every emitted state and exported document is internally consistent.
- A failed or canceled operation does not expose a partially assembled snapshot to the UI or exporter.
- The implementation preserves observable updates after a successful write and does not require a measured crash or corruption claim to pass.
- Regression coverage includes initial load, repeated invalidations, period catch-up, restore, and export while a write is in flight.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent — [GitHub issue #12](https://github.com/AIF31/Financial-App/issues/12).

## 05: Validate and complete replacement restore safely

**Priority:** P1

**What to build:** Replacing the current ledger with a portable backup is an atomic, validated operation. The app previews the backup, rejects future-only or otherwise invalid data before it changes current data, and gives a definitive result for success, failure, cancellation, and duplicate submissions. A failed restore leaves the existing ledger usable.

**Acceptance criteria:**

- A backup is rejected during preflight, before replacement data is committed, only when its earliest budget period begins after the current local date (that is, it is future-only); future periods alongside a current or past period remain valid, with a clear explanation for a future-only rejection.
- Preflight validates date relationships, period catch-up requirements, IDs, currency boundaries, aggregates, and derived-state limits using the same safe rules as normal ledger writes.
- Replacement is committed as one recoverable unit; any validation or persistence failure leaves the previous ledger and current-period state intact.
- Cancellation, screen dismissal, process recreation, and repeated restore taps cannot produce a half-restored ledger or a second concurrent replacement.
- Success reconstructs a usable current period and surfaces the restored summary; failure exposes an actionable message and leaves retry possible.
- Automated coverage exercises future dates, malformed derived data, aggregate overflow, cancellation, duplicate requests, and preservation of pre-restore data.

**Blocked by:** [Audit ticket 02 / GitHub issue #10 — Reject unsafe monetary totals before ledger mutation](https://github.com/AIF31/Financial-App/issues/10).

**Status:** ready-for-agent — [GitHub issue #13](https://github.com/AIF31/Financial-App/issues/13).

## 06: Save an expense once across repeated taps and lifecycle changes

**Priority:** P2

**What to build:** Saving an expense is an idempotent user operation. Repeated taps, recomposition, backgrounding, or process/lifecycle callbacks do not create duplicate Movements. The UI shows that the request is in flight, reports the definitive result, and keeps a failed draft available for correction or one safe retry.

**Acceptance criteria:**

- While a save is pending, the action cannot launch a second write for the same draft, and the user can see that saving is in progress.
- A delayed persistence response followed by repeated taps results in exactly one Movement with the intended Pocket, period, amount, date, payment method, and conversion status.
- The draft has a stable identity across recomposition and relevant lifecycle changes, so a resumed screen cannot generate a second identity for the same submission.
- A definitive failure leaves no partial Movement and makes the draft and error available for correction or retry; a definitive success clears or closes the draft exactly once.
- Cancellation caused by leaving the screen is handled deliberately and does not report success before persistence completes.
- A delayed fake-ledger regression test covers double tap, rotation/background-resume, success, failure, and retry behavior.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent — [GitHub issue #14](https://github.com/AIF31/Financial-App/issues/14).

## 07: Complete the portable backup recovery contract

**Priority:** P2

**What to build:** Portable backup and replacement restore cover the user-owned financial settings and recovery safeguards promised by the product contract. A backup remains versioned plaintext JSON, but it carries the future period and reminder settings needed to reproduce future behavior. Restored reminders are disabled until the user confirms the current device state.

**Acceptance criteria:**

- Export and restore round-trip ledger data plus the preferred future period start day and reminder time, with explicit version handling for older backups.
- The restore preview states that replacement occurs and offers a safety export; the user may explicitly skip it and sees the result.
- A persistent plaintext-backup warning is shown, and the first export requires acknowledgment before the document is written or shared.
- Restored reminder preferences are presented as disabled or awaiting reconfirmation until permission, channel, and scheduling state are verified on the current device.
- Restore continues to require preview and confirmation and uses the safe replacement flow; canceled or failed recovery reports a definitive outcome.
- Regression coverage verifies settings round-trip, legacy backup compatibility, first-use disclosure, safety-export success/skip/failure, and disabled restored reminders.

**Blocked by:** [Audit ticket 05 / GitHub issue #13 — Validate and complete replacement restore safely](https://github.com/AIF31/Financial-App/issues/13).

**Status:** ready-for-agent — [GitHub issue #15](https://github.com/AIF31/Financial-App/issues/15).

## 08: Show truthful reminder permission and scheduling status

**Priority:** P2

**What to build:** Reminder settings describe the actual device state rather than treating a saved preference as proof that a reminder is active. The settings surface distinguishes disabled, permission-required, scheduled, and failed states, explains the next action, and can recover after permission or scheduler changes.

**Acceptance criteria:**

- The displayed status combines the user’s reminder preference with notification permission, notification-channel availability, and the latest scheduling result.
- The UI distinguishes “off,” “permission required,” “scheduled,” and “could not schedule,” with concise recovery guidance and a retry action where applicable.
- Permission denial, channel failure, scheduler failure, and successful scheduling each produce a visible result; a preference write alone cannot show “activated.”
- Returning to the app, reopening settings, and restoring a backup recheck the current permission/channel/scheduler state rather than showing stale status.
- Reminder timing remains documented and tested as best effort; status does not promise an exact delivery time.
- Tests cover first enable, permission request and denial, channel/work failure, retry, app resume, and restored preferences awaiting reconfirmation.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent — [GitHub issue #16](https://github.com/AIF31/Financial-App/issues/16).

## 09: Restore archived Pockets and preserve history filters

**Priority:** P2

**What to build:** Archived Pockets remain discoverable through a stable catalog after new budget periods are created. A user can restore an archived Pocket later without rewriting its historical periods or Movements, and history filters continue to address the same Pocket even when the current-period list changes.

**Acceptance criteria:**

- Archiving a Pocket, advancing one or more periods, and reopening management still exposes the archived Pocket and a working restore action.
- Restoring a Pocket returns it to eligible future periods while leaving prior budgets, availability, rollover releases, and Movements unchanged.
- The catalog distinguishes active, archived, and retired state consistently, and a Pocket is never hidden solely because it is absent from the latest period snapshot.
- Historical views can filter by an archived Pocket using a stable identifier; changing the catalog order or adding a Pocket does not redirect an existing filter to another Pocket.
- A Pocket with historical data remains readable after archive, restore, and period advancement, including when its current budget is zero.
- Regression coverage exercises archive → next period → restore, multiple archived Pockets, historical filtering, repeated refresh, and stable selection after catalog changes.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent — [GitHub issue #17](https://github.com/AIF31/Financial-App/issues/17).

## 10: Refresh the current period at foreground midnight

**Priority:** P2

**What to build:** An app that stays open across a date or budget-period boundary refreshes its current period and projections without requiring an unrelated persistence write. The clock is supplied as a testable dependency, catch-up remains transactional and idempotent, and repeated refreshes on the same date do not create duplicate periods or unnecessary writes.

**Acceptance criteria:**

- A date signal or equivalent foreground check detects a day change while the app remains visible and recomputes elapsed days, projections, and current-period summaries.
- Crossing the preferred start-day boundary triggers automatic period catch-up for every missing period in sequence and selects the correct current period.
- The same date or boundary observed repeatedly is handled idempotently and does not create duplicate periods or repeated ledger mutations.
- The implementation works on resume and after a long-running foreground session, with the clock injected so boundary tests do not depend on wall-clock timing.
- Statistics and Pocket availability update visibly after the boundary without requiring a user edit.
- Regression coverage includes ordinary midnight, a period boundary, multiple missed periods, repeated resume, and a clock that moves backward or is otherwise invalid.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent — [GitHub issue #18](https://github.com/AIF31/Financial-App/issues/18).

## 11: Show document-operation failures during onboarding and recovery

**Priority:** P2

**What to build:** Reading, exporting, sharing, and selecting a portable backup produce visible, accessible feedback even on the onboarding or no-current-period screens. A canceled picker is distinguished from a failed document operation, and every failure gives the user a safe retry or recovery path.

**Acceptance criteria:**

- A failed backup read during onboarding displays an error above or alongside the first-run content and does not silently return to an empty state.
- Export, share, and restore failures remain visible until acknowledged or replaced by a newer operation result, including when no current period exists.
- User cancellation is reported as cancellation or a neutral dismissal and is not presented as a system failure; permission and provider failures identify that action failed.
- Retry reopens or repeats only the failed document operation and does not duplicate a restore, export, or ledger mutation.
- Feedback is accessible to screen readers and remains understandable after rotation, resume, and navigation through the recovery flow.
- UI regression coverage covers onboarding read failure, no-current-period export failure, canceled picker, provider failure, retry, and successful recovery.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent — [GitHub issue #19](https://github.com/AIF31/Financial-App/issues/19).

## 12: Execute managed-device tests as a CI gate

**Priority:** P1

**What to build:** Pull requests and protected branch builds execute the repository’s configured managed-device instrumented suite on the supported API 35 Pixel 6 profile. CI must fail when those tests fail or time out, while retaining the existing host tests, lint, and build checks and preserving reports for diagnosis.

**Acceptance criteria:**

- The CI workflow boots the configured managed device and runs the instrumented test suite on every required PR or protected-branch event.
- A failing, crashed, or timed-out device test makes the job fail and prevents a green workflow from hiding an unexecuted or incomplete suite.
- Test reports, device logs, screenshots where configured, and failure diagnostics are uploaded even when the test job fails.
- Host tests, lint, resource checks, and build/package verification continue to run with their existing failure semantics.
- The job is deterministic enough for routine use, documents emulator/API prerequisites, and does not require personal data or developer-local state.
- A workflow validation exercise demonstrates both a passing suite and a deliberately failing test propagating to the job result.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent — [GitHub issue #20](https://github.com/AIF31/Financial-App/issues/20).

## 13: Verify the full populated schema upgrade chain

**Priority:** P2

**What to build:** Database migrations are tested as a user upgrade journey using populated ledgers, not only empty schemas. The suite starts from the oldest supported schema, applies each supported migration through the current schema, and verifies that financial meaning and user settings survive every transition.

**Acceptance criteria:**

- Fixtures contain populated periods, Pockets, Pocket budgets, Movements, payment methods, currency boundaries, and relevant settings at each supported starting version.
- The supported automatic upgrade path and the manually exercised later migration steps are both covered through the current schema, including data introduced between versions.
- After each migration, IDs, dates, amounts, Pocket state, historical records, rollover inputs, and settings remain valid and semantically equivalent.
- Fixtures include boundary values and legacy rows that should be rejected or defaulted, with explicit assertions for safe behavior and no silent data loss.
- The migration suite runs in the normal test workflow and produces a useful failure artifact identifying the starting version and migration step.
- Regression coverage proves that a migrated populated ledger can open, calculate the current period, add a Movement, export, and restore successfully.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent — [GitHub issue #21](https://github.com/AIF31/Financial-App/issues/21).

## 14: Verify critical device configurations at API 35

**Priority:** P2

**What to build:** Critical user flows remain usable on the supported API 35 device across large fonts, narrow and wide windows, keyboard-visible layouts, and process recreation. Device coverage exercises the screens that create periods, edit Pockets, save Movements, and recover from a portable backup.

**Acceptance criteria:**

- Instrumented coverage runs with large accessibility font scales and confirms that primary actions, validation, summaries, dialogs, and recovery controls remain reachable and readable.
- Narrow, wide, and relevant orientation/window configurations do not clip or hide Pocket availability, save/cancel actions, document-operation feedback, or restore confirmation.
- Opening the keyboard while entering a Movement preserves the draft, keeps validation visible, and leaves the save action usable or scrollable.
- Backgrounding, process recreation, and returning to the app do not duplicate saves, lose a confirmed result, or bypass restore confirmation.
- Permission and scheduling flows remain coherent after configuration changes and process recreation, including failed and retry states.
- The device tests run against the API 35 supported profile and publish failure diagnostics sufficient to reproduce a layout or lifecycle issue.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent — [GitHub issue #22](https://github.com/AIF31/Financial-App/issues/22).
