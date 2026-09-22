package com.vorem.whatstatus.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.vorem.whatstatus.domain.MediaKind
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaStoreManager(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    fun save(source: Uri, kind: MediaKind, originalName: String): Uri {
        val ext = extension(originalName, kind)
        val base = "WhatStatus_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}"
        val name = "$base.$ext"
        val collection = if (kind == MediaKind.IMAGE) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (kind == MediaKind.IMAGE) "image/jpeg" else "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (kind == MediaKind.IMAGE) "${Environment.DIRECTORY_PICTURES}/WhatStatus" else "${Environment.DIRECTORY_MOVIES}/WhatStatus")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val dest = resolver.insert(collection, values) ?: throw IOException("No fue posible crear el archivo")
        try {
            resolver.openInputStream(source).use { input ->
                resolver.openOutputStream(dest).use { output ->
                    requireNotNull(input); requireNotNull(output)
                    input.copyTo(output)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) resolver.update(dest, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return dest
        } catch (e: Exception) {
            runCatching { resolver.delete(dest, null, null) }
            throw e
        }
    }

    fun listSaved(): List<com.vorem.whatstatus.domain.SavedMedia> {
        val out = mutableListOf<com.vorem.whatstatus.domain.SavedMedia>()
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.RELATIVE_PATH)
        val sources = listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI to MediaKind.IMAGE, MediaStore.Video.Media.EXTERNAL_CONTENT_URI to MediaKind.VIDEO)
        sources.forEach { (collection, kind) ->
            runCatching { resolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                while (c.moveToNext()) {
                    val rel = c.getString(4).orEmpty()
                    if (rel.endsWith("WhatStatus/", true)) {
                        val uri = android.content.ContentUris.withAppendedId(collection, c.getLong(0))
                        out += com.vorem.whatstatus.domain.SavedMedia(uri, c.getString(1), kind, c.getLong(3) * 1000L, c.getLong(2))
                    }
                }
            } }
        }
        return out.sortedByDescending { it.modified }
    }

    fun delete(uri: Uri): Boolean = runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)

    private fun extension(name: String, kind: MediaKind): String = name.substringAfterLast('.', "").lowercase(Locale.US).let {
        if (kind == MediaKind.IMAGE && it in setOf("jpg","jpeg","png","webp","heic")) it else if (kind == MediaKind.VIDEO && it in setOf("mp4","3gp","mkv","webm")) it else if (kind == MediaKind.IMAGE) "jpg" else "mp4"
    }
}
