package com.example.docscanner.data.remote

import com.example.docscanner.domain.model.ApplicationType
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderApiService @Inject constructor() {

    /**
     * TODO: Replace with real Retrofit call
     * e.g. return retrofitService.getFolders(applicationType.name)
     */
    suspend fun getFolders(
        applicationType: ApplicationType? = null
    ): ApiFolderResponse {
        delay(800) // simulate network

        val folders = when (applicationType) {
            ApplicationType.PERSONAL_LOAN -> listOf(
                ApiFolderDto(id = "income_proof",    name = "Income Proof",    icon = "💰"),
                ApiFolderDto(id = "bank_statement",  name = "Bank Statement",  icon = "🏦"),
                ApiFolderDto(id = "id_proof",        name = "ID Proof",        icon = "🪪"),
                ApiFolderDto(id = "photo",           name = "Photograph",      icon = "📸"),
                ApiFolderDto(id = "form",            name = "Loan Form",       icon = "📋")
            )
            ApplicationType.HOME_LOAN -> listOf(
                ApiFolderDto(id = "property_docs",   name = "Property Docs",   icon = "🏠"),
                ApiFolderDto(id = "noc",             name = "NOC",             icon = "📜"),
                ApiFolderDto(id = "income_proof",    name = "Income Proof",    icon = "💰"),
                ApiFolderDto(id = "id_proof",        name = "ID Proof",        icon = "🪪"),
                ApiFolderDto(id = "bank_statement",  name = "Bank Statement",  icon = "🏦")
            )
            ApplicationType.BANK_ACCOUNT -> listOf(
                ApiFolderDto(id = "id_proof",        name = "ID Proof",        icon = "🪪"),
                ApiFolderDto(id = "address_proof",   name = "Address Proof",   icon = "🏘️"),
                ApiFolderDto(id = "photo",           name = "Photograph",      icon = "📸"),
                ApiFolderDto(id = "form",            name = "Account Form",    icon = "📋")
            )
            ApplicationType.PASSPORT_APPLICATION -> listOf(
                ApiFolderDto(id = "birth_cert",      name = "Birth Certificate", icon = "📄"),
                ApiFolderDto(id = "address_proof",   name = "Address Proof",   icon = "🏘️"),
                ApiFolderDto(id = "id_proof",        name = "ID Proof",        icon = "🪪"),
                ApiFolderDto(id = "photo",           name = "Photograph",      icon = "📸")
            )
            ApplicationType.VISA_APPLICATION -> listOf(
                ApiFolderDto(id = "passport_copy",   name = "Passport Copy",   icon = "📔"),
                ApiFolderDto(id = "photo",           name = "Photograph",      icon = "📸"),
                ApiFolderDto(id = "bank_statement",  name = "Bank Statement",  icon = "🏦"),
                ApiFolderDto(id = "invitation",      name = "Invitation Letter", icon = "✉️")
            )
            ApplicationType.INSURANCE_CLAIM -> listOf(
                ApiFolderDto(id = "policy_doc",      name = "Policy Document", icon = "🛡️"),
                ApiFolderDto(id = "id_proof",        name = "ID Proof",        icon = "🪪"),
                ApiFolderDto(id = "medical_records", name = "Medical Records", icon = "🏥"),
                ApiFolderDto(id = "claim_form",      name = "Claim Form",      icon = "📋")
            )
            null -> listOf(
                // fallback — original global folders for doc scanner
                ApiFolderDto(id = "aadhaar",  name = "Aadhaar Card",    icon = "🪪"),
                ApiFolderDto(id = "pan",      name = "PAN Card",        icon = "💳"),
                ApiFolderDto(id = "passport", name = "Passport",        icon = "📔"),
                ApiFolderDto(id = "license",  name = "Driving License", icon = "🚗"),
                ApiFolderDto(id = "bills",    name = "Bills",           icon = "🧾"),
                ApiFolderDto(id = "other",    name = "Other",           icon = "📁")
            )
        }

        return ApiFolderResponse(success = true, folders = folders)
    }
}