package com.example.docscanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "application_sessions")
data class ApplicationSessionEntity(
    @PrimaryKey val id: String,
    val name: String,                  // e.g. "Alwin Home Loan"
    val applicationType: String,       // e.g. "HOME_LOAN"
    val referenceNumber: String? = null, // from QR or manual entry
    val status: String = "PENDING",    // PENDING | IN_PROGRESS | COMPLETED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)