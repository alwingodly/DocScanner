package com.example.docscanner.data.processor

import android.graphics.Bitmap
import android.graphics.PointF
import com.example.docscanner.domain.model.DocumentCorners
import com.example.docscanner.domain.model.FilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core document processing engine powered by OpenCV.
 *
 * Why @Singleton?
 * - No internal state, but we want ONE instance to avoid repeated object creation
 * - Hilt provides this same instance to every ViewModel that needs it
 *
 * Why suspend + Dispatchers.Default?
 * - OpenCV operations are CPU-heavy (not IO)
 * - Dispatchers.Default uses a thread pool sized to CPU cores
 * - Keeps the main/UI thread completely free
 *
 * Memory management:
 * - Every OpenCV Mat must be manually released (it's backed by native C++ memory)
 * - We use try/finally to ensure cleanup even on errors
 * - Bitmaps are managed by Android's GC, but Mats are NOT
 */
@Singleton
class DocumentProcessor @Inject constructor() {

    /**
     * STEP 1: Detect document edges in a photo.
     *
     * How it works:
     * 1. Downscale image (faster processing, we don't need full res for edge finding)
     * 2. Convert to grayscale (edges are about brightness changes, not color)
     * 3. Gaussian blur (removes noise that creates false edges)
     * 4. Canny edge detection (finds pixels where brightness changes sharply)
     * 5. Dilate edges (connects small gaps in edge lines)
     * 6. Find contours (traces connected edge pixels into shapes)
     * 7. Find largest 4-sided shape (that's our document)
     * 8. Scale coordinates back to original image size
     *
     * Returns DocumentCorners if found, or fallback corners (90% of image) if not.
     *
     * @param bitmap The captured photo
     * @return Detected document corners
     */
    suspend fun detectEdges(bitmap: Bitmap): DocumentCorners = withContext(Dispatchers.Default) {

        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        try {
            // ── 1. Downscale for speed ──
            // Processing a 12MP image is slow. 500px max dimension is enough
            // to find document edges accurately.
            val maxDim = 500.0
            val ratio = maxDim / maxOf(src.rows(), src.cols())
            val resized = Mat()
            if (ratio < 1.0) {
                Imgproc.resize(src, resized, Size(src.cols() * ratio, src.rows() * ratio))
            } else {
                src.copyTo(resized)
            }

            // ── 2. Grayscale ──
            // Color info is irrelevant for edge detection.
            // A white document on a dark desk = bright region on dark region.
            val gray = Mat()
            Imgproc.cvtColor(resized, gray, Imgproc.COLOR_BGR2GRAY)

            // ── 3. Gaussian Blur ──
            // Smooths the image to remove small noise/texture.
            // Without this, camera noise creates thousands of false edges.
            // Kernel size 5x5 is a good balance (too big = lose real edges).
            val blurred = Mat()
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            // ── 4. Canny Edge Detection ──
            // Finds pixels where brightness changes sharply.
            // threshold1=50: minimum gradient to consider as weak edge
            // threshold2=200: minimum gradient to consider as strong edge
            // Weak edges are kept only if connected to strong edges.
            val edges = Mat()
            Imgproc.Canny(blurred, edges, 50.0, 200.0)

            // ── 5. Dilate ──
            // Expands edge pixels slightly to close small gaps.
            // A document edge might have tiny breaks (shadows, folds) —
            // dilation connects them so contour detection sees one continuous shape.
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            val dilated = Mat()
            Imgproc.dilate(edges, dilated, kernel)

            // ── 6. Find Contours ──
            // Traces connected white pixels into shapes.
            // RETR_EXTERNAL = only outermost contours (ignore shapes inside shapes)
            // CHAIN_APPROX_SIMPLE = compress straight lines to just endpoints (saves memory)
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                dilated, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )

            // ── 7. Find the document ──
            // Sort contours by area (largest first).
            // For each contour, approximate it to a polygon.
            // If the polygon has exactly 4 vertices and covers >10% of the image,
            // that's our document.
            val sortedContours = contours.sortedByDescending { Imgproc.contourArea(it) }

            var foundCorners: DocumentCorners? = null

            for (contour in sortedContours) {
                // Approximate contour to polygon
                // epsilon = 2% of perimeter — controls how much simplification
                val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)

                // Is it a quadrilateral?
                if (approx.rows() == 4) {
                    val area = Imgproc.contourArea(approx)
                    val imageArea = resized.rows().toDouble() * resized.cols().toDouble()

                    // Must cover at least 10% of image to be a real document
                    // (filters out small rectangles like text boxes, icons, etc.)
                    if (area > imageArea * 0.1) {
                        // ── 8. Scale back to original size ──
                        val scaleBack = if (ratio < 1.0) 1.0 / ratio else 1.0
                        val points = approx.toArray().map { point ->
                            PointF(
                                (point.x * scaleBack).toFloat(),
                                (point.y * scaleBack).toFloat()
                            )
                        }

                        foundCorners = orderPoints(points)
                        break  // Found it, stop looking
                    }
                }
            }

            // ── Cleanup native memory ──
            listOf(src, resized, gray, blurred, edges, kernel, dilated, hierarchy)
                .forEach { it.release() }
            contours.forEach { it.release() }

            // Return detected corners, or fallback to 90% of image
            foundCorners ?: fallbackCorners(bitmap.width, bitmap.height)

        } catch (e: Exception) {
            src.release()
            fallbackCorners(bitmap.width, bitmap.height)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ADD everything below AFTER the detectEdges() method
    // and BEFORE the private helper methods (fallbackCorners, orderPoints)
    // ══════════════════════════════════════════════════════════════

    /**
     * STEP 2: Perspective Transform (the "scan" magic)
     *
     * Takes the original photo + 4 corner points, and warps the image
     * so the document becomes a flat, top-down rectangle.
     *
     * How it works:
     * 1. Calculate output dimensions from corner distances
     * 2. Define source points (the 4 corners on the photo)
     * 3. Define destination points (a perfect rectangle)
     * 4. Compute the 3x3 transformation matrix
     * 5. Apply the warp
     *
     * Visual:
     *   BEFORE (perspective)          AFTER (flat)
     *      ╱‾‾‾‾‾‾‾╲                ┌──────────┐
     *     ╱          ╲               │          │
     *    ╱            ╲              │ document │
     *   ╱              ╲             │          │
     *  ╱________________╲            └──────────┘
     *
     * The math behind this is a 3x3 homography matrix that maps
     * every pixel from source quadrilateral to destination rectangle.
     * OpenCV computes this matrix and applies it in one shot.
     *
     * @param bitmap The original captured photo
     * @param corners The 4 document corners (from detectEdges or user adjustment)
     * @return A new bitmap with just the flattened document
     */
    suspend fun perspectiveTransform(
        bitmap: Bitmap,
        corners: DocumentCorners
    ): Bitmap = withContext(Dispatchers.Default) {

        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        try {
            val tl = corners.topLeft
            val tr = corners.topRight
            val bl = corners.bottomLeft
            val br = corners.bottomRight

            // ── 1. Calculate output dimensions ──
            // The document might be viewed at an angle, so top edge and bottom edge
            // have different lengths. We take the max to avoid cutting anything off.
            val widthTop = distance(tl, tr)
            val widthBottom = distance(bl, br)
            val maxWidth = maxOf(widthTop, widthBottom).toInt()

            val heightLeft = distance(tl, bl)
            val heightRight = distance(tr, br)
            val maxHeight = maxOf(heightLeft, heightRight).toInt()

            // Safety check — avoid zero-size output
            if (maxWidth <= 0 || maxHeight <= 0) {
                src.release()
                return@withContext bitmap
            }

            // ── 2. Source points (where corners are NOW in the photo) ──
            val srcPoints = MatOfPoint2f(
                Point(tl.x.toDouble(), tl.y.toDouble()),
                Point(tr.x.toDouble(), tr.y.toDouble()),
                Point(br.x.toDouble(), br.y.toDouble()),
                Point(bl.x.toDouble(), bl.y.toDouble())
            )

            // ── 3. Destination points (where corners SHOULD BE — a rectangle) ──
            val dstPoints = MatOfPoint2f(
                Point(0.0, 0.0),                              // top-left → origin
                Point(maxWidth.toDouble(), 0.0),               // top-right
                Point(maxWidth.toDouble(), maxHeight.toDouble()), // bottom-right
                Point(0.0, maxHeight.toDouble())               // bottom-left
            )

            // ── 4. Compute transformation matrix ──
            // This 3x3 matrix encodes how to map every pixel from src → dst.
            // It's the core of the perspective correction.
            val transformMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

            // ── 5. Apply the warp ──
            // INTER_LINEAR = bilinear interpolation (smooth result, fast)
            val warped = Mat()
            Imgproc.warpPerspective(
                src, warped, transformMatrix,
                Size(maxWidth.toDouble(), maxHeight.toDouble()),
                Imgproc.INTER_LINEAR
            )

            // Convert back to Android Bitmap
            val result = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warped, result)

            // Cleanup
            listOf(src, srcPoints, dstPoints, transformMatrix, warped).forEach { it.release() }

            result
        } catch (e: Exception) {
            src.release()
            bitmap  // Return original on failure — never crash
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ADD everything below AFTER perspectiveTransform() method
    // and BEFORE the private helper methods
    // ══════════════════════════════════════════════════════════════

    /**
     * STEP 3: Apply enhancement filter to a bitmap.
     *
     * This is the public entry point — it delegates to the specific
     * filter implementation based on FilterType.
     *
     * @param bitmap The cropped/warped document image
     * @param filterType Which filter to apply
     * @return Filtered bitmap
     */
    suspend fun applyFilter(
        bitmap: Bitmap,
        filterType: FilterType
    ): Bitmap = withContext(Dispatchers.Default) {
        when (filterType) {
            FilterType.ORIGINAL -> bitmap           // No processing needed
            FilterType.ENHANCED -> enhanceColor(bitmap)
            FilterType.GRAYSCALE -> toGrayscale(bitmap)
            FilterType.BLACK_WHITE -> toBlackWhite(bitmap)
            FilterType.MAGIC -> magicFilter(bitmap)
        }
    }

    /**
     * STEP 4: Adjust brightness and contrast.
     *
     * Uses the formula: output = input * contrast + brightness
     * This is a simple linear transformation applied to every pixel.
     *
     * @param bitmap Source image
     * @param brightness -100 to 100 (0 = no change)
     * @param contrast 0.5 to 2.0 (1.0 = no change)
     * @return Adjusted bitmap
     */
    suspend fun adjustBrightnessContrast(
        bitmap: Bitmap,
        brightness: Double,
        contrast: Double
    ): Bitmap = withContext(Dispatchers.Default) {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        // convertTo applies: dst(x,y) = src(x,y) * alpha + beta
        // alpha = contrast multiplier, beta = brightness offset
        val dst = Mat()
        src.convertTo(dst, -1, contrast, brightness)

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, result)

        src.release()
        dst.release()

        result
    }

    // ══════════════════════════════════════════════════════════════
    // PRIVATE FILTER IMPLEMENTATIONS
    // Add these alongside your existing private helpers
    // ══════════════════════════════════════════════════════════════

    /**
     * ENHANCED filter: CLAHE contrast + sharpening.
     *
     * Best for: Color documents, photos, receipts with colored logos.
     *
     * How it works:
     * 1. Convert to LAB color space (separates lightness from color)
     * 2. Apply CLAHE to L channel (adaptive contrast — enhances locally)
     * 3. Merge back and convert to BGR
     * 4. Apply sharpening kernel
     *
     * Why LAB instead of RGB?
     * - Adjusting contrast in RGB shifts colors (reds get more red, etc.)
     * - LAB separates lightness (L) from color (A,B)
     * - We boost contrast on L only, so colors stay natural
     */
    private fun enhanceColor(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        // Convert RGBA → BGR (OpenCV standard) → LAB
        val bgr = Mat()
        Imgproc.cvtColor(src, bgr, Imgproc.COLOR_RGBA2BGR)
        val lab = Mat()
        Imgproc.cvtColor(bgr, lab, Imgproc.COLOR_BGR2Lab)

        // Split into L, A, B channels
        val channels = mutableListOf<Mat>()
        Core.split(lab, channels)

        // CLAHE on L channel
        // clipLimit=2.0 — limits contrast amplification (prevents noise boost)
        // tileGridSize=8x8 — divides image into 8x8 blocks, enhances each locally
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        clahe.apply(channels[0], channels[0])

        // Merge channels back
        Core.merge(channels, lab)

        // LAB → BGR → RGBA
        Imgproc.cvtColor(lab, bgr, Imgproc.COLOR_Lab2BGR)

        // Sharpen
        val sharpened = sharpen(bgr)

        // Back to RGBA for Android Bitmap
        val rgba = Mat()
        Imgproc.cvtColor(sharpened, rgba, Imgproc.COLOR_BGR2RGBA)

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, result)

        // Cleanup everything
        listOf(src, bgr, lab, sharpened, rgba).forEach { it.release() }
        channels.forEach { it.release() }

        return result
    }

    /**
     * GRAYSCALE filter: Simple color → gray conversion.
     *
     * Best for: Documents where you want to remove color distractions
     * but keep the full tonal range (not just black & white).
     *
     * Formula per pixel: gray = 0.299*R + 0.587*G + 0.114*B
     * (Weighted because human eyes are most sensitive to green)
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

        // Convert back to RGBA (4 channel) — Android Bitmaps need ARGB_8888
        val dst = Mat()
        Imgproc.cvtColor(gray, dst, Imgproc.COLOR_GRAY2RGBA)

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, result)

        listOf(src, gray, dst).forEach { it.release() }
        return result
    }

    /**
     * BLACK & WHITE filter: Otsu's thresholding.
     *
     * Best for: Printed text documents with uniform lighting.
     *
     * Otsu's method automatically finds the best threshold value
     * by analyzing the image histogram. It picks the threshold
     * that minimizes the variance within dark/light pixel groups.
     *
     * Result: Every pixel becomes pure black (0) or pure white (255).
     * Great contrast for printed text, but loses detail in photos.
     */
    private fun toBlackWhite(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

        // THRESH_OTSU automatically calculates optimal threshold
        val bw = Mat()
        Imgproc.threshold(gray, bw, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

        val dst = Mat()
        Imgproc.cvtColor(bw, dst, Imgproc.COLOR_GRAY2RGBA)

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, result)

        listOf(src, gray, bw, dst).forEach { it.release() }
        return result
    }

    /**
     * MAGIC filter: Adaptive thresholding.
     *
     * Best for: Handwritten text, documents with uneven lighting/shadows.
     *
     * Why "magic"?
     * Unlike Otsu (one threshold for entire image), adaptive threshold
     * calculates a DIFFERENT threshold for each small region.
     * This means:
     * - Shadowed areas get their own threshold → text stays visible
     * - Bright areas get their own threshold → no washout
     *
     * blockSize=21: size of each local region (21x21 pixels)
     * C=10: constant subtracted from the calculated threshold (fine-tuning)
     *
     * This is what CamScanner/Adobe Scan use for their "auto" mode.
     */
    private fun magicFilter(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

        // Remove small noise before thresholding
        val denoised = Mat()
        Imgproc.medianBlur(gray, denoised, 3)

        // Adaptive threshold — different threshold per local region
        val adaptive = Mat()
        Imgproc.adaptiveThreshold(
            denoised, adaptive, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,  // Gaussian-weighted mean of neighborhood
            Imgproc.THRESH_BINARY,
            21,     // Block size (must be odd)
            10.0    // Constant C subtracted from mean
        )

        val dst = Mat()
        Imgproc.cvtColor(adaptive, dst, Imgproc.COLOR_GRAY2RGBA)

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, result)

        listOf(src, gray, denoised, adaptive, dst).forEach { it.release() }
        return result
    }

    /**
     * Sharpening using a 3x3 convolution kernel.
     *
     * The kernel:
     *   [ 0, -1,  0]
     *   [-1,  5, -1]   → center pixel = 5x itself minus neighbors
     *   [ 0, -1,  0]
     *
     * This amplifies differences between a pixel and its neighbors,
     * making edges (text, lines) crisper.
     */
    private fun sharpen(src: Mat): Mat {
        val kernel = Mat(3, 3, CvType.CV_32F).apply {
            put(0, 0,
                0.0, -1.0, 0.0,
                -1.0, 5.0, -1.0,
                0.0, -1.0, 0.0
            )
        }
        val dst = Mat()
        Imgproc.filter2D(src, dst, -1, kernel)
        kernel.release()
        return dst
    }
    /**
     * Euclidean distance between two points.
     * Used to calculate document width/height from corner positions.
     */
    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = (p2.x - p1.x).toDouble()
        val dy = (p2.y - p1.y).toDouble()
        return kotlin.math.sqrt(dx * dx + dy * dy).toFloat()
    }
    /**
     * When no document is detected, return corners covering 90% of the image.
     * User can then manually adjust the handles.
     */
    private fun fallbackCorners(width: Int, height: Int): DocumentCorners {
        val margin = 0.05f
        return DocumentCorners(
            topLeft = PointF(width * margin, height * margin),
            topRight = PointF(width * (1 - margin), height * margin),
            bottomLeft = PointF(width * margin, height * (1 - margin)),
            bottomRight = PointF(width * (1 - margin), height * (1 - margin))
        )
    }

    /**
     * Order 4 points as: topLeft, topRight, bottomLeft, bottomRight.
     *
     * OpenCV returns contour points in arbitrary order.
     * We need consistent ordering for perspective transform.
     *
     * Algorithm:
     * - Point with smallest (x+y) = top-left (closest to origin)
     * - Point with largest (x+y) = bottom-right (farthest from origin)
     * - Point with smallest (y-x) = top-right (high x, low y)
     * - Point with largest (y-x) = bottom-left (low x, high y)
     */
    private fun orderPoints(points: List<PointF>): DocumentCorners {
        val sortedBySum = points.sortedBy { it.x + it.y }
        val topLeft = sortedBySum.first()
        val bottomRight = sortedBySum.last()

        val sortedByDiff = points.sortedBy { it.y - it.x }
        val topRight = sortedByDiff.first()
        val bottomLeft = sortedByDiff.last()

        return DocumentCorners(
            topLeft = topLeft,
            topRight = topRight,
            bottomLeft = bottomLeft,
            bottomRight = bottomRight
        )
    }
}