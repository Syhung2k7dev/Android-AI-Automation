# Implementation Status — CoC Agent Foundation

This document separates committed repository work from capabilities that remain unimplemented or unverified.

## Implemented on this feature branch

- CADSS record envelope, SQLite tables, repository and audit trail.
- Typed local account, base-layout, army-composition and game-definition record facade.
- Local Vision Memory image import, original-image persistence, crop metadata support, SHA-256 image metadata and image-to-record linkage.
- CASL manifest/workflow validation, nested IF/ELSE and bounded LOOP validation.
- CASL interpreter with bounded step count, bounded loop count, wait limit, explicit user approval and registered capability providers.
- Versioned skill registry, draft saving, explicit activation approval and latest-revision listing.
- Resource gap calculator and multi-builder dependency scheduler.
- Local ZIP backup export and merge-only restore with archive path checks and expansion-size limit.
- CoC companion dashboard for account/layout/army records, calculations, skill drafts, Vision Memory image import, record browsing and backup/restore.
- Main screen entry point to the companion dashboard.
- Unit tests for resource calculations and building scheduling.
- Architecture, data standard, skill language, migration, quality gate and implementation status documents.

## Important limitations

- CADSS currently uses a versioned JSON record envelope in SQLite. It is not yet the complete normalized relational schema described in the specification.
- CASL runtime is a bounded capability interpreter, not a full production skill platform. It still needs comprehensive automated tests, dependency graph resolution, evaluation datasets, cancellation/progress reporting, persisted execution traces, and stricter per-skill capability-to-step authorization.
- Vision Memory can import and store user-selected images and crops; automatic ML Kit/OpenCV scene extraction and Gemini-assisted validated learning are not yet wired end-to-end.
- The dashboard is a functional initial native UI, not the final polished product experience.
- Backup restore is merge-only and validates archive paths and size, but still needs transactional rollback, per-asset hash verification, duplicate conflict preview, and stress testing on large archives.
- The resource calculator requires values supplied by the user and clearly marks missing source/version information. No current authoritative CoC balance dataset is bundled.
- The new Agent Core has not yet fully replaced/refactored all existing Gemini calls behind a unified gateway.
- Android CI must pass after the latest commits. A successful build does not replace device tests on the target OPPO phone.
- Play Protect and Android Accessibility protections must not be bypassed; distribution and permissions must use normal Android mechanisms.

## Validation required before release

1. GitHub Actions testDebugUnitTest and assembleDebug on the latest commit.
2. Verify the APK artifact size and inspect packaged native libraries and dependencies.
3. Install through normal Android package installation and check Play Protect status; address signing/reputation false positives through legitimate release signing and review channels.
4. On the target device, verify dashboard navigation, profile CRUD, image import, backup export/restore, CASL draft validation, calculator results, and existing app workflows.
5. Add integration tests for database migrations, asset integrity, backup rollback, CASL control flow, nested branches and loop boundaries.

## Product boundary

The CoC companion supports research, analysis, tracking, calculations, planning, data management and reminders. It does not automate gameplay, attacks/farming, in-game account switching, or anti-cheat evasion.
