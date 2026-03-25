package com.example.docscanner.domain.model

enum class ApplicationType(val displayName: String, val icon: String) {
    BANK_ACCOUNT("Bank Account Opening", "🏦"),
    HOME_LOAN("Home Loan", "🏠"),
    PERSONAL_LOAN("Personal Loan", "💼"),
    PASSPORT_APPLICATION("Passport Application", "📔"),
    VISA_APPLICATION("Visa Application", "✈️"),
    INSURANCE_CLAIM("Insurance Claim", "🛡️")
}

enum class ApplicationStatus { PENDING, IN_PROGRESS, COMPLETED }

data class ApplicationSession(
    val id: String = System.currentTimeMillis().toString(),
    val name: String,
    val applicationType: ApplicationType,
    val referenceNumber: String? = null,
    val status: ApplicationStatus = ApplicationStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ApplicationDocument(
    val id: String = System.currentTimeMillis().toString(),
    val sessionId: String,
    val documentId: String,
    val docTypeRequired: String,
    val uploadedAt: Long = System.currentTimeMillis()
)