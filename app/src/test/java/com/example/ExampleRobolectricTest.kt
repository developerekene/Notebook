package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.crypto.AndroidKeystoreCryptoEngine
import com.example.core.export.NoteExporter
import com.example.data.local.NotebookDatabase
import com.example.data.repository.DefaultNoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: NotebookDatabase
    private lateinit var cryptoEngine: AndroidKeystoreCryptoEngine
    private lateinit var repository: DefaultNoteRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NotebookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cryptoEngine = AndroidKeystoreCryptoEngine(context)
        repository = DefaultNoteRepository(
            noteDao = database.noteDao(),
            categoryDao = database.categoryDao(),
            cryptoEngine = cryptoEngine
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app name is Notebook`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Notebook", appName)
    }

    @Test
    fun `saveNote encrypts fields in Room Note entity with auto-generated ID and decrypts cleanly`() = runBlocking {
        val saved = repository.saveNote(
            id = null,
            title = "Zero-Knowledge Architecture",
            content = "All notes are encrypted at rest with AES-256-GCM.",
            categoryName = "Research",
            tags = listOf("security", "Crypto"),
            isPinned = true,
            isLocked = false,
            voiceDurationSec = 12
        )

        assertTrue(saved.id > 0L)
        assertEquals("Zero-Knowledge Architecture", saved.title)
        assertEquals(listOf("security", "crypto"), saved.tags)

        val rawEntity = database.noteDao().getNoteById(saved.id)!!
        assertNotEquals("Zero-Knowledge Architecture", rawEntity.title)
        assertNotEquals("All notes are encrypted at rest with AES-256-GCM.", rawEntity.content)
        assertTrue(rawEntity.titleIv.isNotBlank())
        assertTrue(rawEntity.contentIv.isNotBlank())

        val insights = repository.observeInsights(7).first()
        assertEquals(1, insights.totalNotes)
        assertEquals(1, insights.encryptedNotes)
        assertEquals(1, insights.pinnedNotes)
        assertEquals(2, insights.activeTagCount)
    }

    @Test
    fun `passphrase encrypted bundle round-trips and markdown export formats cleanly`() {
        val exporter = NoteExporter(context, cryptoEngine)
        val bundle = cryptoEngine.encryptWithPassphrase("Secret payload", "vault-pass-99")
        val decrypted = cryptoEngine.decryptWithPassphrase(bundle, "vault-pass-99")
        assertEquals("Secret payload", decrypted)

        val note = com.example.domain.model.NoteItem(
            id = 42L,
            title = "Offline Architecture",
            content = "Room + AES-256-GCM",
            categoryName = "Work",
            categoryColorHex = 0xFF1D4ED8L,
            tags = listOf("offline", "room"),
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
            wordCount = 5
        )
        val markdown = exporter.buildMarkdown(note)
        assertTrue(markdown.contains("title: \"Offline Architecture\""))
        assertTrue(markdown.contains("#offline #room"))
    }

    @Test
    fun `MainActivity ActivityResultRegistry supports RequestPermission without 16-bit requestCode crash`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val launcher = activity.activityResultRegistry.register(
            "test_audio_perm",
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { }
        launcher.launch(android.Manifest.permission.RECORD_AUDIO)
        launcher.unregister()
    }
}
