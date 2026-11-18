package com.example.ui.screens.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.export.NoteExporter
import com.example.data.repository.NoteRepository
import com.example.domain.model.Category
import com.example.domain.model.NoteItem
import com.example.domain.model.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteEditorUiState(
    val noteId: Long? = null,
    val title: String = "",
    val content: String = "",
    val categoryName: String = "General",
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    val voiceDurationSec: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSaving: Boolean = false,
    val statusMessage: String? = null
) {
    val wordCount: Int
        get() {
            val combined = "${title.trim()} ${content.trim()}".trim()
            return if (combined.isEmpty()) 0 else combined.split(Regex("\\s+")).size
        }
}

class NoteEditorViewModel(
    private val noteIdArg: Long?,
    private val noteRepository: NoteRepository,
    private val noteExporter: NoteExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState(noteId = noteIdArg))
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    val categories: StateFlow<List<Category>> = noteRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        if (noteIdArg != null && noteIdArg > 0L) {
            viewModelScope.launch {
                val loaded = noteRepository.getNoteById(noteIdArg)
                if (loaded != null) {
                    _uiState.update {
                        it.copy(
                            noteId = loaded.id,
                            title = loaded.title,
                            content = loaded.content,
                            categoryName = loaded.categoryName,
                            tags = loaded.tags,
                            isPinned = loaded.isPinned,
                            isLocked = loaded.isLocked,
                            voiceDurationSec = loaded.voiceDurationSec,
                            createdAt = loaded.createdAt,
                            updatedAt = loaded.updatedAt
                        )
                    }
                }
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateContent(content: String) {
        _uiState.update { it.copy(content = content) }
    }

    fun appendMarkdownSnippet(snippet: String) {
        _uiState.update { state ->
            val prefix = if (state.content.isBlank() || state.content.endsWith("\n")) "" else "\n"
            state.copy(content = "${state.content}$prefix$snippet")
        }
    }

    fun appendVoiceTranscript(transcript: String, durationSeconds: Int) {
        val clean = transcript.trim()
        if (clean.isEmpty()) return
        _uiState.update { state ->
            val separator = when {
                state.content.isBlank() -> ""
                state.content.endsWith(" ") || state.content.endsWith("\n") -> ""
                else -> "\n\n"
            }
            state.copy(
                content = "${state.content}$separator$clean",
                voiceDurationSec = state.voiceDurationSec + durationSeconds.coerceAtLeast(1)
            )
        }
    }

    fun selectCategory(categoryName: String) {
        _uiState.update { it.copy(categoryName = categoryName) }
    }

    fun addTag(rawTag: String) {
        val clean = rawTag.trim().removePrefix("#").lowercase()
        if (clean.isBlank()) return
        _uiState.update { state ->
            if (state.tags.contains(clean)) state else state.copy(tags = state.tags + clean)
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { state ->
            state.copy(tags = state.tags.filterNot { it == tag })
        }
    }

    fun togglePin() {
        _uiState.update { it.copy(isPinned = !it.isPinned) }
    }

    fun toggleLock() {
        _uiState.update { it.copy(isLocked = !it.isLocked) }
    }

    fun saveNote(onSaved: (Long) -> Unit = {}) {
        val state = _uiState.value
        if (state.title.isBlank() && state.content.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val saved = noteRepository.saveNote(
                id = state.noteId,
                title = state.title,
                content = state.content,
                categoryName = state.categoryName,
                tags = state.tags,
                isPinned = state.isPinned,
                isLocked = state.isLocked,
                voiceDurationSec = state.voiceDurationSec
            )
            _uiState.update {
                it.copy(
                    noteId = saved.id,
                    updatedAt = saved.updatedAt,
                    isSaving = false,
                    statusMessage = "Encrypted & saved locally"
                )
            }
            onSaved(saved.id)
        }
    }

    fun deleteCurrentNote(onDeleted: () -> Unit) {
        val id = _uiState.value.noteId
        if (id == null || id <= 0L) {
            onDeleted()
            return
        }
        viewModelScope.launch {
            noteRepository.deleteNote(id)
            onDeleted()
        }
    }

    fun toSnapshotNoteItem(): NoteItem {
        val s = _uiState.value
        return NoteItem(
            id = s.noteId ?: 0L,
            title = s.title.ifBlank { "Untitled Note" },
            content = s.content,
            categoryName = s.categoryName,
            categoryColorHex = 0xFF3B82F6L,
            tags = s.tags,
            isPinned = s.isPinned,
            isLocked = s.isLocked,
            isEncrypted = true,
            syncState = SyncState.LOCAL_ENCRYPTED,
            createdAt = s.createdAt,
            updatedAt = s.updatedAt,
            wordCount = s.wordCount,
            voiceDurationSec = s.voiceDurationSec
        )
    }

    fun exportMarkdownToUri(uri: Uri) {
        viewModelScope.launch {
            val result = noteExporter.writeMarkdownToUri(uri, listOf(toSnapshotNoteItem()))
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { "Exported Markdown (.md)" },
                        onFailure = { err -> "Export failed: ${err.localizedMessage}" }
                    )
                )
            }
        }
    }

    fun exportPdfToUri(uri: Uri) {
        viewModelScope.launch {
            val result = noteExporter.writePdfToUri(uri, listOf(toSnapshotNoteItem()))
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { "Exported PDF Document (.pdf)" },
                        onFailure = { err -> "PDF export failed: ${err.localizedMessage}" }
                    )
                )
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    class Factory(
        private val noteId: Long?,
        private val noteRepository: NoteRepository,
        private val noteExporter: NoteExporter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NoteEditorViewModel(noteId, noteRepository, noteExporter) as T
        }
    }
}
