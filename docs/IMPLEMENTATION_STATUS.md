# Implementation Status — CoC Agent Foundation

This document separates repository changes from capabilities that are not yet implemented or verified.

## Added to the feature branch

- CADSS record envelope and SQLite database tables.
- Generic CADSS repository for records and audit events.
- Typed account, layout, army, and game-definition facade.
- Vision image/crop persistence with SHA-256 metadata.
- CASL JSON manifest/workflow validation.
- Restricted capability-provider execution skeleton.
- Skill revision registry with explicit approval gate.
- Resource gap calculator and dependency-aware upgrade scheduling.
- Local JSON/ZIP backup export for records and Vision Memory assets.
- Architecture, data standard, skill language, migration, and release documentation.

## Important limitations

- The generic repository is an initial foundation, not the complete relational CADSS schema. Domain payloads are currently JSON records.
- Skill Registry stores revisions, but a full CASL parser, IF/ELSE/LOOP runtime, dependency resolver, sandbox, and evaluation runner remain outstanding.
- Vision Memory stores original images and crops with metadata; OCR/OpenCV/ML Kit outputs are not yet automatically linked into complete SceneMap and validation records.
- Backup export exists as an initial local archive. Restore, incremental backup, streaming large files, archive hardening, and full asset manifest verification are not implemented.
- The current calculators require verified game definitions supplied by the user or a source adapter. They do not embed a current Clash of Clans game-data catalog.
- The Gemini client has not yet been fully refactored behind the new Agent Core gateway.
- The new services are not yet wired into MainActivity screens.
- No claim is made that the project compiles or that an APK has been produced. CI and target-device tests are required.

## Next integration work

1. Run Gradle compilation and unit tests on the feature branch.
2. Fix compiler and migration errors before adding more UI.
3. Add Room or formal relational DAOs and complete CADSS constraints.
4. Add robust backup restore and integrity verification.
5. Connect current ML Kit/OpenCV components to Vision Memory.
6. Implement CASL control flow, dependency checks, evaluation and safe execution limits.
7. Add account/layout/army/knowledge/calculator screens.
8. Refactor GeminiCloudClient behind a consent-aware gateway.
9. Run CI and verify on the target OPPO device.

## Product boundary

CoC skills are limited to companion functions: research, analysis, tracking, calculations, planning, and reminders. They do not automate gameplay, attack/farming, in-game account switching, or anti-cheat evasion.
