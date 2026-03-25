package com.example.docscanner.data.local.dao

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.docscanner.data.local.entity.ApplicationDocumentEntity
import com.example.docscanner.data.local.entity.ApplicationSessionEntity
import com.example.docscanner.data.local.entity.DocumentEntity
import com.example.docscanner.data.local.entity.FolderEntity

@Database(
    entities = [
        FolderEntity::class,
        DocumentEntity::class,
        ApplicationSessionEntity::class,
        ApplicationDocumentEntity::class
    ],
    version = 8,          // ← bump from 7
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun applicationSessionDao(): ApplicationSessionDao
    abstract fun applicationDocumentDao(): ApplicationDocumentDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS application_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        applicationType TEXT NOT NULL,
                        referenceNumber TEXT,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS application_documents (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        documentId TEXT NOT NULL,
                        docTypeRequired TEXT NOT NULL,
                        uploadedAt INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN sessionId TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {  // ← new
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN sessionId TEXT")
            }
        }
    }
}