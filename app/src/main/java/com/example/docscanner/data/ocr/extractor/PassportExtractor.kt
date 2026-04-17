// data/ocr/extractor/PassportExtractor.kt
package com.example.docscanner.data.ocr.extractor

import com.example.docscanner.data.ocr.ExtractionUtils
import com.example.docscanner.data.ocr.ExtractionUtils.DOB_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.DOE_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.DOI_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.NATIONALITY
import com.example.docscanner.data.ocr.ExtractionUtils.PASSPORT_NO
import com.example.docscanner.data.ocr.TextBlock
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.ExtractedFields
import javax.inject.Inject

class PassportExtractor @Inject constructor() : DocumentExtractor {
    override val docType = DocClassType.PASSPORT

    override suspend fun extract(
        blocks: List<TextBlock>,
        rawText: String,
        docHeight: Int,
    ): ExtractedFields.Passport {

        // ── 1. MRZ parsing (most reliable source) ────────────────────────────
        val mrzLines = extractMrz(rawText)
        val mrz = if (mrzLines.size >= 2) parseMrzFull(mrzLines) else null

        // ── 2. Visual / regex extraction (for fields absent from MRZ) ─────────
        val passportNo = mrz?.passportNo
            ?: ExtractionUtils.correctNumericNoise(rawText).let { PASSPORT_NO.find(it)?.groupValues?.getOrNull(1) }

        val surname = mrz?.surname
            ?: extractLabelBelow(blocks, "Surname")
            ?: ExtractionUtils.SURNAME_LABEL.find(rawText)?.groupValues?.getOrNull(1)
                ?.let { titleCase(it) }

        val givenName = mrz?.givenName
            ?: extractLabelBelow(blocks, "Given Name")
            ?: ExtractionUtils.GIVEN_NAME_LABEL.find(rawText)?.groupValues?.getOrNull(1)
                ?.let { titleCase(it) }

        // Full name: prefer MRZ-derived surname + given name for consistency
        val name = when {
            givenName != null && surname != null -> "$givenName $surname"
            givenName != null -> givenName
            surname != null -> surname
            else -> extractNameScored(blocks, rawText, docHeight, isAadhaarFront = false)
        }

        val dob = mrz?.dob
            ?: ExtractionUtils.extractLabeledDate(rawText, DOB_LABEL)

        val doi = extractLabeledDateSpatial(blocks, "Date of Issue")
            ?: ExtractionUtils.extractLabeledDate(rawText, DOI_LABEL)

        val doe = mrz?.doe
            ?: extractLabeledDateSpatial(blocks, "Date of Expiry")
            ?: ExtractionUtils.extractLabeledDate(rawText, DOE_LABEL)

        val nationality = mrz?.nationality
            ?: NATIONALITY.find(rawText)?.groupValues?.getOrNull(1)?.uppercase()
            ?: extractLabelBelow(blocks, "Nationality")?.uppercase()
            ?: "INDIAN"

        val gender = mrz?.sex
            ?: ExtractionUtils.extractGender(rawText)

        val countryCode = mrz?.countryCode
            ?: ExtractionUtils.COUNTRY_CODE_LABEL.find(rawText)?.groupValues?.getOrNull(1)

        val type = mrz?.type ?: extractPassportType(rawText)

        val placeOfIssue = extractLabelBelow(blocks, "Place of Issue")
            ?: ExtractionUtils.PLACE_OF_ISSUE.find(rawText)?.groupValues?.getOrNull(1)?.trim()

        val placeOfBirth = extractLabelBelow(blocks, "Place of Birth")
            ?: ExtractionUtils.PLACE_OF_BIRTH.find(rawText)?.groupValues?.getOrNull(1)?.trim()

        // ── Back-page fields (only extracted / shown when no MRZ = back page) ──
        val isFrontPage = mrzLines.isNotEmpty()

        val fatherName = if (isFrontPage) null else
            extractLabelBelow(blocks, "Name of Father")
                ?: extractLabelBelow(blocks, "Legal Guardian")
                ?: extractLabelBelow(blocks, "Father/Legal Guardian")
                ?: ExtractionUtils.PASSPORT_FATHER.find(rawText)?.groupValues?.getOrNull(1)?.trim()
                    ?.let { normalizePersonName(it) }

        val motherName = if (isFrontPage) null else
            extractLabelBelow(blocks, "Name of Mother")
                ?: extractLabelBelow(blocks, "Mother")
                ?: ExtractionUtils.PASSPORT_MOTHER.find(rawText)?.groupValues?.getOrNull(1)?.trim()
                    ?.let { normalizePersonName(it) }

        val spouseName = if (isFrontPage) null else
            extractLabelBelow(blocks, "Name of Spouse")
                ?: extractLabelBelow(blocks, "Spouse")
                ?: ExtractionUtils.PASSPORT_SPOUSE.find(rawText)?.groupValues?.getOrNull(1)?.trim()
                    ?.let { normalizePersonName(it) }

        val fileNo = if (isFrontPage) null else
            extractLabelBelow(blocks, "File No")
                ?: extractLabelBelow(blocks, "File Number")
                ?: ExtractionUtils.PASSPORT_FILE_NO.find(rawText)?.groupValues?.getOrNull(1)?.trim()

        val address = if (isFrontPage) null else
            extractMultilineBelow(blocks, "Address")
                ?: ExtractionUtils.PASSPORT_ADDRESS.find(rawText)?.groupValues?.getOrNull(1)
                    ?.lines()?.map { it.trim() }?.filter { it.isNotBlank() }?.joinToString(", ")

        val hasCore = passportNo != null && (dob != null || name != null)

        // ── Build side-specific details list ─────────────────────────────────
        // Front (data page): biographic/MRZ-derived fields only.
        // Back (address page): back-page fields only.
        val detailsList = if (isFrontPage) {
            details {
                f("Type",           type)
                f("Country Code",   countryCode)
                f("Passport No",    passportNo)
                f("Surname",        surname)
                f("Given Name",     givenName)
                f("Date of Birth",  dob)
                f("Nationality",    nationality)
                f("Sex",            gender)
                f("Place of Birth", placeOfBirth)
                f("Place of Issue", placeOfIssue)
                f("Date of Issue",  doi)
                f("Date of Expiry", doe)
            }
        } else {
            details {
                f("Passport No",        passportNo)
                f("Father / Guardian",  fatherName)
                f("Mother",             motherName)
                f("Spouse",             spouseName)
                f("Address",            address)
                f("File No",            fileNo)
            }
        }

        return ExtractedFields.Passport(
            name         = name,
            surname      = surname,
            givenName    = givenName,
            idNumber     = passportNo,
            dob          = dob,
            doi          = doi,
            doe          = doe,
            gender       = gender,
            nationality  = nationality,
            countryCode  = countryCode,
            type         = type,
            placeOfIssue = placeOfIssue,
            placeOfBirth = placeOfBirth,
            fatherName   = fatherName,
            motherName   = motherName,
            spouseName   = spouseName,
            address      = address,
            fileNo       = fileNo,
            mrzLines     = mrzLines,
            rawText      = rawText,
            confidence   = if (hasCore) 0.90f else 0.55f,
            details      = detailsList,
        )
    }

