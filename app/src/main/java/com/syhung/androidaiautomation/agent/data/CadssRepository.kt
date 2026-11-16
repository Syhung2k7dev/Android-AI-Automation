package com.syhung.androidaiautomation.agent.data

import android.content.ContentValues
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CadssRepository(context: Context) {
    private val helper = CadssDatabase(context.applicationContext)

    fun put(record: CadssRecord) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val existing = db.rawQuery("SELECT revision, created_at FROM records WHERE id = ?", arrayOf(record.id))
            val revision: Long
            val createdAt: Long
            existing.use {
                if (it.moveToFirst()) {
                    revision = it.getLong(0) + 1
                    createdAt = it.getLong(1)
                } else {
                    revision = record.revision.coerceAtLeast(1)
                    createdAt = record.createdAt
                }
            }
            val values = ContentValues().apply {
                put("id", record.id)
                put("record_type", record.type)
                put("schema_version", record.schemaVersion)
                put("created_at", createdAt)
                put("updated_at", System.currentTimeMillis())
                put("revision", revision)
                put("status", record.status)
                put("owner_ref", record.ownerRef)
                put("source_refs", JSONArray(record.sourceRefs).toString())
                put("tags", JSONArray(record.tags).toString())
                put("payload", record.payload)
                put("extensions", record.extensions)
            }
            val changed = db.update("records", values, "id = ?", arrayOf(record.id))
            if (changed == 0) db.insertOrThrow("records", null, values)
            val audit = ContentValues().apply {
                put("id", java.util.UUID.randomUUID().toString())
                put("event_type", "record_upsert")
                put("record_id", record.id)
                put("created_at", System.currentTimeMillis())
                put("details_json", JSONObject().put("type", record.type).put("revision", revision).toString())
            }
            db.insert("audit_events", null, audit)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun get(id: String): CadssRecord? {
        val c = helper.readableDatabase.rawQuery(
            "SELECT id, record_type, schema_version, created_at, updated_at, revision, status, owner_ref, source_refs, tags, payload, extensions FROM records WHERE id = ?",
            arrayOf(id)
        )
        c.use {
            if (!it.moveToFirst()) return null
            return CadssRecord(
                id = it.getString(0), type = it.getString(1), schemaVersion = it.getInt(2),
                createdAt = it.getLong(3), updatedAt = it.getLong(4), revision = it.getLong(5),
                status = it.getString(6), ownerRef = if (it.isNull(7)) null else it.getString(7),
                sourceRefs = parseArray(it.getString(8)), tags = parseArray(it.getString(9)),
                payload = it.getString(10), extensions = it.getString(11)
            )
        }
    }

    fun list(type: String? = null, includeArchived: Boolean = false, limit: Int = 200): List<CadssRecord> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (type != null) { where += "record_type = ?"; args += type }
        if (!includeArchived) where += "status != 'archived'"
        val sql = "SELECT id FROM records" + if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ") +
            " ORDER BY updated_at DESC LIMIT " + limit.coerceIn(1, 1000)
        val c = helper.readableDatabase.rawQuery(sql, args.toTypedArray())
        val ids = mutableListOf<String>()
        c.use { while (it.moveToNext()) ids += it.getString(0) }
        return ids.mapNotNull(::get)
    }

    fun archive(id: String): Boolean {
        val values = ContentValues().apply {
            put("status", "archived")
            put("updated_at", System.currentTimeMillis())
        }
        return helper.writableDatabase.update("records", values, "id = ?", arrayOf(id)) > 0
    }

    fun close() = helper.close()

    private fun parseArray(value: String): List<String> {
        val array = JSONArray(value)
        return (0 until array.length()).map { array.optString(it) }
    }
}
