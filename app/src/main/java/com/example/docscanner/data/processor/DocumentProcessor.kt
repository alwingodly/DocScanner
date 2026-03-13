package com.example.docscanner.data.processor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PointF
import com.example.docscanner.domain.model.DocumentCorners
import com.example.docscanner.domain.model.FilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Document processor — OpenCV replaced with Android Canvas + pure Kotlin.
 *
 * What changed:
 * ┌─────────────────────────┬────────────────────────────────────────────┐
 * │ Was (OpenCV)            │ Now (Android native)                       │
 * ├─────────────────────────┼────────────────────────────────────────────┤
 * │ Mat / Utils             │ Bitmap + Canvas + ColorMatrix              │
 * │ Imgproc.cvtColor        │ ColorMatrix.setSaturation(0)               │
 * │ Imgproc.threshold Otsu  │ Pure Kotlin histogram threshold            │
 * │ adaptiveThreshold       │ Pure Kotlin local mean threshold           │
 * │ CLAHE + sharpen         │ ColorMatrix contrast + convolution         │
 * │ warpPerspective         │ android.graphics.Matrix.setPolyToPoly      │
 * │ convertTo (brightness)  │ ColorMatrix with scale + translate         │
 * └─────────────────────────┴────────────────────────────────────────────┘
 *
 * Note: detectEdges() is now a thin wrapper that returns fallback corners.
 * Real document detection is handled by ML Kit DocumentScanner, which does
 * edge detection + perspective correction as part of its scanning flow.
 * If you still need manual corner adjustment after a scan, you can call
 * perspectiveTransform() directly with user-adjusted corners.
 */
@Singleton
class DocumentProcessor @Inject constructor() {

    // ══════════════════════════════════════════════════════════════════
    // STEP 1: Edge detection (delegated to ML Kit DocumentScanner)
    // ══════════════════════════════════════════════════════════════════

    /**
     * NOTE: This method now only returns fallback corners.
     *
     * Real document detection + perspective warp is done by ML Kit
     * DocumentScanner (see DocumentScanner.kt). This method is kept
     * for backwards compatibility with any screen that calls it —
     * the returned corners let the user manually adjust if needed.
     *
     * If you had a "manual review" step where the user drags corner
     * handles, it still works — just call perspectiveTransform() after.
     */
    suspend fun detectEdges(bitmap: Bitmap): DocumentCorners = withContext(Dispatchers.Default) {
        fallbackCorners(bitmap.width, bitmap.height)
    }