    // ── MRZ helpers ───────────────────────────────────────────────────────────

    private data class MrzData(
        val type: String?,
        val countryCode: String?,
        val surname: String?,
        val givenName: String?,
        val passportNo: String?,
        val nationality: String?,
        val dob: String?,
        val sex: String?,
        val doe: String?,
    )

    /**
     * Extracts MRZ lines from raw OCR text with structural validation to avoid false
     * positives on back-page address text or barcode OCR noise.
     *
     * Structural requirements (ICAO 9303 / Indian passport):
     *  - Line 1 must start with 'P' + subtype char + 3-letter country code  e.g. "P<IND"
     *  - Line 2 must start with the passport-number prefix [A-Z]\d{6}+
     *  - Both lines must be present; if either is missing → no MRZ → back page
     *
     * Noise tolerance:
     *  - Mixed case → uppercased
     *  - Spaces within line → stripped
     *  - Minor illegal chars → replaced with '<'
     *  - Lines ≥ 35 chars with ≥ 80 % valid MRZ chars are considered candidates
     */
    private fun extractMrz(text: String): List<String> {
        // Step 1: build normalised candidate lines
        val candidates = text.lines().mapNotNull { rawLine ->
            val clean = rawLine.trim().uppercase().replace(" ", "")
            if (clean.length < 35) return@mapNotNull null
            val mrzCount = clean.count { it in 'A'..'Z' || it in '0'..'9' || it == '<' }
            if (mrzCount.toDouble() / clean.length < 0.80) return@mapNotNull null
            clean.map { c -> if (c in 'A'..'Z' || c in '0'..'9' || c == '<') c else '<' }
                .joinToString("").take(44)
        }

        // Step 2: structural validation — BOTH lines must be present
        //
        // Line 1 fingerprint (ICAO 9303, Indian passport):
        //   pos 0   : 'P'  (document type — only passports start with P)
        //   pos 1   : '<' or letter  (subtype — usually '<' for standard)
        //   pos 2-4 : 3-letter country code  (e.g. "IND")
        //   must contain "<<"  ← surname/given-name separator, NEVER in normal prose
        //   Example: "P<INDSHARMA<<RAJESH<<<<<<<<<<<<<<<<<<<<<<<<"
        //
        // Words like "PLACEOFBIRTH", "PASSPORTNO", "PERMANENTADDRESS" start with
        // P + letters(2-4) but do NOT contain "<<", so they are correctly rejected.
        val line1 = candidates.firstOrNull { line ->
            line.length >= 10 &&
                line[0] == 'P' &&
                (line[1] == '<' || line[1].isLetter()) &&
                line.substring(2, 5).all { it.isLetter() } &&
                line.contains("<<")          // ← decisive: MRZ name separator
        }

        // Line 2 fingerprint: starts with the passport-number pattern [A-Z]\d{6,}
        //   Example: "K9087218<IND8001011M2601014<<<<<<<<<<<<<<<6"
        val passportNumStart = Regex("""^[A-Z]\d{6}""")
        val line2 = candidates.firstOrNull { it != line1 && passportNumStart.containsMatchIn(it) }

        // Both lines must be identified; a single match means OCR caught only part of
        // the MRZ (or it's a back page / non-passport).
        return if (line1 != null && line2 != null) listOf(line1, line2) else emptyList()
    }

