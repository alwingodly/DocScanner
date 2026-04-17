// data/ocr/extractor/VoterIdExtractor.kt
package com.example.docscanner.data.ocr.extractor

import com.example.docscanner.data.ocr.ExtractionUtils
import com.example.docscanner.data.ocr.ExtractionUtils.CONSTITUENCY
import com.example.docscanner.data.ocr.ExtractionUtils.DATE_FALLBACK_FULL
import com.example.docscanner.data.ocr.ExtractionUtils.DOB_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.EPIC_NO
import com.example.docscanner.data.ocr.ExtractionUtils.FATHER_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.SPOUSE_LABEL
import com.example.docscanner.data.ocr.ExtractionUtils.STANDALONE_YEAR_LINE
import com.example.docscanner.data.ocr.ExtractionUtils.VOTER_DOB_YEAR_CONTEXT
import com.example.docscanner.data.ocr.ExtractionUtils.YEAR_BIRTH_ONLY
import com.example.docscanner.data.ocr.ExtractionUtils.formatNameDisplay
import com.example.docscanner.data.ocr.ExtractionUtils.indianNameScore
import com.example.docscanner.data.ocr.ExtractionUtils.levenshtein
import com.example.docscanner.data.ocr.ExtractionUtils.nameOk
import com.example.docscanner.data.ocr.ExtractionUtils.normName
import com.example.docscanner.data.ocr.ExtractionUtils.normalizeDate
import com.example.docscanner.data.ocr.ExtractionUtils.sanitizeName
import com.example.docscanner.data.ocr.TextBlock
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.ExtractedFields
import javax.inject.Inject

class VoterIdExtractor @Inject constructor() : DocumentExtractor {
    override val docType = DocClassType.VOTER_ID
    private enum class RelType { FATHER, SPOUSE }
    private data class AdjacentPair(
        val primaryName: String?,
        val relationType: RelType,
        val relationName: String?
    )
    private data class SequentialPair(
        val primaryName: String?,
        val relationType: RelType,
        val relationName: String?
    )

    private val nameWithColon = Regex(
        """(?im)^\s*(?:name|नाम|ನಾಮ|பெயர்)\s*:\s*([^\n|]{2,60})\s*$"""
    )
    private val unlabeledColonLine = Regex("""^\s*:\s*([^\n|]{2,60})\s*$""")
    private val primaryLabelLine = Regex(
        """(?i)^\s*(?:elector(?:'s|s)?\s*name|name\s*of\s*elector|name|नाम|ನಾಮ|பெயர்)\s*[:\-]?\s*(.*?)\s*$"""
    )
    private val fatherLabelLine = Regex(
        """(?i)^\s*(?:father(?:'s)?\s*name|guardian(?:'s)?\s*name|s/o|son\s*of|d/o|daughter\s*of|c/o|पिता|அப்பா)\s*[:\-]?\s*(.*?)\s*$"""
    )
    private val spouseLabelLine = Regex(
        """(?i)^\s*(?:husband(?:'s)?\s*name|wife(?:'s)?\s*name|w/o|h/o|पति|पत्नी)\s*[:\-]?\s*(.*?)\s*$"""
    )
    private val adjacentPrimaryRelation = Regex(
        """(?is)(?:elector(?:'s|s)?\s*name|name\s*of\s*elector|name)\s*[:\-]?\s*(.+?)\s+(father(?:'s)?\s*name|guardian(?:'s)?\s*name|s/o|son\s*of|d/o|daughter\s*of|c/o|husband(?:'s)?\s*name|wife(?:'s)?\s*name|w/o|h/o|पति|पत्नी)\s*[:\-]?\s*(.+?)(?=\s*(?:epic(?:\s*no)?|voter\s*id|dob|date\s*of\s*birth|year\s*of\s*birth|age|gender|sex|address|constituency)\b|$)"""
    )

