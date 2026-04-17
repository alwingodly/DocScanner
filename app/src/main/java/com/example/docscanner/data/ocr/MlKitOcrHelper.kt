// data/ocr/MlKitOcrClient.kt
package com.example.docscanner.data.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.docscanner.data.ocr.fusion.ExtractionFusion
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.ExtractedFields as DomainExtractedFields
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class TextBlock(
    val text: String,
    val lines: List<TextLine>,
    val boundingBox: Rect?,
)

data class TextLine(
    val text: String,
    val boundingBox: Rect?,
)

@Singleton
class MlKitOcrClient @Inject constructor() {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extractText(bitmap: Bitmap): Result<String> =
        suspendCancellableCoroutine { cont ->
            val task = recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { if (cont.isActive) cont.resume(Result.success(it.text.trim())) }
                .addOnFailureListener { if (cont.isActive) cont.resume(Result.failure(it)) }
            cont.invokeOnCancellation { /* ML Kit has no cancel handle; prevent resume */ }
        }

    suspend fun extractBlocks(bitmap: Bitmap): Result<List<TextBlock>> =
        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { vt ->
                    if (!cont.isActive) return@addOnSuccessListener
                    val blocks = vt.textBlocks.map { b ->
                        TextBlock(
                            text = b.text,
                            lines = b.lines.map { l -> TextLine(l.text, l.boundingBox) },
                            boundingBox = b.boundingBox,
                        )
                    }
                    cont.resume(Result.success(blocks))
                }
                .addOnFailureListener { if (cont.isActive) cont.resume(Result.failure(it)) }
            cont.invokeOnCancellation { }
        }

    fun close() = recognizer.close()
}

data class OcrExtractedFields(
    val name: String? = null,
    val idNumber: String? = null,
    val dob: String? = null,
    val gender: String? = null,
    val address: String? = null,
    val rawText: String = "",
    val confidence: Float = 0f,
    val details: List<com.example.docscanner.domain.model.DocumentDetail> = emptyList(),
)

@Singleton
class MlKitOcrHelper @Inject constructor(
    private val ocrClient: MlKitOcrClient,
    private val extractionFusion: ExtractionFusion,
) {
    suspend fun extractText(bitmap: Bitmap): Result<String> = ocrClient.extractText(bitmap)

    suspend fun extractStructuredText(bitmap: Bitmap): Result<List<TextBlock>> =
        ocrClient.extractBlocks(bitmap)

    suspend fun extractFields(bitmap: Bitmap, docType: DocClassType): OcrExtractedFields {
        val extracted = extractionFusion.extract(bitmap, docType)
        return extracted.toOcrExtractedFields()
    }

    fun buildAadhaarGroupId(name: String?, aadhaarNumber: String?): String? =
        extractionFusion.buildAadhaarGroupId(name, aadhaarNumber)
}

private fun DomainExtractedFields.toOcrExtractedFields(): OcrExtractedFields = when (this) {
    is DomainExtractedFields.AadhaarFront -> OcrExtractedFields(
        name = name,
        idNumber = idNumber,
        dob = dob,
        gender = gender,
        rawText = rawText,
        confidence = confidence,
        details = details
    )
    is DomainExtractedFields.AadhaarBack -> OcrExtractedFields(
        idNumber = idNumber,
        address = address,
        rawText = rawText,
        confidence = confidence,
        details = details
    )
    is DomainExtractedFields.Pan -> OcrExtractedFields(
        name = name,
        idNumber = idNumber,
        dob = dob,
        rawText = rawText,
        confidence = confidence,
        details = details
    )
    is DomainExtractedFields.Passport -> OcrExtractedFields(
        name = name,
        idNumber = idNumber,
        dob = dob,
        gender = gender,
        rawText = rawText,
        confidence = confidence,
        details = details
    )
    is DomainExtractedFields.VoterId -> OcrExtractedFields(
        name = name,
        idNumber = idNumber,
        dob = dob ?: age,
        gender = gender,
        address = address,
        rawText = rawText,
        confidence = confidence,
        details = details
    )
    is DomainExtractedFields.DrivingLicence -> OcrExtractedFields(
        name = name,
        idNumber = idNumber,
        dob = dob,
        gender = gender,
        address = address,
        rawText = rawText,
        confidence = confidence,
        details = details
    )
    is DomainExtractedFields.Unknown -> OcrExtractedFields(
        rawText = rawText,
        confidence = confidence,
        details = details
    )
}