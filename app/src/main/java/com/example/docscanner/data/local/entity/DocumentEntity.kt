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
    val mergedFromDocumentIds: String? = null,
    val isMergedSource: Boolean = false,
    val aadhaarSide: String? = null,       // "FRONT", "BACK", or null
    val aadhaarGroupId: String? = null,
    val sessionId: String? = null,   // ← new, null = global doc scanner
    val docGroupId: String? = null,
    val aadhaarName: String? = null,
    val aadhaarDob: String? = null,
    val aadhaarGender: String? = null,
    val aadhaarMaskedNumber: String? = null,
    val aadhaarAddress: String? = null,
    val extractedDetailsJson: String? = null,
    val ocrRawText: String? = null,
    // ── Passport pairing ──────────────────────────────────────────────────────
    val passportGroupId: String? = null,
    val passportSide: String? = null,        // "FRONT" (data page) | "BACK" (address page)
    val passportHolderName: String? = null,  // holder name for display in group tile
    val passportNumHash: String? = null,     // SHA-256(passportNumber) for cross-session matching
)
