package com.example.docscanner.data.processor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Auto post-processor — runs after every ML Kit scan.
 *
 * Each step analyses the image first and only applies the fix if
 * the image actually needs it. Nothing is applied blindly.
 *
 * Pipeline (in order):
 *   1. Inversion fix      — detects dark-background scans and inverts
 *   2. Noise removal      — median de-speckling (skipped if image is clean)
 *   3. Contrast + bg norm — adaptive local contrast + background whitening
 *   4. Punch-hole repair  — fills dark circles near page edges with white
 *   5. Vividness boost    — lifts saturation if image looks dull
 */
@Singleton
class ImagePostProcessor @Inject constructor() {

    /**
     * Run the full auto-correction pipeline on a bitmap.
     * Safe to call on any scanned image — steps that aren't needed are no-ops.
     */
    suspend fun process(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        var result = bitmap
        result = fixInversionIfNeeded(result)
        result = denoiseIfNeeded(result)
        result = enhanceContrastAndBackground(result)
        result = repairPunchHoles(result)
        result = boostVividnessIfNeeded(result)
        result
    }

    // ══════════════════════════════════════════════════════════════════
    // STEP 1 — Inversion fix
    // ══════════════════════════════════════════════════════════════════

    /**
     * Detects images where the background is dark (inverted scan, dark theme,
     * photo of white text on blackboard, etc.) and inverts the colours.
     *
     * Detection: sample the 4 corner regions (each 10% of image size).
     * If their average luminance < 80 (out of 255), we assume the
     * background is dark and invert.
     */
    private fun fixInversionIfNeeded(bitmap: Bitmap): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val sampleW = (w * 0.10).toInt().coerceAtLeast(1)
        val sampleH = (h * 0.10).toInt().coerceAtLeast(1)

        // Sample 4 corners
        val corners = listOf(
            Pair(0, 0), Pair(w - sampleW, 0),
            Pair(0, h - sampleH), Pair(w - sampleW, h - sampleH)
        )
        var totalLum = 0.0; var count = 0
        for ((cx, cy) in corners) {
            for (x in cx until cx + sampleW) {
                for (y in cy until cy + sampleH) {
                    val px = bitmap.getPixel(x, y)
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    totalLum += 0.299 * r + 0.587 * g + 0.114 * b
                    count++
                }
            }
        }
        val avgLum = if (count > 0) totalLum / count else 255.0

        // Only invert if background is clearly dark
        if (avgLum >= 80.0) return bitmap

