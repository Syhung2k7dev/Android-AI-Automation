# Quality, Test and Release Gates

## 1. Required automated checks

### Static and build checks
- Android lint.
- Kotlin compilation for debug and release variants.
- Dependency vulnerability and license review.
- No secrets in repository, generated reports, or logs.
- Reproducible Gradle dependency resolution where possible.

### Unit tests
- CADSS record identity and schema validation.
- Foreign-key and repository invariants.
- Coordinate transforms across image/crop/grid spaces.
- Calculation reproducibility and unit consistency.
- CASL manifest parser and workflow validator.
- IF/ELSE nesting, bounded loops, and dependency cycle detection.
- Skill compatibility and capability enforcement.
- Gemini response schema validation and malformed output handling.

### Storage tests
- Database creation and upgrade from each supported schema version.
- Transaction rollback.
- Image/crop parent integrity.
- Backup and restore hashes.
- Unknown-field preservation.
- Corrupt/missing asset reporting.

### Vision tests
- Image hashing and deduplication.
- Crop bounds and coordinate transforms.
- OCR parsing fixtures.
- OpenCV matching against a labeled local fixture set.
- Confidence and verification states remain distinct.
- pHash stagnation signals do not trigger autonomous recovery.

### Gemini and network tests
- Timeout, 429, 5xx, malformed JSON, empty response.
- Retry budget and circuit-breaker behavior.
- Model identifier configuration validation.
- Consent gate for image uploads.
- No API key or sensitive image content in logs.

### Device tests
- OPPO Reno6 Z 5G / Android 13 / ColorOS.
- Process death and restoration.
- App-private storage and document picker.
- Notification and reminder behavior.
- Battery, thermal, and memory pressure.
- Offline account, layout, army, and calculator views.

## 2. Release gates

A release candidate cannot be declared ready unless:
- all mandatory CI jobs pass;
- migrations pass on a copy of representative legacy data;
- backup and restore pass;
- no critical/high unresolved security findings;
- target-device smoke tests pass;
- release APK is signed with the intended release key;
- Play Protect or Android security warnings are investigated rather than bypassed;
- permissions are granted only through Android system settings and user consent;
- feature inventory and known limitations are documented.

## 3. APK size and feature completeness

APK size alone does not prove feature completeness. The release report must include:
- enabled modules and packaged native ABIs;
- dependency and asset inventory;
- build variant and signing status;
- test results;
- installed APK size and app-installed size;
- target-device smoke-test result.

## 4. Feature acceptance checklist

- [ ] CADSS database and repositories
- [ ] Account/profile tracking
- [ ] Layout storage and coordinate system
- [ ] Army composition storage and capacity validation
- [ ] Versioned game knowledge
- [ ] Deterministic resource calculator
- [ ] Building/task planner
- [ ] Original image and crop memory
- [ ] SceneMap and OCR references
- [ ] Vision provenance and user corrections
- [ ] CASL parser and validator
- [ ] Skill registry and lifecycle
- [ ] Skill evaluation and version compatibility
- [ ] Gemini Gateway and consent
- [ ] Agent Core context and planning
- [ ] Local-first manual backup/restore
- [ ] Offline mode and target-device tests

## 5. Scope exclusions

This release is a CoC companion and analysis agent. It does not include unattended gameplay, attack/farm automation, automated in-game account switching, or techniques to hide automation or evade game/platform enforcement.
