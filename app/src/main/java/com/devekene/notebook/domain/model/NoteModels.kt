package com.devekene.notebook.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class NoteItem(
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val content: String,
    val categoryName: String,
    val categoryColorHex: Long,
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    val isEncrypted: Boolean = true,
    val syncState: SyncState = SyncState.LOCAL_ENCRYPTED,
    val createdAt: Long,
    val updatedAt: Long,
    val wordCount: Int = 0,
    val voiceDurationSec: Int = 0,
    val remoteId: String? = null
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val colorHex: Long,
    val iconKey: String = "folder",
    val isDefault: Boolean = false,
    val noteCount: Int = 0
)

data class DailyActivity(
    val dayLabel: String,
    val dateEpochDay: Long,
    val noteCount: Int,
    val wordCount: Int
)

data class CategoryInsight(
    val category: Category,
    val noteCount: Int,
    val wordCount: Int,
    val shareFraction: Float
)

data class TagInsight(
    val tag: String,
    val count: Int
)

data class InsightsSummary(
    val totalNotes: Int = 0,
    val totalWords: Int = 0,
    val encryptedNotes: Int = 0,
    val pinnedNotes: Int = 0,
    val lockedNotes: Int = 0,
    val queuedSyncCount: Int = 0,
    val activeTagCount: Int = 0,
    val averageWordsPerNote: Int = 0,
    val dailyActivity: List<DailyActivity> = emptyList(),
    val categoryBreakdown: List<CategoryInsight> = emptyList(),
    val topTags: List<TagInsight> = emptyList()
)
