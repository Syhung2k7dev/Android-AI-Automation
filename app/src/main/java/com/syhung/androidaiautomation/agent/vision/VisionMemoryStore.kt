package com.syhung.androidaiautomation.agent.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.syhung.androidaiautomation.agent.data.CadssAsset
import com.syhung.androidaiautomation.agent.data.CadssRecord
import com.syhung.androidaiautomation.agent.data.CadssRecordTypes
import com.syhung.androidaiautomation.agent.data.CadssRepository
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class VisionMemoryStore(context: Context, private val repository: CadssRepository) {
    private val root = File(context.applicationContext.filesDir, "vision-memory").apply { mkdirs() }

    fun saveOriginal(bitmap: Bitmap, label: String = "capture"): CadssAsset {
        return saveBitmap(bitmap, null, label, "image")
    }

    fun saveCrop(parent: CadssAsset, bitmap: Bitmap, bounds: Rect, label: String): CadssAsset {
        require(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= parent.width && bounds.bottom <= parent.height)
        require(bounds.width() > 0 && bounds.height() > 0)
        return saveBitmap(bitmap, parent.id, label, "crop", bounds)
    }

    private fun saveBitmap(bitmap: Bitmap, parentId: String?, label: String, purpose: String, bounds: Rect? = null): CadssAsset {
        val id = UUID.randomUUID().toString()
        val file = File(root, "$id.png")
        file.outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Unable to encode image" }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val asset = CadssAsset(
            id, parentId, "vision-memory/$id.png", "image/png", file.length(), digest,
            bitmap.width, bitmap.height, System.currentTimeMillis(), purpose
        )
        val payload = JSONObject()
            .put("label", label)
            .put("asset_id", id)
            .put("relative_path", asset.relativePath)
            .put("mime_type", asset.mimeType)
            .put("byte_length", asset.byteLength)
            .put("sha256", asset.sha256)
            .put("width", asset.width)
            .put("height", asset.height)
            .put("purpose", purpose)
            .put("parent_image_id", parentId)
            .put("crop_bounds", bounds?.let {
                JSONObject().put("left", it.left).put("top", it.top).put("right", it.right).put("bottom", it.bottom)
            })
            .toString()
        repository.put(CadssRecord(id = id, type = if (parentId == null) CadssRecordTypes.IMAGE else CadssRecordTypes.CROP, payload = payload))
        return asset
    }

    fun resolve(assetId: String): File? {
        val record = repository.get(assetId) ?: return null
        val path = JSONObject(record.payload).optString("relative_path")
        val file = File(root.parentFile, path.substringAfter("vision-memory/"))
        return file.takeIf { it.isFile }
    }
}
