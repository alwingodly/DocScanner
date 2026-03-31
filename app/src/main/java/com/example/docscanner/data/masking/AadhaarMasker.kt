package com.example.docscanner.data.masking

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.example.docscanner.data.ocr.MlKitOcrHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Masks ALL Aadhaar numbers found in a scanned document image.
 *
 * Handles:
 * - Front side: 12-digit Aadhaar number (XXXX XXXX visible last 4)
 * - Back side: same 12-digit number printed again
 * - Combined front+back scans: masks every occurrence
 *
 * Only the last 4 digits remain visible per UIDAI guidelines.
 * VID (16-digit Virtual ID) lines are intentionally left unmasked.
 */
@Singleton
class AadhaarMasker @Inject constructor(
    private val ocrHelper: MlKitOcrHelper
) {
    companion object {
        private const val TAG = "AadhaarMask"
        private val FOUR_DIGIT_GROUP = Regex("""^\d{4}$""")
        private val AADHAAR_12       = Regex("""(\d{4})\s+(\d{4})\s+(\d{4})""")

        // Used only to DETECT and SKIP VID lines — not to mask them
        private val VID_LINE         = Regex("""(\d{4})\s+(\d{4})\s+(\d{4})\s+(\d{4})""")
    }

    /**
     * Mask ALL Aadhaar numbers on the bitmap.
     * Returns a new bitmap with first 8 digits masked (XXXX XXXX xxxx).
     * VID numbers (16-digit) are left untouched.
     * Original bitmap is not modified.
     */
    suspend fun mask(bitmap: Bitmap): Bitmap {
        return try {
            val result = ocrHelper.extractStructuredText(bitmap).getOrNull()
            if (result == null) { Log.w(TAG, "OCR failed"); return bitmap }

            val maskedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas       = Canvas(maskedBitmap)
            var totalMasked  = 0

            // Collect ALL digit word groups with bounding boxes across entire image
            val allDigitWords = mutableListOf<DigitWord>()

            for (block in result) {
                for (line in block.lines) {
                    if (line.boundingBox == null) continue
                    val lineBox  = line.boundingBox!!
                    val lineText = line.text.trim()

                    // Skip VID lines entirely — 16-digit Virtual IDs must NOT be masked.
                    // A VID line also contains a 12-digit sub-match that AADHAAR_12 would
                    // catch, so we must skip before any matching or word-collection.
                    if (VID_LINE.containsMatchIn(lineText)) {
                        Log.d(TAG, "Skipping VID line: $lineText")
                        continue
                    }

                    // Strategy 1: mask full 12-digit Aadhaar numbers found in single lines.
                    // Mask first 2 groups (8 digits), leave last 4 visible.
                    val aadhaarMatches = AADHAAR_12.findAll(lineText).toList()
                    for (match in aadhaarMatches) {
                        val masked = maskGroupsInLine(
                            canvas       = canvas,
                            lineText     = lineText,
                            lineBox      = lineBox,
                            matchRange   = match.range,
                            groupsToMask = 2,
                            totalGroups  = 3,
                            bitmapWidth  = maskedBitmap.width
                        )
                        if (masked) totalMasked++
                    }

                    // Collect individual 4-digit groups for Strategy 2
                    val words      = lineText.split("\\s+".toRegex())
                    val totalChars = lineText.length.coerceAtLeast(1)
                    var charOffset = 0
                    for (word in words) {
                        val wordStart = lineText.indexOf(word, charOffset)
                        if (wordStart < 0) continue
                        if (FOUR_DIGIT_GROUP.matches(word)) {
                            val sf = wordStart.toFloat() / totalChars
                            val ef = (wordStart + word.length).toFloat() / totalChars
                            allDigitWords.add(
                                DigitWord(
                                    text    = word,
                                    box     = RectF(
                                        lineBox.left + lineBox.width() * sf,
                                        lineBox.top.toFloat(),
                                        lineBox.left + lineBox.width() * ef,
                                        lineBox.bottom.toFloat()
                                    ),
                                    centerY = (lineBox.top + lineBox.bottom) / 2f
                                )
                            )
                        }
                        charOffset = wordStart + word.length
                    }
                }
            }

            // Strategy 2: find groups of exactly 3 consecutive 4-digit words on the same
            // line. This catches Aadhaar numbers that ML Kit splits across words but
            // didn't form a full-line Strategy 1 match.
            // Groups of 4 are NOT masked here — those are VID numbers.
            if (totalMasked == 0) {
                val lineGroups = groupByLine(allDigitWords)
                for (group in lineGroups) {
                    val sorted = group.sortedBy { it.box.left }
                    when {
                        sorted.size >= 4 -> {
                            // 4-group sequences are VID numbers — skip entirely
                            Log.d(TAG, "Skipping VID group (strategy 2): ${sorted.map { it.text }}")
                        }
                        sorted.size == 3 -> {
                            // Aadhaar: mask first 2 groups, show last 1
                            maskDigitWord(canvas, sorted[0])
                            maskDigitWord(canvas, sorted[1])
                            totalMasked++
                            Log.d(TAG, "Masked Aadhaar (strategy 2): ${sorted.map { it.text }}")
                        }
                    }
                }
            }

            Log.d(TAG, "Total masked instances: $totalMasked")
            if (totalMasked == 0) { Log.w(TAG, "No Aadhaar numbers found to mask"); return bitmap }
            maskedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Masking failed", e)
            bitmap
        }
    }

    /**
     * Mask specific digit groups within a line that contains an Aadhaar pattern.
     * @param groupsToMask How many 4-digit groups to mask from the start
     * @param totalGroups  Total groups in the pattern (3 for Aadhaar)
     */
    private fun maskGroupsInLine(
        canvas       : Canvas,
        lineText     : String,
        lineBox      : Rect,
        matchRange   : IntRange,
        groupsToMask : Int,
        totalGroups  : Int,
        bitmapWidth  : Int
    ): Boolean {
        val totalChars = lineText.length.coerceAtLeast(1)
        val lineWidth  = lineBox.width().toFloat()
        val lineLeft   = lineBox.left.toFloat()

        val matchText   = lineText.substring(matchRange)
        val digitGroups = matchText
            .split("\\s+".toRegex())
            .filter { it.length == 4 && it.all { c -> c.isDigit() } }
        if (digitGroups.size < totalGroups) return false

        val maskStartChar = matchRange.first
        val groupTexts    = digitGroups.take(groupsToMask)
        val lastMaskGroup = groupTexts.last()
        val endOfMaskGroup =
            lineText.indexOf(lastMaskGroup, maskStartChar) + lastMaskGroup.length

        val startFrac = maskStartChar.toFloat() / totalChars
        val endFrac   = endOfMaskGroup.toFloat() / totalChars

        val maskLeft  = lineLeft + lineWidth * startFrac
        val maskRight = lineLeft + lineWidth * endFrac

        val padX = (maskRight - maskLeft) * 0.05f
        val padY = lineBox.height() * 0.1f

        val rect = RectF(
            (maskLeft  - padX).coerceAtLeast(0f),
            (lineBox.top - padY).coerceAtLeast(0f),
            (maskRight + padX).coerceAtMost(bitmapWidth.toFloat()),
            lineBox.bottom + padY
        )

        val bgPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        canvas.drawRect(rect, bgPaint)

        val maskLabel = (1..groupsToMask).joinToString(" ") { "XXXX" }
        val textPaint = Paint().apply {
            color     = Color.WHITE
            textSize  = lineBox.height() * 0.6f
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            maskLabel,
            (rect.left + rect.right) / 2f,
            rect.top + rect.height() * 0.72f,
            textPaint
        )

        Log.d(TAG, "Masked $groupsToMask groups: ${groupTexts.joinToString(" ")}")
        return true
    }

    private fun maskDigitWord(canvas: Canvas, word: DigitWord) {
        val padX = word.box.width()  * 0.08f
        val padY = word.box.height() * 0.12f
        val rect = RectF(
            word.box.left   - padX,
            word.box.top    - padY,
            word.box.right  + padX,
            word.box.bottom + padY
        )

        canvas.drawRect(
            rect,
            Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        )
        canvas.drawText(
            "XXXX",
            (rect.left + rect.right) / 2f,
            rect.top + rect.height() * 0.73f,
            Paint().apply {
                color       = Color.WHITE
                textSize    = word.box.height() * 0.65f
                typeface    = android.graphics.Typeface.DEFAULT_BOLD
                isAntiAlias = true
                textAlign   = Paint.Align.CENTER
            }
        )
    }

    private fun groupByLine(words: List<DigitWord>): List<List<DigitWord>> {
        if (words.isEmpty()) return emptyList()
        val sorted  = words.sortedBy { it.centerY }
        val groups  = mutableListOf<MutableList<DigitWord>>()
        var current = mutableListOf(sorted.first())
        for (i in 1 until sorted.size) {
            if (kotlin.math.abs(sorted[i].centerY - sorted[i - 1].centerY) <
                sorted[i - 1].box.height() * 0.5f
            ) {
                current.add(sorted[i])
            } else {
                groups.add(current)
                current = mutableListOf(sorted[i])
            }
        }
        groups.add(current)
        return groups
    }

    private data class DigitWord(val text: String, val box: RectF, val centerY: Float)
}