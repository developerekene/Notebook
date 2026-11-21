package com.devekene.notebook.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: Long,
    val iconKey: String,
    val isDefault: Boolean,
    val sortOrder: Int
)

/**
 * Room database entity for storing encrypted user notes locally with auto-generated IDs
 * and modular cloud sync metadata ([remoteId], [syncState]) for future Firebase SDK integration.
 */
@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["category"]),
        Index(value = ["timestamp"]),
        Index(value = ["syncState"])
    ]
)
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "General",
    val tags: String = "",
    // Encryption & future Firebase sync metadata
    val titleIv: String = "",
    val descriptionIv: String = "",
    val contentIv: String = "",
    val isEncrypted: Boolean = true,
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    val createdAt: Long = timestamp,
    val wordCount: Int = 0,
    val voiceDurationSec: Int = 0,
    val remoteId: String? = null,
    val syncState: String = "LOCAL_ENCRYPTED"
)
