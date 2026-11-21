package com.devekene.notebook.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devekene.notebook.data.repository.NoteRepository
import com.devekene.notebook.domain.model.Category
import com.devekene.notebook.domain.model.InsightsSummary
import com.devekene.notebook.domain.model.NoteItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardFilterState(
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val selectedTag: String? = null
)

data class DashboardUiState(
    val isLoaded: Boolean = false,
    val notes: List<NoteItem> = emptyList(),
    val pinnedNotes: List<NoteItem> = emptyList(),
    val regularNotes: List<NoteItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val allTags: List<String> = emptyList(),
    val insights: InsightsSummary = InsightsSummary(),
    val filter: DashboardFilterState = DashboardFilterState()
)

class DashboardViewModel(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(DashboardFilterState())

    val uiState: StateFlow<DashboardUiState> = combine(
        noteRepository.observeNotes(),
        noteRepository.observeCategories(),
        noteRepository.observeInsights(daysWindow = 7),
        filterFlow
    ) { allNotes, categories, insights, filter ->
        val query = filter.searchQuery.trim()
        val filtered = allNotes.filter { note ->
            val matchesCategory = filter.selectedCategory == null || note.categoryName == filter.selectedCategory
            val matchesTag = filter.selectedTag == null || note.tags.contains(filter.selectedTag)
            val matchesQuery = query.isEmpty() ||
                note.title.contains(query, ignoreCase = true) ||
                (!note.isLocked && note.description.contains(query, ignoreCase = true)) ||
                (!note.isLocked && note.content.contains(query, ignoreCase = true)) ||
                note.tags.any { it.contains(query, ignoreCase = true) } ||
                note.categoryName.contains(query, ignoreCase = true)
            matchesCategory && matchesTag && matchesQuery
        }

        val distinctTags = allNotes
            .flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

        DashboardUiState(
            isLoaded = true,
            notes = filtered,
            pinnedNotes = filtered.filter { it.isPinned },
            regularNotes = filtered.filter { !it.isPinned },
            categories = categories,
            allTags = distinctTags,
            insights = insights,
            filter = filter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DashboardUiState()
    )

    fun updateSearchQuery(query: String) {
        filterFlow.update { it.copy(searchQuery = query) }
    }

    fun selectCategory(categoryName: String?) {
        filterFlow.update { current ->
            current.copy(selectedCategory = if (current.selectedCategory == categoryName) null else categoryName)
        }
    }

    fun selectTag(tag: String?) {
        filterFlow.update { current ->
            current.copy(selectedTag = if (current.selectedTag == tag) null else tag)
        }
    }

    fun clearFilters() {
        filterFlow.value = DashboardFilterState()
    }

    fun togglePinned(noteId: Long) {
        viewModelScope.launch {
            noteRepository.togglePinned(noteId)
        }
    }

    class Factory(
        private val noteRepository: NoteRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(noteRepository) as T
        }
    }
}
