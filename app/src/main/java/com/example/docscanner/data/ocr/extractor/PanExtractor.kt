// data/ocr/extractor/PanExtractor.kt
package com.example.docscanner.data.ocr.extractor

import android.util.Log
import com.example.docscanner.data.ocr.ExtractionUtils
import com.example.docscanner.data.ocr.ExtractionUtils.DATE_FALLBACK_FULL
import com.example.docscanner.data.ocr.ExtractionUtils.DOB_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.FATHER_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.IS_REL_LINE
import com.example.docscanner.data.ocr.ExtractionUtils.NUMBER_HEAVY
import com.example.docscanner.data.ocr.ExtractionUtils.PAN_LABEL_LINES
import com.example.docscanner.data.ocr.ExtractionUtils.PAN_SIGNATURE_JUNK
import com.example.docscanner.data.ocr.ExtractionUtils.PAN_STRICT
import com.example.docscanner.data.ocr.ExtractionUtils.REL_PREFIX_FULL
import com.example.docscanner.data.ocr.ExtractionUtils.SKIP_WORDS
import com.example.docscanner.data.ocr.ExtractionUtils.correctPanNoise
import com.example.docscanner.data.ocr.ExtractionUtils.formatNameDisplay
import com.example.docscanner.data.ocr.ExtractionUtils.indianNameScore
import com.example.docscanner.data.ocr.ExtractionUtils.isHeaderLine
import com.example.docscanner.data.ocr.ExtractionUtils.isPanLabelLikeName
import com.example.docscanner.data.ocr.ExtractionUtils.isPanHeaderLine
import com.example.docscanner.data.ocr.ExtractionUtils.levenshtein
import com.example.docscanner.data.ocr.ExtractionUtils.normName
import com.example.docscanner.data.ocr.ExtractionUtils.normalizeDate
import com.example.docscanner.data.ocr.ExtractionUtils.sanitizeName
import com.example.docscanner.data.ocr.ExtractionUtils.stripRelPrefix
import com.example.docscanner.data.ocr.TextBlock
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.ExtractedFields
import javax.inject.Inject

class PanExtractor @Inject constructor() : DocumentExtractor {
    override val docType = DocClassType.PAN
    private val tag = "PanExt"
    private data class PanLabeledPair(
        val name: String?,
        val father: String?
    )
    private val panPrimaryLabelLine = Regex(
        """(?i)^\s*(?:name|नाम)\s*[:\-]?\s*(.*?)\s*$"""
    )
    private val panFatherLabelLine = Regex(
        """(?i)^\s*(?:father(?:'s)?\s*name|guardian(?:'s)?\s*name)\s*[:\-]?\s*(.*?)\s*$"""
    )

    override suspend fun extract(
        blocks: List<TextBlock>,
        rawText: String,
        docHeight: Int,
    ): ExtractedFields.Pan {
        val pan = findPanNumber(rawText)
        val dob = extractPanDob(rawText)

        // T1: labeled
        val labeledPair = extractSequentialLabeledPair(rawText)
        var name = labeledPair?.name
            ?: extractPanNameFromSection(rawText, panPrimaryLabelLine)
            ?: extractLabeledName(rawText)
        var father = labeledPair?.father
            ?: extractPanNameFromSection(rawText, panFatherLabelLine)
            ?: extractRelationalName(rawText, FATHER_LABEL)

        // T2: spatial
        if ((name == null || father == null) && blocks.isNotEmpty() && docHeight > 0) {
            val (spN, spF) = spatial(blocks, docHeight, dob, pan)
            if (name == null) name = spN
            if (father == null) father = spF
        }

        // T3: positional
        if (name == null || father == null) {
            val (poN, poF) = positional(rawText, dob, pan)
            if (name == null) name = poN
            if (father == null) father = poF
        }

        // Cross-field dedup
        if (name != null && father != null) {
            if (name.equals(father, ignoreCase = true)) {
                Log.w(tag, "name == father (exact); dropping father")
                father = null
            } else if (levenshtein(name.normName(), father!!.normName()) <= 2) {
                Log.w(tag, "name ≈ father (fuzzy); dropping father")
                father = null
            }
        }

        return ExtractedFields.Pan(
            name = name,
            fatherName = father,
            idNumber = pan,
            dob = dob,
            rawText = rawText,
            confidence = if (pan != null) 0.92f else 0.50f,
            details = details {
                f("Name", name)
                f("PAN", pan)
                f("Father's Name", father)
                f("DOB", dob)
                extra(ExtractionUtils.genericKV(rawText, setOf("name", "pan", "dob", "father")))
            },
        )
    }

