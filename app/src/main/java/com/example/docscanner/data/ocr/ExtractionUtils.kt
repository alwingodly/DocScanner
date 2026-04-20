// data/ocr/ExtractionUtils.kt
package com.example.docscanner.data.ocr

import com.example.docscanner.domain.model.DocumentDetail
import kotlin.math.abs
import kotlin.math.min

/**
 * Shared regexes, scoring, and helpers used across all DocumentExtractors.
 * All pure functions — safe to call from any thread.
 */
object ExtractionUtils {

    // ── Confidence tiers ──────────────────────────────────────────────────────
    const val C_LABELED = 0.95f
    const val C_ANCHOR = 0.85f
    const val C_SPATIAL = 0.78f
    const val C_HEURISTIC = 0.60f

    // ── Aadhaar ───────────────────────────────────────────────────────────────
    val AADHAAR_12 = Regex("""(?<!\d)(\d{4})\s+(\d{4})\s+(\d{4})(?!\s+\d{4})(?!\d)""")
    val VID_LINE = Regex("""(?<!\d)\d{4}\s+\d{4}\s+\d{4}\s+\d{4}(?!\d)""")

    // ── PAN ───────────────────────────────────────────────────────────────────
    val PAN_STRICT = Regex("""(?<![A-Z0-9])([A-Z]{5}\d{4}[A-Z])(?![A-Z0-9])""")
    val PAN_NOISY = Regex("""(?<![A-Z0-9])([A-Z0O]{5}[0-9O]{4}[A-Z0O])(?![A-Z0-9])""")

    val PAN_HEADER_MARKERS = setOf(
        "income tax", "permanent account", "govt of india", "government of india",
        "भारत सरकार", "आयकर विभाग", "tax department"
    )

    val PAN_SIGNATURE_JUNK = Regex(
        """^(?:Signature|हस्ताक्षर|sign|[A-Z]\.?\s*){1,4}$|^\s*$""",
        RegexOption.IGNORE_CASE
    )

