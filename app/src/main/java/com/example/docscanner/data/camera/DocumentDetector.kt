package com.example.docscanner.data.camera

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.example.docscanner.domain.model.DocumentCorners
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Document detector — fast + reliable.
 *
 * Detection pipeline:
 *   1. CLAHE + blur (shared preprocessing)
 *   2. Dual-threshold Canny: tight + loose edge maps OR-ed together.
 *      Catches both strong edges (white paper on dark desk) and faint
 *      edges (low-contrast or shadowed scenes).
 *   3. findContours on the merged edge map (single call)
 *   4. If contour score < CONFIDENT_THRESHOLD → Hough line fallback
 *
 * Confidence thresholds (lowered from original for faster lock-on):
 *   isConfident = score ≥ 0.22  (was 0.28)  — catches most real docs
 *   fallback    = score ≥ 0.14  (was 0.18)  — returned but marked !confident
 *
 * Typical timing: ~6-10ms at 480px.
 */
object DocumentDetector {

    private const val TAG = "DocDetector"
    const val RECOMMENDED_RESOLUTION = 480

    // Score at which we declare a confident detection and skip the Hough fallback.
    // Lowered from 0.28 → 0.22 so borderline-good quads lock immediately rather
    // than wasting time running the full Hough pipeline.
    private const val CONFIDENT_THRESHOLD = 0.22f   // was 0.28

    // Minimum score returned as a usable (non-confident) result.
    // Lowered from 0.18 → 0.14 so faint detections still contribute to hitScore.
    private const val FALLBACK_THRESHOLD = 0.14f    // was 0.18

    data class DetectionResult(
        val corners: DocumentCorners?,
        val isConfident: Boolean,
        val confidence: Float = 0f,
        val sceneSig: FloatArray? = null
    )

    // Pre-allocated Mats — avoids repeated GC pressure in the analysis loop
    private val srcMat    = Mat()
    private val grayMat   = Mat()
    private val blurMat   = Mat()
    private val edgeTight = Mat()
    private val edgeLoose = Mat()
    private val edgeMerge = Mat()
    private val edgeHough = Mat()
    private val tmpMat    = Mat()
    private val hierMat   = Mat()
    private val linesMat  = Mat()