    // ══════════════════════════════════════════════════════════════════
    // STEP 2: Perspective Transform (manual corner adjust support)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Warps a bitmap so that the quadrilateral defined by [corners]
     * maps to a flat rectangle.
     *
     * Uses android.graphics.Matrix.setPolyToPoly (supports full
     * perspective mapping with 4 point pairs — same math as OpenCV's
     * getPerspectiveTransform + warpPerspective).
     *
     * @param bitmap  The original captured photo
     * @param corners The 4 document corners (from user adjustment or ML Kit result)
     * @return Flat, perspective-corrected document bitmap
     */
    suspend fun perspectiveTransform(
        bitmap: Bitmap,
        corners: DocumentCorners
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            val tl = corners.topLeft
            val tr = corners.topRight
            val bl = corners.bottomLeft
            val br = corners.bottomRight

            // Output dimensions — same logic as original OpenCV version
            val maxWidth  = max(distance(tl, tr), distance(bl, br)).roundToInt()
            val maxHeight = max(distance(tl, bl), distance(tr, br)).roundToInt()

            if (maxWidth <= 0 || maxHeight <= 0) return@withContext bitmap

            // android.graphics.Matrix supports perspective transform with 4 point pairs
            val matrix = android.graphics.Matrix()
            val src = floatArrayOf(
                tl.x, tl.y,
                tr.x, tr.y,
                br.x, br.y,
                bl.x, bl.y
            )
            val dst = floatArrayOf(
                0f, 0f,
                maxWidth.toFloat(), 0f,
                maxWidth.toFloat(), maxHeight.toFloat(),
                0f, maxHeight.toFloat()
            )
            matrix.setPolyToPoly(src, 0, dst, 0, 4)

            val result = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawBitmap(bitmap, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            result

        } catch (e: Exception) {
            bitmap // Return original on failure — never crash
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // STEP 3: Filters
    // ══════════════════════════════════════════════════════════════════

    /**
     * Apply an enhancement filter.
     * Same API as the original OpenCV version — drop-in replacement.
     */
    suspend fun applyFilter(
        bitmap: Bitmap,
        filterType: FilterType
    ): Bitmap = withContext(Dispatchers.Default) {
        when (filterType) {
            FilterType.ORIGINAL   -> bitmap
            FilterType.ENHANCED   -> enhanceColor(bitmap)
            FilterType.GRAYSCALE  -> toGrayscale(bitmap)
            FilterType.BLACK_WHITE -> toBlackWhite(bitmap)
            FilterType.MAGIC      -> magicFilter(bitmap)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // STEP 4: Brightness / Contrast
    // ══════════════════════════════════════════════════════════════════

    /**
     * Adjust brightness and contrast using ColorMatrix.
     *
     * ColorMatrix formula:  R' = R * contrast + brightness
     * Same as OpenCV's convertTo(alpha, beta).
     *
     * @param brightness  -100 to 100 (0 = no change)
     * @param contrast    0.5 to 2.0  (1.0 = no change)
     */
    suspend fun adjustBrightnessContrast(
        bitmap: Bitmap,
        brightness: Double,
        contrast: Double
    ): Bitmap = withContext(Dispatchers.Default) {
        val cm = ColorMatrix(floatArrayOf(
            contrast.toFloat(), 0f, 0f, 0f, brightness.toFloat(),
            0f, contrast.toFloat(), 0f, 0f, brightness.toFloat(),
            0f, 0f, contrast.toFloat(), 0f, brightness.toFloat(),
            0f, 0f, 0f, 1f, 0f
        ))
        applyColorMatrix(bitmap, cm)
    }

    // ══════════════════════════════════════════════════════════════════
    // PRIVATE FILTER IMPLEMENTATIONS
    // ══════════════════════════════════════════════════════════════════

    /**
     * ENHANCED: Boost contrast + saturation + sharpen.
     *
     * Replaces OpenCV's CLAHE on LAB L-channel + sharpening kernel.
     * Uses ColorMatrix for the contrast/saturation boost, then applies
     * a pure-Kotlin 3x3 unsharp mask for sharpening.
     */
    private fun enhanceColor(bitmap: Bitmap): Bitmap {
        // Boost contrast (1.3x) and saturation (1.2x)
        val cm = ColorMatrix()
        cm.setScale(1.3f, 1.3f, 1.3f, 1f)  // contrast
        val sat = ColorMatrix()
        sat.setSaturation(1.2f)              // slight saturation boost
        cm.postConcat(sat)

        val boosted = applyColorMatrix(bitmap, cm)
        return sharpen(boosted)
    }

    /**
     * GRAYSCALE: Remove all color information.
     * Replaces Imgproc.COLOR_RGBA2GRAY + COLOR_GRAY2RGBA.
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val cm = ColorMatrix()
        cm.setSaturation(0f)  // 0 = full grayscale
        return applyColorMatrix(bitmap, cm)
    }

    /**
     * BLACK & WHITE: Otsu-style global threshold.
     *
     * Replaces Imgproc.threshold with THRESH_BINARY + THRESH_OTSU.
     *
     * Algorithm:
     * 1. Compute grayscale luminance per pixel
     * 2. Build histogram (256 buckets)
     * 3. Find threshold that minimizes within-class variance (Otsu)
     * 4. Apply: pixel >= threshold → white, else → black
     */
    private fun toBlackWhite(bitmap: Bitmap): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Build grayscale histogram
        val hist = IntArray(256)
        val gray = IntArray(pixels.size)
        pixels.forEachIndexed { i, px ->
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            // Standard luminance weights
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt().coerceIn(0, 255)
            gray[i] = lum
            hist[lum]++
        }

        // Otsu's method — find optimal threshold
        val total = pixels.size.toDouble()
        var sumB = 0.0; var wB = 0; var maximum = 0.0; var threshold = 128
        val sum1 = hist.indices.sumOf { it * hist[it].toDouble() }
        for (t in 0 until 256) {
            wB += hist[t]; if (wB == 0) continue
            val wF = total - wB; if (wF == 0.0) break
            sumB += t * hist[t]
            val mB = sumB / wB; val mF = (sum1 - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > maximum) { maximum = between; threshold = t }
        }

        // Apply threshold
        val result = IntArray(pixels.size)
        gray.forEachIndexed { i, lum ->
            val v = if (lum >= threshold) 0xFFFFFF else 0x000000
            result[i] = (0xFF shl 24) or v
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * MAGIC: Local adaptive threshold.
     *
     * Replaces Imgproc.adaptiveThreshold with ADAPTIVE_THRESH_GAUSSIAN_C.
     *
     * Unlike Otsu (one threshold for whole image), this calculates a
     * different threshold per pixel based on its local neighbourhood mean.
     * This handles uneven lighting / shadows — the same result as
     * Adobe Scan / CamScanner's "auto" mode.
     *
     * blockSize=21, C=10 — same parameters as original OpenCV call.
     */
    private fun magicFilter(bitmap: Bitmap): Bitmap {
        val blockSize = 21; val C = 10
        val half = blockSize / 2
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Convert to grayscale
        val gray = IntArray(pixels.size)
        pixels.forEachIndexed { i, px ->
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt().coerceIn(0, 255)
        }

        // Compute integral image for fast box-filter mean
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            for (x in 0 until w) {
                integral[(y + 1) * (w + 1) + (x + 1)] =
                    gray[y * w + x] +
                            integral[y * (w + 1) + (x + 1)] +
                            integral[(y + 1) * (w + 1) + x] -
                            integral[y * (w + 1) + x]
            }
        }

        val result = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val x1 = max(0, x - half); val y1 = max(0, y - half)
                val x2 = min(w - 1, x + half); val y2 = min(h - 1, y + half)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integral[(y2 + 1) * (w + 1) + (x2 + 1)] -
                        integral[y1 * (w + 1) + (x2 + 1)] -
                        integral[(y2 + 1) * (w + 1) + x1] +
                        integral[y1 * (w + 1) + x1]
                val mean = (sum / count).toInt()
                val v = if (gray[y * w + x] >= mean - C) 0xFFFFFF else 0x000000
                result[y * w + x] = (0xFF shl 24) or v
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Unsharp mask sharpening — replaces OpenCV filter2D with the
     * [ 0,-1, 0 / -1, 5,-1 / 0,-1, 0 ] kernel.
     *
     * Android approach: blur the bitmap then blend original - blurred
     * to amplify high-frequency detail (equivalent to the laplacian kernel).
     */
    private fun sharpen(bitmap: Bitmap): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val result = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val center = pixels[idx]
                val cR = (center shr 16) and 0xFF
                val cG = (center shr 8) and 0xFF
                val cB = center and 0xFF

                // Laplacian 4-neighbour kernel: 5*center - (top+bottom+left+right)
                val top    = if (y > 0)     pixels[(y-1)*w+x] else center
                val bottom = if (y < h-1)   pixels[(y+1)*w+x] else center
                val left   = if (x > 0)     pixels[y*w+(x-1)] else center
                val right  = if (x < w-1)   pixels[y*w+(x+1)] else center

                fun channel(shift: Int): Int {
                    val c = (center shr shift) and 0xFF
                    val t = (top    shr shift) and 0xFF
                    val b = (bottom shr shift) and 0xFF
                    val l = (left   shr shift) and 0xFF
                    val r = (right  shr shift) and 0xFF
                    return (5*c - t - b - l - r).coerceIn(0, 255)
                }

                result[idx] = (0xFF shl 24) or
                        (channel(16) shl 16) or
                        (channel(8) shl 8) or
                        channel(0)
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    // ══════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════

    /** Apply a ColorMatrix to a bitmap using Canvas. Fast, GPU-accelerated path. */
    private fun applyColorMatrix(bitmap: Bitmap, cm: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    /** Euclidean distance between two PointF — unchanged from original. */
    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = (p2.x - p1.x).toDouble()
        val dy = (p2.y - p1.y).toDouble()
        return sqrt(dx * dx + dy * dy).toFloat()
    }

    /** Fallback corners covering 90% of the image — unchanged from original. */
    private fun fallbackCorners(width: Int, height: Int): DocumentCorners {
        val margin = 0.05f
        return DocumentCorners(
            topLeft     = PointF(width * margin,       height * margin),
            topRight    = PointF(width * (1 - margin), height * margin),
            bottomLeft  = PointF(width * margin,       height * (1 - margin)),
            bottomRight = PointF(width * (1 - margin), height * (1 - margin))
        )
    }
}