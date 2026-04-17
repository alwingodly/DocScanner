package com.example.docscanner.data.local.dao

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.docscanner.data.local.entity.ApplicationDocumentEntity
import com.example.docscanner.data.local.entity.ApplicationSessionEntity
import com.example.docscanner.data.local.entity.DocGroupEntity
import com.example.docscanner.data.local.entity.DocumentEntity
import com.example.docscanner.data.local.entity.FolderEntity

@Database(
    entities = [
        FolderEntity::class,
        DocumentEntity::class,
        ApplicationSessionEntity::class,
        ApplicationDocumentEntity::class,
        DocGroupEntity::class           // ← new
    ],
    version = 17,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun applicationSessionDao(): ApplicationSessionDao
    abstract fun applicationDocumentDao(): ApplicationDocumentDao
    abstract fun docGroupDao(): DocGroupDao         // ← new

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

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN sessionId TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE folders ADD COLUMN docType TEXT NOT NULL DEFAULT 'Other'"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN aadhaarSide TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN aadhaarGroupId TEXT")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN docGroupId TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {     // ← new
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS doc_groups (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN aadhaarName TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN aadhaarDob TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN aadhaarGender TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN aadhaarMaskedNumber TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN aadhaarAddress TEXT")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN extractedDetailsJson TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN ocrRawText TEXT")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN passportGroupId TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN passportSide TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN passportHolderName TEXT")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN passportNumHash TEXT")
            }
        }

    }
}
