// data/ocr/ner/RegexNerExtractor.kt
package com.example.docscanner.data.ocr.ner

import com.example.docscanner.data.ocr.ExtractionUtils
import com.example.docscanner.data.ocr.ExtractionUtils.DATE_FALLBACK_FULL
import com.example.docscanner.data.ocr.ExtractionUtils.INDIAN_STATES
import com.example.docscanner.data.ocr.ExtractionUtils.NAME_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.FATHER_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.SPOUSE_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.indianNameScore
import com.example.docscanner.data.ocr.ExtractionUtils.nameOk
import com.example.docscanner.data.ocr.ExtractionUtils.sanitizeName
import com.example.docscanner.domain.model.NerEntity
import com.example.docscanner.domain.model.NerType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default NER implementation — zero dependencies, always available.
 * Works as a drop-in until the TFLite model is ready. Precision is acceptable
 * for PII redaction; recall on unusual names is limited.
 */
@Singleton
class RegexNerExtractor @Inject constructor() : NerExtractor {

    override suspend fun extract(text: String): List<NerEntity> {
        val out = mutableListOf<NerEntity>()
        out += findPersons(text)
        out += findDates(text)
        out += findLocations(text)
        return out.sortedBy { it.startChar }
    }

    private fun findPersons(text: String): List<NerEntity> {
        val results = mutableListOf<NerEntity>()

        listOf(NAME_LABEL, FATHER_LABEL, SPOUSE_LABEL).forEach { pat ->
            pat.findAll(text).forEach { m ->
                val g = m.groups[1] ?: return@forEach
                val san = sanitizeName(g.value)
                if (nameOk(san)) {
                    results += NerEntity(
                        type = NerType.PERSON,
                        text = san,
                        startChar = g.range.first,
                        endChar = g.range.last + 1,
                        confidence = 0.85f + indianNameScore(san) * 0.10f,
                    )
                }
            }
        }

        // Heuristic PERSON: Title-Case lines with high Indian-name score
        var cursor = 0
        text.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.length in 4..50 && !ExtractionUtils.isHeaderLine(trimmed)) {
                val digitRatio = trimmed.count { it.isDigit() }.toFloat() / trimmed.length
                if (digitRatio < 0.15f) {
                    val score = indianNameScore(trimmed)
                    if (score >= 0.45f && nameOk(sanitizeName(trimmed))) {
                        val start = text.indexOf(trimmed, cursor).coerceAtLeast(0)
                        results += NerEntity(
                            type = NerType.PERSON,
                            text = sanitizeName(trimmed),
                            startChar = start,
                            endChar = start + trimmed.length,
                            confidence = 0.55f + score * 0.15f,
                        )
                    }
                }
            }
            cursor += line.length + 1
        }

        // Dedup overlapping spans — keep highest-confidence
        return results
            .sortedByDescending { it.confidence }
            .fold(mutableListOf<NerEntity>()) { acc, e ->
                if (acc.none { it.overlaps(e) }) acc += e
                acc
            }
    }

    private fun findDates(text: String): List<NerEntity> =
        DATE_FALLBACK_FULL.findAll(text).map { m ->
            NerEntity(
                type = NerType.DATE,
                text = m.value,
                startChar = m.range.first,
                endChar = m.range.last + 1,
                confidence = 0.95f,
            )
        }.toList()

    private fun findLocations(text: String): List<NerEntity> {
        val lower = text.lowercase()
        return INDIAN_STATES.mapNotNull { state ->
            val idx = lower.indexOf(state)
            if (idx >= 0) {
                NerEntity(
                    type = NerType.LOCATION,
                    text = text.substring(idx, idx + state.length),
                    startChar = idx,
                    endChar = idx + state.length,
                    confidence = 0.80f,
                )
            } else null
        }
    }

    private fun NerEntity.overlaps(other: NerEntity): Boolean =
        startChar < other.endChar && other.startChar < endChar
}