    private fun findPanNumber(text: String): String? {
        for (line in text.lines()) {
            PAN_STRICT.find(line)?.groupValues?.getOrNull(1)?.let { return it }
            PAN_STRICT.find(correctPanNoise(line))?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    private fun extractPanDob(raw: String): String? {
        ExtractionUtils.extractLabeledDate(raw, DOB_LABEL)?.let { return it }
        // PAN DOB appears after ≥2 alpha-dominant content lines (the names)
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        var alphaSeen = 0
        for (line in lines) {
            if (isPanHeaderLine(line)) continue
            val digitRatio = line.count { it.isDigit() }.toFloat() / line.length.coerceAtLeast(1)
            if (digitRatio < 0.15f && line.length in 3..50 && !isHeaderLine(line)) alphaSeen++
            if (alphaSeen >= 2) {
                DATE_FALLBACK_FULL.find(line)?.groupValues?.getOrNull(1)
                    ?.let { return normalizeDate(it) }
            }
        }
        return null
    }

    private fun spatial(
        blocks: List<TextBlock>,
        docH: Int,
        dob: String?,
        pan: String?,
    ): Pair<String?, String?> {
        data class Scored(val text: String, val midY: Int, val score: Float, val isRel: Boolean)
        val cands = mutableListOf<Scored>()

        for (block in blocks) {
            val blockMidY = block.boundingBox?.let { (it.top + it.bottom) / 2 } ?: continue
            for (line in block.lines) {
                val lineMidY = line.boundingBox?.let { (it.top + it.bottom) / 2 } ?: blockMidY
                val text = line.text.trim()

                if (isPanHeaderLine(text)) continue
                if (pan != null && PAN_STRICT.containsMatchIn(text)) continue
                if (dob != null && text.contains(dob)) continue
                if (DOB_LABEL.containsMatchIn(text)) continue
                if (DATE_FALLBACK_FULL.containsMatchIn(text)) continue
                if (PAN_SIGNATURE_JUNK.containsMatchIn(text)) continue
                if (PAN_LABEL_LINES.containsMatchIn(text)) continue
                if (text.length < 3) continue

                val isRel = IS_REL_LINE.containsMatchIn(text) || REL_PREFIX_FULL.containsMatchIn(text)
                val san = sanitizeName(stripRelPrefix(text))
                if (panNameOk(san)) {
                    val score = indianNameScore(san) + (if (isRel) 0.0f else 0.1f)
                    cands += Scored(san, lineMidY, score, isRel)
                }
            }
        }

        if (cands.isEmpty()) return null to null

        // If any candidate carries a rel prefix, that's the father — pick best-scoring
        // non-rel candidate for holder.
        val relCand = cands.filter { it.isRel }.maxByOrNull { it.score }
        val nonRel = cands.filter { !it.isRel }

        if (relCand != null) {
            val holder = nonRel.maxByOrNull { it.score }
            return formatNameDisplay(holder?.text ?: return null to formatNameDisplay(relCand.text)) to
                    formatNameDisplay(relCand.text)
        }

        // No rel marker — pick the top-2 scoring, then order by Y (higher = holder).
        val topTwo = cands.sortedByDescending { it.score }.take(2).sortedBy { it.midY }
        return when (topTwo.size) {
            0 -> null to null
            1 -> formatNameDisplay(topTwo[0].text) to null
            else -> formatNameDisplay(topTwo[0].text) to formatNameDisplay(topTwo[1].text)
        }
    }

    private fun positional(raw: String, dob: String?, pan: String?): Pair<String?, String?> {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }

        val anchorIdx = lines.indexOfFirst { line ->
            (dob != null && line.contains(dob.replace("/", "[/\\-]").toRegex())) ||
                    (pan != null && line.contains(pan)) ||
                    DOB_LABEL.containsMatchIn(line) ||
                    DATE_FALLBACK_FULL.containsMatchIn(line) ||
                    PAN_STRICT.containsMatchIn(line)
        }
        if (anchorIdx < 1) return null to null

        var headerEndIdx = 0
        for (i in 0 until anchorIdx) if (isPanHeaderLine(lines[i])) headerEndIdx = i + 1

        val contentLines = mutableListOf<String>()
        for (i in headerEndIdx until anchorIdx) {
            val line = lines[i]
            if (PAN_SIGNATURE_JUNK.containsMatchIn(line)) continue
            if (PAN_LABEL_LINES.containsMatchIn(line)) continue
            if (line.length < 3) continue
            if (NUMBER_HEAVY.matches(line)) continue
            val words = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            val skipCount = words.count { w -> SKIP_WORDS.any { s -> w.lowercase().contains(s) } }
            if (skipCount > 0 && skipCount.toFloat() / words.size >= 0.4f) continue
            contentLines += line
        }
        if (contentLines.isEmpty()) return null to null

        // Each processed entry remembers whether its *original* line had a rel prefix.
        val processed = contentLines.map { line ->
            val hasRelPrefix = IS_REL_LINE.containsMatchIn(line) || REL_PREFIX_FULL.containsMatchIn(line)
            val san = sanitizeName(stripRelPrefix(line))
            Triple(san, panNameOk(san), hasRelPrefix)
        }
        val valid = processed.filter { it.second }

        return when {
            valid.isEmpty() -> null to null
            // If any valid line has a rel prefix, it's authoritatively the father —
            // regardless of position. Pick any other valid line as holder.
            valid.any { it.third } -> {
                val father = valid.first { it.third }.first
                val holder = valid.firstOrNull { !it.third }?.first
                (holder?.let { formatNameDisplay(it) }) to formatNameDisplay(father)
            }
            valid.size == 1 -> formatNameDisplay(valid.first().first) to null
            else -> {
                // No rel markers: last = father, second-last = holder (standard PAN layout)
                val last = valid.last().first
                val secondLast = valid[valid.size - 2].first
                formatNameDisplay(secondLast) to formatNameDisplay(last)
            }
        }
    }

    private fun extractPanNameFromSection(raw: String, labelLine: Regex): String? {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (index in lines.indices) {
            val match = labelLine.matchEntire(lines[index]) ?: continue
            cleanPanNameCandidate(match.groupValues.getOrNull(1).orEmpty())?.let { return it }

            for (offset in 1..2) {
                val candidate = lines.getOrNull(index + offset) ?: break
                if (panPrimaryLabelLine.matches(candidate) || panFatherLabelLine.matches(candidate)) break
                if (DOB_LABEL.containsMatchIn(candidate)) break
                if (DATE_FALLBACK_FULL.containsMatchIn(candidate)) break
                if (PAN_STRICT.containsMatchIn(candidate)) break
                cleanPanNameCandidate(candidate)?.let { return it }
            }
        }
        return null
    }

    private fun extractSequentialLabeledPair(raw: String): PanLabeledPair? {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (index in 0 until lines.lastIndex) {
            val primaryMatch = panPrimaryLabelLine.matchEntire(lines[index]) ?: continue
            val fatherMatch = panFatherLabelLine.matchEntire(lines.getOrNull(index + 1) ?: continue) ?: continue

            val inlineName = cleanPanNameCandidate(primaryMatch.groupValues.getOrNull(1).orEmpty())
            val inlineFather = cleanPanNameCandidate(fatherMatch.groupValues.getOrNull(1).orEmpty())
            val values = buildList {
                lines.drop(index + 2).forEach { line ->
                    if (DOB_LABEL.containsMatchIn(line) || DATE_FALLBACK_FULL.containsMatchIn(line) || PAN_STRICT.containsMatchIn(line)) {
                        return@forEach
                    }
                    cleanPanNameCandidate(line)?.let { add(it) }
                    if (size >= 2) return@forEach
                }
            }

            val name = inlineName ?: values.getOrNull(0)
            val father = inlineFather ?: when {
                inlineName == null -> values.getOrNull(1)
                else -> values.getOrNull(0)
            }
            if (name != null || father != null) return PanLabeledPair(name = name, father = father)
        }
        return null
    }

    private fun cleanPanNameCandidate(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        if (isPanHeaderLine(trimmed)) return null
        if (DOB_LABEL.containsMatchIn(trimmed)) return null
        if (DATE_FALLBACK_FULL.containsMatchIn(trimmed)) return null
        if (PAN_STRICT.containsMatchIn(trimmed)) return null
        if (PAN_SIGNATURE_JUNK.containsMatchIn(trimmed)) return null
        if (PAN_LABEL_LINES.containsMatchIn(trimmed)) return null

        val san = sanitizeName(stripRelPrefix(trimmed))
        if (!panNameOk(san)) return null
        return formatNameDisplay(san)
    }

    private fun panNameOk(candidate: String): Boolean {
        if (candidate.length !in 2..50) return false
        if (NUMBER_HEAVY.matches(candidate)) return false
        if (candidate.any { it.isDigit() }) return false
        if (isHeaderLine(candidate)) return false
        if (IS_REL_LINE.containsMatchIn(candidate)) return false
        if (isPanLabelLikeName(candidate)) return false

        val words = candidate.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 6) return false
        if (words.any { it.length > 20 }) return false
        if (words.none { it.length >= 2 }) return false
        return true
    }
}
