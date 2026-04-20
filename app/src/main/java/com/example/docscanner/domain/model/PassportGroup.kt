package com.example.docscanner.domain.model

/**
 * Represents a pair of passport scans (data page + address/back page) that share
 * the same passport number. Either side may be null if only one page has been scanned.
 *
 * Side convention:
 *   FRONT = biographical data page (contains MRZ, photo, personal details)
 *   BACK  = address / observation page (no MRZ)
 */
data class PassportGroup(
    val groupId: String,
    val holderName: String?,
    val frontDoc: Document?,   // data page (has MRZ)
    val backDoc: Document?,    // address/back page
    val isManuallyGrouped: Boolean = false,
) {
    val isComplete   get() = frontDoc != null && backDoc != null
    val displayName  get() = holderName ?: frontDoc?.passportHolderName ?: backDoc?.passportHolderName ?: "Unknown holder"
    val previewDoc   get() = frontDoc ?: backDoc
}
