# CADSS 1.0 — CoC Agent Data & Skill Standard

Status: Draft specification for implementation review  
Applies to: local-first Clash of Clans companion agent  
Related: CASL 1.0, IMPLEMENTATION_BLUEPRINT.md

## 1. Goals and non-goals

CADSS defines the canonical identity, metadata, relationships, provenance, validation, retention, and migration rules for durable application data.

Goals:
- One consistent record envelope across domain, vision, skills, research, memory, and operations.
- Referential integrity and reproducible calculations.
- Local-first storage with explicit manual backup and restore.
- Safe schema evolution without silently discarding unknown fields.
- Traceability from an AI conclusion to source evidence and user verification.

Non-goals:
- Cloud synchronization.
- Storing game credentials or automating game login/account switching.
- Treating model predictions as verified game facts.
- Making screenshots or image crops public or remotely accessible by default.

## 2. Normative language

The terms MUST, MUST NOT, SHOULD, SHOULD NOT, and MAY are normative requirements.

## 3. Record identity and envelope

Every durable record MUST have:
- `id`: immutable globally unique identifier.
- `record_type`: stable machine-readable type.
- `schema_version`: version of this record's schema.
- `created_at`, `updated_at`: UTC timestamps.
- `revision`: monotonically increasing integer within the record.
- `status`: lifecycle state appropriate to its record type.
- `source_refs`: zero or more provenance references.
- `owner_ref`: optional account/profile or parent owner.
- `tags`: optional searchable labels.
- `extensions`: optional namespaced extension data.

IDs MUST NOT be derived from display names, filenames, screen coordinates, or mutable attributes. Renaming a record MUST NOT change its ID. A revision changes content, not identity.

## 4. Canonical record types

### 4.1 Account and progression
- AccountProfile
- AccountSnapshot
- ResourceSnapshot
- ProgressEvent

AccountProfile stores a user-defined local profile label and optional public player tag/name. It MUST NOT store game passwords, session cookies, authentication tokens, or other login secrets.

AccountSnapshot captures time-bound progression values. Mutable values such as resources, trophies, league, and building levels SHOULD be snapshots rather than overwriting historical state.

### 4.2 Layout
- Layout
- LayoutObject
- LayoutMedia
- LayoutCoordinateSpace

A Layout references its account when known, Town Hall level, base category, source, game-data version, and verification state.

LayoutObject references a building definition when known, stores normalized bounds and center, confidence, verification state, and coordinate-space ID.

### 4.3 Army
- Army
- ArmyUnit
- ArmySpell
- ArmyEquipment
- ArmyEvaluation

Army data stores composition and intent, not game actions. It includes troop/spell counts, capacity assumptions, Town Hall constraints, heroes, equipment, siege machine, Clan Castle composition, and evaluation provenance.

### 4.4 Game knowledge
- GameDefinition
- UpgradeDefinition
- GameRule
- GameDataRelease
- EvidenceRecord

Every value that can change between game updates MUST be tied to a GameDataRelease or an explicit unknown/unversioned state. Facts require source references, retrieval/verification timestamps, and verification status.

### 4.5 Calculations and tasks
- CalculationRun
- CalculationInput
- CalculationResult
- Task
- TaskDependency
- Reminder
- TaskHistory

A CalculationRun MUST retain the input references, formula identifier/version, units, assumptions, output, and validation status. A result must be reproducible or clearly marked approximate.

A Task stores intent, dependencies, optional estimate, status, due/reminder data, and account references. Scheduling is advisory and does not interact with game controls.

### 4.6 Vision
- ImageAsset
- CropAsset
- SceneMap
- DetectedObject
- OCRRecord
- VisionRecord
- VisionValidation

Every CropAsset MUST reference its original ImageAsset. Every DetectedObject MUST reference a SceneMap. A VisionRecord MUST identify the processing pipeline and model versions used.

### 4.7 Skills and research
- SkillManifest
- SkillRevision
- SkillDependency
- SkillEvaluation
- ResearchItem
- SourceRecord
- EvidenceRecord