    private fun parseMrzFull(mrzLines: List<String>): MrzData? {
        if (mrzLines.size < 2) return null
        val line1 = mrzLines[0].padEnd(44, '<')
        val line2 = mrzLines[1].padEnd(44, '<')
        if (line1.length < 44 || line2.length < 44) return null

        val type = line1[0].takeIf { it.isLetter() && it != '<' }?.toString()

        val countryCode = line1.substring(2, 5).replace("<", "")
            .takeIf { it.length == 3 && it.all { c -> c.isLetter() } }

        val (surname, givenName) = parseMrzName(line1.substring(5))

        // Passport number: MRZ positions 0–8 is a 9-char field (padded with '<' to fill).
        // Indian passport: format is [A-Z]\d{7} (8 chars), position 8 is '<' padding.
        // If OCR misses the '<', position 8 becomes the check digit (a digit).
        // → Prefer the 8-char match; only fall back to longer if clearly not check digit.
        val passportField = line2.substring(0, 9).replace("<", "")
        val passportNo = when {
            passportField.length >= 8 &&
            passportField.take(8).matches(Regex("[A-Z]\\d{7}")) ->
                passportField.take(8)          // correct Indian format
            passportField.isNotBlank() -> passportField
            else -> null
        }

        val nationality = line2.substring(10, 13).replace("<", "")
            .takeIf { it.isNotBlank() && it.all { c -> c.isLetter() } }

        val dob = parseMrzDate(line2.substring(13, 19))

        val sex = when (line2[20]) {
            'M' -> "Male"
            'F' -> "Female"
            else -> null
        }

        val doe = parseMrzDate(line2.substring(21, 27))

        return MrzData(type, countryCode, surname, givenName, passportNo, nationality, dob, sex, doe)
    }

    /**
     * MRZ name field format: SURNAME<<GIVEN<NAMES<<<…
     * `<` within a component = space, `<<` = separator between surname and given names.
     */
    private fun parseMrzName(nameField: String): Pair<String?, String?> {
        val parts = nameField.split("<<")
        val surname = parts.getOrNull(0)
            ?.replace("<", " ")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { titleCase(it) }
        val givenName = parts.getOrNull(1)
            ?.replace("<", " ")?.trim()
            ?.split("\\s+".toRegex())?.filter { it.isNotBlank() }
            ?.joinToString(" ")
            ?.takeIf { it.isNotBlank() }
            ?.let { titleCase(it) }
        return surname to givenName
    }

