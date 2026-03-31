package com.example.docscanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val exportType: String = "PDF",
    val createdAt: Long,
    val documentCount: Int = 0,
    val sortOrder: Int = 0,
    val sessionId: String? = null,
    val docType: String = "Other"   // ← new: maps to DocClassType.displayName
)