    override suspend fun extract(
        blocks: List<TextBlock>,
        rawText: String,
        docHeight: Int,
    ): ExtractedFields.VoterId {
        val epic = EPIC_NO.find(rawText)?.value
        val extractedName = extractNameScored(blocks, rawText, docHeight, isAadhaarFront = false)
        val colonValues = extractUnlabeledColonValues(rawText)
        val adjacentPair = extractAdjacentPrimaryRelation(rawText)
        val sequentialPair = extractSequentialLabeledPair(rawText)
        var father = adjacentPair
            ?.takeIf { it.relationType == RelType.FATHER }
            ?.relationName
            ?: sequentialPair
                ?.takeIf { it.relationType == RelType.FATHER }
                ?.relationName
            ?: extractNameFromLabeledSection(rawText, fatherLabelLine)
            ?: extractRelationalName(rawText, FATHER_LABEL)
            ?: colonValues.getOrNull(1)
        var spouse = adjacentPair
            ?.takeIf { it.relationType == RelType.SPOUSE }
            ?.relationName
            ?: sequentialPair
                ?.takeIf { it.relationType == RelType.SPOUSE }
                ?.relationName
            ?: extractNameFromLabeledSection(rawText, spouseLabelLine)
            ?: extractRelationalName(rawText, SPOUSE_LABEL)
        val name = adjacentPair?.primaryName
            ?: sequentialPair?.primaryName
            ?: extractNameFromLabeledSection(rawText, primaryLabelLine)
            ?: extractDelimitedName(rawText)
            ?: colonValues.firstOrNull()
            ?: extractedName
            ?.takeUnless { ExtractionUtils.isHeaderLine(it) || isGovernmentLikeName(it) }
            ?.let { normalizePersonName(it) }
            ?.takeUnless { isWeakVoterName(it) }
            ?: fallbackVoterName(rawText, father, spouse)
        father = father?.takeUnless { name != null && levenshtein(it.normName(), name.normName()) <= 2 }
        spouse = spouse?.takeUnless { name != null && levenshtein(it.normName(), name.normName()) <= 2 }
        val dob = extractVoterDob(rawText)
        val age: String? = null
        val gender = ExtractionUtils.extractGender(rawText)
        val addr = ExtractionUtils.extractAddress(rawText)
        val pin = ExtractionUtils.extractPin(addr ?: rawText)
        val constit = CONSTITUENCY.find(rawText)?.groupValues?.getOrNull(1)?.trim()

        return ExtractedFields.VoterId(
            name = name,
            fatherName = father,
            spouseName = spouse,
            idNumber = epic,
            dob = dob,
            age = age,
            gender = gender,
            address = addr,
            pinCode = pin,
            constituency = constit,
            rawText = rawText,
            confidence = if (epic != null) 0.90f else 0.55f,
            details = details {
                f("Name", name)
                f("Voter ID", epic)
                f("Father's Name", father)
                f("Spouse Name", spouse)
                f("DOB", dob)
                f("Gender", gender)
                f("Constituency", constit)
                f("Address", addr, multiline = true)
                f("PIN Code", pin)
                extra(
                    ExtractionUtils.genericKV(
                        rawText,
                        setOf("name", "epic", "dob", "age", "gender", "sex", "address", "constituency")
                    )
                )
            },
        )
    }

    private fun extractVoterDob(raw: String): String? {
        // 1. Labeled full date
        ExtractionUtils.extractLabeledDate(raw, DOB_LABEL)?.let { return it }
        // 2. Any full date
        DATE_FALLBACK_FULL.find(raw)?.groupValues?.getOrNull(1)
            ?.let { return normalizeDate(it) }

        // 3. Year-only with context
        VOTER_DOB_YEAR_CONTEXT.find(raw)?.let { m ->
            val yr = m.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.toIntOrNull()
            if (yr != null && yr in 1920..2010) return yr.toString()
        }

        // 4. Standalone year-only line
        raw.lines().forEach { line ->
            val t = line.trim()
            if (STANDALONE_YEAR_LINE.matches(t)) {
                val yr = t.toIntOrNull()
                if (yr != null && yr in 1920..2010) return t
            }
        }

        // 5. Masked-range scan: reject any year that falls inside an EPIC
        //    number or any 6+ digit run. Positions resolved directly on `raw`
        //    — never across a mutated masked string.
        val maskedRanges = buildList {
            addAll(EPIC_NO.findAll(raw).map { it.range })
            addAll(Regex("""\d{6,}""").findAll(raw).map { it.range })
        }
        YEAR_BIRTH_ONLY.findAll(raw)
            .firstOrNull { m ->
                val yr = m.groupValues[1].toIntOrNull() ?: return@firstOrNull false
                if (yr !in 1920..2010) return@firstOrNull false
                maskedRanges.none { it.first <= m.range.first && m.range.last <= it.last }
            }
            ?.let { return it.groupValues[1] }

        return null
    }

