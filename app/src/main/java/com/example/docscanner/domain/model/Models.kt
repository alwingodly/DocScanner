package com.example.docscanner.domain.model

import android.graphics.Bitmap
import android.graphics.PointF

/**
 * Document classification types from TFLite model.
 *
 * Named DocClassType (not DocumentType) to avoid clash with
 * com.example.docscanner.presentation.viewer.DocumentType (PDF/IMAGE).
 *
 * ⚠️ The label-to-index mapping is in DocumentClassifier.LABELS.
 */
enum class DocClassType(val displayName: String) {
    AADHAAR("Aadhaar"),
    PAN("PAN Card"),
    VOTER_ID("Voter ID"),
    DRIVING_LICENSE("DL"),
    PASSPORT("Passport"),
    OTHER("Document")
}

/**
 * Represents a single scanned page.
 *
 * A page goes through this lifecycle:
 *   1. Camera captures → originalBitmap (raw photo)
 *   2. Edge detection  → corners (4 points of document boundary)
 *   3. Crop & warp     → croppedBitmap (flattened top-down view)
 *   4. Filter applied   → enhancedBitmap (B&W, grayscale, etc.)
 *   5. Classification   → docClassType (Aadhaar, PAN, etc.)
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
    val docClassType: DocClassType? = null,       // null = not yet classified
    val createdAt: Long = System.currentTimeMillis()
) {
    val displayBitmap: Bitmap
        get() = enhancedBitmap ?: croppedBitmap ?: originalBitmap
}

enum class FolderExportType { PDF, IMAGES }

/**
 * Four corners of a detected document.
 */
data class DocumentCorners(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomLeft: PointF,
    val bottomRight: PointF
) {
    fun toList(): List<PointF> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
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

enum class FilterType(val displayName: String) {
    ORIGINAL("Original"),
    ENHANCED("Enhanced"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("B&W"),
    MAGIC("Magic")
}

enum class ExportFormat(val displayName: String, val extension: String) {
    PDF("PDF Document", "pdf"),
    JPEG("JPEG Image", "jpg"),
    PNG("PNG Image", "png")
}

// ─── Folder ───────────────────────────────────────────
data class Folder(
    val id: String = System.currentTimeMillis().toString(),
    val name: String,
    val icon: String = "📄",
    val exportType: FolderExportType = FolderExportType.PDF,
    val createdAt: Long = System.currentTimeMillis(),
    val documentCount: Int = 0
)

// ─── Document (saved scan inside a folder) ────────────
data class Document(
    val id: String = System.currentTimeMillis().toString(),
    val folderId: String,
    val name: String,
    val pageCount: Int,
    val thumbnailPath: String? = null,
    val pdfPath: String? = null,
    val docClassLabel: String? = null,    // store classification label for saved docs
    val createdAt: Long = System.currentTimeMillis()
)