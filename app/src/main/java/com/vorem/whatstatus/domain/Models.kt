package com.vorem.whatstatus.domain

import android.net.Uri

enum class MediaKind { IMAGE, VIDEO }

data class StatusItem(
    val uri: Uri,
    val name: String,
    val kind: MediaKind,
    val modified: Long,
    val size: Long,
    val source: SourceKind,
    val saved: Boolean = false
)

enum class SourceKind { MEDIA_STORE, SAF }

data class SavedMedia(
    val uri: Uri,
    val name: String,
    val kind: MediaKind,
    val modified: Long,
    val size: Long
)
