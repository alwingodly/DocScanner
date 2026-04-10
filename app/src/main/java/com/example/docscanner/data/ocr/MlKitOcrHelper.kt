package com.example.docscanner.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.docscanner.data.security.AadhaarSecureHelper
import com.example.docscanner.domain.model.DocClassType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class ExtractedFields(
    val name: String? = null,
    val idNumber: String? = null,   // always full 12-digit for Aadhaar, no spaces
    val rawText: String = "",
    val dob: String? = null,
    val gender: String? = null,
    val address: String? = null
)

@Singleton
class MlKitOcrHelper @Inject constructor(@ApplicationContext context: Context) {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    companion object {
        private const val TAG = "MlKitOcr"

        // ── Aadhaar Patterns (Same as your Masker) ───────────────────────────
        // Matches 12 digits with spaces: "1234 5678 9012"
        private val AADHAAR_12 = Regex("""(\d{4})\s+(\d{4})\s+(\d{4})""")

        // Matches 16 digits: used to DETECT and SKIP VID lines
        private val VID_LINE   = Regex("""(\d{4})\s+(\d{4})\s+(\d{4})\s+(\d{4})""")

        // ── Other ID Patterns ───────────────────────────────────────────────
        private val PAN_NUMBER      = Regex("""[A-Z]{5}\d{4}[A-Z]""")
        private val PASSPORT_NUMBER = Regex("""[A-Z]\d{7}""")
        private val VOTER_ID_NUMBER = Regex("""[A-Z]{3}\d{7}""")

        // ── Name & Label Patterns ───────────────────────────────────────────
        private val NAME_LABEL    = Regex("""(?:Name|नाम)\s*[:\-]?\s*(.+)""", RegexOption.IGNORE_CASE)
        private val DOB_LABEL     = Regex(
            """(?:DOB|Date\s*of\s*Birth|Year\s*of\s*Birth|जन्म\s*(?:तिथि|वर्ष))\s*[:\-]?\s*([0-9]{2}[/-][0-9]{2}[/-][0-9]{4}|[0-9]{4})""",
            RegexOption.IGNORE_CASE
        )
        private val DOB_FALLBACK  = Regex("""\b\d{2}[/-]\d{2}[/-]\d{4}\b""")
        private val GENDER_LABEL  = Regex("""\b(MALE|FEMALE|TRANSGENDER)\b""", RegexOption.IGNORE_CASE)
        private val ADDRESS_LABEL = Regex("""(?:Address|पता)\s*[:\-]?\s*(.*)""", RegexOption.IGNORE_CASE)
        private val PIN_CODE      = Regex("""\b\d{6}\b""")
        private val NUMBER_HEAVY  = Regex("""^[\d\s/\-.:,]+$""")
        private val SPECIAL_CHARS = Regex("""[^A-Za-z\s]""")

        private val SKIP_WORDS = setOf(
            "government", "govt", "india", "republic", "department", "bharat", "sarkar",
            "income", "tax", "permanent", "account", "number", "card",
            "unique", "identification", "authority", "uidai", "aadhaar",
            "election", "commission", "voter", "photo", "passport", "driving", "licence",
            "father", "husband", "wife", "mother", "dob", "date", "birth", "address",
            "pin", "state", "district", "village", "town", "city", "enrolment", "vid"
        )
    }

