package com.syhung.androidaiautomation.agent.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CadssDatabase(context: Context) : SQLiteOpenHelper(context, "cadss.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE records (
                id TEXT PRIMARY KEY NOT NULL,
                record_type TEXT NOT NULL,
                schema_version INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                revision INTEGER NOT NULL,
                status TEXT NOT NULL,
                owner_ref TEXT,
                source_refs TEXT NOT NULL DEFAULT '[]',
                tags TEXT NOT NULL DEFAULT '[]',
                payload TEXT NOT NULL,
                extensions TEXT NOT NULL DEFAULT '{}'
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_records_type_status ON records(record_type, status)")
        db.execSQL("CREATE INDEX idx_records_owner ON records(owner_ref)")
        db.execSQL("""
            CREATE TABLE assets (
                id TEXT PRIMARY KEY NOT NULL,
                parent_image_id TEXT,
                relative_path TEXT NOT NULL UNIQUE,
                mime_type TEXT NOT NULL,
                byte_length INTEGER NOT NULL,
                sha256 TEXT NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                purpose TEXT NOT NULL,
                FOREIGN KEY(parent_image_id) REFERENCES assets(id) ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_assets_parent ON assets(parent_image_id)")
        db.execSQL("""
            CREATE TABLE skill_revisions (
                skill_id TEXT NOT NULL,
                revision INTEGER NOT NULL,
                casl_version TEXT NOT NULL,
                cadss_min_version TEXT NOT NULL,
                status TEXT NOT NULL,
                manifest_json TEXT NOT NULL,
                workflow_json TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY(skill_id, revision)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE audit_events (
                id TEXT PRIMARY KEY NOT NULL,
                event_type TEXT NOT NULL,
                record_id TEXT,
                created_at INTEGER NOT NULL,
                details_json TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_audit_created ON audit_events(created_at)")
        db.execSQL("""
            CREATE TABLE schema_meta (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("INSERT INTO schema_meta(key, value) VALUES('cadss_version', '1.0.0')")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Forward-only migrations. Never drop user data during an upgrade.
        if (oldVersion < 2 && newVersion >= 2) {
            // Reserved for the next additive schema migration.
        }
    }
}
