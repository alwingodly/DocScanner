package com.example.docscanner.domain.model

import android.graphics.Bitmap
import android.graphics.PointF

/**
 * Represents a single scanned page.
 *
 * A page goes through this lifecycle:
 *   1. Camera captures → originalBitmap (raw photo)
 *   2. Edge detection  → corners (4 points of document boundary)
 *   3. Crop & warp     → croppedBitmap (flattened top-down view)
 *   4. Filter applied   → enhancedBitmap (B&W, grayscale, etc.)
 *
 * displayBitmap always returns the most processed version available.
 */
data class ScannedPage(
    val id: String = System.currentTimeMillis().toString(),
    val originalBitmap: Bitmap,
    val croppedBitmap: Bitmap? = null,
    val enhancedBitmap: Bitmap? = null,
    val corners: DocumentCorners? = null,
    val filterType: FilterType = FilterType.ORIGINAL,
    val createdAt: Long = System.currentTimeMillis()
) {
    val displayBitmap: Bitmap
        get() = enhancedBitmap ?: croppedBitmap ?: originalBitmap
}

enum class FolderExportType { PDF, IMAGES }
/**
 * Four corners of a detected document.
 *
 * Why PointF? It stores x,y as floats — we need sub-pixel precision
 * for accurate perspective correction.
 *
 * Corner order matters for perspective transform:
 *   topLeft -------- topRight
 *   |                       |
 *   |     (document)        |
 *   |                       |
 *   bottomLeft --- bottomRight
 */
data class DocumentCorners(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomLeft: PointF,
    val bottomRight: PointF
) {
    /** Convert to list in order: TL, TR, BR, BL (clockwise) */
    fun toList(): List<PointF> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        /** Create from a list of 4 points (TL, TR, BR, BL order) */
        fun fromList(points: List<PointF>): DocumentCorners {
            require(points.size == 4) { "Exactly 4 corners required" }
            return DocumentCorners(
                topLeft = points[0],
                topRight = points[1],
                bottomRight = points[2],
                bottomLeft = points[3]
            )
        }
    }
}

/**
 * Image enhancement filters.
 *
 * ORIGINAL   → no processing, raw cropped image
 * ENHANCED   → CLAHE contrast + sharpening (good for photos/color docs)
 * GRAYSCALE  → simple gray conversion
 * BLACK_WHITE → Otsu's threshold (good for printed text)
 * MAGIC      → Adaptive threshold (best for handwritten + printed text)
 *
 * Each maps to a specific OpenCV operation in DocumentProcessor.
 */
enum class FilterType(val displayName: String) {
    ORIGINAL("Original"),
    ENHANCED("Enhanced"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("B&W"),
    MAGIC("Magic")
}


/**
 * Export format options.
 * PDF uses Android's built-in PdfDocument API (no extra library).
 * JPEG/PNG use standard Bitmap.compress().
 */
enum class ExportFormat(val displayName: String, val extension: String) {
    PDF("PDF Document", "pdf"),
    JPEG("JPEG Image", "jpg"),
    PNG("PNG Image", "png")
}

// ─── Folder ───────────────────────────────────────────
data class Folder(
    val id: String = System.currentTimeMillis().toString(),
    val name: String,
    val icon: String = "📄",      // emoji icon
    val exportType: FolderExportType = FolderExportType.PDF,  // ADD
    val createdAt: Long = System.currentTimeMillis(),
    val documentCount: Int = 0
)


// ─── Document (saved scan inside a folder) ────────────
data class Document(
    val id: String = System.currentTimeMillis().toString(),
    val folderId: String,
    val name: String,
    val pageCount: Int,
    val thumbnailPath: String? = null,   // first page image path
    val pdfPath: String? = null,         // saved PDF path
    val createdAt: Long = System.currentTimeMillis()
)