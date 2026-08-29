---
status: accepted
date: 2026-08-29
---

# Keep portable backups plaintext and replace data on restore

Pocket backups are versioned, human-portable JSON documents. A full backup contains the complete financial ledger and app-owned settings that affect financial behavior, including the future period start day and reminder time. A restored reminder remains disabled until the user explicitly reconfirms it.

Restoring a backup transactionally replaces the current ledger after a preview and explicit confirmation. Before replacement, Pocket offers to export a safety backup. The user may explicitly continue without creating that safety file. Pocket does not merge two ledgers during restore.

Backup files remain plaintext. The export flow permanently explains that anyone with the file can read its contents and requires confirmation on first use. This favors dependable recovery and portability for a private, offline-first personal tool over password and key-recovery complexity.

This decision must be reconsidered if Pocket later introduces accounts, synchronization, automatic off-device storage, or materially broader sharing.
