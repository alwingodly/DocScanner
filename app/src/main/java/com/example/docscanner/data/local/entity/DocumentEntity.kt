package com.example.docscanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey
    val id: String,
    val folderId: String,
    val name: String,
    val pageCount: Int,
    val thumbnailPath: String?,
    val pdfPath: String?,
    val docClassLabel: String?,
    val createdAt: Long,

    /** Comma-separated IDs of source documents (for merged PDFs) */
    val mergedFromDocumentIds: String? = null,

    /** True if this document is hidden because it was merged into a PDF */
    val isMergedSource: Boolean = false
)