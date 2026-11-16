# CoC Agent Implementation Blueprint

**Repository:** Syhung2k7dev/Android-AI-Automation  
**Target device:** OPPO Reno6 Z 5G, Android 13 / ColorOS  
**Baseline:** current `main` branch reviewed for this blueprint  
**Standards:** CADSS 1.0 and CASL 1.0  
**Architecture:** Gemini-centered hybrid, local-first storage, manual backup

## 1. Product boundary

The product direction is a Clash of Clans specialist companion agent. Gemini remains the primary reasoning and research model. Local Android components provide OCR, image processing, structured storage, deterministic calculations, retrieval, workflow validation, and offline access to approved knowledge.

The CoC skill domain is for research, profile tracking, layout and army analysis, resource calculations, upgrade planning, reminders, and strategy advice. It must not use Accessibility gestures to automate attacks, farming, account switching in the game, or other gameplay; it must not conceal automation or evade anti-cheat or platform protections. Existing generic, explicitly user-started accessibility workflows remain isolated from CoC skills and cannot be invoked by the CoC skill registry.

## 2. Current repository baseline

The repository is a single Android application module using package `com.syhung.androidaiautomation`.

### Existing components to retain

| Current component | Current responsibility | Blueprint disposition |
|---|---|---|
| MainActivity | Large programmatic UI and workflow editor | Retain temporarily; progressively split into feature screens |
| AutomationAccessibilityService | User-started gestures, screenshots, OCR conditions, image conditions, dashboard, watchdog | Isolate as a generic user-controlled service; do not expose gameplay actions to CoC skills |
| AutomationForegroundService | Visible foreground notification for user-started workflows | Retain for eligible user-started workflows |
| WorkflowStep | Existing tap, swipe, wait, OCR/image conditions and branches | Preserve as legacy workflow model; do not treat it as the final CASL model |
| WorkflowJsonCodec | Version-2 workflow import/export | Retain as legacy importer/exporter and add migration |
| GeminiCloudClient | Gemini text, image analysis, workflow draft, usage and resilience | Refactor behind Gemini Gateway; preserve opt-in image upload and consent |
| CloudKeyStore | Encrypted API key storage | Retain initially; review dependency lifecycle and key handling |
| TemplateStore | Local PNG template storage | Evolve into Vision Asset Store |
| LocalImageMatcher | Kotlin image matching fallback | Retain as fallback and add tests |
| OpenCvImageMatcher | Native OpenCV template matching | Retain as local vision primitive |
| PerceptualHash | Screen similarity/inactivity signal | Retain only as a diagnostic signal, not an autonomous recovery trigger |
| SchedulerReceiver / SchedulerBootReceiver | One-shot and recurring reminders | Retain and migrate configuration into structured task storage |

### Existing limitations confirmed from source

- The current workflow is a small gesture-oriented model with a maximum of 50 steps and nested IF validation; it is not a general-purpose skill language.
- Workflow JSON version 2 supports tap, swipe, wait, OCR conditions, image-template conditions, and branches. Gemini workflow drafting currently emits a narrower subset and returns a draft for review.
- Profile queue data is not a normalized multi-account domain model.
- TemplateStore stores named PNG files in app-private storage; it does not yet provide a complete original-image, crop, scene-map, provenance, and validation model.
- GeminiCloudClient owns request handling directly. It should become a transport/service behind a gateway, not be called independently by every feature.
- MainActivity and AutomationAccessibilityService are large classes and should be split gradually, not rewritten in one pass.
- The current CI workflow builds a debug APK but does not yet demonstrate a complete unit, migration, instrumentation, or device-compatibility test suite.
- The current application uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9 in CI, compile SDK 35, target SDK 35, minimum SDK 26, and packages arm64-v8a.

## 3. Target architecture

### 3.1 Application layers

1. **Presentation:** dashboard, accounts, layouts, armies, resources, skills, research, vision library, history, and backup screens.
2. **Agent Core:** goal interpretation, task planning, skill selection, context assembly, result evaluation, and user confirmation.
3. **AI Gateway:** Gemini model configuration, request queue, consent checks, token accounting, bounded retry, circuit breaker, and response validation.
4. **Skill Platform:** skill registry, manifest validation, CASL parser, workflow validator, sandboxed execution, dependency resolution, evaluation, and version lifecycle.
5. **Domain Services:** account, layout, army, building, resource, task, and game-knowledge services.
6. **Vision Platform:** image ingestion, crop management, grid mapping, OCR, OpenCV matching, local inference, scene maps, and vision validation.
7. **Data Platform:** repositories, SQLite database, file asset store, full-text search, memory, migration, and backup.
8. **Android Platform Adapters:** accessibility, notifications, scheduler, document picker, lifecycle, and device capability checks.