    suspend fun extractText(bitmap: Bitmap): Result<String> =
        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { cont.resume(Result.success(it.text.trim())) }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
        }

    suspend fun extractStructuredText(bitmap: Bitmap): Result<List<TextBlock>> =
        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { vt ->
                    cont.resume(Result.success(vt.textBlocks.map { b ->
                        TextBlock(
                            b.text,
                            b.lines.map { l -> TextLine(l.text, l.boundingBox) },
                            b.boundingBox
                        )
                    }))
                }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
        }

    suspend fun extractFields(bitmap: Bitmap, docType: DocClassType): ExtractedFields {
        val structuredResult = try {
            extractStructuredText(bitmap).getOrNull() ?: return ExtractedFields()
        } catch (_: Exception) {
            return ExtractedFields()
        }

        val fullRawText = structuredResult.joinToString("\n") { it.text }

        return when (docType) {
            DocClassType.AADHAAR_FRONT,
            DocClassType.AADHAAR_BACK -> {
                val number = findAadhaarInStructuredBlocks(structuredResult)
                val name   = extractAadhaarName(fullRawText)
                val dob    = extractDob(fullRawText)
                val gender = extractGender(fullRawText)
                val address = if (docType == DocClassType.AADHAAR_BACK) {
                    extractAddress(fullRawText)
                } else {
                    null
                }
                Log.d(TAG, "Aadhaar extracted → name=$name | number=$number")
                ExtractedFields(
                    name = name,
                    idNumber = number,
                    rawText = fullRawText,
                    dob = dob,
                    gender = gender,
                    address = address
                )
            }

            DocClassType.PAN      -> extractPanFields(fullRawText)

            DocClassType.PASSPORT -> ExtractedFields(
                name     = extractName(fullRawText),
                idNumber = PASSPORT_NUMBER.find(fullRawText)?.value,
                rawText  = fullRawText
            )

            DocClassType.VOTER_ID -> ExtractedFields(
                name     = extractName(fullRawText),
                idNumber = VOTER_ID_NUMBER.find(fullRawText)?.value,
                rawText  = fullRawText
            )

            DocClassType.OTHER -> ExtractedFields(
                name    = extractName(fullRawText),
                rawText = fullRawText
            )
        }
    }

    /**
     * Replicates Masker logic: Skips any line containing a 16-digit VID
     * before looking for the 12-digit Aadhaar number.
     */
    private fun findAadhaarInStructuredBlocks(blocks: List<TextBlock>): String? {
        val candidates = mutableListOf<String>()

        for (block in blocks) {
            for (line in block.lines) {
                val text = line.text.trim()

                // 1. Skip VID lines entirely (16 digits)
                if (VID_LINE.containsMatchIn(text)) {
                    Log.d(TAG, "Skipping VID line: $text")
                    continue
                }

                // 2. Search for 12-digit pattern
                val match = AADHAAR_12.find(text)
                if (match != null) {
                    val digitsOnly = match.value.replace("\\s".toRegex(), "")
                    if (isValidAadhaar(digitsOnly)) {
                        candidates.add(digitsOnly)
                    }
                }
            }
        }
        return candidates.lastOrNull() // Usually the main ID is the last one found
    }

    private fun isValidAadhaar(num: String): Boolean {
        return num.length == 12 &&
                num[0] !in listOf('0', '1') &&
                num.toSet().size > 1
    }

    // ── Group Key Builder ─────────────────────────────────────────────────────

    // Inject secureHelper into MlKitOcrHelper
    @Inject lateinit var secureHelper: AadhaarSecureHelper

    fun buildAadhaarGroupId(name: String?, aadhaarNumber: String?): String? {
        val normalizedName = name
            ?.trim()?.lowercase()
            ?.replace("\\s+".toRegex(), "_")
            ?.replace("[^a-z_]".toRegex(), "")
            ?.takeIf { it.length >= 3 }

        val digits  = aadhaarNumber?.filter { it.isDigit() }
        val full12  = digits?.takeIf { it.length == 12 }
        val last4   = digits?.takeLast(4)?.takeIf { it.length == 4 }

        // Hash instead of embedding raw digits
        val numHash   = full12?.let  { secureHelper.hashAadhaarNumber(it) }
        val last4Hash = last4?.let   { secureHelper.hashLast4(digits!!)  }

        return when {
            normalizedName != null && numHash  != null -> "ag_${normalizedName}_$numHash"
            numHash        != null                     -> "ag_n_$numHash"
            normalizedName != null && last4Hash != null -> "ag_${normalizedName}_l4_$last4Hash"
            last4Hash      != null                     -> "ag_l4_$last4Hash"
            normalizedName != null                     -> "ag_name_$normalizedName"
            else                                       -> null
        }
    }

    // ── Name & PAN Extraction ─────────────────────────────────────────────────

    private fun extractPanFields(text: String): ExtractedFields {
        val pan = PAN_NUMBER.find(text)?.value
        val name = extractName(text)
        return ExtractedFields(name, pan, text)
    }

    private fun extractAadhaarName(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        for (line in lines) {
            val match = NAME_LABEL.find(line) ?: continue
            val candidate = sanitizeName(match.groupValues.getOrElse(1) { "" })
            val validated = validateAadhaarName(candidate)
            if (validated != null) return formatName(validated)
        }

        val anchorIndex = lines.indexOfFirst { line ->
            DOB_LABEL.containsMatchIn(line) ||
                DOB_FALLBACK.containsMatchIn(line) ||
                GENDER_LABEL.containsMatchIn(line) ||
                AADHAAR_12.containsMatchIn(line)
        }

        if (anchorIndex >= 0) {
            val windowStart = maxOf(0, anchorIndex - 3)
            val windowCandidates = lines.subList(windowStart, anchorIndex)
                .asReversed()
                .mapNotNull { validateAadhaarName(sanitizeName(it)) }
            if (windowCandidates.isNotEmpty()) {
                return formatName(windowCandidates.first())
            }
        }

        return lines
            .mapNotNull { validateAadhaarName(sanitizeName(it)) }
            .firstOrNull()
            ?.let(::formatName)
    }

    private fun extractDob(text: String): String? {
        DOB_LABEL.find(text)?.groupValues?.getOrNull(1)?.trim()?.let { return it }
        return DOB_FALLBACK.find(text)?.value
    }

    private fun extractGender(text: String): String? =
        GENDER_LABEL.find(text)?.value?.uppercase()

    private fun extractAddress(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val collected = mutableListOf<String>()
        var collecting = false

        for (line in lines) {
            if (!collecting) {
                val match = ADDRESS_LABEL.find(line) ?: continue
                collecting = true
                val firstLine = match.groupValues.getOrElse(1) { "" }.trim()
                if (firstLine.isNotBlank()) collected += firstLine
                if (PIN_CODE.containsMatchIn(firstLine)) break
                continue
            }

            val lower = line.lowercase()
            if (
                lower.contains("uidai") ||
                lower.contains("www.") ||
                lower.contains("help@") ||
                lower.contains("1947") ||
                lower.contains("enrolment") ||
                lower.contains("enrollment") ||
                lower.contains("vid") ||
                lower.contains("download")
            ) {
                break
            }

            collected += line
            if (PIN_CODE.containsMatchIn(line) || collected.size >= 6) break
        }

        return collected
            .joinToString(", ")
            .replace("\\s+".toRegex(), " ")
            .removePrefix("Address :")
            .removePrefix("Address:")
            .removePrefix("address :")
            .removePrefix("address:")
            .trim()
            .ifBlank { null }
    }

    private fun extractName(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Pass 1: Label detection
        for (line in lines) {
            val m = NAME_LABEL.find(line) ?: continue
            val raw = sanitizeName(m.groupValues[1])
            if (raw.length >= 3 && !isHeaderLine(raw)) return formatName(raw)
        }

        // Pass 2: Heuristic detection
        for (line in lines) {
            if (line.length !in 3..40 || isHeaderLine(line) ||
                NUMBER_HEAVY.matches(line) || line.any { it.isDigit() }) continue

            val cleaned = sanitizeName(line)
            val words = cleaned.split("\\s+".toRegex())
            if (words.size in 1..4 && words.all { it.length >= 2 }) return formatName(cleaned)
        }
        return null
    }

    private fun sanitizeName(raw: String): String =
        raw
            .replace(SPECIAL_CHARS, " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun validateAadhaarName(candidate: String): String? {
        if (candidate.length !in 3..40) return null
        if (NUMBER_HEAVY.matches(candidate) || candidate.any { it.isDigit() }) return null
        if (isHeaderLine(candidate)) return null

        val normalized = candidate.lowercase()
        val disallowedPhrases = listOf(
            "government of india",
            "unique identification authority",
            "government",
            "bharat sarkar",
            "aadhaar",
            "enrolment"
        )
        if (disallowedPhrases.any { normalized.contains(it) }) return null

        val words = candidate.split("\\s+".toRegex())
        if (words.size !in 1..4) return null
        if (words.any { it.length < 2 }) return null

        return candidate
    }

    private fun isHeaderLine(line: String): Boolean {
        val words     = line.lowercase().split("\\s+".toRegex())
        val skipCount = words.count { word -> SKIP_WORDS.any { skip -> word.contains(skip) } }
        return skipCount > 0 && (skipCount.toFloat() / words.size) >= 0.4f
    }

    private fun formatName(raw: String): String =
        raw.lowercase()
            .split("\\s+".toRegex())
            .joinToString("_") { it.replaceFirstChar { c -> c.uppercaseChar() } }

    fun close() = recognizer.close()
}

// ── Data Models ──────────────────────────────────────────────────────────────

data class TextBlock(
    val text        : String,
    val lines       : List<TextLine>,
    val boundingBox : android.graphics.Rect?
)

data class TextLine(
    val text        : String,
    val boundingBox : android.graphics.Rect?
)