    val PAN_LABEL_LINES = Regex(
        """^(?:Name|Father['s]*\s*Name|Guardian['s]*\s*Name|Date\s*of\s*Birth|Signature|हस्ताक्षर|नाम|जन्म)\s*[:\-]?\s*$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    // ── Passport ──────────────────────────────────────────────────────────────
    /**
     * 1 uppercase letter + exactly 7 digits = Indian passport number format.
     * No trailing (?!\d) — MRZ stores the number as K12345671IND… (check digit follows),
     * so the negative lookahead would block detection on MRZ-only OCR output.
     */
    val PASSPORT_NO = Regex("""(?<![A-Z0-9])([A-Z]\d{7})""")

    // Back-page passport labels
    /** "Name of Father / Legal Guardian", "Legal Guardian", "Father/Legal Guardian" */
    val PASSPORT_FATHER = Regex(
        """(?:Name\s+of\s+Father|Father[\s/]+(?:Legal\s+)?Guardian|Legal\s+Guardian|Name\s+of\s+Legal\s+Guardian)\s*[:\-]?\s*\n?\s*([\p{L}][\p{L}\s'\.]{1,60})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    /** "Name of Mother", "Mother" label */
    val PASSPORT_MOTHER = Regex(
        """(?:Name\s+of\s+Mother|Mother['s]*\s*Name)\s*[:\-]?\s*\n?\s*([\p{L}][\p{L}\s'\.]{1,60})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    /** "Name of Spouse", "Spouse" label */
    val PASSPORT_SPOUSE = Regex(
        """(?:Name\s+of\s+Spouse|Spouse['s]*\s*Name)\s*[:\-]?\s*\n?\s*([\p{L}][\p{L}\s'\.]{1,60})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    /** "File No.", "File Number" */
    val PASSPORT_FILE_NO = Regex(
        """(?:File\s*No\.?|File\s+Number)\s*[:\-]?\s*([A-Z0-9/\-]{4,25})""",
        RegexOption.IGNORE_CASE
    )
    /** Passport address block: everything after "Address" label until blank line or 5 lines */
    val PASSPORT_ADDRESS = Regex(
        """Address\s*[:\-]?\s*\n?\s*([\p{L}0-9][^\n]{2,80}(?:\n[\p{L}0-9][^\n]{2,80}){0,4})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    val MRZ_LINE = Regex("""[A-Z0-9<]{44}""")

    /** Matches "Surname\n<value>" or "Surname: <value>" */
    val SURNAME_LABEL = Regex(
        """(?:^|\n)\s*Surname\s*(?:\n\s*|\s*[:\-]\s*)([\p{L}][\p{L}\s'\.]{1,50})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    /** Matches "Given Name(s)\n<value>" or "Given Names?: <value>" */
    val GIVEN_NAME_LABEL = Regex(
        """(?:^|\n)\s*Given\s+Names?\s*\(?s?\)?\s*(?:\n\s*|\s*[:\-]\s*)([\p{L}][\p{L}\s'\.]{1,50})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    /** Place of Issue label (inline or label-on-top) */
    val PLACE_OF_ISSUE = Regex(
        """(?:Place\s*of\s*Issue|Issuing\s*(?:Authority|Office|Place))\s*(?:\n\s*|[:\-]\s*)([\p{L}][\p{L}\s,\.]{2,40})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    /** Place of Birth label */
    val PLACE_OF_BIRTH = Regex(
        """(?:Place\s*of\s*Birth|Birth\s*Place|POB|जन्म\s*स्थान)\s*(?:\n\s*|[:\-]\s*)([\p{L}][\p{L}\s,\.]{2,40})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    /** Country Code inline label */
    val COUNTRY_CODE_LABEL = Regex(
        """(?:Country\s*Code|Issuing\s*Country)\s*[:\-]?\s*([A-Z]{3})""",
        RegexOption.IGNORE_CASE
    )

    // ── Voter ID ──────────────────────────────────────────────────────────────
    val EPIC_NO = Regex("""(?<![A-Z])[A-Z]{3}\d{7}(?!\d)""")
    val CONSTITUENCY = Regex(
        """(?:Constituency|Vidhan\s*Sabha|विधानसभा|Part\s*No)\s*[:\-]?\s*(.+)""",
        RegexOption.IGNORE_CASE
    )
    val VOTER_DOB_YEAR_CONTEXT = Regex(
        """(?:DOB|D\.O\.B|Date\s*of\s*Birth|Year\s*of\s*Birth|जन्म|वर्ष|Age\s*as\s*on)\s*[:\-/]?\s*(19\d{2}|20[0-2]\d)""",
        RegexOption.IGNORE_CASE
    )
    val STANDALONE_YEAR_LINE = Regex("""^\s*(19[2-9]\d|20[0-2]\d)\s*$""")

    // ── DL ────────────────────────────────────────────────────────────────────
    val DL_NUMBER = Regex(
        """[A-Z]{2}[\-\s]?\d{2}[\-\s]?\d{4}[\-\s]?\d{7}|[A-Z]{2}\d{2}\s?\d{11}"""
    )
    val VEHICLE_CAT = Regex(
        """\b(LMV|MCWG|MCWOG|HMV|MGV|HGV|PSV|TRANS|INVALID\s*CARRIAGE)\b""",
        RegexOption.IGNORE_CASE
    )
    val DL_VALIDITY = Regex(
        """(?:Valid\s*(?:Till|To|Upto|Up\s*to)|Non[\-\s]?Transport|NT)\s*[:\-]?\s*(\d{2}[/-]\d{2}[/-]\d{4})""",
        RegexOption.IGNORE_CASE
    )

    // ── Name labels ───────────────────────────────────────────────────────────
    val NAME_LABEL = Regex(
        """(?:^|\n)\s*(?:Name|नाम|ನಾಮ|பெயர்)\s*[:\-]\s*([\p{L}][\p{L}\s'\.]{1,50})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    val FATHER_LABEL = Regex(
        """(?:Father['s]*\s*Name|Guardian['s]*\s*Name|S/O|Son\s*of|D/O|Daughter\s*of|C/O|पिता|அப்பா)\s*[:\-]?\s*([\p{L}][\p{L}\s'\.]{1,50})""",
        RegexOption.IGNORE_CASE
    )
    val SPOUSE_LABEL = Regex(
        """(?:Husband['s]*\s*Name|Wife['s]*\s*Name|W/O|H/O|पति|पत्नी)\s*[:\-]?\s*([\p{L}][\p{L}\s'\.]{1,50})""",
        RegexOption.IGNORE_CASE
    )

    val REL_PREFIX_FULL = Regex(
        """^\s*(?:S/O|D/O|W/O|H/O|C/O|Son\s+of|Daughter\s+of|Wife\s+of|Husband\s+of)\s*[:\-]?\s*""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    val REL_PREFIX_EXTENDED = Regex(
        """^\s*(?:S/O|D/O|W/O|H/O|C/O|Son\s+of|Daughter\s+of|Wife\s+of|Husband\s+of|Father\s*[:\-]|Mother\s*[:\-])\s*""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    val IS_REL_LINE = Regex(
        """^\s*(?:S/O|D/O|W/O|H/O|C/O|Son\s+of|Daughter\s+of|Wife\s+of|Husband\s+of|Father['s]*\s*(?:Name)?|Mother['s]*\s*(?:Name)?)\s*[:\-]""",
        RegexOption.IGNORE_CASE
    )

    // ── Dates ─────────────────────────────────────────────────────────────────
    private const val DATE_PAT =
        """(\d{2}[/-]\d{2}[/-]\d{4}|\d{4}|\d{2}[\s\-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\s\-/]\d{4})"""

    val DOB_LABEL = Regex(
        """(?:DOB|D\.O\.B\.?|Date\s*of\s*Birth|Year\s*of\s*Birth|जन्म\s*(?:तिथि|वर्ष))\s*[:\-/]?\s*$DATE_PAT""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    val DOI_LABEL = Regex(
        """(?:Date\s*of\s*Issue|Issue\s*Date|जारी\s*(?:तिथि|दिनांक))\s*[:\-]?\s*$DATE_PAT""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    val DOE_LABEL = Regex(
        """(?:Date\s*of\s*Expiry|Expiry\s*Date|Valid\s*(?:Till|Upto|Up\s*to)|Expire[sd]?)\s*[:\-]?\s*$DATE_PAT""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    val DATE_FALLBACK_FULL = Regex(
        """(\d{2}[/-]\d{2}[/-]\d{4}|\d{2}[\s\-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\s\-/]\d{4})""",
        RegexOption.IGNORE_CASE
    )
    val YEAR_BIRTH_ONLY = Regex("""\b(19[2-9]\d|20[0-2]\d)\b""")

    private val MONTH_MAP = mapOf(
        "jan" to "01", "feb" to "02", "mar" to "03", "apr" to "04",
        "may" to "05", "jun" to "06", "jul" to "07", "aug" to "08",
        "sep" to "09", "oct" to "10", "nov" to "11", "dec" to "12"
    )
    private val DATE_WORD_PAT = Regex(
        """(\d{2})[\s\-/](jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[\s\-/](\d{4})""",
        RegexOption.IGNORE_CASE
    )

    // ── Gender ────────────────────────────────────────────────────────────────
    val GENDER_FULL = Regex(
        """(?:^|[\s/|,])(MALE|FEMALE|TRANSGENDER|पुरुष|महिला|किन्नर|ಪುರುಷ|ಮಹಿಳೆ|ஆண்|பெண்)(?=$|[\s/|,\.])""",
        RegexOption.IGNORE_CASE
    )
    val GENDER_HINT = Regex(
        """(?:Gender|Sex|लिंग)""",
        RegexOption.IGNORE_CASE
    )
    val GENDER_SHORT_LINE = Regex("""^[MF]$""", RegexOption.IGNORE_CASE)
    val GENDER_LABEL = Regex(
        """(?:Gender|Sex|लिंग)\s*[:\-]\s*(Male|Female|Transgender|M|F|पुरुष|महिला)""",
        RegexOption.IGNORE_CASE
    )
    val GENDER_ANYWHERE = Regex(
        """(?<!\p{L})(Male|Female|Transgender|M|F|पुरुष|महिला|किन्नर|ಪುರುಷ|ಮಹಿಳೆ|ஆண்|பெண்)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    // ── Blood group (AB before A|B to avoid greedy short-match) ───────────────
    val BLOOD_GROUP = Regex(
        """(?:Blood\s*(?:Group|Type)\s*[:\-]?\s*)?(?<!\w)(AB|A|B|O)\s*([+\-]|[Pp]ositive|[Nn]egative|ve|VE)(?!\w)""",
        RegexOption.IGNORE_CASE
    )
    val BLOOD_CONTEXT_WINDOW = 40

    // ── Age / Nationality ─────────────────────────────────────────────────────
    val AGE_LABEL = Regex(
        """(?:Age|आयु|வயது)\s*[:\-]?\s*(\d{1,3})(?:\s*(?:Yrs?|Years?|वर्ष))?""",
        RegexOption.IGNORE_CASE
    )
    val NATIONALITY = Regex(
        """(?:Nationality|Natn|राष्ट्रीयता)\s*[:\-]?\s*([A-Z]{3,20})""",
        RegexOption.IGNORE_CASE
    )

    // ── Address ───────────────────────────────────────────────────────────────
    val ADDR_LABEL = Regex(
        """(?:^|\n)\s*(?:Address|Addr|पता|ವಿಳಾಸ|முகவரி)\s*[:\-]?\s*(.*)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    val PIN_CODE = Regex("""\b([1-9]\d{5})\b""")
    val ADDR_NOISE = setOf(
        "uidai", "www.", "help@", "1947", "enrolment", "enrollment",
        "download", "website", "visit", "contact", "toll free", "helpline",
        "income tax", "government of india", "election commission"
    )

    val INDIAN_STATES = setOf(
        "andhra pradesh", "arunachal pradesh", "assam", "bihar", "chhattisgarh",
        "goa", "gujarat", "haryana", "himachal pradesh", "jharkhand", "karnataka",
        "kerala", "madhya pradesh", "maharashtra", "manipur", "meghalaya", "mizoram",
        "nagaland", "odisha", "punjab", "rajasthan", "sikkim", "tamil nadu",
        "telangana", "tripura", "uttar pradesh", "uttarakhand", "west bengal",
        "andaman", "chandigarh", "dadra", "delhi", "jammu", "kashmir",
        "ladakh", "lakshadweep", "puducherry", "pondicherry"
    )

    // ── Generic KV ────────────────────────────────────────────────────────────
    val GENERIC_KV = Regex("""^\s*([\p{L}][\p{L} /()_\-']{1,40})\s*[:\-]\s*(.+)$""")
    val NUMBER_HEAVY = Regex("""^[\d\s/\-.:,()]+$""")
    val SPECIAL_CHARS = Regex("""[^\p{L}\s\-']""")

    val SKIP_WORDS = setOf(
        "government", "govt", "india", "republic", "department", "bharat", "sarkar",
        "income", "tax", "permanent", "account", "number", "card",
        "unique", "identification", "authority", "uidai", "aadhaar",
        "election", "commission", "voter", "photo", "passport", "driving", "licence",
        "dob", "date", "birth", "address", "nationality",
        "pin", "state", "district", "village", "town", "city", "enrolment", "vid",
        "ministry", "transport", "road", "highway"
    )
    val HEADER_PHRASES = listOf(
        "government of india", "unique identification authority", "bharat sarkar",
        "election commission", "income tax department", "ministry of road",
        "ministry of external affairs", "transport department"
    )

    val COMMON_INDIAN_SUFFIXES = setOf(
        "kumar", "singh", "sharma", "gupta", "verma", "yadav", "patel", "shah",
        "mehta", "joshi", "pandey", "mishra", "tiwari", "shukla", "dubey",
        "chauhan", "rao", "reddy", "nair", "pillai", "menon", "iyer", "iyengar",
        "das", "devi", "bai", "kaur", "begum", "bibi", "khan", "ansari",
        "hussain", "ali", "ahmed", "siddiqui", "chaudhary", "malik",
        "naidu", "choudhary", "sinha", "thakur"
    )

    // ══════════════════════════════════════════════════════════════════════════
    // String operations
    // ══════════════════════════════════════════════════════════════════════════

    fun sanitizeName(raw: String): String =
        raw.replace(SPECIAL_CHARS, " ").replace("\\s+".toRegex(), " ").trim()

    /**
     * Normalize OCR text from Indic scripts to improve regex extraction:
     * - Convert Indic digits to ASCII digits
     * - Normalize full-width/variant punctuation frequently seen in OCR
     */
    fun normalizeIndicText(raw: String): String {
        val sb = StringBuilder(raw.length)
        raw.forEach { ch ->
            when (ch) {
                '：', '﹕', '꞉', 'ː' -> sb.append(':')
                '－', '–', '—', '‒' -> sb.append('-')
                else -> sb.append(ch.toAsciiDigitIfIndic())
            }
        }
        return sb.toString()
    }

    private fun Char.toAsciiDigitIfIndic(): Char = when (this) {
        in '०'..'९' -> ('0'.code + (this.code - '०'.code)).toChar() // Devanagari
        in '০'..'৯' -> ('0'.code + (this.code - '০'.code)).toChar() // Bengali
        in '੦'..'੯' -> ('0'.code + (this.code - '੦'.code)).toChar() // Gurmukhi
        in '૦'..'૯' -> ('0'.code + (this.code - '૦'.code)).toChar() // Gujarati
        in '୦'..'୯' -> ('0'.code + (this.code - '୦'.code)).toChar() // Odia
        in '௦'..'௯' -> ('0'.code + (this.code - '௦'.code)).toChar() // Tamil
        in '౦'..'౯' -> ('0'.code + (this.code - '౦'.code)).toChar() // Telugu
        in '೦'..'೯' -> ('0'.code + (this.code - '೦'.code)).toChar() // Kannada
        in '൦'..'൯' -> ('0'.code + (this.code - '൦'.code)).toChar() // Malayalam
        else -> this
    }

    fun stripRelPrefix(raw: String): String =
        REL_PREFIX_EXTENDED.replace(raw, "").trim()

    fun formatNameDisplay(raw: String): String =
        raw.lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }

    fun String.normName(): String =
        lowercase().replace("\\s+".toRegex(), " ").trim()

    fun maskAadhaar(id: String): String =
        id.filter { it.isDigit() }.takeLast(4).takeIf { it.length == 4 }
            ?.let { "xxxx xxxx $it" } ?: id

    fun String.last4(): String = filter { it.isDigit() }.takeLast(4)

    // ══════════════════════════════════════════════════════════════════════════
    // Validation
    // ══════════════════════════════════════════════════════════════════════════

    fun nameOk(candidate: String): Boolean {
        if (candidate.length !in 2..50) return false
        if (NUMBER_HEAVY.matches(candidate)) return false
        if (candidate.any { it.isDigit() }) return false
        if (isHeaderLine(candidate)) return false
        if (IS_REL_LINE.containsMatchIn(candidate)) return false
        val words = candidate.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 5) return false
        if (words.any { it.length < 2 }) return false
        return true
    }

    fun isPanLabelLikeName(candidate: String): Boolean {
        val normalized = candidate.lowercase()
            .replace(Regex("""[^\p{L}\s]"""), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

        if (normalized.isBlank()) return false
        if (
            normalized == "name" ||
            normalized == "father name" ||
            normalized == "fathers name" ||
            normalized == "father s name" ||
            normalized == "guardian name" ||
            normalized == "guardians name" ||
            normalized == "guardian s name" ||
            normalized == "date of birth" ||
            normalized == "signature"
        ) {
            return true
        }

        return normalized.endsWith(" name") &&
            levenshtein(normalized, "huf name", maxDist = 2) <= 2
    }

    fun isHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        if (HEADER_PHRASES.any { lower.contains(it) }) return true
        val words = lower.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val skipCount = words.count { w -> SKIP_WORDS.any { s -> w.contains(s) } }
        return skipCount > 0 && skipCount.toFloat() / words.size >= 0.4f
    }

    fun isPanHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        if (PAN_HEADER_MARKERS.any { lower.contains(it) }) return true
        return isHeaderLine(line)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Name scoring (Indian surname heuristic, 0.0..1.0)
    // ══════════════════════════════════════════════════════════════════════════

    fun indianNameScore(name: String): Float {
        var score = 0.0f
        val words = name.lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (words.isEmpty()) return 0.0f
        if (words.any { w -> COMMON_INDIAN_SUFFIXES.any { s -> w == s || w.endsWith(s) } }) score += 0.30f
        if (words.size in 2..3) score += 0.20f
        if (name.split("\\s+".toRegex()).all { it.isNotEmpty() && it[0].isUpperCase() }) score += 0.15f
        if (words.all { it.length in 2..15 }) score += 0.15f
        if (name.length in 5..30) score += 0.10f
        if (words.size == words.distinct().size) score += 0.10f
        return score.coerceIn(0.0f, 1.0f)
    }

    fun levenshtein(a: String, b: String, maxDist: Int = 5): Int {
        if (a == b) return 0
        if (abs(a.length - b.length) > maxDist) return maxDist + 1
        val dp = Array(a.length + 1) { IntArray(b.length + 1) { it } }
        for (i in 1..a.length) dp[i][0] = i
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else 1 + min(dp[i - 1][j], min(dp[i][j - 1], dp[i - 1][j - 1]))
            }
        }
        return dp[a.length][b.length]
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OCR noise correction
    // ══════════════════════════════════════════════════════════════════════════

    fun correctNumericNoise(text: String): String = text
        .replace(Regex("""(?<=\d)[OoQ]|[OoQ](?=\d)"""), "0")
        .replace(Regex("""(?<=\d)[Il]|[Il](?=\d)"""), "1")
        .replace(Regex("""(?<=\d)[Ss]|[Ss](?=\d)"""), "5")
        .replace(Regex("""(?<=\d)Z|Z(?=\d)"""), "2")

    fun correctPanNoise(line: String): String =
        PAN_NOISY.replace(line) { mr ->
            val r = mr.groupValues[1].takeIf { it.length == 10 } ?: return@replace mr.value
            val l1 = r.take(5).map { if (it == '0') 'O' else it }.joinToString("")
            val d = r.substring(5, 9).map { c -> if (c == 'O' || c == 'o') '0' else c }.joinToString("")
            val l2 = r.last().let { if (it == '0') 'O' else it }
            "$l1$d$l2"
        }

    // ══════════════════════════════════════════════════════════════════════════
    // Dates
    // ══════════════════════════════════════════════════════════════════════════

    fun normalizeDate(raw: String): String {
        val t = raw.trim()
        DATE_WORD_PAT.find(t)?.let { m ->
            val (d, mon, y) = m.destructured
            return "$d/${MONTH_MAP[mon.lowercase()] ?: mon}/$y"
        }
        return t.replace('-', '/')
    }

    fun extractLabeledDate(text: String, pat: Regex): String? =
        pat.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            ?.let { normalizeDate(it) }

    // ══════════════════════════════════════════════════════════════════════════
    // Gender / Blood / State
    // ══════════════════════════════════════════════════════════════════════════

    fun extractGender(text: String): String? {
        GENDER_LABEL.find(text)?.groupValues?.getOrNull(1)?.let { g ->
            normalizeGenderToken(g)?.let { return it }
        }

        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        lines.forEachIndexed { index, line ->
            if (!GENDER_HINT.containsMatchIn(line)) return@forEachIndexed

            normalizeGenderToken(line)?.let { return it }
            for (offset in 1..3) {
                val candidate = lines.getOrNull(index + offset) ?: break
                normalizeGenderToken(candidate)?.let { return it }
            }
        }

        if (GENDER_HINT.containsMatchIn(text)) {
            GENDER_ANYWHERE.find(text)?.groupValues?.getOrNull(1)?.let { token ->
                normalizeGenderToken(token)?.let { return it }
            }
        }

        GENDER_FULL.find(" $text ")?.groupValues?.getOrNull(1)?.let { r ->
            normalizeGenderToken(r)?.let { return it }
        }
        lines.forEach { line ->
            val s = line.trim()
            if (s.length <= 6 && GENDER_SHORT_LINE.matches(s)) {
                return when (s.uppercase()) { "M" -> "Male"; "F" -> "Female"; else -> null }
            }
            normalizeGenderToken(s)?.let { return it }
        }
        return null
    }

    private fun normalizeGenderToken(raw: String): String? {
        val compact = raw.lowercase().replace(Regex("""[^\p{L}]"""), "")
        return when {
            compact.isEmpty() -> null
            compact == "m" || compact.contains("male") ||
                compact.contains("पुरुष") || compact.contains("ಪುರುಷ") || compact.contains("ஆண்") -> "Male"
            compact == "f" || compact.contains("female") ||
                compact.contains("महिला") || compact.contains("ಮಹಿಳೆ") || compact.contains("பெண்") -> "Female"
            compact.contains("transgender") || compact.contains("किन्नर") -> "Transgender"
            else -> null
        }
    }

    /**
     * Blood group: only accepted when either
     *   (a) the match includes a sign/keyword (handled by regex), AND
     *   (b) "blood" appears within BLOOD_CONTEXT_WINDOW chars of the match,
     *       unless the match itself starts with "Blood".
     */
    fun extractBloodGroup(text: String): String? {
        val lower = text.lowercase()
        val matches = BLOOD_GROUP.findAll(text).toList()
        for (m in matches) {
            val start = m.range.first
            val ctxStart = maxOf(0, start - BLOOD_CONTEXT_WINDOW)
            val ctxEnd = minOf(text.length, m.range.last + BLOOD_CONTEXT_WINDOW)
            val ctx = lower.substring(ctxStart, ctxEnd)
            if (!ctx.contains("blood")) continue

            val type = m.groupValues.getOrNull(1)?.uppercase() ?: continue
            val sign = m.groupValues.getOrNull(2)?.uppercase() ?: continue
            val normSign = when {
                sign == "+" || sign.contains("POSITIVE") || sign == "VE" -> "+"
                sign == "-" || sign.contains("NEGATIVE") -> "-"
                else -> ""
            }
            val result = "$type$normSign"
            if (result.length in 2..3) return result
        }
        return null
    }

    fun detectState(text: String): String? {
        val lower = text.lowercase()
        return INDIAN_STATES.firstOrNull { lower.contains(it) }
            ?.split(" ")?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    }

    fun extractPin(text: String): String? = PIN_CODE.find(text)?.groupValues?.getOrNull(1)

    // ══════════════════════════════════════════════════════════════════════════
    // Address
    // ══════════════════════════════════════════════════════════════════════════

    fun extractAddress(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val collected = mutableListOf<String>()
        var collecting = false

        for (line in lines) {
            if (!collecting) {
                val m = ADDR_LABEL.find(line) ?: continue
                collecting = true
                val first = m.groupValues.getOrElse(1) { "" }.trim()
                if (first.isNotBlank()) {
                    collected += first
                    if (PIN_CODE.containsMatchIn(first)) break
                }
                continue
            }
            val lower = line.lowercase()
            if (ADDR_NOISE.any { lower.contains(it) }) break
            if (GENERIC_KV.containsMatchIn(line) && collected.size >= 2) break
            collected += line
            if (PIN_CODE.containsMatchIn(line)) break
            if (collected.size >= 9) break
        }

        if (collected.isNotEmpty() && collected.none { PIN_CODE.containsMatchIn(it) }) {
            val lastIdx = lines.indexOf(collected.last())
            if (lastIdx in 0 until lines.size - 1) {
                val next = lines[lastIdx + 1]
                if (PIN_CODE.containsMatchIn(next) &&
                    ADDR_NOISE.none { next.lowercase().contains(it) }
                ) collected += next
            }
        }

        return collected.joinToString(", ")
            .replace("\\s+".toRegex(), " ")
            .replace(Regex("""(?i)^[Aa]ddress\s*[:\-]?\s*"""), "")
            .trim().ifBlank { null }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Generic key-value sweep
    // ══════════════════════════════════════════════════════════════════════════

    fun genericKV(text: String, exclude: Set<String> = emptySet()): List<DocumentDetail> {
        val result = mutableListOf<DocumentDetail>()
        val used = mutableSetOf<String>()
        text.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            val m = GENERIC_KV.find(line) ?: return@forEach
            val label = m.groupValues[1]
                .replace("_", " ").replace("\\s+".toRegex(), " ").trim()
                .split(" ").joinToString(" ") { tok ->
                    tok.lowercase().replaceFirstChar { it.uppercaseChar() }
                }
            val value = m.groupValues[2].trim()
            val key = label.lowercase()
            if (label.length < 2 || value.isBlank() || value.length > 120) return@forEach
            if (SKIP_WORDS.any { s -> key.contains(s) }) return@forEach
            if (exclude.any { ex -> key.contains(ex) }) return@forEach
            if (!used.add(key)) return@forEach
            result += DocumentDetail(label = label, value = value, multiline = value.length > 42)
        }
        return result
    }
}
