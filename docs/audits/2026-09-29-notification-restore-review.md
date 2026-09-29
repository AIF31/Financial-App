# Notification restore replay review (2026-09-29)

## Scope

Reviewed the notification store fix and two regressions against `origin/main` at `5a0271d`, the 2026-09-29 restore handoff, and [ADR 0002](../adr/0002-auto-record-notification-payments-for-known-merchants.md). PR #37 changed release documentation only; the notification code remained at the version on which both regressions failed before the fix. The reviewed change also advances the required Android patch version from 1.0.5/6 to 1.0.6/7.

## Standards

The store uses the existing derived Movement ID and DAO query. Its direct `ingest` guard runs in the existing transaction, and the conversation `contains` check prevents parsing the repost. The two checks serve different callers; no new abstraction or schema is needed. No code-standard violation or actionable code smell was found. The review identified the missing changelog and dated audit required by [README.md](../../README.md); both are included with this fix.

## Spec

The captured conversation regression covers a restored auto-recorded Movement after its Pocket is archived. The direct store regression covers capture without auto-recording. Both fail on the prior code and pass with the shared guard. A surviving `ntf-<suggestionId>` Movement is checked before a suggestion can be created. `PocketNotificationListener` filters `IGNORED` outcomes before calling the recorder or showing a fresh detection alert.

Pending replays created before this fix are not automatically removed. The old branch's `RoomPocketLedger.confirmSuggestion` guard could consume one only if confirmation is reached; it does not cover archived Pockets or disabled auto-recording, so it was not retained without a separate migration contract. Suggestion tombstones are still omitted from backups. If a Movement was deleted before backup, restore has neither the Movement nor its tombstone, so a repost can be captured again. That pre-existing backup behavior needs a separate product decision if tombstones must survive restore.

## Verification and limits

- The two new tests failed before the fix: the conversation repost was captured, and direct `ingest` returned `CREATED` instead of `IGNORED`. Both passed after the fix.
- Four notification host test classes passed. The full `:app:testDebugUnitTest` suite passed 276 tests with zero failures, errors, or skips.
- `:app:lintDebug`, `:app:assembleDebug`, `:app:assembleRelease`, and `:app:assembleDebugAndroidTest` passed. `:app:pixel6Api35DebugAndroidTest` passed with 30 tests executed and none failed or skipped.
- No physical-device test was run. The absence of a fresh detection alert follows from the listener's `IGNORED` filter and was not tested through the system notification UI. The local Graphify launcher could not start its configured Python executable, so source and ADRs were inspected directly.

**Review totals:** Standards: one documentation-workflow finding, resolved. Spec: no defect in the requested surviving-Movement replay path; two pre-existing or migration limits recorded above.