    private fun isGovernmentLikeName(name: String): Boolean {
        val lower = name.lowercase()
        val signals = listOf(
            "government", "govt", "india", "bharat",
            "election", "commiss", "commission", "commissioner", "commision",
            "department", "authority", "ministry", "uidai"
        )
        return signals.count { lower.contains(it) } >= 2
    }

    private fun fallbackVoterName(raw: String, fatherName: String?, spouseName: String?): String? {
        val relationalNames = setOfNotNull(fatherName?.lowercase(), spouseName?.lowercase())
        val skipIfContains = listOf(
            "father", "mother", "husband", "wife", "guardian",
            "s/o", "d/o", "w/o", "h/o", "c/o",
            "dob", "date of birth", "age", "gender", "sex",
            "address", "constituency", "epic", "election", "commission",
            "elector", "electors name", "elector's name", "name of elector"
        )
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.length !in 2..50) continue
            val lower = trimmed.lowercase()
            if (skipIfContains.any { lower.contains(it) }) continue
            if (ExtractionUtils.isHeaderLine(trimmed)) continue
            if (isGovernmentLikeName(trimmed)) continue
            if (EPIC_NO.containsMatchIn(trimmed)) continue
            if (DATE_FALLBACK_FULL.containsMatchIn(trimmed)) continue

