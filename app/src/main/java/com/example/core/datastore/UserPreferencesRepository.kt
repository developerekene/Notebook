package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.crypto.CryptoEngine
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notebookPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notebook_preferences"
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val biometricLockEnabled: Boolean = false,
    val lockOnBackground: Boolean = true,
    val hasVaultPin: Boolean = false,
    val lastBackupTimestamp: Long = 0L,
    val lastSyncAttemptTimestamp: Long = 0L
)

class UserPreferencesRepository(
    private val context: Context,
    private val cryptoEngine: CryptoEngine
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock_enabled")
        val LOCK_ON_BACKGROUND = booleanPreferencesKey("lock_on_background")
        val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
        val VAULT_PIN_SALT = stringPreferencesKey("vault_pin_salt")
        val LAST_BACKUP_TS = longPreferencesKey("last_backup_ts")
        val LAST_SYNC_TS = longPreferencesKey("last_sync_ts")
    }

    val preferencesFlow: Flow<UserPreferences> = context.notebookPrefsDataStore.data.map { prefs ->
        val themeRaw = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        val theme = ThemeMode.entries.firstOrNull { it.name == themeRaw } ?: ThemeMode.DARK
        UserPreferences(
            themeMode = theme,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            lockOnBackground = prefs[Keys.LOCK_ON_BACKGROUND] ?: true,
            hasVaultPin = !prefs[Keys.VAULT_PIN_HASH].isNullOrBlank(),
            lastBackupTimestamp = prefs[Keys.LAST_BACKUP_TS] ?: 0L,
            lastSyncAttemptTimestamp = prefs[Keys.LAST_SYNC_TS] ?: 0L
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.notebookPrefsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.notebookPrefsDataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setLockOnBackground(enabled: Boolean) {
        context.notebookPrefsDataStore.edit { it[Keys.LOCK_ON_BACKGROUND] = enabled }
    }

    suspend fun setVaultPin(pin: String) {
        val salt = cryptoEngine.generateSaltBase64()
        val hash = cryptoEngine.hashPin(pin, salt)
        context.notebookPrefsDataStore.edit {
            it[Keys.VAULT_PIN_SALT] = salt
            it[Keys.VAULT_PIN_HASH] = hash
        }
    }

    suspend fun clearVaultPin() {
        context.notebookPrefsDataStore.edit {
            it.remove(Keys.VAULT_PIN_SALT)
            it.remove(Keys.VAULT_PIN_HASH)
        }
    }

    suspend fun verifyVaultPin(pin: String): Boolean {
        val prefs = context.notebookPrefsDataStore.data.first()
        val salt = prefs[Keys.VAULT_PIN_SALT] ?: return false
        val expectedHash = prefs[Keys.VAULT_PIN_HASH] ?: return false
        return cryptoEngine.hashPin(pin, salt) == expectedHash
    }

    suspend fun recordBackupTimestamp(timestamp: Long = System.currentTimeMillis()) {
        context.notebookPrefsDataStore.edit { it[Keys.LAST_BACKUP_TS] = timestamp }
    }

    suspend fun recordSyncAttemptTimestamp(timestamp: Long = System.currentTimeMillis()) {
        context.notebookPrefsDataStore.edit { it[Keys.LAST_SYNC_TS] = timestamp }
    }
}
