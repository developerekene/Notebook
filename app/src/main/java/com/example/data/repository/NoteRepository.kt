package com.example.data.repository

import com.example.core.crypto.CryptoEngine
import com.example.core.crypto.EncryptedPayload
import com.example.data.local.CategoryDao
import com.example.data.local.CategoryEntity
import com.example.data.local.Note
import com.example.data.local.NoteDao
import com.example.domain.model.Category
import com.example.domain.model.CategoryInsight
import com.example.domain.model.DailyActivity
import com.example.domain.model.InsightsSummary
import com.example.domain.model.NoteItem
import com.example.domain.model.SyncState
import com.example.domain.model.TagInsight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

interface NoteRepository {
    suspend fun ensureDefaultCategories()
    fun observeNotes(): Flow<List<NoteItem>>
    fun observeCategories(): Flow<List<Category>>
    fun observeInsights(daysWindow: Int = 7): Flow<InsightsSummary>
    suspend fun getNoteById(id: Long): NoteItem?
    suspend fun saveNote(
        id: Long?,
        title: String,
        content: String,
        categoryName: String,
        tags: List<String>,
        isPinned: Boolean,
        isLocked: Boolean,
        voiceDurationSec: Int
    ): NoteItem
    suspend fun togglePinned(noteId: Long)
    suspend fun toggleLocked(noteId: Long)
    suspend fun deleteNote(noteId: Long)
    suspend fun createCategory(name: String, colorHex: Long, iconKey: String): Category
    suspend fun deleteCustomCategory(categoryId: String)
    suspend fun importDecryptedNotes(notes: List<NoteItem>, categories: List<Category>): Int
}

