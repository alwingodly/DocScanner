package com.example.docscanner.data.remote

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderApiService @Inject constructor() {

    /**
     * TODO: Replace with real Retrofit call
     * e.g. return retrofitService.getFolders()
     */
    suspend fun getFolders(): ApiFolderResponse {
        delay(800) // simulate network
        return ApiFolderResponse(
            success = true,
            folders = listOf(
                ApiFolderDto(id = "aadhaar",  name = "Aadhaar Card",   icon = "🪪"),
                ApiFolderDto(id = "pan",      name = "PAN Card",        icon = "💳"),
                ApiFolderDto(id = "passport", name = "Passport",        icon = "📔"),
                ApiFolderDto(id = "license",  name = "Driving License", icon = "🚗"),
                ApiFolderDto(id = "bills",    name = "Bills",           icon = "🧾"),
                ApiFolderDto(id = "other",    name = "Other",           icon = "📁"),
            )
        )
    }
}