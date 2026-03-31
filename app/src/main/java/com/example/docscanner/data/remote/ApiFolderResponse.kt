package com.example.docscanner.data.remote

data class ApiFolderResponse(
    val success: Boolean,
    val folders: List<ApiFolderDto>
)

data class ApiFolderDto(
    val id: String,
    val name: String,
    val icon: String = "📁",
    val documentCount: Int = 0,
    // The single DocClassType.displayName this folder accepts.
    // "Other" means it accepts any unmatched doc.
    val docType: String = "Other"
)