package com.devekene.notebook.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Note::class, CategoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class NotebookDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: NotebookDatabase? = null
        @Volatile
        private var cachedAppContext: Context? = null

        fun getInstance(context: Context): NotebookDatabase {
            val appContext = context.applicationContext
            val current = INSTANCE
            if (current != null && cachedAppContext === appContext && current.isOpen) {
                return current
            }
            return synchronized(this) {
                val existing = INSTANCE
                if (existing != null && cachedAppContext === appContext && existing.isOpen) {
                    existing
                } else {
                    runCatching { existing?.close() }
                    Room.databaseBuilder(
                        appContext,
                        NotebookDatabase::class.java,
                        "notebook_encrypted_vault.db"
                    )
                        .fallbackToDestructiveMigration(dropAllTables = true)
                        .build()
                        .also {
                            INSTANCE = it
                            cachedAppContext = appContext
                        }
                }
            }
        }
    }
}
