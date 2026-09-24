# Remaining issues implementation review — 2026-09-24

Fixed point: `ebc0ccfd0d78664e028f94b33174589b97e35b7d`. Scope: local implementation of GitHub issues #14, #15, #16, #17, #21, and #22. This review excludes documentation and tooling changes that were already dirty in the checkout.

## Standards

The standards review found that the new backup format needed operational documentation and a changelog entry. `Info/release-signing-and-recovery.md` now describes version 5, plaintext handling, the safety export, and restored reminder confirmation. The local `Info/CHANGELOG.md` was updated without staging its pre-existing untracked contents. `graphify update .` refreshed the knowledge graph; its pre-existing generated files remain outside the implementation commit.

The review also identified string-based recovery status and repeated reminder scheduling branches as judgement calls. Recovery status now uses a typed state and an explicit export result instead of matching dialog copy. The reminder branches remain local to the settings screen and have focused UI coverage.

## Spec

The issue review found gaps in permission-result refresh, keyboard-visible saving, background/resume coverage, intermediate migration fixtures, and persistence assertions in the delayed expense test. Permission callbacks now refresh status immediately. Device tests save with the keyboard visible, resume after backgrounding, verify document feedback and scheduler retry, and confirm restore state across recreation. Intermediate migration fixtures now include financial rows where the schema supports them. The delayed fake ledger persists a Movement and proves a late response cannot duplicate it.

## Verification and limits

The initial local gate passed 205 host tests and 29 Pixel 6 API 35 managed-device tests, with no failures or skips, plus lint and debug/release builds. No personal Android device was used. PR CI exposed a stale host-test assertion: negative funds receive the ledger's domain rejection, not the parse error left on screen from the prior input. The assertion now waits for the domain rejection. A subsequent run found that document feedback could move from a dialog into onboarding while ledger state loaded; the app now waits for known non-onboarding state before showing the dialog, and the two feedback tests wait for onboarding before interacting. PR #26 carries these corrections through required CI; issue closure awaits merge.
