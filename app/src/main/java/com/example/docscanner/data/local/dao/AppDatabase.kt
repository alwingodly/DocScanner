package com.example.docscanner.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.docscanner.data.local.dao.DocumentDao
import com.example.docscanner.data.local.dao.FolderDao
import com.example.docscanner.data.local.entity.DocumentEntity
import com.example.docscanner.data.local.entity.FolderEntity

@Database(
    entities     = [FolderEntity::class, DocumentEntity::class],
    version      = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
}