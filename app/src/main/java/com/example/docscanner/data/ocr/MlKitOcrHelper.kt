package com.example.docscanner.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
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
    val idNumber: String? = null,
    val rawText: String = ""
)

@Singleton
class MlKitOcrHelper @Inject constructor(@ApplicationContext context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    companion object {
        private const val TAG = "MlKitOcr"

        private val AADHAAR_NUMBER = Regex("""(\d{4}\s?\d{4}\s?\d{4})""")
        private val PAN_NUMBER = Regex("""[A-Z]{5}\d{4}[A-Z]""")
        private val PASSPORT_NUMBER = Regex("""[A-Z]\d{7}""")
        private val VOTER_ID_NUMBER = Regex("""[A-Z]{3}\d{7}""")
        private val DL_NUMBER = Regex("""[A-Z]{2}\d{2}\s?\d{4,13}""")

        private val NAME_LABEL = Regex("""(?:Name|नाम)\s*[:\-]?\s*(.+)""", RegexOption.IGNORE_CASE)
        private val NUMBER_HEAVY = Regex("""^[\d\s/\-.:,]+$""")
        private val SPECIAL_CHARS = Regex("""[^A-Za-z\s]""")

        // Comprehensive skip list for all Indian document headers and labels
        private val SKIP_WORDS = setOf(
            "government", "govt", "india", "republic", "department", "bharat", "sarkar",
            "income", "tax", "permanent", "account", "number", "card",
            "unique", "identification", "authority", "uidai", "aadhaar",
            "election", "commission", "electoral", "voter", "electors", "photo", "epic",
            "passport", "travel", "document", "nationality",
            "driving", "licence", "license", "transport", "motor", "vehicle", "rto",
            "father", "husband", "wife", "mother", "son", "daughter",
            "dob", "date", "birth", "year", "age", "sex", "gender",
            "male", "female", "transgender",
            "address", "signature", "valid", "expiry", "issue", "issued",
            "pin", "state", "district", "village", "town", "city",
            "download", "verify", "www", "http", "gov", "nic",
            "enrolment", "enrollment", "vid", "help", "helpline",
            "your", "this", "the", "for", "and", "from", "has", "been",
            "order", "central", "union", "ministry", "finance", "revenue"
        )
    }

    suspend fun extractText(bitmap: Bitmap): Result<String> = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(Result.success(it.text.trim())) }
            .addOnFailureListener { cont.resume(Result.failure(it)) }
    }

    suspend fun extractStructuredText(bitmap: Bitmap): Result<List<TextBlock>> =
        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener { vt ->
                cont.resume(Result.success(vt.textBlocks.map { b ->
                    TextBlock(
                        b.text,
                        b.lines.map { l -> TextLine(l.text, l.boundingBox) },
                        b.boundingBox
                    )
                }))
            }.addOnFailureListener { cont.resume(Result.failure(it)) }
        }

    suspend fun extractFields(bitmap: Bitmap, docType: DocClassType): ExtractedFields {
        val raw = try {
            extractText(bitmap).getOrNull() ?: return ExtractedFields()
        } catch (_: Exception) {
            return ExtractedFields()
        }
        Log.d(TAG, "OCR [${docType.name}]:\n$raw")
        return when (docType) {
            DocClassType.AADHAAR -> ExtractedFields(
                extractName(raw),
                AADHAAR_NUMBER.find(raw)?.value?.replace("\\s".toRegex(), ""),
                raw
            )

            DocClassType.PAN -> extractPanFields(raw)
            DocClassType.PASSPORT -> ExtractedFields(
                extractName(raw),
                PASSPORT_NUMBER.find(raw)?.value,
                raw
            )

            DocClassType.VOTER_ID -> ExtractedFields(
                extractName(raw),
                VOTER_ID_NUMBER.find(raw)?.value,
                raw
            )

            DocClassType.DRIVING_LICENSE -> ExtractedFields(
                extractName(raw),
                DL_NUMBER.find(raw)?.value,
                raw
            )

            DocClassType.OTHER -> ExtractedFields(extractName(raw), null, raw)
        }
    }

    /**
     * PAN-specific: name is typically the line AFTER the PAN number.
     * PAN layout:
     *   INCOME TAX DEPARTMENT / भारत सरकार
     *   GOVT OF INDIA
     *   Permanent Account Number
     *   ABCDE1234F          <-- PAN number
     *   ALWIN JOSE           <-- name (this is what we want)
     *   JOSE THOMAS           <-- father's name
     *   01/01/1990            <-- DOB
     */
    private fun extractPanFields(text: String): ExtractedFields {
        val pan = PAN_NUMBER.find(text)?.value
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        var name: String? = null

        // Strategy 1: find line after PAN number
        if (pan != null) {
            val panIdx = lines.indexOfFirst { it.contains(pan) }
            if (panIdx >= 0) {
                for (i in (panIdx + 1) until lines.size) {
                    val line = lines[i]
                    if (isHeaderLine(line)) continue
                    if (NUMBER_HEAVY.matches(line)) continue
                    if (line.any { it.isDigit() }) continue
                    // Check for "father" type labels — skip those too
                    val lower = line.lowercase()
                    if (lower.startsWith("father") || lower.startsWith("s/o") || lower.startsWith("d/o") || lower.startsWith(
                            "w/o"
                        )
                    ) continue
                    val cleaned = line.replace(SPECIAL_CHARS, "").trim()
                    if (cleaned.length >= 3 && cleaned.split("\\s+".toRegex()).size in 1..5) {
                        name = formatName(cleaned)
                        break
                    }
                }
            }
        }

        // Strategy 2: look for explicit "Name" label
        if (name == null) {
            for (line in lines) {
                val m = NAME_LABEL.find(line) ?: continue
                val raw = m.groupValues[1].replace(SPECIAL_CHARS, "").trim()
                if (raw.length >= 3 && !isHeaderLine(raw)) {
                    name = formatName(raw); break
                }
            }
        }

        // Strategy 3: fallback generic
        if (name == null) name = extractName(text)

        return ExtractedFields(name, pan, text)
    }

    private fun extractName(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Look for "Name:" label first
        for (line in lines) {
            val m = NAME_LABEL.find(line) ?: continue
            val raw = m.groupValues[1].replace(SPECIAL_CHARS, "").trim()
            if (raw.length >= 3 && raw.split("\\s+".toRegex()).size in 1..5 && !isHeaderLine(raw)) return formatName(
                raw
            )
        }

        // Heuristic: first line that looks like a person name
        for (line in lines) {
            if (line.length < 3 || line.length > 40) continue
            if (isHeaderLine(line)) continue
            if (NUMBER_HEAVY.matches(line)) continue
            if (line.any { it.isDigit() }) continue
            val cleaned = line.replace(SPECIAL_CHARS, "").trim()
            val words = cleaned.split("\\s+".toRegex())
            if (words.size in 1..4 && words.all { it.length >= 2 } && cleaned.length >= 3) return formatName(
                cleaned
            )
        }
        return null
    }

    /** Check if a line is a document header/label that should be skipped */
    private fun isHeaderLine(line: String): Boolean {
        val words = line.lowercase().split("\\s+".toRegex())
        // If more than half the words are skip words, it's a header
        val skipCount = words.count { word -> SKIP_WORDS.any { skip -> word.contains(skip) } }
        return skipCount > 0 && (skipCount.toFloat() / words.size) >= 0.4f
    }

    private fun formatName(raw: String): String = raw.lowercase().split("\\s+".toRegex())
        .joinToString("_") { it.replaceFirstChar { c -> c.uppercaseChar() } }

    fun close() {
        recognizer.close()
    }
}

data class TextBlock(
    val text: String,
    val lines: List<TextLine>,
    val boundingBox: android.graphics.Rect?
)

data class TextLine(val text: String, val boundingBox: android.graphics.Rect?)