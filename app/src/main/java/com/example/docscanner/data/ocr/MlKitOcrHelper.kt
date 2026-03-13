package com.example.docscanner.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * ML Kit OCR — replaces TesseractOcrHelper.
 *
 * Key differences from Tesseract:
 * - No tessdata file to ship in assets/ (delete assets/tessdata/eng.traineddata)
 * - No grayscale preprocessing needed — ML Kit handles it internally
 * - On-device, works offline, no API key required
 * - Significantly faster than Tesseract on modern devices
 *
 * The recognizer is created once and reused (it's thread-safe and cheap to hold).
 */
@Singleton
class MlKitOcrHelper @Inject constructor(context: Context) {

    // Lazy so it's only created when first used
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Extract text from a bitmap.
     *
     * Drop-in replacement for TesseractOcrHelper.extractText().
     * Same signature: takes a Bitmap, returns Result<String>.
     *
     * No preprocessing needed — ML Kit handles skew, noise,
     * contrast, and orientation automatically.
     *
     * @param bitmap The document image (cropped + perspective-corrected)
     * @return Result.success(text) or Result.failure(exception)
     */
    suspend fun extractText(bitmap: Bitmap): Result<String> =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // visionText.text = full extracted string
                    // visionText.textBlocks = structured blocks (paragraphs, lines, words)
                    val text = visionText.text.trim()
                    continuation.resume(Result.success(text))
                }
                .addOnFailureListener { exception ->
                    continuation.resume(Result.failure(exception))
                }

            // If the coroutine is cancelled, close the recognizer gracefully
            continuation.invokeOnCancellation {
                // recognizer is reused — don't close it here
            }
        }

    /**
     * Extract structured text with block/line/word positions.
     *
     * Use this if you need bounding boxes (e.g., to highlight text on screen).
     *
     * @param bitmap The document image
     * @return List of (text, boundingBox) pairs per text block
     */
    suspend fun extractStructuredText(bitmap: Bitmap): Result<List<TextBlock>> =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val blocks = visionText.textBlocks.map { block ->
                        TextBlock(
                            text = block.text,
                            lines = block.lines.map { line ->
                                TextLine(
                                    text = line.text,
                                    boundingBox = line.boundingBox
                                )
                            },
                            boundingBox = block.boundingBox
                        )
                    }
                    continuation.resume(Result.success(blocks))
                }
                .addOnFailureListener { exception ->
                    continuation.resume(Result.failure(exception))
                }
        }

    /**
     * Release the recognizer when no longer needed (e.g., app close).
     * Not strictly required since ML Kit manages its own lifecycle,
     * but good practice if you want deterministic cleanup.
     */
    fun close() {
        recognizer.close()
    }
}

/** Structured output types for extractStructuredText() */
data class TextBlock(
    val text: String,
    val lines: List<TextLine>,
    val boundingBox: android.graphics.Rect?
)

data class TextLine(
    val text: String,
    val boundingBox: android.graphics.Rect?
)