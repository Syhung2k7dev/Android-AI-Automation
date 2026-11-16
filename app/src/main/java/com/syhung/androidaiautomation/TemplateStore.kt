package com.syhung.androidaiautomation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object TemplateStore {
    private fun directory(context: Context): File =
        File(context.filesDir, "image_templates").apply { mkdirs() }

    fun names(context: Context): List<String> =
        directory(context).listFiles()?.filter { it.isFile && it.extension == "png" }
            ?.map { it.nameWithoutExtension }?.sorted().orEmpty()

    fun import(context: Context, uri: Uri, requestedName: String): String {
        val name = requestedName.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
        require(name.isNotBlank())
        val source = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open selected image.")
        val bitmap = source.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalArgumentException("Selected file is not a readable image.")
        try {
            require(bitmap.width >= 8 && bitmap.height >= 8 && bitmap.width <= 6000 && bitmap.height <= 6000)
            saveBitmap(context, bitmap, name)
            return name
        } finally {
            bitmap.recycle()
        }
    }

    fun saveBitmap(context: Context, bitmap: Bitmap, requestedName: String): String {
        val name = requestedName.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
        require(name.isNotBlank() && bitmap.width in 8..6000 && bitmap.height in 8..6000)
        val target = File(directory(context), "$name.png")
        FileOutputStream(target).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        require(target.length() <= 8L * 1024 * 1024) { "Image template exceeds the 8 MB storage limit." }
        return name
    }

    fun delete(context: Context, name: String): Boolean {
        if (!name.matches(Regex("[A-Za-z0-9_-]{1,40}"))) return false
        return File(directory(context), "$name.png").delete()
    }

    fun load(context: Context, name: String): Bitmap? {
        if (!name.matches(Regex("[A-Za-z0-9_-]{1,40}"))) return null
        val file = File(directory(context), "$name.png")
        if (!file.isFile || file.length() > 8L * 1024 * 1024) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }
}
