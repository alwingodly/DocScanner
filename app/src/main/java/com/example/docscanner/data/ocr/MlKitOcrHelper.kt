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
    val rawText: String = ""
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
                val name   = extractName(fullRawText)
                Log.d(TAG, "Aadhaar extracted → name=$name | number=$number")
                ExtractedFields(name = name, idNumber = number, rawText = fullRawText)
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

    private fun extractName(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Pass 1: Label detection
        for (line in lines) {
            val m = NAME_LABEL.find(line) ?: continue
            val raw = m.groupValues[1].replace(SPECIAL_CHARS, "").trim()
            if (raw.length >= 3 && !isHeaderLine(raw)) return formatName(raw)
        }

        // Pass 2: Heuristic detection
        for (line in lines) {
            if (line.length !in 3..40 || isHeaderLine(line) ||
                NUMBER_HEAVY.matches(line) || line.any { it.isDigit() }) continue

            val cleaned = line.replace(SPECIAL_CHARS, "").trim()
            val words = cleaned.split("\\s+".toRegex())
            if (words.size in 1..4 && words.all { it.length >= 2 }) return formatName(cleaned)
        }
        return null
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