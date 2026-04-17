// data/ocr/ner/NerExtractor.kt
package com.example.docscanner.data.ocr.ner

import com.example.docscanner.domain.model.NerEntity

/**
 * Produces named entity spans from OCR text. Implementations must be safe to
 * call from a background dispatcher and must never perform network I/O.
 */
interface NerExtractor {
    suspend fun extract(text: String): List<NerEntity>
}