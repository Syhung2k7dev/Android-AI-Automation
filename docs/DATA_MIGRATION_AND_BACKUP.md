# Data Migration, Backup and Restore Plan

## 1. Principles

Migration is additive, reversible, and evidence-driven. Existing data remains intact until import and integrity checks succeed. No migration may silently discard unknown fields or overwrite newer records.

## 2. Legacy sources

| Legacy source | Target |
|---|---|
| SharedPreferences profile/workflow queue | LegacyWorkflowProfile records |
| Reminder/scheduler preferences | Reminder and Task records |
| TemplateStore PNG files | ImageAsset and TemplateAsset records |
| Current workflow JSON v1/v2 | LegacyWorkflow records |
| Existing OCR and image-analysis output | Expiring WorkingMemory or explicit VisionRecord |
| Gemini usage preferences | AIUsageLedger records |
| User-entered profile labels | AccountProfile local display labels, only after user confirmation |

Legacy tap/swipe workflows must not be automatically imported as CoC skills. They remain separate legacy artifacts.

## 3. Migration stages

1. Inventory: detect all old preference keys, files, workflow versions, and existing records.
2. Backup: create a local snapshot and asset manifest.
3. Preview: show record counts, skipped files, conflicts, and estimated storage.
4. Import: create new records in a staging transaction.
5. Verify: compare counts, IDs, hashes, foreign keys, and asset references.
6. Activate: mark the new data store active only after validation.
7. Retain: preserve legacy data for a defined rollback window.
8. Cleanup: remove legacy copies only after explicit policy and successful backup.

## 4. Failure handling

A failed import must leave the old store usable. Partial imports are marked incomplete and are not visible as canonical records. A MigrationRun report records stage, timestamps, counts, warnings, and failure reason without storing secrets.

## 5. Backup package

A backup contains a manifest, database snapshot, selected asset files, skills, knowledge, memory, and optional task history. Each file entry includes a relative path/key, byte length, media type, and cryptographic hash.

Backup scope options:
- Full: all durable records and referenced assets.
- Data-only: structured records without large image assets, clearly warning about missing media.
- Selected: user-selected accounts, layouts, armies, skills, or date ranges.

No backup includes API keys, login secrets, Android private credentials, or transient screenshot buffers.

## 6. Restore

Restore flow:
1. Select backup through Android document picker.
2. Validate package format and manifest.
3. Verify hashes and archive entry paths.
4. Check CADSS/CASL/app compatibility.
5. Preview counts, conflicts, and storage requirements.
6. Create rollback snapshot of current local data.
7. Restore into staging.
8. Verify foreign keys and all included asset links.
9. Activate only after validation.
10. Retain rollback snapshot until user confirms success.

Path traversal, duplicate archive entries, oversized decompression, and unsupported schema versions must be rejected.

## 7. Acceptance tests

- Corrupt hash rejects restore.
- Missing image asset is reported and never silently linked to another image.
- Duplicate IDs produce a conflict report.
- Unknown extension fields survive export/import.
- Failed migration preserves the original data.
- Full backup restores records and image/crop relationships.
- Data-only backup clearly marks unavailable media.
- Incremental backup, if implemented, cannot restore without its exact base.
