package com.example.docscanner.data.masking

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import com.example.docscanner.data.ocr.MlKitOcrHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Masks the first 5 characters of a PAN number found in a scanned PAN card image.
 *
 * PAN format: ABCDE1234F (5 letters + 4 digits + 1 letter)
 * After masking: XXXXX1234F
 *
 * Only invoke this for DocClassType.PAN_CARD.
 */
@Singleton
class PanMasker @Inject constructor(
    private val ocrHelper: MlKitOcrHelper
) {
    companion object {
        private const val TAG = "PanMasker"

        // Matches a valid PAN: 5 uppercase letters + 4 digits + 1 uppercase letter
        private val PAN_REGEX = Regex("""[A-Z]{5}[0-9]{4}[A-Z]""")
    }

    /**
     * Finds the PAN number on the bitmap via OCR and masks the first 5 characters.
     * Returns a new masked bitmap. Original is not modified.
     */
    suspend fun mask(bitmap: Bitmap): Bitmap {
        return try {
            val result = ocrHelper.extractStructuredText(bitmap).getOrNull()
            if (result == null) {
                Log.w(TAG, "OCR failed — returning original")
                return bitmap
            }

            val maskedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(maskedBitmap)
            var totalMasked = 0

            for (block in result) {
                for (line in block.lines) {
                    if (line.boundingBox == null) continue
                    val lineBox = line.boundingBox!!
                    val lineText = line.text.trim()

                    val matches = PAN_REGEX.findAll(lineText)
                    for (match in matches) {
                        val panNumber = match.value           // e.g. "ABCDE1234F"
                        val first5 = panNumber.take(5)        // "ABCDE"
                        val last5 = panNumber.takeLast(5)     // "1234F"

                        val totalChars = lineText.length.coerceAtLeast(1)
                        val lineWidth = lineBox.width().toFloat()
                        val lineLeft = lineBox.left.toFloat()

                        // Pixel range of the first 5 chars of PAN within the line
                        val panStart = match.range.first
                        val maskEnd = panStart + 5  // first 5 chars only

                        val startFrac = panStart.toFloat() / totalChars
                        val endFrac = maskEnd.toFloat() / totalChars

                        val maskLeft = lineLeft + lineWidth * startFrac
                        val maskRight = lineLeft + lineWidth * endFrac

                        val padX = (maskRight - maskLeft) * 0.05f
                        val padY = lineBox.height() * 0.1f

                        val rect = RectF(
                            (maskLeft - padX).coerceAtLeast(0f),
                            (lineBox.top - padY).coerceAtLeast(0f),
                            (maskRight + padX).coerceAtMost(maskedBitmap.width.toFloat()),
                            lineBox.bottom + padY
                        )

                        // Black background
                        canvas.drawRect(rect, Paint().apply {
                            color = Color.BLACK
                            style = Paint.Style.FILL
                        })

                        // "XXXXX" overlay
                        canvas.drawText(
                            "XXXXX",
                            (rect.left + rect.right) / 2f,
                            rect.top + rect.height() * 0.72f,
                            Paint().apply {
                                color = Color.WHITE
                                textSize = lineBox.height() * 0.6f
                                typeface = Typeface.DEFAULT_BOLD
                                isAntiAlias = true
                                textAlign = Paint.Align.CENTER
                            }
                        )

                        totalMasked++
                        Log.d(TAG, "Masked PAN: ${first5}XXXXX → showing $last5")
                    }
                }
            }

            Log.d(TAG, "Total PAN instances masked: $totalMasked")
            if (totalMasked == 0) {
                Log.w(TAG, "No PAN number found on image")
                return bitmap
            }
            maskedBitmap

        } catch (e: Exception) {
            Log.e(TAG, "Masking failed", e)
            bitmap
        }
    }
}