### 3.2 Dependency direction

Presentation calls domain/application services. Agent Core calls Skill Platform and domain services. Skills can request approved capabilities through typed interfaces. Domain services use repositories. Repositories use the database or file store. Gemini is accessed only through AI Gateway.

A skill must never access Android services, arbitrary files, network connections, or database internals directly. All capabilities are explicitly granted and validated.

## 4. CADSS 1.0 data platform

### 4.1 Storage choices

- **SQLite, preferably through Room:** canonical structured records and relational integrity.
- **App-private file storage:** original images, crops, thumbnails, exported packages, and other binary assets.
- **SQLite FTS:** initial keyword/full-text search.
- **Optional local vector index:** later phase only, after measuring device memory, storage, and retrieval quality.
- **DataStore or a small preferences store:** non-domain settings only. Do not use SharedPreferences as the main account/layout/army database.
- **Manual backup package:** exported through Android's document picker; no automatic cloud sync in this release.

### 4.2 Canonical record envelope

Every durable domain record must carry a stable ID, type, schema version, created and updated timestamps, revision, status, source/provenance reference, and optional owner/account reference. IDs are immutable. Display names are not identifiers.

### 4.3 Core record families

| Family | Main records | Key relationships |
|---|---|---|
| Accounts | AccountProfile, AccountSnapshot, ResourceSnapshot | Account to layouts, armies, tasks, snapshots |
| Layouts | Layout, LayoutObject, LayoutMedia | Layout to source image, scene map, objects |
| Armies | Army, ArmyUnit, ArmySpell, ArmyEquipment | Army to account, game data and evaluations |
| Game knowledge | BuildingDefinition, TroopDefinition, SpellDefinition, UpgradeDefinition | Definitions to game-data version and source |
| Calculations | CalculationRun, CalculationInput, CalculationResult | Calculation to source snapshots and formula version |
| Tasks | Task, TaskDependency, Reminder, TaskHistory | Task to account, calculation and schedule |
| Vision | ImageAsset, CropAsset, SceneMap, DetectedObject, OCRRecord, VisionRecord | Image to crops, objects, OCR and validation |
| Skills | SkillManifest, SkillRevision, SkillDependency, SkillEvaluation | Skill to CASL workflow and knowledge references |
| Research | ResearchItem, SourceRecord, EvidenceRecord | Research to source, claim and skill |
| Memory | WorkingMemory, EpisodicMemory, SemanticMemory | Memory to task, evidence and skill |
| Operations | AuditEvent, BackupManifest, MigrationRun | Operations to revisions and app version |

### 4.4 Relationship rules

- Use immutable IDs, never display names, as foreign references.
- Every crop must reference its parent image and retain source-coordinate bounds.
- Every detected object must reference a SceneMap and a coordinate-space definition.
- Every game-data value must include source, verification state, and applicable game-data version.
- Every calculation must retain its input references, formula version, assumptions, units, and output.
- Every skill dependency must point to a specific compatible skill range or pinned revision.
- Deletion of referenced records must be restricted; use archive/tombstone states and explicit dependency handling.
- Avoid duplicating canonical data across account, layout, army, and memory stores. Use references and snapshots.

### 4.5 Confidence and provenance

Do not use one confidence value to represent every kind of certainty. Store separately:
- model confidence or score;
- verification status;
- source reliability;
- freshness or last-verified time;
- reviewer or verification method.

Gemini-generated claims remain unverified until checked against an authoritative source or explicitly confirmed by the user. A model's confidence is not proof.

## 5. Vision Memory specification

### 5.1 Asset lifecycle

Original image -> metadata and hash -> selected crops -> OCR and local features -> SceneMap -> Gemini interpretation when consented -> validation -> reusable memory.

Store original images and crops, but apply configurable retention and size limits. Avoid retaining screenshots containing personal or sensitive information. Cloud image analysis requires explicit consent for each upload and must show what is being sent.

### 5.2 Coordinate spaces

Each spatial record must declare its coordinate space:
- source-image normalized coordinates;
- crop-relative normalized coordinates;
- grid coordinates with grid dimensions and origin;
- layout-relative coordinates where a validated layout coordinate system exists.

