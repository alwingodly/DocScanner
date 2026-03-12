package com.example.docscanner.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TesseractOcrHelper(private val context: Context) {

    private val tessDataPath: String by lazy {
        val dir = File(context.filesDir, "tessdata")
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, "eng.traineddata")
        if (!target.exists()) {
            context.assets.open("tessdata/eng.traineddata").use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        }
        // TessBaseAPI expects the *parent* of tessdata/, not tessdata/ itself
        context.filesDir.absolutePath
    }

    suspend fun extractText(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        val api = TessBaseAPI()
        try {
            val inited = api.init(tessDataPath, "eng")
            if (!inited) return@withContext Result.failure(Exception("Tesseract init failed"))

            // Tune these for better results
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD  // auto detect orientation
            api.setVariable("tessedit_char_whitelist",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.,!?@#$%&*()-+/:;\"' \n")
            api.setVariable("tessedit_pageseg_mode", "1")

            // Pre-process: convert to grayscale for better accuracy
            val grayscale = toGrayscale(bitmap)

            api.setImage(grayscale)
            val text = api.utF8Text?.trim() ?: ""
            api.recycle()
            Result.success(text)
        } catch (e: Exception) {
            api.recycle()
            Result.failure(e)
        }
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val gray = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(gray)
        val paint = android.graphics.Paint()
        val matrix = android.graphics.ColorMatrix()
        matrix.setSaturation(0f)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return gray
    }
}