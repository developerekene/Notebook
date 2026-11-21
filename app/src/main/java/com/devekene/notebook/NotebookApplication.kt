package com.devekene.notebook

import android.app.Application
import android.content.Context
import com.devekene.notebook.core.crypto.AndroidKeystoreCryptoEngine
import com.devekene.notebook.core.crypto.CryptoEngine
import com.devekene.notebook.core.datastore.UserPreferencesRepository
import com.devekene.notebook.core.export.NoteExporter
import com.devekene.notebook.core.security.BiometricLockManager
import com.devekene.notebook.core.sync.OfflineVaultSyncEngine
import com.devekene.notebook.core.sync.SyncEngine
import com.devekene.notebook.data.local.NotebookDatabase
import com.devekene.notebook.data.repository.DefaultNoteRepository
import com.devekene.notebook.data.repository.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val cryptoEngine: CryptoEngine by lazy {
        AndroidKeystoreCryptoEngine(appContext)
    }

    val biometricLockManager: BiometricLockManager by lazy {
        BiometricLockManager(appContext)
    }

    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext, cryptoEngine)
    }

    val database: NotebookDatabase by lazy {
        NotebookDatabase.getInstance(appContext)
    }

    val noteRepository: NoteRepository by lazy {
        DefaultNoteRepository(
            noteDao = database.noteDao(),
            categoryDao = database.categoryDao(),
            cryptoEngine = cryptoEngine
        )
    }

    val syncEngine: SyncEngine by lazy {
        OfflineVaultSyncEngine(
            noteDao = database.noteDao(),
            preferencesRepository = preferencesRepository
        )
    }

    val noteExporter: NoteExporter by lazy {
        NoteExporter(appContext, cryptoEngine)
    }
}

class NotebookApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            container.noteRepository.ensureDefaultCategories()
        }
    }
}
