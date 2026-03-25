package com.example.docscanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "application_documents")
data class ApplicationDocumentEntity(
    @PrimaryKey val id: String,
    val sessionId: String,        // FK → application_sessions.id
    val documentId: String,       // FK → documents.id (existing table)
    val docTypeRequired: String,  // e.g. "Aadhaar", "PAN Card", "Salary Slip"
    val uploadedAt: Long = System.currentTimeMillis()
)