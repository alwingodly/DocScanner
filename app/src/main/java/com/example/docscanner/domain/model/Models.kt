package com.example.docscanner.domain.model

import android.graphics.Bitmap
import android.graphics.PointF
import org.json.JSONArray
import org.json.JSONObject

enum class DocClassType(val displayName: String) {
    AADHAAR_FRONT("Aadhaar Front"),
    AADHAAR_BACK("Aadhaar Back"),
    PAN("PAN Card"),
    VOTER_ID("Voter ID"),
    PASSPORT("Passport"),
    OTHER("Other");

    val isAadhaar get() = this == AADHAAR_FRONT || this == AADHAAR_BACK
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
    ORIGINAL("Original"), ENHANCED("Enhanced"), GRAYSCALE(
        "Grayscale"
    ),
    BLACK_WHITE("B&W"), MAGIC("Magic")
}

enum class ExportFormat(val displayName: String, val extension: String) {
    PDF(
        "PDF Document",
        "pdf"
    ),
    JPEG("JPEG Image", "jpg"), PNG("PNG Image", "png")
}

data class Folder(
    val id: String = System.currentTimeMillis().toString(),
    val name: String,
    val icon: String = "📄",
    val exportType: FolderExportType = FolderExportType.PDF,
    val createdAt: Long = System.currentTimeMillis(),
    val documentCount: Int = 0,
    val docType: String = "Other"   // ← new
)

data class DocumentDetail(
    val label: String,
    val value: String,
    val multiline: Boolean = false
)

data class Document(
    val id: String = System.currentTimeMillis().toString(),
    val folderId: String,
    val name: String,
    val pageCount: Int,
    val thumbnailPath: String? = null,
    val pdfPath: String? = null,
    val docClassLabel: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val mergedFromDocumentIds: String? = null,
    val isMergedSource: Boolean = false,
    val sessionId: String? = null,
    val aadhaarSide: String? = null,
    val aadhaarGroupId: String? = null,
    val docGroupId: String? = null,         // ← new generic group
    val aadhaarName: String? = null,
    val aadhaarDob: String? = null,
    val aadhaarGender: String? = null,
    val aadhaarMaskedNumber: String? = null,
    val aadhaarAddress: String? = null,
    val extractedDetailsJson: String? = null,
    val ocrRawText: String? = null,
    val passportGroupId: String? = null,
    val passportSide: String? = null,
    val passportHolderName: String? = null,
    val passportNumHash: String? = null,
) {
    val isMergedPdf: Boolean get() = !mergedFromDocumentIds.isNullOrEmpty()
    val sourceDocumentIds: List<String>
        get() = mergedFromDocumentIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    val extractedDetails: List<DocumentDetail>
        get() = parseDocumentDetails(extractedDetailsJson)
}

fun parseDocumentDetails(json: String?): List<DocumentDetail> {
    if (json.isNullOrBlank()) return emptyList()

    return runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val label = item.optString("label").trim()
                val value = item.optString("value").trim()
                if (label.isNotEmpty() && value.isNotEmpty()) {
                    add(
                        DocumentDetail(
                            label = label,
                            value = value,
                            multiline = item.optBoolean("multiline", false)
                        )
                    )
                }
            }
        }
    }.getOrDefault(emptyList())
}

fun List<DocumentDetail>.toDocumentDetailsJson(): String? {
    val normalized = this
        .mapNotNull { detail ->
            val label = detail.label.trim()
            val value = detail.value.trim()
            if (label.isEmpty() || value.isEmpty()) null
            else detail.copy(label = label, value = value)
        }

    if (normalized.isEmpty()) return null

    return JSONArray().apply {
        normalized.forEach { detail ->
            put(
                JSONObject().apply {
                    put("label", detail.label)
                    put("value", detail.value)
                    put("multiline", detail.multiline)
                }
            )
        }
    }.toString()
}