Never mix coordinate spaces without an explicit transform. Pixel coordinates are derived display values, not canonical coordinates.

### 5.3 SceneMap contents

A SceneMap contains image reference, dimensions, grid specification, detected object references, OCR references, feature summaries, processing model/version, and validation state. Detected objects contain class label, bounding box, center, optional grid cells, color/edge summaries, confidence, and verification state.

### 5.4 Learning safeguards

Store predicted labels, user-verified labels, rejected predictions, and corrections separately. Do not automatically promote Gemini predictions into verified templates or canonical game knowledge. Template updates require duplicate checks, provenance, and validation.

## 6. CASL 1.0 skill platform

### 6.1 Three-part skill package

1. **Manifest:** identity, description, category, version, compatibility, input/output contracts, dependencies, permissions, and provenance.
2. **Workflow:** typed steps for context retrieval, reasoning requests, research requests, image analysis, local calculations, conditions, skill calls, memory operations, validation, and output.
3. **Evaluation specification:** test fixtures, expected outputs, validation rules, failure cases, and minimum acceptance criteria.

YAML or JSON is used for manifests and structured metadata. A dedicated declarative workflow format is used for execution. The workflow format must not evaluate arbitrary Kotlin, Java, shell, JavaScript, or unrestricted expressions.

### 6.2 Required workflow capabilities

- Typed input and output contracts.
- Context retrieval with explicit source references.
- Gemini reasoning and research requests through the AI Gateway.
- Vision analysis through the Vision Platform.
- Deterministic local calculation calls.
- Bounded IF/ELSE branching.
- Bounded loops and timeouts.
- Skill-to-skill calls with dependency checks.
- Memory reads and writes with provenance.
- Validation and structured error handling.
- User confirmation gates for sensitive or irreversible changes.

### 6.3 Execution restrictions

CASL is declarative and capability-based. No unrestricted file access, arbitrary networking, hidden background execution, dynamic code loading, or permission escalation. CoC skills are limited to research, analysis, tracking, calculations, planning, and reminders. They cannot dispatch gameplay gestures, automate attacks or farming, switch game accounts, or bypass game/platform protections.

### 6.4 Skill lifecycle

Discovered -> Researching -> Draft -> Testing -> Approved -> Active -> Deprecated -> Archived.

A skill cannot become Active solely because Gemini says it is ready. It must pass schema validation, dependency validation, permission review, deterministic tests where applicable, and user approval for initial activation.

### 6.5 Skill version compatibility

Each skill declares:
- CASL minimum and maximum tested version;
- CADSS minimum compatible version;
- dependency IDs and compatible revisions;
- required capabilities;
- migration notes;
- evaluation suite revision.

Unknown or incompatible skills are rejected or opened in read-only inspection mode. They are never silently executed.

## 7. Gemini-centered hybrid execution

Gemini remains the primary reasoning and research model. Local components handle:
- OCR with ML Kit;
- image processing and matching with OpenCV and Kotlin fallback;
- optional compact on-device inference through LiteRT after benchmarking;
- structured calculations;
- database retrieval and memory;
- workflow parsing and validation;
- local search and offline access.

All Gemini calls go through one AI Gateway. It owns model selection, API version, consent, token usage, bounded retries, rate tracking, circuit breaker, request logging, and output schema validation. Verify the configured Gemini model identifier against Google's current model catalog before release; do not assume the current constant remains valid.

Gemini should receive only the relevant context selected by the Agent Core, not the entire database or all stored images.

## 8. Local calculators

### Resource and building calculator

Inputs are versioned building definitions and an account's verified resource snapshot. Outputs include missing resources, totals, upgrade costs, assumptions, and source references.

### Building and task planner

Inputs include builder availability, task duration, prerequisites, priority, and account context. Outputs include a dependency-aware schedule and completion estimates. Calculations must be deterministic and reproducible from stored inputs and formula versions.

Neither calculator should invent missing game values. Unknown or stale values must be surfaced as unknown or requiring refresh.

## 9. Migration from current implementation

