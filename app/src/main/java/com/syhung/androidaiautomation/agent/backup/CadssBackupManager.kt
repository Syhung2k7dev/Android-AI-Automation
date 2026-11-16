package com.syhung.androidaiautomation.agent.backup

import android.content.Context
import com.syhung.androidaiautomation.agent.data.CadssRepository
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

data class BackupResult(val file: File, val sha256: String, val bytes: Long)

class CadssBackupManager(context: Context, private val repository: CadssRepository) {
    private val app = context.applicationContext

    /**
     * Exports canonical JSON records and image assets into an app-private archive.
     * The caller should expose the resulting file through ACTION_CREATE_DOCUMENT.
     * API keys and Android credentials are intentionally excluded.
     */
    fun createBackup(destination: File): BackupResult {
        val records = repository.list(includeArchived = true, limit = 1000)
        val manifest = JSONObject()
            .put("format", "CADSS-BACKUP-1")
            .put("created_at", System.currentTimeMillis())
            .put("record_count", records.size)
            .put("cadss_version", "1.0.0")
        ZipOutputStream(FileOutputStream(destination)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("records.json"))
            val array = org.json.JSONArray()
            records.forEach { record ->
                array.put(JSONObject()
                    .put("id", record.id).put("type", record.type)
                    .put("schema_version", record.schemaVersion).put("created_at", record.createdAt)
                    .put("updated_at", record.updatedAt).put("revision", record.revision)
                    .put("status", record.status).put("owner_ref", record.ownerRef)
                    .put("source_refs", org.json.JSONArray(record.sourceRefs))
                    .put("tags", org.json.JSONArray(record.tags))
                    .put("payload", record.payload).put("extensions", record.extensions))
            }
            zip.write(array.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val vision = File(app.filesDir, "vision-memory")
            if (vision.exists()) vision.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = "assets/" + file.relativeTo(app.filesDir).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(relative))
                FileInputStream(file).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(destination.readBytes()).joinToString("") { "%02x".format(it) }
        return BackupResult(destination, hash, destination.length())
    }
}
