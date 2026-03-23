package com.example.docscanner.domain.model

import android.graphics.Bitmap
import android.graphics.PointF

enum class DocClassType(val displayName: String) {
    AADHAAR("Aadhaar"),
    PAN("PAN Card"),
    VOTER_ID("Voter ID"),
    DRIVING_LICENSE("DL"),
    PASSPORT("Passport"),
    OTHER("Other")
}

data class ScannedPage(
    val id: String = System.currentTimeMillis().toString(),
    val originalBitmap: Bitmap,
    val croppedBitmap: Bitmap? = null,
    val enhancedBitmap: Bitmap? = null,
    val corners: DocumentCorners? = null,
    val filterType: FilterType = FilterType.ORIGINAL,
    val docClassType: DocClassType? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // OCR fields
    val extractedName: String? = null,
    val extractedId: String? = null,
    val ocrRawText: String? = null
) {
    val displayBitmap: Bitmap get() = enhancedBitmap ?: croppedBitmap ?: originalBitmap
}

enum class FolderExportType { PDF, IMAGES }

data class DocumentCorners(
    val topLeft: PointF, val topRight: PointF,
    val bottomLeft: PointF, val bottomRight: PointF
) {
    fun toList(): List<PointF> = listOf(topLeft, topRight, bottomRight, bottomLeft)
    companion object {
        fun fromList(points: List<PointF>): DocumentCorners {
            require(points.size == 4) { "Exactly 4 corners required" }
            return DocumentCorners(topLeft = points[0], topRight = points[1], bottomRight = points[2], bottomLeft = points[3])
        }
    }
}

enum class FilterType(val displayName: String) { ORIGINAL("Original"), ENHANCED("Enhanced"), GRAYSCALE("Grayscale"), BLACK_WHITE("B&W"), MAGIC("Magic") }
enum class ExportFormat(val displayName: String, val extension: String) { PDF("PDF Document", "pdf"), JPEG("JPEG Image", "jpg"), PNG("PNG Image", "png") }

data class Folder(val id: String = System.currentTimeMillis().toString(), val name: String, val icon: String = "📄", val exportType: FolderExportType = FolderExportType.PDF, val createdAt: Long = System.currentTimeMillis(), val documentCount: Int = 0)

data class Document(
    val id: String = System.currentTimeMillis().toString(), val folderId: String, val name: String, val pageCount: Int,
    val thumbnailPath: String? = null, val pdfPath: String? = null, val docClassLabel: String? = null,
    val createdAt: Long = System.currentTimeMillis(), val mergedFromDocumentIds: String? = null, val isMergedSource: Boolean = false
) {
    val isMergedPdf: Boolean get() = !mergedFromDocumentIds.isNullOrEmpty()
    val sourceDocumentIds: List<String> get() = mergedFromDocumentIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
}

/**
 * A group of pages that belong to the same logical document.
 * Created by auto-grouping after batch scan.
 */
data class DocumentGroup(
    val id: String = System.currentTimeMillis().toString(),
    val pages: MutableList<ScannedPage>,
    val docType: DocClassType,
    val personName: String?,
    val documentId: String?,
    val suggestedFileName: String,
    val userLabel: String? = null
) {
    val pageCount: Int get() = pages.size
    val thumbnail: Bitmap get() = pages.first().displayBitmap
    val isKnownType: Boolean get() = docType != DocClassType.OTHER
    val displayName: String get() = userLabel ?: suggestedFileName
}