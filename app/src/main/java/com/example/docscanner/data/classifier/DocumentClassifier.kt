package com.example.docscanner.data.classifier

import android.content.Context
import android.graphics.Bitmap
import com.example.docscanner.domain.model.DocClassType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class DocumentClassifier(context: Context) {

    companion object {
        private const val MODEL_FILE = "aadhaar_model_quantized.tflite"
        private const val INPUT_SIZE = 224
        private const val PIXEL_SIZE = 3
        private const val CONFIDENCE_THRESHOLD = 0.6f

        // Model output: [1, 2] → index 0 = Other, index 1 = Aadhaar
        private val LABELS = listOf(
            DocClassType.OTHER,      // output index 0
            DocClassType.AADHAAR     // output index 1
        )
    }

    private val interpreter: Interpreter

    init {
        val model = loadModelFile(context)
        val options = Interpreter.Options().apply { setNumThreads(2) }
        interpreter = Interpreter(model, options)
    }

    fun classify(bitmap: Bitmap): DocClassType {
        val inputBuffer = preprocessBitmap(bitmap)
        val output = Array(1) { FloatArray(LABELS.size) }

        interpreter.run(inputBuffer, output)

        android.util.Log.d("DocClassify", "Raw probabilities: ${output[0].toList()}")

        val probabilities = output[0]
        val maxIdx = probabilities.indices.maxByOrNull { probabilities[it] } ?: return DocClassType.OTHER
        val confidence = probabilities[maxIdx]

        return if (confidence >= CONFIDENCE_THRESHOLD) LABELS[maxIdx] else DocClassType.OTHER
    }

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            byteBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            byteBuffer.putFloat((pixel and 0xFF) / 255.0f)
        }

        if (resized !== bitmap) resized.recycle()
        return byteBuffer
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILE)
        val input = FileInputStream(fd.fileDescriptor)
        return input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    fun close() { interpreter.close() }
}