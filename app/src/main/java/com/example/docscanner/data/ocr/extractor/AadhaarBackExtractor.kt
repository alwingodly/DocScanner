// data/ocr/extractor/AadhaarBackExtractor.kt
package com.example.docscanner.data.ocr.extractor

import com.example.docscanner.data.ocr.ExtractionUtils
import com.example.docscanner.data.ocr.ExtractionUtils.AADHAAR_12
import com.example.docscanner.data.ocr.ExtractionUtils.VID_LINE
import com.example.docscanner.data.ocr.ExtractionUtils.correctNumericNoise
import com.example.docscanner.data.ocr.ExtractionUtils.maskAadhaar
import com.example.docscanner.data.ocr.TextBlock
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.ExtractedFields
import javax.inject.Inject

class AadhaarBackExtractor @Inject constructor() : DocumentExtractor {
    override val docType = DocClassType.AADHAAR_BACK

    override suspend fun extract(
        blocks: List<TextBlock>,
        rawText: String,
        docHeight: Int,
    ): ExtractedFields.AadhaarBack {
        val number = findAadhaarNumber(blocks)
        val address = ExtractionUtils.extractAddress(rawText)
        val pin = ExtractionUtils.extractPin(address ?: rawText)
        val state = ExtractionUtils.detectState(rawText)

        return ExtractedFields.AadhaarBack(
            idNumber = number,
            address = address,
            pinCode = pin,
            state = state,
            rawText = rawText,
            confidence = if (address != null) 0.85f else 0.50f,
            details = details {
                f("Aadhaar", number?.let { maskAadhaar(it) })
                f("Address", address, multiline = true)
                f("PIN Code", pin)
                f("State", state)
            },
        )
    }

    private fun findAadhaarNumber(blocks: List<TextBlock>): String? {
        for (block in blocks) for (line in block.lines) {
            val text = line.text.trim()
            if (VID_LINE.containsMatchIn(text)) continue
            val m = AADHAAR_12.find(text) ?: AADHAAR_12.find(correctNumericNoise(text)) ?: continue
            val digits = "${m.groupValues[1]}${m.groupValues[2]}${m.groupValues[3]}"
            if (digits.length == 12 && digits[0] !in listOf('0', '1') && digits.toSet().size > 2)
                return digits
        }
        return null
    }
}