class DefaultNoteRepository(
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao,
    private val cryptoEngine: CryptoEngine
) : NoteRepository {

    override suspend fun ensureDefaultCategories() = withContext(Dispatchers.IO) {
        if (categoryDao.countCategories() == 0) {
            val defaults = listOf(
                CategoryEntity("cat_general", "General", 0xFF3B82F6L, "notes", isDefault = true, sortOrder = 0),
                CategoryEntity("cat_work", "Work", 0xFF1D4ED8L, "work", isDefault = true, sortOrder = 1),
                CategoryEntity("cat_research", "Research", 0xFF06B6D4L, "science", isDefault = true, sortOrder = 2),
                CategoryEntity("cat_ideas", "Ideas", 0xFF10B981L, "lightbulb", isDefault = true, sortOrder = 3),
                CategoryEntity("cat_personal", "Personal", 0xFF6366F1L, "person", isDefault = true, sortOrder = 4)
            )
            categoryDao.insertAll(defaults)
        }
    }

    override fun observeNotes(): Flow<List<NoteItem>> = combine(
        noteDao.observeAllNotes(),
        categoryDao.observeAllCategories()
    ) { entities, categories ->
        val catByName = categories.associateBy { it.name }
        entities.map { entity -> decryptNote(entity, catByName) }
    }.flowOn(Dispatchers.Default)

    override fun observeCategories(): Flow<List<Category>> = combine(
        categoryDao.observeAllCategories(),
        noteDao.observeAllNotes()
    ) { categories, notes ->
        val counts = notes.groupingBy { it.category }.eachCount()
        categories.map { entity ->
            Category(
                id = entity.id,
                name = entity.name,
                colorHex = entity.colorHex,
                iconKey = entity.iconKey,
                isDefault = entity.isDefault,
                noteCount = counts[entity.name] ?: 0
            )
        }
    }.flowOn(Dispatchers.Default)

    override fun observeInsights(daysWindow: Int): Flow<InsightsSummary> = combine(
        observeNotes(),
        observeCategories()
    ) { notes, categories ->
        buildInsightsSummary(notes, categories, daysWindow)
    }.flowOn(Dispatchers.Default)

    override suspend fun getNoteById(id: Long): NoteItem? = withContext(Dispatchers.IO) {
        val entity = noteDao.getNoteById(id) ?: return@withContext null
        val catByName = categoryDao.getAllCategories().associateBy { it.name }
        decryptNote(entity, catByName)
    }

    override suspend fun saveNote(
        id: Long?,
        title: String,
        content: String,
        categoryName: String,
        tags: List<String>,
        isPinned: Boolean,
        isLocked: Boolean,
        voiceDurationSec: Int
    ): NoteItem = withContext(Dispatchers.IO) {
        ensureDefaultCategories()
        val now = System.currentTimeMillis()
        val existing = id?.takeIf { it > 0L }?.let { noteDao.getNoteById(it) }
        val createdAt = existing?.createdAt ?: now
        val normalizedTags = tags
            .map { it.trim().removePrefix("#").lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .distinct()

        val cleanTitle = title.trim()
        val words = countWords("$cleanTitle $content")

        val encTitle = cryptoEngine.encrypt(cleanTitle)
        val encContent = cryptoEngine.encrypt(content)

        val validCategory = categoryDao.getCategoryByName(categoryName)
            ?: categoryDao.getCategoryByName("General")
            ?: CategoryEntity("cat_general", "General", 0xFF3B82F6L, "notes", true, 0)

        val entity = Note(
            id = existing?.id ?: 0L,
            title = encTitle.ciphertextBase64,
            content = encContent.ciphertextBase64,
            timestamp = now,
            category = validCategory.name,
            tags = normalizedTags.joinToString(","),
            titleIv = encTitle.ivBase64,
            contentIv = encContent.ivBase64,
            isEncrypted = true,
            isPinned = isPinned,
            isLocked = isLocked,
            createdAt = createdAt,
            wordCount = words,
            voiceDurationSec = voiceDurationSec,
            remoteId = existing?.remoteId,
            syncState = SyncState.QUEUED_FOR_SYNC.name
        )
        val generatedId = noteDao.upsertNote(entity)
        val finalId = if (entity.id > 0L) entity.id else generatedId

        NoteItem(
            id = finalId,
            title = cleanTitle,
            content = content,
            categoryName = validCategory.name,
            categoryColorHex = validCategory.colorHex,
            tags = normalizedTags,
            isPinned = isPinned,
            isLocked = isLocked,
            isEncrypted = true,
            syncState = SyncState.QUEUED_FOR_SYNC,
            createdAt = createdAt,
            updatedAt = now,
            wordCount = words,
            voiceDurationSec = voiceDurationSec,
            remoteId = existing?.remoteId
        )
    }

    override suspend fun togglePinned(noteId: Long) = withContext(Dispatchers.IO) {
        val entity = noteDao.getNoteById(noteId) ?: return@withContext
        noteDao.upsertNote(entity.copy(isPinned = !entity.isPinned, timestamp = System.currentTimeMillis()))
        Unit
    }

    override suspend fun toggleLocked(noteId: Long) = withContext(Dispatchers.IO) {
        val entity = noteDao.getNoteById(noteId) ?: return@withContext
        noteDao.upsertNote(entity.copy(isLocked = !entity.isLocked, timestamp = System.currentTimeMillis()))
        Unit
    }

    override suspend fun deleteNote(noteId: Long) = withContext(Dispatchers.IO) {
        noteDao.deleteNoteById(noteId)
    }

    override suspend fun createCategory(name: String, colorHex: Long, iconKey: String): Category =
        withContext(Dispatchers.IO) {
            val trimmed = name.trim()
            require(trimmed.isNotBlank()) { "Category name cannot be empty." }
            val existing = categoryDao.getCategoryByName(trimmed)
            if (existing != null) {
                return@withContext Category(
                    id = existing.id,
                    name = existing.name,
                    colorHex = existing.colorHex,
                    iconKey = existing.iconKey,
                    isDefault = existing.isDefault,
                    noteCount = 0
                )
            }
            val currentCount = categoryDao.countCategories()
            val entity = CategoryEntity(
                id = "cat_${UUID.randomUUID().toString().take(8)}",
                name = trimmed,
                colorHex = colorHex,
                iconKey = iconKey,
                isDefault = false,
                sortOrder = currentCount
            )
            categoryDao.upsertCategory(entity)
            Category(
                id = entity.id,
                name = entity.name,
                colorHex = entity.colorHex,
                iconKey = entity.iconKey,
                isDefault = false,
                noteCount = 0
            )
        }

    override suspend fun deleteCustomCategory(categoryId: String) = withContext(Dispatchers.IO) {
        ensureDefaultCategories()
        val cat = categoryDao.getAllCategories().firstOrNull { it.id == categoryId && !it.isDefault }
        if (cat != null) {
            noteDao.reassignCategory(cat.name, "General")
            categoryDao.deleteCustomCategory(categoryId)
        }
    }

    override suspend fun importDecryptedNotes(notes: List<NoteItem>, categories: List<Category>): Int =
        withContext(Dispatchers.IO) {
            ensureDefaultCategories()
            val existingByName = categoryDao.getAllCategories().associateBy { it.name }
            categories.forEach { cat ->
                if (!existingByName.containsKey(cat.name)) {
                    categoryDao.upsertCategory(
                        CategoryEntity(
                            id = cat.id.ifBlank { "cat_${UUID.randomUUID().toString().take(8)}" },
                            name = cat.name,
                            colorHex = cat.colorHex,
                            iconKey = cat.iconKey,
                            isDefault = cat.isDefault,
                            sortOrder = 99
                        )
                    )
                }
            }
            val validNames = categoryDao.getAllCategories().map { it.name }.toSet()
            val entities = notes.map { note ->
                val encTitle = cryptoEngine.encrypt(note.title)
                val encContent = cryptoEngine.encrypt(note.content)
                Note(
                    id = 0L,
                    title = encTitle.ciphertextBase64,
                    content = encContent.ciphertextBase64,
                    timestamp = note.updatedAt,
                    category = if (note.categoryName in validNames) note.categoryName else "General",
                    tags = note.tags.joinToString(","),
                    titleIv = encTitle.ivBase64,
                    contentIv = encContent.ivBase64,
                    isEncrypted = true,
                    isPinned = note.isPinned,
                    isLocked = note.isLocked,
                    createdAt = note.createdAt,
                    wordCount = countWords("${note.title} ${note.content}"),
                    voiceDurationSec = note.voiceDurationSec,
                    remoteId = note.remoteId,
                    syncState = SyncState.LOCAL_ENCRYPTED.name
                )
            }
            noteDao.upsertNotes(entities)
            entities.size
        }

    private fun decryptNote(
        entity: Note,
        catByName: Map<String, CategoryEntity>
    ): NoteItem {
        val title = if (entity.isEncrypted && entity.titleIv.isNotBlank()) {
            try {
                cryptoEngine.decrypt(EncryptedPayload(entity.title, entity.titleIv))
            } catch (_: Exception) {
                "Encrypted Note"
            }
        } else {
            entity.title
        }
        val content = if (entity.isEncrypted && entity.contentIv.isNotBlank()) {
            try {
                cryptoEngine.decrypt(EncryptedPayload(entity.content, entity.contentIv))
            } catch (_: Exception) {
                ""
            }
        } else {
            entity.content
        }
        val category = catByName[entity.category]
        val syncState = SyncState.entries.firstOrNull { it.name == entity.syncState } ?: SyncState.LOCAL_ENCRYPTED
        val tags = entity.tags
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return NoteItem(
            id = entity.id,
            title = title,
            content = content,
            categoryName = entity.category.ifBlank { "General" },
            categoryColorHex = category?.colorHex ?: 0xFF3B82F6L,
            tags = tags,
            isPinned = entity.isPinned,
            isLocked = entity.isLocked,
            isEncrypted = entity.isEncrypted,
            syncState = syncState,
            createdAt = entity.createdAt,
            updatedAt = entity.timestamp,
            wordCount = entity.wordCount,
            voiceDurationSec = entity.voiceDurationSec,
            remoteId = entity.remoteId
        )
    }

    private fun countWords(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split(Regex("\\s+")).size
    }

    private fun buildInsightsSummary(
        notes: List<NoteItem>,
        categories: List<Category>,
        daysWindow: Int
    ): InsightsSummary {
        val totalNotes = notes.size
        val totalWords = notes.sumOf { it.wordCount }
        val encryptedNotes = notes.count { it.isEncrypted }
        val pinnedNotes = notes.count { it.isPinned }
        val lockedNotes = notes.count { it.isLocked }
        val queuedSyncCount = notes.count { it.syncState == SyncState.QUEUED_FOR_SYNC }

        val tagCounts = mutableMapOf<String, Int>()
        notes.forEach { note ->
            note.tags.forEach { tag ->
                tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
            }
        }
        val topTags = tagCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { TagInsight(it.key, it.value) }

        val notesByCat = notes.groupBy { it.categoryName }
        val categoryBreakdown = categories
            .map { cat ->
                val catNotes = notesByCat[cat.name].orEmpty()
                val count = catNotes.size
                val words = catNotes.sumOf { it.wordCount }
                CategoryInsight(
                    category = cat,
                    noteCount = count,
                    wordCount = words,
                    shareFraction = if (totalNotes > 0) count.toFloat() / totalNotes else 0f
                )
            }
            .sortedByDescending { it.noteCount }

        val dayFormatter = SimpleDateFormat("EEE", Locale.getDefault())
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val msPerDay = 86_400_000L
        val todayStart = cal.timeInMillis
        val dailyList = (daysWindow - 1 downTo 0).map { offset ->
            val dayStart = todayStart - offset * msPerDay
            val dayEnd = dayStart + msPerDay
            val dayNotes = notes.filter { it.updatedAt in dayStart until dayEnd }
            DailyActivity(
                dayLabel = dayFormatter.format(Date(dayStart)).take(2),
                dateEpochDay = dayStart / msPerDay,
                noteCount = dayNotes.size,
                wordCount = dayNotes.sumOf { it.wordCount }
            )
        }

        return InsightsSummary(
            totalNotes = totalNotes,
            totalWords = totalWords,
            encryptedNotes = encryptedNotes,
            pinnedNotes = pinnedNotes,
            lockedNotes = lockedNotes,
            queuedSyncCount = queuedSyncCount,
            activeTagCount = tagCounts.size,
            averageWordsPerNote = if (totalNotes > 0) totalWords / totalNotes else 0,
            dailyActivity = dailyList,
            categoryBreakdown = categoryBreakdown,
            topTags = topTags
        )
    }
}