A skill revision is immutable after activation. Changes create a new revision. Research claims MUST link to evidence records. Evidence records distinguish direct source statements, user-provided information, and model inference.

### 4.8 Memory and operations
- WorkingMemory
- EpisodicMemory
- SemanticMemory
- AuditEvent
- BackupManifest
- MigrationRun

WorkingMemory is temporary and expires. EpisodicMemory records a task or decision event. SemanticMemory stores reusable claims with evidence and verification state. AuditEvent records important changes without storing secrets.

## 5. Provenance and verification

Verification states:
- `unverified`
- `model_inferred`
- `user_confirmed`
- `source_verified`
- `conflicted`
- `stale`
- `rejected`

Confidence score, source reliability, verification status, and freshness MUST be stored separately. A high model confidence score MUST NOT automatically change verification state.

SourceRecord should include source type, title/label, canonical URL where applicable, retrieval time, publication/update time if known, and a compact content fingerprint. Store only the minimum source excerpt required for traceability.

Conflicting evidence MUST remain visible. The system must not silently choose the newest or highest-confidence claim as truth without a documented resolution rule.

## 6. Coordinate system contract

Every spatial value MUST declare a coordinate-space reference.

Supported spaces:
1. Source-image normalized coordinates, with origin at top-left and values in the range 0..1.
2. Crop-relative normalized coordinates, with origin at top-left and values in the range 0..1.
3. Grid coordinates, with explicit row/column counts, origin, and orientation.
4. Layout-relative coordinates, only when a validated layout coordinate system exists.

Transforms MUST record source and target coordinate-space IDs and transform version. Pixel coordinates are derived from image dimensions and are not canonical identity.

## 7. Storage and integrity

Structured records belong in a relational database with foreign keys and transactions. Binary assets belong in app-private storage and are referenced by asset IDs plus relative storage keys. Raw absolute filesystem paths MUST NOT be exported as stable references.

Required constraints:
- Unique IDs.
- Foreign-key enforcement.
- Transactional multi-record updates.
- Asset hash and byte length.
- Parent-child constraints for crops and scene objects.
- Soft-delete/archive behavior for referenced records.
- Referential integrity audit before backup and restore.

## 8. Retention and privacy

- Image retention MUST be configurable.
- Temporary screenshots SHOULD expire unless explicitly saved into Vision Memory.
- Screenshots containing personal or sensitive information MUST NOT be sent to Gemini.
- Cloud image analysis requires clear user consent and a preview of the image/region being uploaded.
- API keys and secrets MUST never appear in logs, exports, skill manifests, or memory.
- Backups are local user-controlled files; sharing/export is always user initiated.

## 9. Versioning and migration

CADSS uses semantic versioning. Major versions may break compatibility; minor versions add backward-compatible fields or record types; patch versions clarify constraints or fix non-breaking defects.

Migrations MUST:
1. Check source and target versions.
2. Create or require a verified backup.
3. Run transactionally where possible.
4. Preserve unknown extension fields.
5. Validate record counts, IDs, foreign keys, and asset references.
6. Produce a MigrationRun report.
7. Roll back or retain the old dataset if validation fails.

A migration must never silently overwrite newer user data.

## 10. Backup package contract

A backup package contains:
- manifest with CADSS/CASL/app versions;
- database snapshot;
- asset manifest;
- image originals and crops included by selected scope;
- skill manifests and revisions;
- knowledge and memory records;
- optional task/reminder history;
- integrity hashes and byte lengths.

Restore MUST offer a preview, validate hashes, check schema compatibility, detect ID collisions, and preserve a rollback point. Full and incremental backup MAY be supported; incremental restore must verify its base backup.

## 11. Acceptance criteria

CADSS 1.0 implementation is acceptable when:
- all durable records use stable IDs and versioned envelopes;
- foreign-key and asset-reference checks pass;
- crops always resolve to original images;
- every calculation is traceable to inputs and formula version;
- model inference cannot be promoted to verified knowledge without a verification event;
- migrations preserve unknown fields and pass rollback tests;
- backup/restore preserves records and asset links;
- local browsing and deterministic calculations work offline.
