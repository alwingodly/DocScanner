package com.example.docscanner.domain.model

data class AadhaarGroup(
    val groupId: String,           // derived: "aadhaar_<normalized_name>_<last4>" or UUID for manual
    val holderName: String?,
    val frontDoc: Document?,
    val backDoc: Document?,
    val isManuallyGrouped: Boolean = false
) {
    val isComplete get() = frontDoc != null && backDoc != null
    val displayName get() = holderName ?: "Unknown holder"
}