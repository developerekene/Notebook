package com.devekene.notebook.ui.screens.editor

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devekene.notebook.core.export.NoteExporter
import com.devekene.notebook.data.repository.NoteRepository
import com.devekene.notebook.domain.model.Category
import com.devekene.notebook.domain.model.NoteItem
import com.devekene.notebook.domain.model.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class VoiceTargetField(val label: String) {
    TITLE("Title"),
    DESCRIPTION("Description"),
    CONTENT("Note")
}

data class NoteEditorUiState(
    val noteId: Long? = null,
    val titleField: TextFieldValue = TextFieldValue(""),
    val descriptionField: TextFieldValue = TextFieldValue(""),
    val contentField: TextFieldValue = TextFieldValue(""),
    val categoryName: String = "General",
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    val voiceDurationSec: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val statusMessage: String? = null
) {
    val title: String get() = titleField.text
    val description: String get() = descriptionField.text
    val content: String get() = contentField.text

    val wordCount: Int
        get() {
            val combined = "${titleField.text.trim()} ${descriptionField.text.trim()} ${contentField.text.trim()}".trim()
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
    private val saveMutex = Mutex()

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
                            titleField = TextFieldValue(
                                text = loaded.title,
                                selection = TextRange(loaded.title.length)
                            ),
                            descriptionField = TextFieldValue(
                                text = loaded.description,
                                selection = TextRange(loaded.description.length)
                            ),
                            contentField = TextFieldValue(
                                text = loaded.content,
                                selection = TextRange(loaded.content.length)
                            ),
                            categoryName = loaded.categoryName,
                            tags = loaded.tags,
                            isPinned = loaded.isPinned,
                            isLocked = loaded.isLocked,
                            voiceDurationSec = loaded.voiceDurationSec,
                            createdAt = loaded.createdAt,
                            updatedAt = loaded.updatedAt,
                            hasUnsavedChanges = false
                        )
                    }
                }
            }
        }
    }

    fun updateTitleField(value: TextFieldValue) {
        _uiState.update { it.copy(titleField = value, hasUnsavedChanges = true) }
    }

    fun updateDescriptionField(value: TextFieldValue) {
        _uiState.update { it.copy(descriptionField = value, hasUnsavedChanges = true) }
    }

    fun updateContentField(value: TextFieldValue) {
        _uiState.update { it.copy(contentField = value, hasUnsavedChanges = true) }
    }

    fun insertAtCursor(snippet: String, addSpaceIfNeeded: Boolean = false) {
        if (snippet.isBlank()) return
        _uiState.update { state ->
            state.copy(
                contentField = insertIntoField(state.contentField, snippet, addSpaceIfNeeded),
                hasUnsavedChanges = true
            )
        }
    }

    fun insertVoiceTranscript(
        transcript: String,
        durationSeconds: Int,
        target: VoiceTargetField = VoiceTargetField.CONTENT
    ) {
        val clean = transcript.trim()
        if (clean.isEmpty()) return
        _uiState.update { state ->
            val updatedDuration = state.voiceDurationSec + durationSeconds.coerceAtLeast(1)
            when (target) {
                VoiceTargetField.TITLE -> state.copy(
                    titleField = insertIntoField(state.titleField, clean, addSpaceIfNeeded = true),
                    voiceDurationSec = updatedDuration,
                    hasUnsavedChanges = true
                )
                VoiceTargetField.DESCRIPTION -> state.copy(
                    descriptionField = insertIntoField(state.descriptionField, clean, addSpaceIfNeeded = true),
                    voiceDurationSec = updatedDuration,
                    hasUnsavedChanges = true
                )
                VoiceTargetField.CONTENT -> state.copy(
                    contentField = insertIntoField(state.contentField, clean, addSpaceIfNeeded = true),
                    voiceDurationSec = updatedDuration,
                    hasUnsavedChanges = true
                )
            }
        }
    }

    private fun insertIntoField(
        current: TextFieldValue,
        snippet: String,
        addSpaceIfNeeded: Boolean
    ): TextFieldValue {
        val text = current.text
        val start = current.selection.start.coerceIn(0, text.length)
        val end = current.selection.end.coerceIn(0, text.length)
        val minIdx = minOf(start, end)
        val maxIdx = maxOf(start, end)

        val needsLeadingSpace = addSpaceIfNeeded && minIdx > 0 && !text[minIdx - 1].isWhitespace()
        val insertion = if (needsLeadingSpace) " $snippet" else snippet
        val newText = text.substring(0, minIdx) + insertion + text.substring(maxIdx)
        val newCursor = minIdx + insertion.length
        return TextFieldValue(text = newText, selection = TextRange(newCursor))
    }

    fun selectCategory(categoryName: String) {
        _uiState.update { it.copy(categoryName = categoryName, hasUnsavedChanges = true) }
    }

    fun addTag(rawTag: String) {
        val clean = rawTag.trim().removePrefix("#").lowercase()
        if (clean.isBlank()) return
        _uiState.update { state ->
            if (state.tags.contains(clean)) state
            else state.copy(tags = state.tags + clean, hasUnsavedChanges = true)
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { state ->
            state.copy(tags = state.tags.filterNot { it == tag }, hasUnsavedChanges = true)
        }
    }

    fun togglePin() {
        _uiState.update { it.copy(isPinned = !it.isPinned, hasUnsavedChanges = true) }
    }

    fun toggleLock() {
        _uiState.update { it.copy(isLocked = !it.isLocked, hasUnsavedChanges = true) }
    }

    fun saveNote(onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            saveMutex.withLock {
                val state = _uiState.value
                if (state.title.isBlank() && state.description.isBlank() && state.content.isBlank()) {
                    onSaved(0L)
                    return@withLock
                }
                if (!state.hasUnsavedChanges && state.noteId != null) {
                    onSaved(state.noteId)
                    return@withLock
                }
                _uiState.update { it.copy(isSaving = true) }
                val resolvedTitle = state.title.ifBlank {
                    state.description.trim().lineSequence().firstOrNull()?.take(48)
                        ?: state.content.trim().lineSequence().firstOrNull()?.take(48)
                        ?: ""
                }
                val saved = noteRepository.saveNote(
                    id = state.noteId,
                    title = resolvedTitle,
                    description = state.description,
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
                        titleField = TextFieldValue(saved.title, TextRange(saved.title.length)),
                        updatedAt = saved.updatedAt,
                        isSaving = false,
                        hasUnsavedChanges = false,
                        statusMessage = "Saved & encrypted"
                    )
                }
                onSaved(saved.id)
            }
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
            description = s.description,
            content = s.content,
            categoryName = s.categoryName,
            categoryColorHex = 0xFF0F2D5EL,
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
