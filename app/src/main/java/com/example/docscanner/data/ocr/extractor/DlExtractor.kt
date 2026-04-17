// data/ocr/extractor/DlExtractor.kt
package com.example.docscanner.data.ocr.extractor

import com.example.docscanner.data.ocr.ExtractionUtils
import com.example.docscanner.data.ocr.ExtractionUtils.DL_NUMBER
import com.example.docscanner.data.ocr.ExtractionUtils.DL_VALIDITY
import com.example.docscanner.data.ocr.ExtractionUtils.DOB_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.DOE_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.DOI_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.FATHER_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.VEHICLE_CAT
import com.example.docscanner.data.ocr.TextBlock
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.ExtractedFields
import javax.inject.Inject

/** Also used for OTHER/generic identity docs. */
class DlExtractor @Inject constructor() : DocumentExtractor {
    override val docType = DocClassType.OTHER

    override suspend fun extract(
        blocks: List<TextBlock>,
        rawText: String,
        docHeight: Int,
    ): ExtractedFields.DrivingLicence {
        val dl = DL_NUMBER.find(rawText)?.value
        val name = extractNameScored(blocks, rawText, docHeight, isAadhaarFront = false)
        val father = extractRelationalName(rawText, FATHER_LABEL)
        val dob = ExtractionUtils.extractLabeledDate(rawText, DOB_LABEL)
        val doi = ExtractionUtils.extractLabeledDate(rawText, DOI_LABEL)
        val doe = ExtractionUtils.extractLabeledDate(rawText, DOE_LABEL)
            ?: DL_VALIDITY.find(rawText)?.groupValues?.getOrNull(1)
        val gender = ExtractionUtils.extractGender(rawText)
        val blood = ExtractionUtils.extractBloodGroup(rawText)
        val addr = ExtractionUtils.extractAddress(rawText)
        val pin = ExtractionUtils.extractPin(addr ?: rawText)
        val state = ExtractionUtils.detectState(rawText)
        val vcats = VEHICLE_CAT.findAll(rawText).map { it.value.uppercase() }.distinct().toList()

        return ExtractedFields.DrivingLicence(
            name = name,
            fatherName = father,
            idNumber = dl,
            dob = dob,
            doi = doi,
            doe = doe,
            gender = gender,
            bloodGroup = blood,
            address = addr,
            pinCode = pin,
            state = state,
            vehicleCategories = vcats,
            rawText = rawText,
            confidence = if (name != null || dl != null) 0.75f else 0.40f,
            details = details {
                f("Name", name)
                f(if (dl != null) "DL Number" else null, dl)
                f("Father's Name", father)
                f("DOB", dob)
                f("Gender", gender)
                f("Blood Group", blood)
                if (vcats.isNotEmpty()) f("Vehicle Categories", vcats.joinToString(", "))
                f("Date of Issue", doi)
                f("Valid Till", doe)
                f("Address", addr, multiline = true)
                f("PIN Code", pin)
                f("State", state)
                extra(ExtractionUtils.genericKV(rawText, setOf("name", "dob", "gender", "address")))
            },
        )
    }
}