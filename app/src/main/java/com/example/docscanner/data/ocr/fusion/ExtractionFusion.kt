// data/ocr/fusion/ExtractionFusion.kt
package com.example.docscanner.data.ocr.fusion

import android.graphics.Bitmap
import android.util.Log
import com.example.docscanner.data.ocr.ExtractionUtils
import com.example.docscanner.data.ocr.ExtractionUtils.formatNameDisplay
import com.example.docscanner.data.ocr.ExtractionUtils.indianNameScore
import com.example.docscanner.data.ocr.ExtractionUtils.isPanLabelLikeName
import com.example.docscanner.data.ocr.ExtractionUtils.levenshtein
import com.example.docscanner.data.ocr.ExtractionUtils.normName
import com.example.docscanner.data.ocr.ExtractionUtils.sanitizeName
import com.example.docscanner.data.ocr.MlKitOcrClient
import com.example.docscanner.data.ocr.TextBlock
import com.example.docscanner.data.ocr.extractor.DocumentExtractor
import com.example.docscanner.data.ocr.extractor.estimateDocHeight
import com.example.docscanner.data.ocr.ner.NerExtractor
import com.example.docscanner.data.security.AadhaarSecureHelper
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.ExtractedFields
import com.example.docscanner.domain.model.NerEntity
import com.example.docscanner.domain.model.NerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates OCR → per-doc regex extraction → NER → merge.
 *
 * Routing principle:
 *   • ID numbers, dates, PIN codes — trust REGEX (deterministic, high precision).
 *   • Names — prefer NER PERSON entities, use regex as tiebreaker.
 *   • Addresses — if NER produces LOCATION clusters, prefer those; else regex.
 */
