// data/ocr/extractor/DocumentExtractor.kt
package com.example.docscanner.data.ocr.extractor

import com.example.docscanner.data.ocr.TextBlock
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.DocumentDetail
import com.example.docscanner.domain.model.ExtractedFields

/** Produces a regex-based first-pass extraction. NER fusion happens downstream. */
interface DocumentExtractor {
    val docType: DocClassType
    suspend fun extract(
        blocks: List<TextBlock>,
        rawText: String,
        docHeight: Int,
    ): ExtractedFields
}

internal enum class Zone { HEADER, UPPER, MIDDLE, LOWER, FOOTER }

internal fun zoneOf(top: Int, docH: Int): Zone {
    if (docH == 0) return Zone.MIDDLE
    return when (top.toFloat() / docH) {
        in 0.00f..0.15f -> Zone.HEADER
        in 0.15f..0.40f -> Zone.UPPER
        in 0.40f..0.62f -> Zone.MIDDLE
        in 0.62f..0.82f -> Zone.LOWER
        else -> Zone.FOOTER
    }
}

internal fun estimateDocHeight(blocks: List<TextBlock>): Int =
    blocks.mapNotNull { it.boundingBox?.bottom }.maxOrNull() ?: 0

/** Small DSL for building ordered, deduped detail lists. */
internal class DetailScope {
    private val items = mutableListOf<DocumentDetail>()
    private val labels = mutableSetOf<String>()

    fun f(label: String?, value: String?, multiline: Boolean = false) {
        label ?: return
        val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (!labels.add(label.lowercase())) return
        items += DocumentDetail(label = label, value = v, multiline = multiline)
    }

    fun extra(ds: List<DocumentDetail>) = ds.forEach { f(it.label, it.value, it.multiline) }
    fun build(): List<DocumentDetail> = items.toList()
}

internal fun details(block: DetailScope.() -> Unit): List<DocumentDetail> =
    DetailScope().apply(block).build()