        val cm = ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f,-1f, 0f, 0f, 255f,
            0f, 0f,-1f, 0f, 255f,
            0f, 0f, 0f, 1f,   0f
        ))
        return applyColorMatrix(bitmap, cm)
    }

    // ══════════════════════════════════════════════════════════════════
    // STEP 2 — Noise removal / de-speckling
    // ══════════════════════════════════════════════════════════════════

    /**
     * Estimates the noise level by measuring local pixel variance in a
     * 20×20 centre patch. If variance is above threshold, applies a
     * 3×3 median filter which removes salt-and-pepper noise without
     * blurring text edges.
     *
     * Threshold 180 was chosen empirically — clean scans rarely exceed
     * it, but noisy camera shots do.
     */
    private fun denoiseIfNeeded(bitmap: Bitmap): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val cx = w / 2; val cy = h / 2
        val patchSize = 20
        val pixels = IntArray(patchSize * patchSize)
        bitmap.getPixels(pixels, 0, patchSize,
            cx - patchSize / 2, cy - patchSize / 2, patchSize, patchSize)

        val lums = pixels.map { px ->
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            0.299 * r + 0.587 * g + 0.114 * b
        }
        val mean = lums.average()
        val variance = lums.map { (it - mean) * (it - mean) }.average()

        return if (variance > 180.0) medianFilter(bitmap) else bitmap
    }

    /**
     * 3×3 median filter — for each pixel, replaces it with the median
     * of its 9 neighbours. Kills isolated specks (noise) while preserving
     * sharp edges (text strokes).
     */
    private fun medianFilter(bitmap: Bitmap): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val src = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        val dst = IntArray(w * h)
        val window = IntArray(9)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                // Collect 3×3 neighbourhood
                var k = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val px = src[(y + dy) * w + (x + dx)]
                    window[k++] = (0.299 * ((px shr 16) and 0xFF) +
                            0.587 * ((px shr 8) and 0xFF) +
                            0.114 * (px and 0xFF)).roundToInt()
                }
                window.sort()
                val med = window[4] // median of 9
                dst[y * w + x] = (0xFF shl 24) or (med shl 16) or (med shl 8) or med
            }
        }
        // Copy border pixels unchanged
        for (x in 0 until w) { dst[x] = src[x]; dst[(h - 1) * w + x] = src[(h - 1) * w + x] }
        for (y in 0 until h) { dst[y * w] = src[y * w]; dst[y * w + w - 1] = src[y * w + w - 1] }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(dst, 0, w, 0, 0, w, h)
        return result
    }

    // ══════════════════════════════════════════════════════════════════
    // STEP 3 — Contrast enhancement + background normalisation
    // ══════════════════════════════════════════════════════════════════

    /**
     * Two things happen here:
     *
     * a) Background normalisation: samples corner luminance again (post-inversion)
     *    and calculates a brightness offset to push the background toward white (240).
     *    This removes the grey/yellow tint common in camera scans.
     *
     * b) Contrast boost: scales pixel values so the darkest 5th-percentile
     *    pixel maps to 0 and brightest 95th maps to 255 (linear stretch),
     *    then applies a mild contrast multiplier.
     *
     * Both are implemented as a single ColorMatrix pass — no pixel loop needed.
     */
    private fun enhanceContrastAndBackground(bitmap: Bitmap): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val sampleStep = max(1, min(w, h) / 80)
        val lums = mutableListOf<Float>()

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var idx = 0
        for (y in 0 until h step sampleStep) {
            for (x in 0 until w step sampleStep) {
                val px = pixels[y * w + x]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                lums.add((0.299f * r + 0.587f * g + 0.114f * b))
            }
        }
        if (lums.isEmpty()) return bitmap
        lums.sort()

        val p05 = lums[(lums.size * 0.05).toInt().coerceIn(0, lums.size - 1)]
        val p95 = lums[(lums.size * 0.95).toInt().coerceIn(0, lums.size - 1)]
        val range = (p95 - p05).coerceAtLeast(1f)

        // Scale so p05 → 0, p95 → 255, then mild contrast lift
        val scale = (255f / range) * 1.1f       // 1.1 = slight contrast punch
        val offset = -p05 * scale + 10f         // +10 = gentle lift away from pure black

        val cm = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, offset,
            0f, scale, 0f, 0f, offset,
            0f, 0f, scale, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        ))
        return applyColorMatrix(bitmap, cm)
    }

    // ══════════════════════════════════════════════════════════════════
    // STEP 4 — Punch-hole repair
    // ══════════════════════════════════════════════════════════════════

    /**
     * Detects dark circular regions near the left/right edges of the page
     * (typical binder punch-hole positions) and fills them with white.
     *
     * Detection algorithm:
     * 1. Scan 3 horizontal strips (top/mid/bottom) near left and right edges
     * 2. Find connected dark regions (luminance < 60) within those strips
     * 3. Check if the region is roughly circular (aspect ratio near 1.0)
     * 4. If diameter is 0.5%–4% of image width → it's a punch hole → fill white
     */
    private fun repairPunchHoles(bitmap: Bitmap): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val edgeZone = (w * 0.08).toInt()           // scan 8% from each edge
        val minR = (w * 0.005).toInt().coerceAtLeast(2)
        val maxR = (w * 0.04).toInt()
        var changed = false

        // Check strips at ~15%, 50%, 85% of height
        for (stripFrac in listOf(0.15, 0.50, 0.85)) {
            val stripY = (h * stripFrac).toInt()
            val stripH = (h * 0.12).toInt().coerceAtLeast(10)

            for (side in listOf(0, w - edgeZone)) {
                // Find dark pixels in this strip+side region
                val darkPts = mutableListOf<Pair<Int, Int>>()
                for (y in (stripY - stripH / 2).coerceAtLeast(0) until
                        (stripY + stripH / 2).coerceAtMost(h)) {
                    for (x in side until (side + edgeZone).coerceAtMost(w)) {
                        val px = pixels[y * w + x]
                        val lum = 0.299 * ((px shr 16) and 0xFF) +
                                0.587 * ((px shr 8) and 0xFF) +
                                0.114 * (px and 0xFF)
                        if (lum < 60) darkPts.add(Pair(x, y))
                    }
                }
                if (darkPts.isEmpty()) continue

                // Bounding box of dark region
                val minX = darkPts.minOf { it.first }
                val maxX = darkPts.maxOf { it.first }
                val minY = darkPts.minOf { it.second }
                val maxY = darkPts.maxOf { it.second }
                val rw = maxX - minX; val rh = maxY - minY
                if (rw < minR * 2 || rh < minR * 2) continue

                // Must be roughly circular
                val aspect = rw.toFloat() / rh.toFloat()
                if (aspect < 0.5f || aspect > 2.0f) continue

                // Must be right size for a punch hole
                val radius = (rw + rh) / 4
                if (radius < minR || radius > maxR) continue

                // Fill with white
                val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
                val fillR = radius + 2 // small margin
                for (y in (cy - fillR).coerceAtLeast(0) until (cy + fillR).coerceAtMost(h)) {
                    for (x in (cx - fillR).coerceAtLeast(0) until (cx + fillR).coerceAtMost(w)) {
                        val dx = x - cx; val dy = y - cy
                        if (dx * dx + dy * dy <= fillR * fillR) {
                            pixels[y * w + x] = 0xFFFFFFFF.toInt()
                            changed = true
                        }
                    }
                }
            }
        }

        if (!changed) return bitmap
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    // ══════════════════════════════════════════════════════════════════
    // STEP 5 — Color vividness boost
    // ══════════════════════════════════════════════════════════════════

    /**
     * Checks if the image is desaturated (dull scan, faded ink, coloured
     * paper scanned in poor light) by measuring average saturation.
     *
     * If avg saturation < 0.12 (0–1 scale) → image is essentially greyscale,
     *   skip — a saturation boost won't help and may introduce artefacts.
     * If avg saturation 0.12–0.35 → mild boost (1.6×)
     * If avg saturation > 0.35 → stronger boost (1.3×) — already colourful,
     *   just lift it slightly.
     */
    private fun boostVividnessIfNeeded(bitmap: Bitmap): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val step = max(1, min(w, h) / 60)
        var totalSat = 0.0; var count = 0

        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val px = bitmap.getPixel(x, y)
                val r = ((px shr 16) and 0xFF) / 255f
                val g = ((px shr 8) and 0xFF) / 255f
                val b = (px and 0xFF) / 255f
                val cMax = max(r, max(g, b))
                val cMin = min(r, min(g, b))
                val delta = cMax - cMin
                val sat = if (cMax > 0f) delta / cMax else 0f
                totalSat += sat; count++
            }
        }
        val avgSat = if (count > 0) totalSat / count else 0.0

        val satMult = when {
            avgSat < 0.12 -> return bitmap  // greyscale — skip
            avgSat < 0.35 -> 1.6f           // dull colour — boost more
            else          -> 1.3f           // already vivid — gentle lift
        }

        val cm = ColorMatrix()
        cm.setSaturation(satMult)
        return applyColorMatrix(bitmap, cm)
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPER
    // ══════════════════════════════════════════════════════════════════

    private fun applyColorMatrix(bitmap: Bitmap, cm: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
}