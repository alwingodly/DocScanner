package com.example.docscanner.data.classifier

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.docscanner.domain.model.DocClassType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class DocumentClassifier(context: Context) {

    companion object {
        private const val TAG = "DocClassifier"
        private const val MODEL_FILE = "document_classifier_v3.tflite"
        private const val INPUT_SIZE = 224
        private const val PIXEL_SIZE = 3

        // Minimum confidence to accept a predicted class.
        // Below this threshold → DocClassType.OTHER regardless of predicted class.
        private const val CONFIDENCE_THRESHOLD = 0.70f

        // Minimum confidence for second-best class when top prediction is unknown.
        // Allows fallback to a specific class if it still has reasonable confidence.
        private const val UNKNOWN_FALLBACK_THRESHOLD = 0.40f

        // Alphabetical order — must match training label order exactly.
        // Model output shape: [1, 6]
        // 0 = aadhaar_back
        // 1 = aadhaar_front
        // 2 = pan_card
        // 3 = passport
        // 4 = unknown
        // 5 = voter_id
        private val LABELS = listOf(
            DocClassType.AADHAAR,   // 0 → aadhaar_back
            DocClassType.AADHAAR,   // 1 → aadhaar_front
            DocClassType.PAN,       // 2 → pan_card
            DocClassType.PASSPORT,  // 3 → passport
            DocClassType.OTHER,     // 4 → unknown
            DocClassType.VOTER_ID   // 5 → voter_id
        )
    }

    private val interpreter: Interpreter?

    init {
        interpreter = try {
            val model   = loadModelFile(context)
            val options = Interpreter.Options().apply { setNumThreads(2) }
            Interpreter(model, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model. Classification disabled.", e)
            null
        }
    }

    fun classify(bitmap: Bitmap): DocClassType {
        val interp = interpreter ?: return DocClassType.OTHER

        return try {
            // Step 1 — Preprocess image
            val inputBuffer = preprocessBitmap(bitmap)

            // Step 2 — Run inference
            val output = Array(1) { FloatArray(LABELS.size) }
            interp.run(inputBuffer, output)

            // Step 3 — Read probability vector
            val probabilities = output[0]
            Log.d(TAG, "Raw probabilities: ${probabilities.toList()}")

            // Step 4 — Find highest confidence class
            val sortedIndices = probabilities.indices.sortedByDescending { probabilities[it] }
            var maxIdx        = sortedIndices.first()
            var confidence    = probabilities[maxIdx]
            var bestLabel     = LABELS[maxIdx]

            Log.d(TAG, "Top prediction: $bestLabel ($confidence)")

            // Step 5 — Unknown fallback.
            // If top prediction is unknown, check second-best specific class.
            // Use it only if confidence is reasonable — avoids forcing a wrong label.
            if (bestLabel == DocClassType.OTHER && sortedIndices.size > 1) {
                val secondIdx        = sortedIndices[1]
                val secondLabel      = LABELS[secondIdx]
                val secondConfidence = probabilities[secondIdx]

                if (secondLabel != DocClassType.OTHER &&
                    secondConfidence >= UNKNOWN_FALLBACK_THRESHOLD) {
                    maxIdx     = secondIdx
                    confidence = secondConfidence
                    bestLabel  = secondLabel
                    Log.d(TAG, "Unknown fallback → $bestLabel ($confidence)")
                }
            }

            // Step 6 — Confidence threshold gate.
            // If winning score is below threshold, return OTHER.
            // Prevents low-confidence wrong labels.
            if (confidence < CONFIDENCE_THRESHOLD) {
                Log.d(TAG, "Below threshold ($confidence < $CONFIDENCE_THRESHOLD) → OTHER")
                return DocClassType.OTHER
            }

            Log.d(TAG, "Final: $bestLabel ($confidence)")
            bestLabel

        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            DocClassType.OTHER
        }
    }

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val resized    = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            // Send raw [0, 255] float values — DO NOT divide by 255.
            // The TFLite model has MobileNetV2 preprocess_input baked into
            // its graph, so it applies (pixel / 127.5 - 1.0) internally.
            byteBuffer.putFloat(((pixel shr 16) and 0xFF).toFloat()) // R
            byteBuffer.putFloat(((pixel shr 8)  and 0xFF).toFloat()) // G
            byteBuffer.putFloat((pixel           and 0xFF).toFloat()) // B
        }

        if (resized !== bitmap) resized.recycle()
        return byteBuffer
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fd    = context.assets.openFd(MODEL_FILE)
        val input = FileInputStream(fd.fileDescriptor)
        return input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    fun close() { interpreter?.close() }
}