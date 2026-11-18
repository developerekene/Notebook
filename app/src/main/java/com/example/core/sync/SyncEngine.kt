package com.example.core.sync

import com.example.core.datastore.UserPreferencesRepository
import com.example.data.local.NoteDao
import com.example.domain.model.NoteItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

enum class VaultStorageStatus {
    OFFLINE_ENCRYPTED,
    PENDING_BACKUP
}

data class VaultSyncState(
    val status: VaultStorageStatus = VaultStorageStatus.OFFLINE_ENCRYPTED,
    val pendingCount: Int = 0,
    val lastBackupTimestamp: Long = 0L,
    val statusMessage: String = "Offline AES-256-GCM Room vault active"
)

/**
 * Modular sync contract designed so a custom Firebase SDK adapter can be
 * plugged in later without changing Room, Crypto, ViewModels, or UI.
 */
interface RemoteNoteSyncAdapter {
    val isConfigured: Boolean
    suspend fun pushNotes(notes: List<NoteItem>): Result<Map<Long, String>>
    suspend fun pullNotesSince(timestamp: Long): Result<List<NoteItem>>
}

interface SyncEngine {
    fun observeState(): Flow<VaultSyncState>
}

class OfflineVaultSyncEngine(
    private val noteDao: NoteDao,
    private val preferencesRepository: UserPreferencesRepository
) : SyncEngine {

    override fun observeState(): Flow<VaultSyncState> = combine(
        noteDao.observeQueuedSyncCount(),
        preferencesRepository.preferencesFlow
    ) { queuedCount, prefs ->
        VaultSyncState(
            status = if (queuedCount > 0) VaultStorageStatus.PENDING_BACKUP else VaultStorageStatus.OFFLINE_ENCRYPTED,
            pendingCount = queuedCount,
            lastBackupTimestamp = prefs.lastBackupTimestamp,
            statusMessage = "100% Offline AES-256-GCM Room Vault • Ready for future Firebase SDK"
        )
    }
}