@Singleton
class ExtractionFusion @Inject constructor(
    private val ocr: MlKitOcrClient,
    private val extractors: Map<DocClassType, @JvmSuppressWildcards DocumentExtractor>,
    private val ner: NerExtractor,
    private val secureHelper: AadhaarSecureHelper,
) {
    private val tag = "ExtractionFusion"

    suspend fun extract(bitmap: Bitmap, docType: DocClassType): ExtractedFields =
        withContext(Dispatchers.Default) {
            val blocks = ocr.extractBlocks(bitmap).getOrNull() ?: return@withContext fallback()
            val raw = ExtractionUtils.normalizeIndicText(blocks.joinToString("\n") { it.text })
            val docH = estimateDocHeight(blocks)
            val extractor = extractors[docType] ?: extractors[DocClassType.OTHER]
            ?: return@withContext ExtractedFields.Unknown(raw)

            coroutineScope {
                val regexDeferred = async { extractor.extract(blocks, raw, docH) }
                val nerDeferred = async { ner.extract(raw) }
                val regex = regexDeferred.await()
                val entities = nerDeferred.await()
                Log.d(tag, "NER entities: ${entities.size} (${entities.groupBy { it.type }.mapValues { it.value.size }})")
                merge(regex, entities, blocks)
            }
        }

    private fun fallback() = ExtractedFields.Unknown(rawText = "", confidence = 0f)

    // ═════════════════════════════════════════════════════════════════════════
    // Merge: overlay NER findings onto regex result without losing regex fields
    // ═════════════════════════════════════════════════════════════════════════

    private fun merge(
        regex: ExtractedFields,
        entities: List<NerEntity>,
        blocks: List<TextBlock>,
    ): ExtractedFields {
        val persons = entities.filter { it.type == NerType.PERSON }
            .sortedByDescending { it.confidence }
        val locations = entities.filter { it.type == NerType.LOCATION }

        return when (regex) {
            is ExtractedFields.AadhaarFront -> regex.copy(
                name = chooseName(regex.name, persons),
            )

            is ExtractedFields.AadhaarBack -> regex.copy(
                address = chooseAddress(regex.address, locations, blocks),
            )

            is ExtractedFields.Pan -> {
                val (name, father) = choosePanNames(
                    regexName = regex.name,
                    regexFather = regex.fatherName,
                    persons = persons,
                    blocks = blocks,
                    rawText = regex.rawText
                )
                regex.copy(name = name, fatherName = father)
            }

            is ExtractedFields.Passport -> regex.copy(
                name = chooseName(regex.name, persons),
            )

            is ExtractedFields.VoterId -> regex.copy(
                name = chooseVoterPrimaryName(
                    regexName = regex.name,
                    fatherName = regex.fatherName,
                    spouseName = regex.spouseName,
                    persons = persons,
                    rawText = regex.rawText
                ),
                address = chooseAddress(regex.address, locations, blocks),
            )

            is ExtractedFields.DrivingLicence -> regex.copy(
                name = chooseName(regex.name, persons),
                address = chooseAddress(regex.address, locations, blocks),
            )

            is ExtractedFields.Unknown -> regex
        }
    }

    /**
     * If regex produced no name, take the highest-confidence NER PERSON.
     * If both produced names and they disagree by > Levenshtein 2, prefer the
     * one with the higher composite score (NER confidence × Indian-name score).
     */
    private fun chooseName(regexName: String?, persons: List<NerEntity>): String? {
        if (persons.isEmpty()) return regexName
        val topNer = persons.first()
        val topNerName = formatNameDisplay(sanitizeName(topNer.text))

        if (regexName == null) return topNerName
        if (levenshtein(regexName.normName(), topNerName.normName()) <= 2) return regexName

        val regexScore = indianNameScore(regexName)
        val nerScore = topNer.confidence * 0.6f + indianNameScore(topNerName) * 0.4f
        return if (nerScore > regexScore + 0.1f) topNerName else regexName
    }

    private fun chooseVoterPrimaryName(
        regexName: String?,
        fatherName: String?,
        spouseName: String?,
        persons: List<NerEntity>,
        rawText: String
    ): String? {
        val safeRegexName = regexName?.takeUnless { isGovernmentLikeName(it) }
        val relationalNames = setOfNotNull(fatherName?.normName(), spouseName?.normName())
        val filtered = persons.filterNot { entity ->
            val personNorm = formatNameDisplay(sanitizeName(entity.text)).normName()
            personNorm in relationalNames ||
                isRelationalPersonEntity(entity, rawText) ||
                isGovernmentLikeName(personNorm)
        }
        return chooseName(safeRegexName, filtered)
    }

    private fun isRelationalPersonEntity(entity: NerEntity, rawText: String): Boolean {
        val start = entity.startChar.coerceAtLeast(0)
        val prefixStart = (start - 32).coerceAtLeast(0)
        val prefix = rawText.substring(prefixStart, start).lowercase()
        return prefix.contains("father") ||
            prefix.contains("mother") ||
            prefix.contains("husband") ||
            prefix.contains("wife") ||
            prefix.contains("guardian") ||
            prefix.contains("s/o") ||
            prefix.contains("d/o") ||
            prefix.contains("w/o") ||
            prefix.contains("h/o") ||
            prefix.contains("c/o")
    }

    /**
     * PAN needs both holder and father. If NER found ≥ 2 PERSON entities, use
     * their spatial Y position to resolve holder (higher = smaller Y) vs father.
     */
    private fun choosePanNames(
        regexName: String?,
        regexFather: String?,
        persons: List<NerEntity>,
        blocks: List<TextBlock>,
        rawText: String,
    ): Pair<String?, String?> {
        if (regexName != null && regexFather != null &&
            levenshtein(regexName.normName(), regexFather.normName()) > 2
        ) {
            // PAN layouts are strongly ordered; if regex already found both fields,
            // prefer that pair and use NER only when a field is missing.
            return regexName to regexFather
        }

        val filteredPersons = persons.filterNot { entity ->
            val cleaned = formatNameDisplay(sanitizeName(entity.text))
            isGovernmentLikeName(cleaned) ||
                isLikelyHeaderEntity(entity, rawText) ||
                isPanLabelLikeName(cleaned)
        }

        if (filteredPersons.size < 2) {
            // Single or no NER PERSON — fall through to regex, optionally topping up holder
            val holder = chooseName(regexName, filteredPersons)
            return holder to regexFather
        }

        val topTwo = filteredPersons.take(2).map { e ->
            val midY = nerEntityMidY(e, blocks) ?: Int.MAX_VALUE
            Triple(formatNameDisplay(sanitizeName(e.text)), midY, e.confidence)
        }.sortedBy { it.second }

        val holder = topTwo[0].first
        val father = topTwo[1].first

        // Protect against regex disagreeing strongly — if regex is very confident
        // (labeled), regex wins.
        val finalHolder = if (regexName != null && levenshtein(regexName.normName(), holder.normName()) > 3) {
            regexName
        } else holder
        val finalFather = if (regexFather != null && levenshtein(regexFather.normName(), father.normName()) > 3) {
            regexFather
        } else father

        return finalHolder to finalFather
    }

    private fun isLikelyHeaderEntity(entity: NerEntity, rawText: String): Boolean {
        val start = entity.startChar.coerceAtLeast(0)
        val prefixStart = (start - 64).coerceAtLeast(0)
        val prefix = rawText.substring(prefixStart, start).lowercase()
        return prefix.contains("government") ||
            prefix.contains("govt") ||
            prefix.contains("ministry") ||
            prefix.contains("election commission") ||
            prefix.contains("income tax")
    }

    private fun isGovernmentLikeName(name: String): Boolean {
        val lower = name.lowercase()
        val signals = listOf(
            "government", "govt", "india", "bharat",
            "election", "commiss", "commission", "commissioner", "commision",
            "income", "tax", "department", "ministry", "authority", "uidai"
        )
        val hitCount = signals.count { lower.contains(it) }
        return hitCount >= 2
    }

    /**
     * Clusters LOCATION entities that fall within the same block or adjacent
     * blocks (Y-wise), returning the joined text as an address.
     */
    private fun chooseAddress(
        regexAddr: String?,
        locations: List<NerEntity>,
        blocks: List<TextBlock>,
    ): String? {
        if (locations.size < 2) return regexAddr

        val blockYs = locations.mapNotNull { loc ->
            val block = blocks.find { b -> b.text.contains(loc.text) }
            val y = block?.boundingBox?.let { (it.top + it.bottom) / 2 }
            if (y != null) loc to y else null
        }.sortedBy { it.second }

        if (blockYs.size < 2) return regexAddr

        // Cluster by vertical proximity
        val clustered = blockYs.map { it.first.text }.distinct()
        val nerAddress = clustered.joinToString(", ")

        // Prefer regex if it's longer and contains a PIN; NER is for gap filling
        return when {
            regexAddr != null && regexAddr.length >= nerAddress.length -> regexAddr
            regexAddr != null && regexAddr.contains(Regex("""\d{6}""")) -> regexAddr
            else -> nerAddress
        }
    }

    private fun nerEntityMidY(entity: NerEntity, blocks: List<TextBlock>): Int? {
        val block = blocks.firstOrNull { it.text.contains(entity.text) } ?: return null
        return block.boundingBox?.let { (it.top + it.bottom) / 2 }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Group ID (retained from original helper)
    // ═════════════════════════════════════════════════════════════════════════

    fun buildAadhaarGroupId(name: String?, aadhaarNumber: String?): String? {
        val normName = name?.trim()?.lowercase()
            ?.replace("\\s+".toRegex(), "_")?.replace("[^a-z_]".toRegex(), "")
            ?.takeIf { it.length >= 3 }
        val digits = aadhaarNumber?.filter { it.isDigit() }
        val full12 = digits?.takeIf { it.length == 12 }
        val last4 = digits?.takeLast(4)?.takeIf { it.length == 4 }
        val numHash = full12?.let { secureHelper.hashAadhaarNumber(it) }
        val l4Hash = last4?.let { secureHelper.hashLast4(digits!!) }
        return when {
            normName != null && numHash != null -> "ag_${normName}_$numHash"
            numHash != null -> "ag_n_$numHash"
            normName != null && l4Hash != null -> "ag_${normName}_l4_$l4Hash"
            l4Hash != null -> "ag_l4_$l4Hash"
            normName != null -> "ag_name_$normName"
            else -> null
        }
    }
}
