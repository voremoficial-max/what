package com.vorem.whatstatus.scanner

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.vorem.whatstatus.domain.MediaKind
import com.vorem.whatstatus.domain.SourceKind
import com.vorem.whatstatus.domain.StatusItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StatusScanner(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    suspend fun scan(treeUri: Uri?): List<StatusItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<StatusItem>()
        queryMediaStore(items)
        if (treeUri != null) scanTree(treeUri, items)
        items.distinctBy { it.uri.toString() + "|" + it.size + "|" + it.modified }
            .sortedByDescending { it.modified }
    }

    private fun queryMediaStore(out: MutableList<StatusItem>) {
        val collections = listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI to MediaKind.IMAGE,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to MediaKind.VIDEO)
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.MIME_TYPE)
        for ((collection, kind) in collections) {
            runCatching {
                resolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                    val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val size = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val date = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val rel = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    val mime = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    while (c.moveToNext()) {
                        val path = if (rel >= 0) c.getString(rel).orEmpty() else ""
                        val n = c.getString(name).orEmpty()
                        val m = if (mime >= 0) c.getString(mime).orEmpty() else ""
                        val looksLikeStatus = path.contains(".Statuses", ignoreCase = true) ||
                            path.contains("/Statuses", ignoreCase = true) || n.contains("status", true) && path.contains("whatsapp", true)
                        if (looksLikeStatus && isSupported(m, kind, n)) {
                            val uri = android.content.ContentUris.withAppendedId(collection, c.getLong(id))
                            out += StatusItem(uri, n, kind, c.getLong(date) * 1000L, c.getLong(size), SourceKind.MEDIA_STORE)
                        }
                    }
                }
            }
        }
    }

    private fun scanTree(treeUri: Uri, out: MutableList<StatusItem>) {
        fun walk(dir: Uri, depth: Int) {
            if (depth > 8) return
            runCatching {
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(dir, DocumentsContract.getTreeDocumentId(dir))
                resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0)
                        val name = c.getString(1).orEmpty()
                        val mime = c.getString(2).orEmpty()
                        val modified = c.getLong(3)
                        val size = c.getLong(4)
                        val child = DocumentsContract.buildDocumentUriUsingTree(dir, id)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) walk(child, depth + 1)
                        else {
                            val kind = when {
                                mime.startsWith("image/") -> MediaKind.IMAGE
                                mime.startsWith("video/") -> MediaKind.VIDEO
                                else -> null
                            }
                            if (kind != null && isSupported(mime, kind, name)) out += StatusItem(child, name, kind, modified, size, SourceKind.SAF)
                        }
                    }
                }
            }
        }
        walk(treeUri, 0)
    }

    private fun isSupported(mime: String, kind: MediaKind, name: String): Boolean {
        if (name.startsWith(".")) return false
        return when (kind) {
            MediaKind.IMAGE -> mime.startsWith("image/") || name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) || name.endsWith(".png", true) || name.endsWith(".webp", true)
            MediaKind.VIDEO -> mime.startsWith("video/") || name.endsWith(".mp4", true) || name.endsWith(".3gp", true) || name.endsWith(".mkv", true)
        }
    }
}
