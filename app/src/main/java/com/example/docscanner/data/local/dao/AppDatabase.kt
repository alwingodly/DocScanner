package com.example.docscanner.data.local.dao

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.docscanner.data.local.entity.DocumentEntity
import com.example.docscanner.data.local.entity.FolderEntity

@Database(
    entities     = [FolderEntity::class, DocumentEntity::class],
    version      = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
}