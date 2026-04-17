// data/ocr/extractor/NameExtraction.kt
package com.example.docscanner.data.ocr.extractor

import com.example.docscanner.data.ocr.ExtractionUtils
import com.example.docscanner.data.ocr.ExtractionUtils.AADHAAR_12
import com.example.docscanner.data.ocr.ExtractionUtils.DATE_FALLBACK_FULL
import com.example.docscanner.data.ocr.ExtractionUtils.DOB_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.GENDER_FULL
import com.example.docscanner.data.ocr.ExtractionUtils.GENDER_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.IS_REL_LINE
import com.example.docscanner.data.ocr.ExtractionUtils.NAME_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.PAN_STRICT
import com.example.docscanner.data.ocr.ExtractionUtils.REL_PREFIX_FULL
import com.example.docscanner.data.ocr.ExtractionUtils.formatNameDisplay
import com.example.docscanner.data.ocr.ExtractionUtils.indianNameScore
import com.example.docscanner.data.ocr.ExtractionUtils.nameOk
import com.example.docscanner.data.ocr.ExtractionUtils.sanitizeName
import com.example.docscanner.data.ocr.ExtractionUtils.stripRelPrefix
import com.example.docscanner.data.ocr.TextBlock

private val RELATIONAL_HINT = Regex(
    """(?i)\b(?:father(?:'s)?\s*name|mother(?:'s)?\s*name|husband(?:'s)?\s*name|wife(?:'s)?\s*name|guardian(?:'s)?\s*name|s/o|d/o|w/o|h/o|c/o|son\s+of|daughter\s+of|wife\s+of|husband\s+of)\b"""
)

internal data class NameCandidate(
    val value: String,
    val confidence: Float,
    val strategy: String,
)

internal fun extractLabeledName(raw: String): String? {
    val m = NAME_LABEL.find(raw) ?: return null
    val captured = m.groupValues.getOrElse(1) { "" }.trim()
    val clean = captured
        .split(Regex("""(?i)\s+(?:S/O|D/O|W/O|H/O|Son\s+of|Daughter\s+of)"""))[0]
        .trim()
    val san = sanitizeName(clean)
    return if (nameOk(san)) formatNameDisplay(san) else null
}

internal fun extractRelationalName(raw: String, pat: Regex): String? {
    val m = pat.find(raw) ?: return null
    val captured = m.groupValues.getOrElse(1) { "" }.trim()
    val clean = captured.split(Regex("""\s{2,}|\|"""))[0].trim()
    val san = sanitizeName(clean)
    return if (nameOk(san)) formatNameDisplay(san) else null
}

/**
 * Confidence-scored multi-strategy name extractor used by Aadhaar/Passport/Voter/DL.
 * Strategy tiers:
 *   S1 — explicit "Name:" label
 *   S2 — anchor window above DOB/gender/ID line
 *   S3 — spatial zone (Aadhaar front)
 *   S4 — heuristic (Title-Case line with low digit ratio)
 */
internal fun extractNameScored(
    blocks: List<TextBlock>,
    raw: String,
    docH: Int,
    isAadhaarFront: Boolean,
): String? {
    val pool = mutableListOf<NameCandidate>()
    val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }

    // S1
    extractLabeledName(raw)?.let {
        pool += NameCandidate(it, ExtractionUtils.C_LABELED, "S1-label")
    }

    // S2
    val anchorIdx = lines.indexOfFirst { l ->
        DOB_LABEL.containsMatchIn(l) ||
                DATE_FALLBACK_FULL.containsMatchIn(l) ||
                GENDER_FULL.containsMatchIn(l) ||
                GENDER_LABEL.containsMatchIn(l) ||
                AADHAAR_12.containsMatchIn(l) ||
                PAN_STRICT.containsMatchIn(l)
    }
    if (anchorIdx >= 1) {
        val wStart = maxOf(0, anchorIdx - 5)
        lines.subList(wStart, anchorIdx).asReversed().forEachIndexed { offset, line ->
            if (IS_REL_LINE.containsMatchIn(line)) return@forEachIndexed
            if (REL_PREFIX_FULL.containsMatchIn(line)) return@forEachIndexed
            if (RELATIONAL_HINT.containsMatchIn(line)) return@forEachIndexed
            val san = sanitizeName(stripRelPrefix(line))
            if (nameOk(san)) {
                val boost = indianNameScore(san) * 0.05f
                val conf = (ExtractionUtils.C_ANCHOR - offset * 0.05f + boost).coerceAtLeast(0.50f)
                pool += NameCandidate(formatNameDisplay(san), conf, "S2-off$offset")
            }
        }
    }

    // S3
    if (isAadhaarFront && docH > 0) {
        for (block in blocks) {
            val box = block.boundingBox ?: continue
            val zone = zoneOf(box.top, docH)
            if (zone != Zone.UPPER && zone != Zone.MIDDLE) continue
            for (lt in block.lines.map { it.text.trim() }) {
                if (IS_REL_LINE.containsMatchIn(lt)) continue
                val san = sanitizeName(stripRelPrefix(lt))
                if (nameOk(san)) {
                    pool += NameCandidate(formatNameDisplay(san), ExtractionUtils.C_SPATIAL, "S3-$zone")
                    break
                }
            }
        }
    }

    // S4
    for (line in lines) {
        if (line.length !in 2..50) continue
        if (ExtractionUtils.isHeaderLine(line)) continue
        if (IS_REL_LINE.containsMatchIn(line)) continue
        if (REL_PREFIX_FULL.containsMatchIn(line)) continue
        if (RELATIONAL_HINT.containsMatchIn(line)) continue
        val digitRatio = line.count { it.isDigit() }.toFloat() / line.length.coerceAtLeast(1)
        if (digitRatio > 0.15f) continue
        val san = sanitizeName(stripRelPrefix(line))
        if (nameOk(san)) {
            val score = ExtractionUtils.C_HEURISTIC + indianNameScore(san) * 0.05f
            pool += NameCandidate(formatNameDisplay(san), score, "S4-heur")
        }
    }

    if (pool.isEmpty()) return null
    val best = pool.maxByOrNull { it.confidence }!!
    return best.value
}