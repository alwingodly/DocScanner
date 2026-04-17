// domain/model/ExtractedFields.kt
package com.example.docscanner.domain.model

sealed interface ExtractedFields {
    val rawText: String
    val confidence: Float
    val details: List<DocumentDetail>

    data class AadhaarFront(
        val name: String?,
        val idNumber: String?,
        val dob: String?,
        val gender: String?,
        val bloodGroup: String?,
        override val rawText: String,
        override val confidence: Float,
        override val details: List<DocumentDetail>,
    ) : ExtractedFields

    data class AadhaarBack(
        val idNumber: String?,
        val address: String?,
        val pinCode: String?,
        val state: String?,
        override val rawText: String,
        override val confidence: Float,
        override val details: List<DocumentDetail>,
    ) : ExtractedFields

    data class Pan(
        val name: String?,
        val fatherName: String?,
        val idNumber: String?,
        val dob: String?,
        override val rawText: String,
        override val confidence: Float,
        override val details: List<DocumentDetail>,
    ) : ExtractedFields

    data class Passport(
        val name: String?,
        val surname: String? = null,
        val givenName: String? = null,
        val idNumber: String?,
        val dob: String?,
        val doi: String?,
        val doe: String?,
        val gender: String?,
        val nationality: String?,
        val countryCode: String? = null,
        val type: String? = null,
        val placeOfIssue: String? = null,
        val placeOfBirth: String? = null,
        // Back-page fields (address / observation page)
        val fatherName: String? = null,
        val motherName: String? = null,
        val spouseName: String? = null,
        val address: String? = null,
        val fileNo: String? = null,
        val mrzLines: List<String>,
        override val rawText: String,
        override val confidence: Float,
        override val details: List<DocumentDetail>,
    ) : ExtractedFields

    data class VoterId(
        val name: String?,
        val fatherName: String?,
        val spouseName: String?,
        val idNumber: String?,
        val dob: String?,
        val age: String?,
        val gender: String?,
        val address: String?,
        val pinCode: String?,
        val constituency: String?,
        override val rawText: String,
        override val confidence: Float,
        override val details: List<DocumentDetail>,
    ) : ExtractedFields

    data class DrivingLicence(
        val name: String?,
        val fatherName: String?,
        val idNumber: String?,
        val dob: String?,
        val doi: String?,
        val doe: String?,
        val gender: String?,
        val bloodGroup: String?,
        val address: String?,
        val pinCode: String?,
        val state: String?,
        val vehicleCategories: List<String>,
        override val rawText: String,
        override val confidence: Float,
        override val details: List<DocumentDetail>,
    ) : ExtractedFields

    data class Unknown(
        override val rawText: String,
        override val confidence: Float = 0f,
        override val details: List<DocumentDetail> = emptyList(),
    ) : ExtractedFields
}