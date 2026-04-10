package com.example.docscanner.domain.model

data class AadhaarGroup(
    val groupId: String,           // derived: "aadhaar_<normalized_name>_<last4>" or UUID for manual
    val holderName: String?,
    val frontDoc: Document?,
    val backDoc: Document?,
    val isManuallyGrouped: Boolean = false
) {
    val isComplete get() = frontDoc != null && backDoc != null
    val displayName get() = frontDoc?.aadhaarName ?: backDoc?.aadhaarName ?: holderName ?: "Unknown holder"
    val dateOfBirth get() = frontDoc?.aadhaarDob ?: backDoc?.aadhaarDob
    val gender get() = frontDoc?.aadhaarGender ?: backDoc?.aadhaarGender
    val maskedNumber get() = frontDoc?.aadhaarMaskedNumber ?: backDoc?.aadhaarMaskedNumber
    val address get() = backDoc?.aadhaarAddress ?: frontDoc?.aadhaarAddress
}