| Existing storage or class | Migration path |
|---|---|
| SharedPreferences profile queue | Import into Profile/Workflow records; preserve original until validated |
| Scheduler SharedPreferences | Import into Reminder and Task records |
| OCR result preferences | Move transient results to session/working memory with expiry |
| TemplateStore PNG directory | Import as ImageAsset or TemplateAsset records with hashes and metadata |
| WorkflowJsonCodec v2 | Keep as legacy importer; map supported steps to a versioned legacy workflow representation |
| WorkflowStep | Keep isolated from CASL domain models; no direct inheritance or semantic equivalence |
| GeminiCloudClient | Wrap/refactor behind Gemini Gateway while preserving opt-in consent |
| MainActivity | Split incrementally by feature after domain/data foundations exist |
| Accessibility service | Keep isolated as a generic user-controlled platform adapter |

Migration must be additive and reversible. Do not delete old data until import, integrity checks, and backup validation pass.

## 10. Build and quality gates

### Required test layers

- Unit tests: schema validation, ID generation, calculations, coordinate transforms, CASL parser, workflow validation, version comparison.
- Repository tests: CRUD, foreign-key integrity, migrations, backup manifests.
- Vision tests: crop coordinate correctness, hash stability, template matching fixtures, OCR result parsing.
- Skill tests: valid/invalid manifests, dependency cycles, permission denial, timeout handling, malformed Gemini output.
- Instrumentation tests: document picker, database lifecycle, image storage, notification/scheduler behavior.
- Device validation: OPPO Reno6 Z / Android 13 / ColorOS, memory pressure, thermal behavior, process recreation, permission state, offline operation.

### CI improvements

The current workflow only assembles a debug APK. Extend CI in stages to run unit tests, lint/static checks, migration tests, and then assemble the APK. Upload the APK only when all required gates pass. Keep debug and release signing separate. Do not treat APK size or a successful source commit as proof of feature completeness.

### Release gates

- No secrets in source control or logs.
- No unvalidated skill execution.
- No database migration without a tested rollback path.
- No cloud screenshot upload without explicit consent.
- No silent Accessibility permission activation or security bypass.
- Verify model availability and API compatibility before release.
- Investigate Play Protect warnings; never instruct users to disable protection.

## 11. Recommended implementation phases

### Phase 0 — Stabilize the baseline
Record current build status, inspect the latest CI result, add baseline tests, and document current behavior. Do not add large features in this phase.

### Phase 1 — CADSS foundation
Introduce database/repository boundaries, canonical IDs, schema metadata, source records, and migration infrastructure. Import existing profiles, reminders, and templates without removing their original copies.

### Phase 2 — Domain storage
Add Account, Snapshot, Layout, Army, BuildingDefinition, ResourceSnapshot, and Calculation records. Build read-only views first, then editing and validation.

### Phase 3 — Vision Memory
Introduce ImageAsset, CropAsset, SceneMap, OCRRecord, VisionRecord, coordinate-space definitions, provenance, and storage limits. Reuse current OpenCV and Kotlin matchers.

### Phase 4 — CASL and Skill Registry
Define manifest validation, workflow grammar, parser, validator, capability registry, dependency resolver, skill lifecycle, and legacy workflow adapter. Keep execution restricted to analysis and planning capabilities.

### Phase 5 — Gemini Gateway and Agent Core
Centralize Gemini calls; add context retrieval, goal interpretation, planning, skill selection, structured output validation, and memory policies.

### Phase 6 — Skill Research and Builder
Add source capture, evidence records, draft skill generation, evaluation fixtures, approval flow, versioning, and rollback.

### Phase 7 — Calculators, planner, and backup
Complete deterministic resource/building calculations, dependency-aware planning, full backup, incremental backup, integrity checks, and restore validation.

### Phase 8 — UI and device hardening
Split MainActivity into feature areas, add dashboard observability, benchmark memory/thermal usage, verify ColorOS behavior, and complete release signing and security review.

## 12. Definition of done

The architecture is ready for a first production-oriented release when:
- all durable CoC data uses CADSS records and repositories;
- all active skills have validated CASL manifests and evaluation results;
- Vision records retain image/crop provenance and coordinate-space definitions;
- Gemini calls are centralized and consent-aware;
- local calculations are deterministic and reproducible;
- migrations and backup/restore pass integrity tests;
- offline data browsing and local calculations work without Gemini;
- the app builds and passes CI and target-device checks;
- CoC skills remain within the documented companion/analysis boundary.

## 13. Immediate next work

Start with Phase 0 and Phase 1 only. Do not attempt a large rewrite of MainActivity, AccessibilityService, or the workflow engine before the data foundation and migration tests exist. The first implementation milestone is a stable CADSS database layer that can import current profiles, reminders, and image templates without data loss.
