package com.example.docscanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val folderId: String,
    val name: String,
    val pageCount: Int,
    val thumbnailPath: String? = null,
    val pdfPath: String? = null,
    val docClassLabel: String? = null,
    val createdAt: Long
)