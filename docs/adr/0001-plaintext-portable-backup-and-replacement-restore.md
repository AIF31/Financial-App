---
status: accepted
date: 2026-08-29
---

# Keep portable backups plaintext and replace data on restore

## Decision status

This ADR records the accepted target behavior. At the time of acceptance, the
ledger-only plaintext backup and transactional replacement restore are
implemented. The following parts remain future work and must not be treated as
current recovery guarantees: exporting app-owned reminder settings, leaving a
restored reminder disabled pending reconfirmation, offering a pre-restore
safety export, and presenting a persistent plaintext warning with first-use
confirmation. Delivery is tracked in
`docs/product/FUTURE_UPDATES_DECISION_SPEC.md`.

## Target behavior

Pocket backups are versioned, human-portable JSON documents. A full backup contains the complete financial ledger and app-owned settings that affect financial behavior, including the future period start day and reminder time. A restored reminder remains disabled until the user explicitly reconfirms it.

Restoring a backup transactionally replaces the current ledger after a preview and explicit confirmation. Before replacement, Pocket offers to export a safety backup. The user may explicitly continue without creating that safety file. Pocket does not merge two ledgers during restore.

Backup files remain plaintext. The export flow permanently explains that anyone with the file can read its contents and requires confirmation on first use. This favors dependable recovery and portability for a private, offline-first personal tool over password and key-recovery complexity.

This decision must be reconsidered if Pocket later introduces accounts, synchronization, automatic off-device storage, or materially broader sharing.
