package com.example.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.crypto.CryptoEngine
import com.example.core.datastore.UserPreferences
import com.example.core.datastore.UserPreferencesRepository
import com.example.core.export.NoteExporter
import com.example.core.security.BiometricAvailability
import com.example.core.security.BiometricLockManager
import com.example.data.repository.NoteRepository
import com.example.domain.model.Category
import com.example.domain.model.ThemeMode
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

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBiometricLockEnabled(enabled)
            statusFlow.value = if (enabled) "Vault lock enabled" else "Vault lock disabled"
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
            statusFlow.value = "Vault PIN saved & lock enabled"
        }
    }

    fun clearVaultPin() {
        viewModelScope.launch {
            preferencesRepository.clearVaultPin()
            statusFlow.value = "Vault PIN cleared"
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
                onSuccess = { "Encrypted vault backup saved (${notes.size} notes)" },
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
                    statusFlow.value = "Imported & encrypted $imported note(s) into local vault"
                },
                onFailure = {
                    statusFlow.value = "Unable to decrypt backup. Verify passphrase and file format."
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
