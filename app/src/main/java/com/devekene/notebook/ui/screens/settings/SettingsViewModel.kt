package com.devekene.notebook.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devekene.notebook.core.crypto.CryptoEngine
import com.devekene.notebook.core.datastore.UserPreferences
import com.devekene.notebook.core.datastore.UserPreferencesRepository
import com.devekene.notebook.core.export.NoteExporter
import com.devekene.notebook.core.security.BiometricAvailability
import com.devekene.notebook.core.security.BiometricLockManager
import com.devekene.notebook.data.repository.NoteRepository
import com.devekene.notebook.domain.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val categories: List<Category> = emptyList(),
    val noteCount: Int = 0,
    val keyFingerprint: String = "",
    val encryptionAlgorithm: String = "AES-256-GCM",
    val keyStorageProvider: String = "",
    val biometricAvailability: BiometricAvailability = BiometricAvailability.UNAVAILABLE,
    val statusMessage: String? = null
)

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val noteRepository: NoteRepository,
    private val cryptoEngine: CryptoEngine,
    private val biometricLockManager: BiometricLockManager,
    private val noteExporter: NoteExporter
) : ViewModel() {

    private val statusFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferencesFlow,
        noteRepository.observeCategories(),
        noteRepository.observeNotes(),
        statusFlow
    ) { prefs, categories, notes, message ->
        SettingsUiState(
            preferences = prefs,
            categories = categories,
            noteCount = notes.size,
            keyFingerprint = cryptoEngine.getKeyFingerprint(),
            encryptionAlgorithm = cryptoEngine.algorithmName,
            keyStorageProvider = cryptoEngine.keyStorageProvider,
            biometricAvailability = biometricLockManager.checkAvailability(),
            statusMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = SettingsUiState(
            keyFingerprint = cryptoEngine.getKeyFingerprint(),
            encryptionAlgorithm = cryptoEngine.algorithmName,
            keyStorageProvider = cryptoEngine.keyStorageProvider,
            biometricAvailability = biometricLockManager.checkAvailability()
        )
    )

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBiometricLockEnabled(enabled)
            statusFlow.value = if (enabled) "App lock enabled" else "App lock disabled"
        }
    }

    fun setLockOnBackground(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setLockOnBackground(enabled)
        }
    }

    fun saveVaultPin(pin: String) {
        if (pin.length < 4) {
            statusFlow.value = "PIN must be at least 4 digits"
            return
        }
        viewModelScope.launch {
            preferencesRepository.setVaultPin(pin)
            preferencesRepository.setBiometricLockEnabled(true)
            statusFlow.value = "Passcode PIN saved & lock enabled"
        }
    }

    fun clearVaultPin() {
        viewModelScope.launch {
            preferencesRepository.clearVaultPin()
            statusFlow.value = "Passcode PIN cleared"
        }
    }

    fun createCategory(name: String, colorHex: Long) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching {
                noteRepository.createCategory(name, colorHex, "folder")
            }.onSuccess {
                statusFlow.value = "Category '${it.name}' added"
            }.onFailure {
                statusFlow.value = it.localizedMessage ?: "Failed to add category"
            }
        }
    }

    fun deleteCustomCategory(categoryId: String) {
        viewModelScope.launch {
            noteRepository.deleteCustomCategory(categoryId)
            statusFlow.value = "Category removed; notes moved to General"
        }
    }

    fun exportAllMarkdown(uri: Uri) {
        viewModelScope.launch {
            val notes = noteRepository.observeNotes().first()
            val res = noteExporter.writeMarkdownToUri(uri, notes)
            statusFlow.value = res.fold(
                onSuccess = { "Exported ${notes.size} note(s) as Markdown" },
                onFailure = { "Markdown export failed: ${it.localizedMessage}" }
            )
        }
    }

    fun exportAllPdf(uri: Uri) {
        viewModelScope.launch {
            val notes = noteRepository.observeNotes().first()
            val res = noteExporter.writePdfToUri(uri, notes)
            statusFlow.value = res.fold(
                onSuccess = { "Exported ${notes.size} note(s) as PDF" },
                onFailure = { "PDF export failed: ${it.localizedMessage}" }
            )
        }
    }

    fun exportEncryptedVault(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            val notes = noteRepository.observeNotes().first()
            val categories = noteRepository.observeCategories().first()
            val res = noteExporter.writeEncryptedVaultToUri(uri, notes, categories, passphrase)
            if (res.isSuccess) {
                preferencesRepository.recordBackupTimestamp()
            }
            statusFlow.value = res.fold(
                onSuccess = { "Encrypted backup saved (${notes.size} notes)" },
                onFailure = { "Backup failed: ${it.localizedMessage}" }
            )
        }
    }

    fun importEncryptedVault(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            val res = noteExporter.readEncryptedVaultFromUri(uri, passphrase)
            res.fold(
                onSuccess = { payload ->
                    val imported = noteRepository.importDecryptedNotes(payload.notes, payload.categories)
                    statusFlow.value = "Imported & encrypted $imported note(s)"
                },
                onFailure = {
                    statusFlow.value = "Unable to decrypt backup. Verify passphrase and file."
                }
            )
        }
    }

    fun clearStatusMessage() {
        statusFlow.update { null }
    }

    class Factory(
        private val preferencesRepository: UserPreferencesRepository,
        private val noteRepository: NoteRepository,
        private val cryptoEngine: CryptoEngine,
        private val biometricLockManager: BiometricLockManager,
        private val noteExporter: NoteExporter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                preferencesRepository,
                noteRepository,
                cryptoEngine,
                biometricLockManager,
                noteExporter
            ) as T
        }
    }
}
