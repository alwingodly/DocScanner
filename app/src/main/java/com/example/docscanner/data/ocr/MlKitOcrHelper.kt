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

        // Full 12-digit Aadhaar: handles "2316 6535 8128" or "231665358128"
        // Allows optional spaces between groups of 4
        private val AADHAAR_12 = Regex("""\b(\d{4})\s?(\d{4})\s?(\d{4})\b""")

        // Fallback: any standalone 4-digit group
        private val FOUR_DIGIT = Regex("""\b\d{4}\b""")

        private val PAN_NUMBER      = Regex("""[A-Z]{5}\d{4}[A-Z]""")
        private val PASSPORT_NUMBER = Regex("""[A-Z]\d{7}""")
        private val VOTER_ID_NUMBER = Regex("""[A-Z]{3}\d{7}""")

        private val NAME_LABEL    = Regex(
            """(?:Name|नाम)\s*[:\-]?\s*(.+)""",
            RegexOption.IGNORE_CASE
        )
        private val NUMBER_HEAVY  = Regex("""^[\d\s/\-.:,]+$""")
        private val SPECIAL_CHARS = Regex("""[^A-Za-z\s]""")

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
        val raw = try {
            extractText(bitmap).getOrNull() ?: return ExtractedFields()
        } catch (_: Exception) {
            return ExtractedFields()
        }
        Log.d(TAG, "OCR [${docType.name}]:\n$raw")

        return when (docType) {
            DocClassType.AADHAAR_FRONT,
            DocClassType.AADHAAR_BACK -> {
                val number = extractAadhaarNumber(raw)
                val name   = extractName(raw)
                Log.d(TAG, "Aadhaar extracted → name=$name | number=$number")
                ExtractedFields(name = name, idNumber = number, rawText = raw)
            }

            DocClassType.PAN      -> extractPanFields(raw)

            DocClassType.PASSPORT -> ExtractedFields(
                name     = extractName(raw),
                idNumber = PASSPORT_NUMBER.find(raw)?.value,
                rawText  = raw
            )

            DocClassType.VOTER_ID -> ExtractedFields(
                name     = extractName(raw),
                idNumber = VOTER_ID_NUMBER.find(raw)?.value,
                rawText  = raw
            )

            DocClassType.OTHER -> ExtractedFields(
                name    = extractName(raw),
                rawText = raw
            )
        }
    }

    // ── Aadhaar number extraction ─────────────────────────────────────────────
    //
    // Goal: always return the full 12-digit number as a plain digit string.
    //
    // Strategy:
    //   1. Find ALL 12-digit sequences (groups of 4+4+4, optional spaces).
    //      Filter out obvious non-Aadhaar sequences:
    //        • starts with 0 or 1  (Aadhaar numbers never start with 0/1)
    //        • all same digit      (000000000000 etc.)
    //      Take the LAST valid match — on back cards address digits may
    //      appear before the actual number.
    //
    //   2. If no 12-digit match, collect all standalone 4-digit groups,
    //      filter same rules, and return the last one as a partial key.
    //      This is a fallback — it won't match across cards by itself,
    //      but the reconciliation loop in ScannerViewModel handles that.
    //
    private fun extractAadhaarNumber(raw: String): String? {
        // ── Pass 1: full 12-digit ─────────────────────────────────────────────
        val fullMatches = AADHAAR_12.findAll(raw)
            .map { mr ->
                // Concatenate the three captured groups (strips spaces)
                mr.groupValues[1] + mr.groupValues[2] + mr.groupValues[3]
            }
            .filter { num ->
                num.length == 12 &&
                        num[0] != '0' &&        // Aadhaar never starts with 0
                        num[0] != '1' &&        // Aadhaar never starts with 1
                        num.toSet().size > 1    // not all same digit
            }
            .toList()

        Log.d(TAG, "Aadhaar 12-digit candidates: $fullMatches")

        if (fullMatches.isNotEmpty()) {
            val best = fullMatches.last()
            Log.d(TAG, "Aadhaar selected (full): $best")
            return best
        }

        // ── Pass 2: partial 4-digit fallback ─────────────────────────────────
        // Used when the card is masked or OCR couldn't read all three groups
        val partials = FOUR_DIGIT.findAll(raw)
            .map { it.value }
            .filter { it.toSet().size > 1 }   // not 0000 / 1111 etc.
            .toList()

        Log.d(TAG, "Aadhaar 4-digit partials: $partials")

        // Return the last 4-digit group — on masked Aadhaar the unmasked
        // suffix (8128) appears at the end of the number sequence
        return partials.lastOrNull()?.also {
            Log.d(TAG, "Aadhaar selected (partial fallback): $it")
        }
    }

    // ── Group key builder ─────────────────────────────────────────────────────
    //
    // Priority:
    //   1. name + full 12-digit  →  perfect, zero collision risk
    //   2. full 12-digit alone   →  strong, no name OCR needed
    //   3. name + last-4         →  medium, name disambiguates
    //   4. last-4 alone          →  weak fallback
    //   5. name alone            →  very weak, avoid if possible
    //   6. null                  →  OCR found nothing; caller uses timestamp key
    //
    fun buildAadhaarGroupId(name: String?, aadhaarNumber: String?): String? {
        val normalizedName = name
            ?.trim()
            ?.lowercase()
            ?.replace("\\s+".toRegex(), "_")
            ?.replace("[^a-z_]".toRegex(), "")
            ?.takeIf { it.length >= 3 }

        val digits = aadhaarNumber?.filter { it.isDigit() }

        // Full 12-digit number available
        val full12 = digits?.takeIf { it.length == 12 }

        // Partial: last 4 of whatever digits we have
        val last4  = digits?.takeLast(4)?.takeIf { it.length == 4 }

        Log.d(TAG, "buildAadhaarGroupId → name=$normalizedName | full12=$full12 | last4=$last4")

        return when {
            normalizedName != null && full12 != null -> "aadhaar_${normalizedName}_$full12"
            full12         != null                   -> "aadhaar_num_$full12"
            normalizedName != null && last4 != null  -> "aadhaar_${normalizedName}_$last4"
            last4          != null                   -> "aadhaar_num_$last4"
            normalizedName != null                   -> "aadhaar_name_$normalizedName"
            else                                     -> null
        }
    }

    // ── PAN-specific extraction ───────────────────────────────────────────────

    private fun extractPanFields(text: String): ExtractedFields {
        val pan   = PAN_NUMBER.find(text)?.value
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        var name: String? = null

        if (pan != null) {
            val panIdx = lines.indexOfFirst { it.contains(pan) }
            if (panIdx >= 0) {
                for (i in (panIdx + 1) until lines.size) {
                    val line  = lines[i]
                    val lower = line.lowercase()
                    if (isHeaderLine(line)) continue
                    if (NUMBER_HEAVY.matches(line)) continue
                    if (line.any { it.isDigit() }) continue
                    if (lower.startsWith("father") || lower.startsWith("s/o") ||
                        lower.startsWith("d/o")    || lower.startsWith("w/o")) continue
                    val cleaned = line.replace(SPECIAL_CHARS, "").trim()
                    if (cleaned.length >= 3 &&
                        cleaned.split("\\s+".toRegex()).size in 1..5) {
                        name = formatName(cleaned); break
                    }
                }
            }
        }

        if (name == null) {
            for (line in lines) {
                val m   = NAME_LABEL.find(line) ?: continue
                val raw = m.groupValues[1].replace(SPECIAL_CHARS, "").trim()
                if (raw.length >= 3 && !isHeaderLine(raw)) {
                    name = formatName(raw); break
                }
            }
        }

        if (name == null) name = extractName(text)

        return ExtractedFields(name, pan, text)
    }

    // ── Generic name extraction ───────────────────────────────────────────────

    private fun extractName(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Pass 1: explicit "Name:" label
        for (line in lines) {
            val m   = NAME_LABEL.find(line) ?: continue
            val raw = m.groupValues[1].replace(SPECIAL_CHARS, "").trim()
            if (raw.length >= 3 &&
                raw.split("\\s+".toRegex()).size in 1..5 &&
                !isHeaderLine(raw))
                return formatName(raw)
        }

        // Pass 2: heuristic — first line that looks like a person name
        for (line in lines) {
            if (line.length < 3 || line.length > 40) continue
            if (isHeaderLine(line)) continue
            if (NUMBER_HEAVY.matches(line)) continue
            if (line.any { it.isDigit() }) continue
            val cleaned = line.replace(SPECIAL_CHARS, "").trim()
            val words   = cleaned.split("\\s+".toRegex())
            if (words.size in 1..4 &&
                words.all { it.length >= 2 } &&
                cleaned.length >= 3)
                return formatName(cleaned)
        }
        return null
    }

    private fun isHeaderLine(line: String): Boolean {
        val words     = line.lowercase().split("\\s+".toRegex())
        val skipCount = words.count { word ->
            SKIP_WORDS.any { skip -> word.contains(skip) }
        }
        return skipCount > 0 && (skipCount.toFloat() / words.size) >= 0.4f
    }

    private fun formatName(raw: String): String =
        raw.lowercase()
            .split("\\s+".toRegex())
            .joinToString("_") { it.replaceFirstChar { c -> c.uppercaseChar() } }

    fun close() {
        recognizer.close()
    }
}

data class TextBlock(
    val text        : String,
    val lines       : List<TextLine>,
    val boundingBox : android.graphics.Rect?
)

data class TextLine(
    val text        : String,
    val boundingBox : android.graphics.Rect?
)