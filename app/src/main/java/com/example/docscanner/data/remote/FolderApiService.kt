package com.example.docscanner.data.remote

import com.example.docscanner.domain.model.ApplicationType
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

// ── ML-routable docTypes (model can classify these directly) ──────────────────
// "Aadhaar" | "PAN Card" | "Passport" | "Voter ID" | "Other"
//
// ── Manual-only docTypes (model always returns Other for these) ───────────────
// "DL" | "Photograph" | "Income Document" | "Bank Statement" |
// "Property Document" | "Rent Agreement" | "Bill" | "Insurance" |
// "Medical Record" | "Educational Certificate" | "Tax Document" |
// "Business Document"
//
// Flow:
//   Scan → ML classifies → label set → doc lands in matching folder
//   If ML says "Other" → lands in "Other Docs" → user sees Unclassified tab
//   User moves doc to correct folder (DL, Bill etc) via "Move to folder"
//   docClassLabel updates to that folder's docType → disappears from Unclassified

@Singleton
class FolderApiService @Inject constructor() {

    suspend fun getFolders(
        applicationType: ApplicationType? = null
    ): ApiFolderResponse {
        delay(300)

        val folders = when (applicationType) {

            ApplicationType.PERSONAL_LOAN -> listOf(
                // ML-routable
                ApiFolderDto(id = "aadhaar",  name = "Aadhaar",  icon = "🪪", docType = "Aadhaar"),
                ApiFolderDto(id = "pan",      name = "PAN Card", icon = "💳", docType = "PAN Card"),
                ApiFolderDto(id = "voter",    name = "Voter ID", icon = "🗳️", docType = "Voter ID"),
                ApiFolderDto(id = "passport", name = "Passport", icon = "📔", docType = "Passport"),
                // Manual-only
                ApiFolderDto(id = "dl",       name = "Driving License",  icon = "🚗", docType = "DL"),
                ApiFolderDto(id = "income",   name = "Income Document",  icon = "💰", docType = "Income Document"),
                ApiFolderDto(id = "bank",     name = "Bank Statement",   icon = "🏦", docType = "Bank Statement"),
                // Catch-all (ML Other lands here)
                ApiFolderDto(id = "other",    name = "Other Docs",       icon = "📁", docType = "Other")
            )

            ApplicationType.HOME_LOAN -> listOf(
                // ML-routable
                ApiFolderDto(id = "aadhaar",  name = "Aadhaar",  icon = "🪪", docType = "Aadhaar"),
                ApiFolderDto(id = "pan",      name = "PAN Card", icon = "💳", docType = "PAN Card"),
                ApiFolderDto(id = "voter",    name = "Voter ID", icon = "🗳️", docType = "Voter ID"),
                ApiFolderDto(id = "passport", name = "Passport", icon = "📔", docType = "Passport"),
                // Manual-only
                ApiFolderDto(id = "dl",       name = "Driving License",  icon = "🚗", docType = "DL"),
                ApiFolderDto(id = "income",   name = "Income Document",  icon = "💰", docType = "Income Document"),
                ApiFolderDto(id = "bank",     name = "Bank Statement",   icon = "🏦", docType = "Bank Statement"),
                ApiFolderDto(id = "property", name = "Property Document",icon = "🏠", docType = "Property Document"),
                ApiFolderDto(id = "rent",     name = "Rent Agreement",   icon = "📋", docType = "Rent Agreement"),
                // Catch-all
                ApiFolderDto(id = "other",    name = "Other Docs",       icon = "📁", docType = "Other")
            )

            ApplicationType.BANK_ACCOUNT -> listOf(
                // ML-routable
                ApiFolderDto(id = "aadhaar",  name = "Aadhaar",  icon = "🪪", docType = "Aadhaar"),
                ApiFolderDto(id = "pan",      name = "PAN Card", icon = "💳", docType = "PAN Card"),
                ApiFolderDto(id = "voter",    name = "Voter ID", icon = "🗳️", docType = "Voter ID"),
                ApiFolderDto(id = "passport", name = "Passport", icon = "📔", docType = "Passport"),
                // Manual-only
                ApiFolderDto(id = "dl",       name = "Driving License",  icon = "🚗", docType = "DL"),
                ApiFolderDto(id = "photo",    name = "Photograph",       icon = "📷", docType = "Photograph"),
                // Catch-all
                ApiFolderDto(id = "other",    name = "Other Docs",       icon = "📁", docType = "Other")
            )

            ApplicationType.PASSPORT_APPLICATION -> listOf(
                // ML-routable
                ApiFolderDto(id = "aadhaar",  name = "Aadhaar",  icon = "🪪", docType = "Aadhaar"),
                ApiFolderDto(id = "pan",      name = "PAN Card", icon = "💳", docType = "PAN Card"),
                ApiFolderDto(id = "voter",    name = "Voter ID", icon = "🗳️", docType = "Voter ID"),
                ApiFolderDto(id = "passport", name = "Passport", icon = "📔", docType = "Passport"),
                // Manual-only
                ApiFolderDto(id = "dl",       name = "Driving License",  icon = "🚗", docType = "DL"),
                ApiFolderDto(id = "photo",    name = "Photograph",       icon = "📷", docType = "Photograph"),
                ApiFolderDto(id = "bill",     name = "Address Proof",    icon = "🧾", docType = "Bill"),
                // Catch-all
                ApiFolderDto(id = "other",    name = "Other Docs",       icon = "📁", docType = "Other")
            )

            ApplicationType.VISA_APPLICATION -> listOf(
                // ML-routable
                ApiFolderDto(id = "passport", name = "Passport", icon = "📔", docType = "Passport"),
                ApiFolderDto(id = "aadhaar",  name = "Aadhaar",  icon = "🪪", docType = "Aadhaar"),
                ApiFolderDto(id = "pan",      name = "PAN Card", icon = "💳", docType = "PAN Card"),
                ApiFolderDto(id = "voter",    name = "Voter ID", icon = "🗳️", docType = "Voter ID"),
                // Manual-only
                ApiFolderDto(id = "dl",       name = "Driving License",  icon = "🚗", docType = "DL"),
                ApiFolderDto(id = "photo",    name = "Photograph",       icon = "📷", docType = "Photograph"),
                ApiFolderDto(id = "bank",     name = "Bank Statement",   icon = "🏦", docType = "Bank Statement"),
                ApiFolderDto(id = "income",   name = "Income Document",  icon = "💰", docType = "Income Document"),
                // Catch-all
                ApiFolderDto(id = "other",    name = "Other Docs",       icon = "📁", docType = "Other")
            )

            ApplicationType.INSURANCE_CLAIM -> listOf(
                // ML-routable
                ApiFolderDto(id = "aadhaar",  name = "Aadhaar",  icon = "🪪", docType = "Aadhaar"),
                ApiFolderDto(id = "pan",      name = "PAN Card", icon = "💳", docType = "PAN Card"),
                ApiFolderDto(id = "voter",    name = "Voter ID", icon = "🗳️", docType = "Voter ID"),
                ApiFolderDto(id = "passport", name = "Passport", icon = "📔", docType = "Passport"),
                // Manual-only
                ApiFolderDto(id = "dl",        name = "Driving License", icon = "🚗", docType = "DL"),
                ApiFolderDto(id = "insurance", name = "Insurance Policy",icon = "🛡️", docType = "Insurance"),
                ApiFolderDto(id = "medical",   name = "Medical Record",  icon = "🏥", docType = "Medical Record"),
                // Catch-all
                ApiFolderDto(id = "other",     name = "Other Docs",      icon = "📁", docType = "Other")
            )

            // ── Global doc scanner (no session) ──────────────────────────────
            null -> listOf(
                // ML-routable (model knows these)
                ApiFolderDto(id = "aadhaar",  name = "Aadhaar",  icon = "🪪", docType = "Aadhaar"),
                ApiFolderDto(id = "pan",      name = "PAN Card", icon = "💳", docType = "PAN Card"),
                ApiFolderDto(id = "passport", name = "Passport", icon = "📔", docType = "Passport"),
                ApiFolderDto(id = "voter",    name = "Voter ID", icon = "🗳️", docType = "Voter ID"),
                // Manual-only (model returns Other for these, user moves manually)
                ApiFolderDto(id = "dl",        name = "Driving License",         icon = "🚗", docType = "DL"),
                ApiFolderDto(id = "photo",     name = "Photograph",              icon = "📷", docType = "Photograph"),
                ApiFolderDto(id = "income",    name = "Income Document",         icon = "💰", docType = "Income Document"),
                ApiFolderDto(id = "bank",      name = "Bank Statement",          icon = "🏦", docType = "Bank Statement"),
                ApiFolderDto(id = "property",  name = "Property Document",       icon = "🏠", docType = "Property Document"),
                ApiFolderDto(id = "rent",      name = "Rent Agreement",          icon = "📋", docType = "Rent Agreement"),
                ApiFolderDto(id = "bill",      name = "Bill",                    icon = "🧾", docType = "Bill"),
                ApiFolderDto(id = "insurance", name = "Insurance",               icon = "🛡️", docType = "Insurance"),
                ApiFolderDto(id = "medical",   name = "Medical Record",          icon = "🏥", docType = "Medical Record"),
                ApiFolderDto(id = "education", name = "Educational Certificate", icon = "🎓", docType = "Educational Certificate"),
                ApiFolderDto(id = "tax",       name = "Tax Document",            icon = "📊", docType = "Tax Document"),
                ApiFolderDto(id = "business",  name = "Business Document",       icon = "💼", docType = "Business Document"),
                // Catch-all (ML Other lands here)
                ApiFolderDto(id = "other",     name = "Other Docs",              icon = "📁", docType = "Other")
            )
        }

        return ApiFolderResponse(success = true, folders = folders)
    }
}