    // Cached kernels
    private val kernel3 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
    private val kernel5 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))

    fun detect(bitmap: Bitmap): DetectionResult {
        return try {
            detectInternal(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "detect() crash: ${e.message}", e)
            DetectionResult(null, false)
        }
    }

    private fun detectInternal(bitmap: Bitmap): DetectionResult {
        val W = bitmap.width.toFloat()
        val H = bitmap.height.toFloat()
        val imageArea = (W * H).toDouble()
        val diag = sqrt((W * W + H * H).toDouble())

        Utils.bitmapToMat(bitmap, srcMat)
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

        val sceneSig = sceneSignature(grayMat)

        // CLAHE: boosts local contrast (helps low-contrast docs)
        val clahe = Imgproc.createCLAHE(2.5, Size(8.0, 8.0))
        clahe.apply(grayMat, grayMat)
        Imgproc.GaussianBlur(grayMat, blurMat, Size(5.0, 5.0), 0.0)

        // ── Dual-threshold Canny → merged edge map ────────────────────────
        // Otsu threshold gives a scene-adaptive baseline.
        val otsu = Imgproc.threshold(blurMat, tmpMat, 0.0, 255.0,
            Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

        // Tight: strong / clean edges
        Imgproc.Canny(blurMat, edgeTight, max(30.0, otsu * 0.50), max(80.0, otsu * 1.0))

        // Loose: faint edges (shadows, textured backgrounds, coloured paper)
        Imgproc.Canny(blurMat, edgeLoose, max(10.0, otsu * 0.20), max(30.0, otsu * 0.55))

        // Merge and dilate to bridge nearby edge fragments
        Core.bitwise_or(edgeTight, edgeLoose, edgeMerge)
        Imgproc.dilate(edgeMerge, edgeMerge, kernel3)

        // Keep a copy for the Hough fallback before findContours modifies the Mat
        edgeMerge.copyTo(edgeHough)

        // ── Tier 1: Contour detection ─────────────────────────────────────
        val t1 = contourQuad(edgeMerge, imageArea, diag, W, H)

        if (t1 != null && t1.score >= CONFIDENT_THRESHOLD) {
            return DetectionResult(t1.corners, true, t1.score, sceneSig)
        }

        // ── Tier 2: Hough line fallback (broken / faint edges) ───────────
        Imgproc.dilate(edgeHough, edgeHough, kernel5)
        val t2 = houghQuad(edgeHough, W, H, imageArea, diag)

        val best = listOfNotNull(t1, t2).maxByOrNull { it.score }
        if (best != null && best.score >= FALLBACK_THRESHOLD) {
            return DetectionResult(best.corners, best.score >= CONFIDENT_THRESHOLD,
                best.score, sceneSig)
        }

        return DetectionResult(null, false, 0f, sceneSig)
    }

    // ══════════════════════════════════════════════════════════════════════
    // TIER 1: Contour quad
    // ══════════════════════════════════════════════════════════════════════

    private data class ScoredResult(val corners: DocumentCorners, val score: Float)

    private fun contourQuad(
        edges: Mat, imageArea: Double, diag: Double, W: Float, H: Float
    ): ScoredResult? {
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(edges, contours, hierMat,
            Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        // Lower minArea from 4% → 3% of frame so small/near documents are caught.
        val minArea = imageArea * 0.03        // was 0.04
        val minSide = diag * 0.05
        val minDist = diag * 0.04
        var best: ScoredResult? = null

        // Check top 10 contours (was 7) for better coverage in cluttered scenes.
        contours.sortedByDescending { Imgproc.contourArea(it) }
            .take(10)
            .forEach { contour ->
                val area = Imgproc.contourArea(contour)
                if (area < minArea) { contour.release(); return@forEach }
                val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)

                // Three epsilon values: tight → medium → loose approximation
                for (eps in doubleArrayOf(0.02, 0.05, 0.10)) {
                    val c2f = MatOfPoint2f(*contour.toArray())
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(c2f, approx, eps * peri, true)
                    c2f.release()
                    val pts = approx.toArray()
                    approx.release()

                    if (pts.size != 4) continue
                    if (!isValidQuad(pts, minDist, minSide)) continue

                    val ordered = orderByAngle(pts)
                    if (!isConvex(ordered)) continue

                    val corners = assignCorners(ordered, W, H) ?: continue
                    val score = scoreQuad(ordered, area / imageArea, W, H)
                    if (score > (best?.score ?: 0f)) {
                        best = ScoredResult(corners, score)
                    }
                    break
                }
                contour.release()
            }
        return best
    }

    // ══════════════════════════════════════════════════════════════════════
    // TIER 2: Hough line quad
    // ══════════════════════════════════════════════════════════════════════

    private fun houghQuad(
        edges: Mat, W: Float, H: Float, imageArea: Double, diag: Double
    ): ScoredResult? {
        // Lower minLen slightly to catch partial edges on cropped documents.
        val minLen = diag * 0.10          // was 0.12
        Imgproc.HoughLinesP(edges, linesMat, 1.0, Math.PI / 180, 40, minLen, 15.0)

        val n = linesMat.rows()
        if (n < 4) return null

        val count = minOf(n, 60)
        val lx1 = DoubleArray(count); val ly1 = DoubleArray(count)
        val lx2 = DoubleArray(count); val ly2 = DoubleArray(count)
        val angles = DoubleArray(count)

        for (i in 0 until count) {
            val l = linesMat.get(i, 0)
            lx1[i] = l[0]; ly1[i] = l[1]; lx2[i] = l[2]; ly2[i] = l[3]
            var a = Math.toDegrees(Math.atan2(ly2[i] - ly1[i], lx2[i] - lx1[i]))
            if (a < 0) a += 180.0
            angles[i] = a
        }

        data class LRef(val idx: Int, val len: Double)
        val hLines = mutableListOf<LRef>()
        val vLines = mutableListOf<LRef>()

        for (i in 0 until count) {
            val len = sqrt((lx2[i]-lx1[i]).let{it*it} + (ly2[i]-ly1[i]).let{it*it})
            val a = angles[i]
            if (a < 35 || a > 145) hLines.add(LRef(i, len))
            else vLines.add(LRef(i, len))
        }

        if (hLines.size < 2 || vLines.size < 2) return null

        val topH = hLines.sortedByDescending { it.len }.take(3)
        val topV = vLines.sortedByDescending { it.len }.take(3)

        val minDist = diag * 0.04; val minSide = diag * 0.05
        var best: ScoredResult? = null

        for (h1 in topH) for (h2 in topH) {
            if (h1.idx == h2.idx) continue
            for (v1 in topV) for (v2 in topV) {
                if (v1.idx == v2.idx) continue
                val p1 = lineIntersect(lx1,ly1,lx2,ly2,h1.idx,v1.idx) ?: continue
                val p2 = lineIntersect(lx1,ly1,lx2,ly2,h1.idx,v2.idx) ?: continue
                val p3 = lineIntersect(lx1,ly1,lx2,ly2,h2.idx,v1.idx) ?: continue
                val p4 = lineIntersect(lx1,ly1,lx2,ly2,h2.idx,v2.idx) ?: continue

                val pts = arrayOf(p1, p2, p3, p4)
                if (pts.any { it.x < -10 || it.x > W+10 || it.y < -10 || it.y > H+10 }) continue
                pts.forEach { it.x = it.x.coerceIn(0.0,W.toDouble()); it.y = it.y.coerceIn(0.0,H.toDouble()) }
                if (!isValidQuad(pts, minDist, minSide)) continue

                val ordered = orderByAngle(pts)
                if (!isConvex(ordered)) continue
                val area = shoelace(ordered)
                val ratio = area / imageArea
                if (ratio < 0.03 || ratio > 0.95) continue  // minArea lowered to match contour tier

                val corners = assignCorners(ordered, W, H) ?: continue
                val score = scoreQuad(ordered, ratio, W, H) * 0.85f
                if (score > (best?.score ?: 0f)) best = ScoredResult(corners, score)
            }
        }
        return best
    }

    private fun lineIntersect(
        x1: DoubleArray, y1: DoubleArray, x2: DoubleArray, y2: DoubleArray,
        a: Int, b: Int
    ): Point? {
        val d = (x1[a]-x2[a])*(y1[b]-y2[b]) - (y1[a]-y2[a])*(x1[b]-x2[b])
        if (abs(d) < 1e-6) return null
        val t = ((x1[a]-x1[b])*(y1[b]-y2[b]) - (y1[a]-y1[b])*(x1[b]-x2[b])) / d
        return Point(x1[a] + t*(x2[a]-x1[a]), y1[a] + t*(y2[a]-y1[a]))
    }

    // ══════════════════════════════════════════════════════════════════════
    // SCORING
    // ══════════════════════════════════════════════════════════════════════

    private fun scoreQuad(sorted: Array<Point>, areaRatio: Double, W: Float, H: Float): Float {
        if (areaRatio < 0.03 || areaRatio > 0.97) return 0f

        // Area scoring: wider sweet-spot for small documents (starts rewarding at 5%)
        val areaS = when {
            areaRatio < 0.05  -> 0.10f   // very small — possible but unlikely doc
            areaRatio < 0.08  -> 0.40f
            areaRatio < 0.13  -> 0.70f
            areaRatio <= 0.85 -> 1.0f    // main sweet-spot (unchanged)
            areaRatio <= 0.92 -> 0.60f
            areaRatio <= 0.96 -> 0.20f
            else -> 0f
        }

        val m = 8.0
        val edges = listOf(
            sorted.any { it.x < m }, sorted.any { it.x > W - m },
            sorted.any { it.y < m }, sorted.any { it.y > H - m })
        if (edges.all { it }) return 0f
        val borderP = if (edges.count { it } >= 3) 0.5f else 1.0f

        // Angle scoring: more forgiving for lightly perspective-skewed shots
        var angleS = 1.0f
        for (i in sorted.indices) {
            val a = sorted[(i+3)%4]; val b = sorted[i]; val c = sorted[(i+1)%4]
            val ab = Point(b.x-a.x, b.y-a.y); val cb = Point(b.x-c.x, b.y-c.y)
            val ang = Math.toDegrees(Math.atan2(
                abs(ab.x*cb.y - ab.y*cb.x), ab.x*cb.x + ab.y*cb.y))
            if (ang < 25.0) return 0f    // was 30 — allows slightly more skew
            val dev = abs(ang - 90.0)
            angleS *= when {
                dev <= 15 -> 1.0f        // perfect or near-perfect rectangle
                dev <= 25 -> 0.88f
                dev <= 35 -> 0.60f
                dev <= 45 -> 0.28f       // was 0.30 — mild tightening at extreme skew
                else      -> 0.08f
            }
        }

        val s = sides(sorted)
        val dW = (s[0]+s[2])/2.0; val dH = (s[1]+s[3])/2.0
        val asp = if (max(dW,dH) > 0) min(dW,dH)/max(dW,dH) else 0.0

        // Aspect: allow thin/tall documents (business cards, receipts)
        val aspS = when {
            asp < 0.10 -> return 0f
            asp < 0.20 -> 0.15f          // very thin — still plausible
            asp < 0.40 -> 0.50f
            asp < 0.55 -> 0.80f
            asp <= 1.0 -> 1.0f
            else -> 0.5f
        }

        val tb = min(s[0],s[2]) / max(s[0],s[2]).coerceAtLeast(1.0)
        val lr = min(s[1],s[3]) / max(s[1],s[3]).coerceAtLeast(1.0)
        val parS = ((tb+lr)/2.0).toFloat().coerceIn(0f, 1f)

        // Weights: angle and parallelism are the most reliable signals for
        // real documents; area and aspect support them.
        return (areaS * 0.22f + angleS * 0.32f + aspS.toFloat() * 0.18f + parS * 0.28f) * borderP
    }

    // ══════════════════════════════════════════════════════════════════════
    // CORNER ASSIGNMENT
    // ══════════════════════════════════════════════════════════════════════

    private fun assignCorners(ordered: Array<Point>, W: Float, H: Float): DocumentCorners? {
        var bestS = -1.0; var bestR = -1
        for (r in 0 until 4) {
            val tl = ordered[r]; val tr = ordered[(r+1)%4]
            val br = ordered[(r+2)%4]; val bl = ordered[(r+3)%4]
            var s = (br.x+br.y) - (tl.x+tl.y)
            if (tr.x > tl.x) s += (tr.x-tl.x)
            if (bl.y > tl.y) s += (bl.y-tl.y)
            if (br.y > tr.y) s += (br.y-tr.y)
            if (br.x > bl.x) s += (br.x-bl.x)
            if (s > bestS) { bestS = s; bestR = r }
        }
        if (bestR < 0) return null
        val tl = ordered[bestR]; val tr = ordered[(bestR+1)%4]
        val br = ordered[(bestR+2)%4]; val bl = ordered[(bestR+3)%4]
        return DocumentCorners(
            topLeft     = PointF(tl.x.toFloat().coerceIn(0f,W), tl.y.toFloat().coerceIn(0f,H)),
            topRight    = PointF(tr.x.toFloat().coerceIn(0f,W), tr.y.toFloat().coerceIn(0f,H)),
            bottomLeft  = PointF(bl.x.toFloat().coerceIn(0f,W), bl.y.toFloat().coerceIn(0f,H)),
            bottomRight = PointF(br.x.toFloat().coerceIn(0f,W), br.y.toFloat().coerceIn(0f,H)),
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // SCENE SIGNATURE
    // ══════════════════════════════════════════════════════════════════════

    private fun sceneSignature(gray: Mat): FloatArray {
        val rows = gray.rows(); val cols = gray.cols()
        val sig = FloatArray(16)
        val cH = rows/4; val cW = cols/4
        for (r in 0 until 4) for (c in 0 until 4) {
            val roi = gray.submat(r*cH, min((r+1)*cH,rows), c*cW, min((c+1)*cW,cols))
            sig[r*4+c] = Core.mean(roi).`val`[0].toFloat()
        }
        return sig
    }

    fun sceneDistance(a: FloatArray?, b: FloatArray?): Float {
        if (a == null || b == null || a.size != b.size) return Float.MAX_VALUE
        var sum = 0f
        for (i in a.indices) { val d = a[i]-b[i]; sum += d*d }
        return sqrt(sum / a.size)
    }

    // ══════════════════════════════════════════════════════════════════════
    // GEOMETRY UTILITIES
    // ══════════════════════════════════════════════════════════════════════

    private fun isValidQuad(pts: Array<Point>, minDist: Double, minSide: Double): Boolean {
        for (i in 0 until 4) for (j in i+1 until 4) {
            val dx = pts[i].x-pts[j].x; val dy = pts[i].y-pts[j].y
            if (sqrt(dx*dx+dy*dy) < minDist) return false
        }
        val o = orderByAngle(pts)
        for (i in 0 until 4) {
            val a = o[i]; val b = o[(i+1)%4]
            val dx = b.x-a.x; val dy = b.y-a.y
            if (sqrt(dx*dx+dy*dy) < minSide) return false
        }
        return true
    }

    private fun sides(pts: Array<Point>) = (0..3).map { i ->
        val a = pts[i]; val b = pts[(i+1)%4]
        sqrt((b.x-a.x)*(b.x-a.x) + (b.y-a.y)*(b.y-a.y))
    }

    private fun shoelace(pts: Array<Point>): Double {
        var a = 0.0
        for (i in pts.indices) { val j = (i+1)%pts.size
            a += pts[i].x*pts[j].y - pts[j].x*pts[i].y }
        return abs(a)/2.0
    }

    private fun orderByAngle(pts: Array<Point>): Array<Point> {
        val cx = pts.map{it.x}.average(); val cy = pts.map{it.y}.average()
        return pts.sortedBy { Math.atan2(it.y-cy, it.x-cx) }.toTypedArray()
    }

    private fun isConvex(pts: Array<Point>): Boolean {
        var sign = 0
        for (i in pts.indices) {
            val o = pts[i]; val a = pts[(i+1)%4]; val b = pts[(i+2)%4]
            val cross = (a.x-o.x)*(b.y-o.y) - (a.y-o.y)*(b.x-o.x)
            val sg = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (sg != 0) { if (sign == 0) sign = sg else if (sign != sg) return false }
        }
        return sign != 0
    }
}