            val candidate = formatNameDisplay(sanitizeName(trimmed))
            if (!nameOk(candidate)) continue
            if (isWeakVoterName(candidate)) continue
            if (candidate.lowercase() in relationalNames) continue
            return normalizePersonName(candidate)
        }
        return null
    }

    private fun extractAdjacentPrimaryRelation(raw: String): AdjacentPair? {
        val flattened = raw
            .lines()
            .joinToString(" ") { it.trim() }
            .replace("\\s+".toRegex(), " ")
            .trim()
        if (flattened.isBlank()) return null

        val match = adjacentPrimaryRelation.find(flattened) ?: return null
        val primaryName = cleanSectionCandidate(match.groupValues.getOrNull(1).orEmpty())
        val relationLabel = match.groupValues.getOrNull(2).orEmpty().lowercase()
        val relationName = cleanSectionCandidate(match.groupValues.getOrNull(3).orEmpty())

        val relationType = when {
            relationLabel.contains("husband") ||
                relationLabel.contains("wife") ||
                relationLabel.contains("w/o") ||
                relationLabel.contains("h/o") ||
                relationLabel.contains("पति") ||
                relationLabel.contains("पत्नी") -> RelType.SPOUSE
            else -> RelType.FATHER
        }

        if (primaryName == null && relationName == null) return null
        return AdjacentPair(
            primaryName = primaryName,
            relationType = relationType,
            relationName = relationName
        )
    }

    private fun extractSequentialLabeledPair(raw: String): SequentialPair? {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (index in 0 until lines.lastIndex) {
            val primaryMatch = primaryLabelLine.matchEntire(lines[index]) ?: continue
            val relationLine = lines.getOrNull(index + 1) ?: continue
            val relationType = when {
                spouseLabelLine.matches(relationLine) -> RelType.SPOUSE
                fatherLabelLine.matches(relationLine) -> RelType.FATHER
                else -> continue
            }

            val inlinePrimary = cleanSectionCandidate(primaryMatch.groupValues.getOrNull(1).orEmpty())
            val relationMatch = when (relationType) {
                RelType.SPOUSE -> spouseLabelLine.matchEntire(relationLine)
                RelType.FATHER -> fatherLabelLine.matchEntire(relationLine)
            } ?: continue
            val inlineRelation = cleanSectionCandidate(relationMatch.groupValues.getOrNull(1).orEmpty())

            val values = buildList {
                lines.drop(index + 2).forEach { line ->
                    cleanSectionCandidate(line)?.let { add(it) }
                    if (size >= 2) return@forEach
                }
            }

            val primaryName = inlinePrimary ?: values.getOrNull(0)
            val relationName = inlineRelation ?: when {
                inlinePrimary == null -> values.getOrNull(1)
                else -> values.getOrNull(0)
            }

            if (primaryName != null || relationName != null) {
                return SequentialPair(
                    primaryName = primaryName,
                    relationType = relationType,
                    relationName = relationName
                )
            }
        }
        return null
    }

    private fun extractNameFromLabeledSection(raw: String, labelLine: Regex): String? {
        val lines = raw.lines()
        for (index in lines.indices) {
            val match = labelLine.matchEntire(lines[index].trim()) ?: continue

            cleanSectionCandidate(match.groupValues.getOrNull(1).orEmpty())?.let { return it }

            for (offset in 1..2) {
                cleanSectionCandidate(lines.getOrNull(index + offset).orEmpty())?.let { return it }
            }
        }
        return null
    }

    private fun cleanSectionCandidate(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        if (looksLikeMetadataLine(trimmed)) return null

        val candidate = normalizePersonName(trimmed)
        if (!nameOk(candidate)) return null
        if (isWeakVoterName(candidate)) return null
        if (ExtractionUtils.isHeaderLine(candidate) || isGovernmentLikeName(candidate)) return null
        if (isLabelLikeName(candidate)) return null
        if (looksLikeMetadataLine(candidate)) return null
        return candidate
    }

    private fun looksLikeMetadataLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("father") ||
            lower.contains("mother") ||
            lower.contains("husband") ||
            lower.contains("wife") ||
            lower.contains("guardian") ||
            lower.contains("elector") ||
            lower.contains("name of elector") ||
            lower.contains("dob") ||
            lower.contains("date of birth") ||
            lower.contains("age") ||
            lower.contains("gender") ||
            lower.contains("sex") ||
            lower.contains("address") ||
            lower.contains("constituency") ||
            lower.contains("epic") ||
            lower == "name"
    }

    private fun normalizePersonName(name: String): String {
        val cleaned = name
            .replace(Regex("""(?i)^\s*(?:name|नाम|ನಾಮ|பெயர்)\s*[:\-]?\s*"""), "")
            .replace(
                Regex(
                    """(?i)^\s*(?:elector(?:'s|s)?\s*name|name\s*of\s*elector)\s*[:\-]?\s*"""
                ),
                ""
            )
            .trim()
        return formatNameDisplay(cleaned)
    }

    private fun extractDelimitedName(raw: String): String? {
        val captured = nameWithColon.find(raw)?.groupValues?.getOrNull(1)?.trim() ?: return null
        val candidate = normalizePersonName(captured)
        if (!nameOk(candidate)) return null
        if (isWeakVoterName(candidate)) return null
        if (ExtractionUtils.isHeaderLine(candidate) || isGovernmentLikeName(candidate)) return null
        if (isLabelLikeName(candidate)) return null
        return candidate
    }

    private fun extractUnlabeledColonValues(raw: String): List<String> {
        return raw.lines().mapNotNull { line ->
            val captured = unlabeledColonLine.find(line)?.groupValues?.getOrNull(1)?.trim() ?: return@mapNotNull null
            val candidate = normalizePersonName(captured)
            if (!nameOk(candidate)) return@mapNotNull null
            if (isWeakVoterName(candidate)) return@mapNotNull null
            if (ExtractionUtils.isHeaderLine(candidate) || isGovernmentLikeName(candidate)) return@mapNotNull null
            if (isLabelLikeName(candidate)) return@mapNotNull null
            candidate
        }
    }

    private fun isWeakVoterName(name: String): Boolean {
        val words = name.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        if (words.any { it.length < 3 }) return true
        return indianNameScore(name) < 0.20f
    }

    private fun isLabelLikeName(name: String): Boolean {
        val lower = name.lowercase()
        return lower == "name" ||
            lower == "elector name" ||
            lower == "electors name" ||
            lower == "elector's name" ||
            lower == "name of elector"
    }
}
