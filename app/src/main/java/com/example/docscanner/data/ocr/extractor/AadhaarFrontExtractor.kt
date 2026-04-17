// data/ocr/extractor/AadhaarFrontExtractor.kt
package com.example.docscanner.data.ocr.extractor

import com.example.docscanner.data.ocr.ExtractionUtils
import com.example.docscanner.data.ocr.ExtractionUtils.AADHAAR_12
import com.example.docscanner.data.ocr.ExtractionUtils.DATE_FALLBACK_FULL
import com.example.docscanner.data.ocr.ExtractionUtils.VID_LINE
import com.example.docscanner.data.ocr.ExtractionUtils.correctNumericNoise
import com.example.docscanner.data.ocr.ExtractionUtils.maskAadhaar
import com.example.docscanner.data.ocr.ExtractionUtils.normalizeDate
import com.example.docscanner.data.ocr.TextBlock
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.ExtractedFields
import javax.inject.Inject

class AadhaarFrontExtractor @Inject constructor() : DocumentExtractor {
    override val docType = DocClassType.AADHAAR_FRONT

    override suspend fun extract(
        blocks: List<TextBlock>,
        rawText: String,
        docHeight: Int,
    ): ExtractedFields.AadhaarFront {
        val number = findAadhaarNumber(blocks)
        val name = extractNameScored(blocks, rawText, docHeight, isAadhaarFront = true)
        val dob = ExtractionUtils.extractLabeledDate(rawText, ExtractionUtils.DOB_LABEL)
            ?: DATE_FALLBACK_FULL.find(rawText)?.groupValues?.getOrNull(1)?.let { normalizeDate(it) }
        val gender = ExtractionUtils.extractGender(rawText)
        val blood = ExtractionUtils.extractBloodGroup(rawText)

        return ExtractedFields.AadhaarFront(
            name = name,
            idNumber = number,
            dob = dob,
            gender = gender,
            bloodGroup = blood,
            rawText = rawText,
            confidence = if (number != null && name != null) 0.92f else 0.62f,
            details = details {
                f("Name", name)
                f("Aadhaar", number?.let { maskAadhaar(it) })
                f("DOB", dob)
                f("Gender", gender)
                f("Blood Group", blood)
            },
        )
    }

    private fun findAadhaarNumber(blocks: List<TextBlock>): String? {
        data class C(val digits: String, val diversity: Int)
        val pool = mutableListOf<C>()
        for (block in blocks) for (line in block.lines) {
            val text = line.text.trim()
            if (VID_LINE.containsMatchIn(text)) continue
            val m = AADHAAR_12.find(text) ?: AADHAAR_12.find(correctNumericNoise(text)) ?: continue
            val digits = "${m.groupValues[1]}${m.groupValues[2]}${m.groupValues[3]}"
            if (isValidAadhaar(digits)) pool += C(digits, digits.toSet().size)
        }
        return pool.maxByOrNull { it.diversity }?.digits
    }

    private fun isValidAadhaar(num: String): Boolean =
        num.length == 12 && num[0] !in listOf('0', '1') && num.toSet().size > 2
}