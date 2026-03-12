package com.example.docscanner.data.remote

data class ApiFolderResponse(
    val success: Boolean,
    val folders: List<ApiFolderDto>
)

data class ApiFolderDto(
    val id: String,
    val name: String,
    val icon: String = "📁",
    val documentCount: Int = 0
)