    private fun parseMrzDate(yymmdd: String): String? {
        if (yymmdd.length < 6 || !yymmdd.all { it.isDigit() }) return null
        val yy = yymmdd.take(2).toIntOrNull() ?: return null
        val mm = yymmdd.substring(2, 4)
        val dd = yymmdd.substring(4, 6)
        val yyyy = if (yy > 30) "19$yy" else "20$yy"
        return "$dd/$mm/$yyyy"
    }

    // ── Spatial helpers ───────────────────────────────────────────────────────

    /**
     * Finds the text block immediately BELOW the block whose text contains [label].
     * Used for Indian passport layout where label is printed on one line and the
     * value is printed on the next line directly below it.
     */
    private fun extractLabelBelow(blocks: List<TextBlock>, label: String): String? {
        val labelLower = label.lowercase()
        val labelBlock = blocks.firstOrNull { b ->
            b.text.trim().lowercase().let { t ->
                t == labelLower || t.startsWith(labelLower) || t.contains(labelLower)
            }
        } ?: return null

        val labelBottom = labelBlock.boundingBox?.bottom ?: return null
        val labelLeft   = labelBlock.boundingBox?.left   ?: 0
        val labelRight  = labelBlock.boundingBox?.right  ?: Int.MAX_VALUE

        // Accept blocks whose top is within ~120px below label bottom
        // and that overlap horizontally with the label block.
        return blocks
            .filter { b ->
                val top    = b.boundingBox?.top    ?: return@filter false
                val bLeft  = b.boundingBox?.left   ?: 0
                val bRight = b.boundingBox?.right  ?: Int.MAX_VALUE
                top > labelBottom &&
                    top < labelBottom + 120 &&
                    bLeft < labelRight &&
                    bRight > labelLeft &&
                    b.text.trim() != labelBlock.text.trim()
            }
            .minByOrNull { b -> b.boundingBox?.top ?: Int.MAX_VALUE }
            ?.text?.trim()
            ?.takeIf { it.isNotBlank() && it.length > 1 }
    }

    /**
     * Tries to extract a date from the block that sits spatially below a label block.
     * Returns null if no spatial match is found (caller falls back to labeled regex).
     */
    private fun extractLabeledDateSpatial(
        blocks: List<TextBlock>,
        label: String,
    ): String? {
        val raw = extractLabelBelow(blocks, label) ?: return null
        return ExtractionUtils.DATE_FALLBACK_FULL.find(raw)
            ?.value?.let { ExtractionUtils.normalizeDate(it) }
    }

    /**
     * Collects up to [maxLines] text blocks that sit below [label] within ~200 px,
     * joined as a single string. Useful for multi-line address fields.
     */
    private fun extractMultilineBelow(
        blocks: List<TextBlock>,
        label: String,
        maxLines: Int = 4,
    ): String? {
        val labelLower = label.lowercase()
        val labelBlock = blocks.firstOrNull { b ->
            b.text.trim().lowercase().let { it == labelLower || it.startsWith(labelLower) || it.contains(labelLower) }
        } ?: return null

        val labelBottom = labelBlock.boundingBox?.bottom ?: return null
        val labelLeft   = labelBlock.boundingBox?.left   ?: 0
        val labelRight  = labelBlock.boundingBox?.right  ?: Int.MAX_VALUE

        val below = blocks
            .filter { b ->
                val top    = b.boundingBox?.top    ?: return@filter false
                val bLeft  = b.boundingBox?.left   ?: 0
                val bRight = b.boundingBox?.right  ?: Int.MAX_VALUE
                top > labelBottom &&
                    top < labelBottom + 250 &&
                    bLeft < labelRight + 60 &&
                    bRight > labelLeft - 60 &&
                    b.text.trim() != labelBlock.text.trim()
            }
            .sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }
            .take(maxLines)

        return below.joinToString(", ") { it.text.trim() }
            .takeIf { it.isNotBlank() }
    }

    /** Strip leading label noise like "Name:", "Mother:", "Address:" from OCR-extracted value. */
    private fun normalizePersonName(raw: String): String? {
        val stripped = raw
            .replace(Regex("""^(?:Name|Father|Mother|Guardian|Address)\s*[:\-]\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
        return stripped.takeIf { it.length > 1 }
    }

    private fun extractPassportType(text: String): String? {
        // Look for "Type" label followed by a single capital letter
        val typeLabel = Regex(
            """(?:^|\n)\s*Type\s*[:\-]?\s*([A-Z])(?:\s|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )
        return typeLabel.find(text)?.groupValues?.getOrNull(1)
    }

    private fun titleCase(s: String): String =
        s.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
}
