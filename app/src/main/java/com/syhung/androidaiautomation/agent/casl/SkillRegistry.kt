package com.syhung.androidaiautomation.agent.casl

import android.content.ContentValues
import android.content.Context
import com.syhung.androidaiautomation.agent.data.CadssDatabase
import org.json.JSONObject

data class RegisteredSkill(val skillId: String, val revision: Long, val status: String, val manifest: String, val workflow: String)

class SkillRegistry(context: Context) {
    private val helper = CadssDatabase(context.applicationContext)

    fun saveDraft(manifestText: String, workflowText: String): RegisteredSkill {
        val validation = CaslValidator.validate(manifestText, workflowText)
        require(validation.valid) { validation.errors.joinToString("; ") }
        val manifest = JSONObject(manifestText)
        val id = manifest.getString("skill_id")
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT MAX(revision) FROM skill_revisions WHERE skill_id = ?", arrayOf(id))
            val next = cursor.use { if (it.moveToFirst()) it.getLong(0) + 1 else 1L }
            val values = ContentValues().apply {
                put("skill_id", id)
                put("revision", next)
                put("casl_version", manifest.optString("casl_version", "1.0"))
                put("cadss_min_version", manifest.optString("cadss_min_version", "1.0.0"))
                put("status", "draft")
                put("manifest_json", manifestText)
                put("workflow_json", workflowText)
                put("created_at", System.currentTimeMillis())
            }
            db.insertOrThrow("skill_revisions", null, values)
            db.setTransactionSuccessful()
            return RegisteredSkill(id, next, "draft", manifestText, workflowText)
        } finally {
            db.endTransaction()
        }
    }

    fun activate(skillId: String, revision: Long, userApproved: Boolean): Boolean {
        if (!userApproved) return false
        val db = helper.writableDatabase
        val c = db.rawQuery(
            "SELECT manifest_json, workflow_json FROM skill_revisions WHERE skill_id = ? AND revision = ?",
            arrayOf(skillId, revision.toString())
        )
        val pair = c.use { if (it.moveToFirst()) it.getString(0) to it.getString(1) else null } ?: return false
        val validation = CaslValidator.validate(pair.first, pair.second)
        if (!validation.valid) return false
        val values = ContentValues().apply { put("status", "active") }
        return db.update("skill_revisions", values, "skill_id = ? AND revision = ?", arrayOf(skillId, revision.toString())) > 0
    }

    fun get(skillId: String, revision: Long? = null): RegisteredSkill? {
        val sql = if (revision == null) {
            "SELECT skill_id, revision, status, manifest_json, workflow_json FROM skill_revisions WHERE skill_id = ? ORDER BY revision DESC LIMIT 1"
        } else {
            "SELECT skill_id, revision, status, manifest_json, workflow_json FROM skill_revisions WHERE skill_id = ? AND revision = ?"
        }
        val args = if (revision == null) arrayOf(skillId) else arrayOf(skillId, revision.toString())
        val c = helper.readableDatabase.rawQuery(sql, args)
        c.use {
            if (!it.moveToFirst()) return null
            return RegisteredSkill(it.getString(0), it.getLong(1), it.getString(2), it.getString(3), it.getString(4))
        }
    }


    fun listRecent(limit: Int = 100): List<RegisteredSkill> {
        val c = helper.readableDatabase.rawQuery(
            "SELECT s.skill_id, s.revision, s.status, s.manifest_json, s.workflow_json " +
                "FROM skill_revisions s INNER JOIN (SELECT skill_id, MAX(revision) AS revision FROM skill_revisions GROUP BY skill_id) latest " +
                "ON latest.skill_id = s.skill_id AND latest.revision = s.revision ORDER BY s.created_at DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 500).toString())
        )
        val result = mutableListOf<RegisteredSkill>()
        c.use {
            while (it.moveToNext()) result += RegisteredSkill(
                it.getString(0), it.getLong(1), it.getString(2), it.getString(3), it.getString(4)
            )
        }
        return result
    }

    fun close() = helper.close()
}
