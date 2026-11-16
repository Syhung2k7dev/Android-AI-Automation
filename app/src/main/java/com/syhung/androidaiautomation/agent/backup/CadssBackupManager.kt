package com.syhung.androidaiautomation.agent.backup

import android.content.Context
import com.syhung.androidaiautomation.agent.data.CadssRecord
import com.syhung.androidaiautomation.agent.data.CadssRepository
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class BackupResult(val file: File, val sha256: String, val bytes: Long)

class CadssBackupManager(context: Context, private val repository: CadssRepository) {
    private val app = context.applicationContext

    fun createBackup(destination: File): BackupResult {
        val records = repository.list(includeArchived = true, limit = 1000)
        val manifest = JSONObject().put("format", "CADSS-BACKUP-1")
            .put("created_at", System.currentTimeMillis()).put("record_count", records.size)
            .put("cadss_version", "1.0.0")
        ZipOutputStream(FileOutputStream(destination)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("records.json"))
            val array = JSONArray()
            records.forEach { record ->
                array.put(JSONObject().put("id", record.id).put("type", record.type)
                    .put("schema_version", record.schemaVersion).put("created_at", record.createdAt)
                    .put("updated_at", record.updatedAt).put("revision", record.revision)
                    .put("status", record.status).put("owner_ref", record.ownerRef)
                    .put("source_refs", JSONArray(record.sourceRefs)).put("tags", JSONArray(record.tags))
                    .put("payload", record.payload).put("extensions", record.extensions))
            }
            zip.write(array.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val vision = File(app.filesDir, "vision-memory")
            if (vision.exists()) vision.walkTopDown().filter { it.isFile }.forEach { file ->
                zip.putNextEntry(ZipEntry("assets/" + file.relativeTo(app.filesDir).invariantSeparatorsPath))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return BackupResult(destination, sha256(destination), destination.length())
    }

    /**
     * Validates the archive before importing. Restore merges by stable ID and never
     * clears the current database. Credentials and Android private settings are excluded.
     */
    fun restoreBackup(source: File): Int {
        require(source.isFile && source.length() <= 250L * 1024L * 1024L) { "Backup file is missing or too large" }
        val staging = File(app.cacheDir, "cadss-restore-" + System.currentTimeMillis()).apply { mkdirs() }
        try {
            var expanded = 0L
            ZipInputStream(FileInputStream(source)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(!File(name).isAbsolute && !name.contains("..") && !name.startsWith("/") && !name.contains("\\"))
                    if (!entry.isDirectory) {
                        val target = File(staging, name)
                        require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator))
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                expanded += count
                                require(expanded <= 250L * 1024L * 1024L) { "Archive expands beyond size limit" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val manifest = JSONObject(File(staging, "manifest.json").readText())
            require(manifest.optString("format") == "CADSS-BACKUP-1") { "Unsupported backup format" }
            val records = JSONArray(File(staging, "records.json").readText())
            require(records.length() == manifest.optInt("record_count")) { "Record count mismatch" }

            val assets = File(staging, "assets/vision-memory")
            if (assets.exists()) {
                val targetRoot = File(app.filesDir, "vision-memory").apply { mkdirs() }
                assets.walkTopDown().filter { it.isFile }.forEach { file ->
                    val target = File(targetRoot, file.relativeTo(assets).path)
                    target.parentFile?.mkdirs()
                    file.copyTo(target, overwrite = true)
                }
            }

            var restored = 0
            for (i in 0 until records.length()) {
                val item = records.getJSONObject(i)
                val id = item.getString("id")
                val type = item.getString("type")
                require(id.isNotBlank() && type.isNotBlank())
                repository.put(CadssRecord(
                    id = id, type = type, schemaVersion = item.optInt("schema_version", 1),
                    createdAt = item.optLong("created_at", System.currentTimeMillis()),
                    updatedAt = item.optLong("updated_at", System.currentTimeMillis()),
                    revision = item.optLong("revision", 1), status = item.optString("status", "active"),
                    ownerRef = item.optString("owner_ref").takeIf { it.isNotBlank() },
                    sourceRefs = strings(item.optJSONArray("source_refs")),
                    tags = strings(item.optJSONArray("tags")), payload = item.getString("payload"),
                    extensions = item.optString("extensions", "{}")
                ))
                restored++
            }
            return restored
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun strings(array: JSONArray?): List<String> =
        if (array == null) emptyList() else (0 until array.length()).map { array.optString(it) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
