# Pocket project information

This directory is the human-oriented operational knowledge base for Pocket. It sits at the repository root so release, device, and recovery information is not mixed into the Android `app` module.

## Start here

- [Install Pocket on an Android device](installing-pocket.md): official downloads, sideloading, source builds, updates, and data-safety notes.
- [Implementation reference](implementation-reference.md): product boundaries, architecture, durable decisions, and repository history.
- [Windows, Android Studio, and physical-device testing](windows-android-studio-device-testing.md): working environment, build commands, ADB workflow, and device-test precautions.
- [Release signing and recovery](release-signing-and-recovery.md): permanent key handling, signed builds, verification, backup, uninstall, and restore.
- [Release verification](verification/release-verification.md): retained test evidence and known coverage gaps.

## Authority and maintenance

GitHub Issue [AIF31/Financial-App#1](https://github.com/AIF31/Financial-App/issues/1) is the canonical MVP product contract. `CONTEXT.md` defines canonical domain vocabulary. This directory records operational findings and evidence; it does not override either source.

Update these documents when the package name, SDK, signing certificate, backup format, release process, or supported test-device workflow changes. Never commit keystores, passwords, DPAPI credential files, raw financial backups, device serials, or other personal identifiers.

## Current handoff status

The MVP is implemented on `main`. Automated host, lint, and managed-device checks are retained in the verification record; physical-device checks should be repeated on a representative device before a release. Maximum-font and TalkBack checks remain release evidence gaps until they are explicitly exercised.
