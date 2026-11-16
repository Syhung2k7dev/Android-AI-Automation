# CASL 1.0 — CoC Agent Skill Language

Status: Draft specification for implementation review  
Related: CADSS 1.0, IMPLEMENTATION_BLUEPRINT.md

## 1. Purpose

CASL is a declarative, capability-limited language for describing CoC companion skills. It separates skill metadata, workflow definition, and evaluation. It is not a general programming language and MUST NOT execute arbitrary source code.

CASL skills are intended for research, analysis, account/profile tracking, layout and army assessment, deterministic calculations, task planning, and reminders. CASL skills do not automate gameplay, attacks, farming, in-game account switching, or evasion of game/platform protections.

## 2. Package structure

Every skill package has three logical parts:

1. Manifest: identity, version, contracts, dependencies, permissions, provenance.
2. Workflow: typed declarative steps and control flow.
3. Evaluation: fixtures, expected outcomes, constraints, and acceptance thresholds.

A package is immutable once approved and activated. Updates create a new skill revision.

## 3. Manifest contract

Required metadata:
- skill_id: immutable ID.
- name and description.
- skill_version: semantic version.
- casl_version: supported CASL version.
- cadss_min_version: minimum data-standard version.
- category and purpose.
- lifecycle status.
- provenance and author/source.
- input and output contracts.
- dependency list.
- capability/permission list.
- workflow reference.
- evaluation specification reference.
- created/updated timestamps.

Input contract fields:
- name;
- type;
- required;
- optional default;
- allowed range or enum;
- unit where relevant;
- source requirement;
- validation rule.

Output contract fields:
- name;
- type;
- unit where relevant;
- confidence semantics;
- verification semantics;
- references to records/evidence;
- warnings and errors.

Unknown manifest fields must be preserved in extension data or cause a clear compatibility warning. They must never be silently discarded.

## 4. Workflow model

A workflow is an ordered graph of typed steps. Each step has a stable step ID, type, declared inputs, declared outputs, timeout where relevant, and error policy.

Supported conceptual step families:

### Context and retrieval
- INPUT
- CONTEXT
- READ_RECORD
- SEARCH_KNOWLEDGE
- READ_MEMORY
- GET_SOURCE

### AI and research
- REASON
- RESEARCH
- SUMMARIZE_EVIDENCE
- REQUEST_USER_CONFIRMATION

### Vision
- ANALYZE_IMAGE
- GET_CROP
- GET_SCENE_MAP
- OCR_QUERY
- MATCH_TEMPLATE

### Deterministic local services
- CALCULATE
- COMPARE
- VALIDATE
- PLAN_TASKS

### Composition and memory
- CALL_SKILL
- WRITE_MEMORY
- UPDATE_RECORD
- CREATE_TASK
- CREATE_REMINDER

### Control and output
- IF
- ELSE
- END_IF
- LOOP with a mandatory maximum iteration count
- WAIT with a bounded duration, only for application scheduling
- OUTPUT
- ON_ERROR

These are semantic operation names, not permission to access arbitrary platform APIs. Each operation is implemented by a registered capability provider.

## 5. Control-flow constraints

- Workflows must have a finite maximum number of steps.
- IF/ELSE blocks must be balanced and properly nested.
- LOOP must declare a finite maximum iteration count.
- Every external or model call must have a timeout and bounded output size.
- Recursive skill calls are forbidden.
- Dependency cycles are forbidden.
- A workflow cannot dynamically construct executable code.
- A workflow cannot bypass validation or permission checks through nested skill calls.

The parser must reject malformed workflows before execution. Runtime must enforce limits again; parser validation alone is not a security boundary.

## 6. Capability model

Each skill requests named capabilities, such as:
- knowledge.read
- account.read
- account.update_user_fields
- layout.read
- layout.analyze
- army.read
- calculation.run
- vision.local_analyze
- vision.cloud_analyze
- memory.read
- memory.write
- task.create
- reminder.create
- gemini.reason
- gemini.research

Capabilities are granted by the application, not by the skill author or Gemini. A skill cannot grant capabilities to itself. Cloud analysis requires explicit user consent for the specific data being uploaded.

The CoC skill capability registry MUST NOT expose gameplay gesture dispatch, game account login/switching, unattended gameplay, attack/farm loops, or anti-cheat evasion.

## 7. Runtime execution contract

Execution lifecycle:
1. Resolve skill ID and exact revision.
2. Verify package signature/integrity where available.
3. Check CASL/CADSS compatibility.
4. Validate input contract.
5. Resolve dependencies and detect cycles.
6. Compute granted capability set.
7. Build minimal context from CADSS references.
8. Execute each step through registered providers.
9. Enforce timeout, step, loop, and output limits.
10. Validate outputs and provenance.
11. Persist permitted results and an audit event.
12. Return structured result to Agent Core.

Runtime result includes:
- status;
- primary output;
- typed output values;
- confidence and verification state;
- source/evidence references;
- warnings;
- errors;
- execution duration;
- skill ID and revision;
- model and data versions where relevant.

## 8. Error handling

Error categories:
- invalid_manifest;
- incompatible_version;
- missing_dependency;
- permission_denied;
- invalid_input;
- provider_unavailable;
- timeout;
- invalid_model_output;
- validation_failed;
- storage_failure;
- cancelled_by_user.

A skill must not silently convert a failed or missing result into a verified value. ON_ERROR may choose a safe fallback, request confirmation, or stop. It may not disable permission checks.

## 9. Skill lifecycle and approval

Discovered -> Researching -> Draft -> Testing -> Approved -> Active -> Deprecated -> Archived.

Transitions:
- Draft to Testing requires valid manifest and parseable workflow.
- Testing to Approved requires passing evaluation and permission review.
- Approved to Active requires explicit user approval.
- Active to Deprecated requires replacement or support policy.
- Deprecated to Archived requires retention and dependency checks.

Gemini may propose a new skill, manifest, or workflow, but cannot activate it or grant permissions.

## 10. Evaluation specification

Each skill evaluation defines:
- test fixture IDs;
- input cases;
- expected output properties;
- deterministic assertions;
- acceptable confidence/verification state;
- known failure cases;
- timeout and resource budgets;
- minimum pass ratio;
- evaluation version.

AI-generated skills require adversarial malformed-input tests and unsupported-claim checks. Deterministic calculations require exact expected results. Vision skills require labeled test images with explicit usage rights and coordinate annotations.

## 11. Compatibility

Each skill declares:
- supported CASL version range;
- minimum CADSS version;
- tested dependency revisions;
- required capability identifiers;
- migration notes.

Incompatible skills are not executable. Read-only inspection may be permitted. Dependency upgrades must rerun evaluation.

## 12. Security and trust

- No arbitrary Kotlin, Java, shell, JavaScript, reflection, dynamic class loading, or unrestricted expressions.
- No arbitrary network or filesystem access.
- No direct database access.
- No secret access.
- No hidden execution or permission escalation.
- Gemini output is untrusted input and must pass parser, schema, capability, and semantic validation.
- All persisted AI-derived facts retain provenance and verification state.
- User cancellation must stop the current skill promptly.

## 13. CASL 1.0 acceptance criteria

- Valid manifests parse; invalid manifests are rejected with actionable errors.
- Workflow parser rejects unknown operations and malformed control flow.
- Runtime enforces finite step, loop, output, and time limits.
- Capability denial is enforced even for nested skill calls.
- Dependency cycles and incompatible revisions are rejected.
- Gemini-generated workflows remain drafts until evaluation and user approval.
- Every result identifies skill revision, data/model version, provenance, and verification status.
