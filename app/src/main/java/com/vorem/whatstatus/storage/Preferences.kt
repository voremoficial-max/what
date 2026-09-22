package com.vorem.whatstatus.storage

import android.content.Context
import android.net.Uri

class Preferences(context: Context) {
    private val p = context.getSharedPreferences("whatstatus", Context.MODE_PRIVATE)
    fun treeUri(): Uri? = p.getString("tree_uri", null)?.let(Uri::parse)
    fun setTreeUri(uri: Uri?) = p.edit().putString("tree_uri", uri?.toString()).apply()
    fun introSeen() = p.getBoolean("intro_seen", false)
    fun markIntroSeen() = p.edit().putBoolean("intro_seen", true).apply()
}
