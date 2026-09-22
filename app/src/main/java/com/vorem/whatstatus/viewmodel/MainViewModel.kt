package com.vorem.whatstatus.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vorem.whatstatus.domain.MediaKind
import com.vorem.whatstatus.domain.SavedMedia
import com.vorem.whatstatus.domain.StatusItem
import com.vorem.whatstatus.scanner.StatusScanner
import com.vorem.whatstatus.storage.MediaStoreManager
import com.vorem.whatstatus.storage.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Preferences(app)
    private val scanner = StatusScanner(app)
    private val media = MediaStoreManager(app)
    private val _statuses = MutableStateFlow<List<StatusItem>>(emptyList())
    val statuses: StateFlow<List<StatusItem>> = _statuses.asStateFlow()
    private val _saved = MutableStateFlow<List<SavedMedia>>(emptyList())
    val saved: StateFlow<List<SavedMedia>> = _saved.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun hasIntro() = prefs.introSeen()
    fun markIntro() = prefs.markIntroSeen()
    fun treeUri(): Uri? = prefs.treeUri()
    fun setTreeUri(uri: Uri) { prefs.setTreeUri(uri); scan() }
    fun scan() = viewModelScope.launch {
        _busy.value = true
        _saved.value = media.listSaved()
        runCatching { _statuses.value = scanner.scan(prefs.treeUri()).map { it.copy(saved = isAlreadySaved(it)) } }
            .onFailure { _message.value = "No fue posible escanear los estados." }
        _busy.value = false
    }
    fun loadSaved() { viewModelScope.launch { _saved.value = media.listSaved() } }
    fun save(item: StatusItem) = viewModelScope.launch {
        runCatching { media.save(item.uri, item.kind, item.name) }
            .onSuccess { _message.value = "Guardado correctamente"; scan() }
            .onFailure { _message.value = "No fue posible guardar el archivo." }
    }
    fun deleteSaved(item: SavedMedia) = viewModelScope.launch {
        if (media.delete(item.uri)) { _message.value = "Archivo eliminado"; loadSaved() } else _message.value = "No fue posible eliminar el archivo."
    }
    fun clearMessage() { _message.value = null }
    private fun isAlreadySaved(item: StatusItem): Boolean = _saved.value.any { it.name.substringAfterLast('.') == item.name.substringAfterLast('.') && it.size == item.size }
}
