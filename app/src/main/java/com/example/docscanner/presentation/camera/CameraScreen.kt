package com.example.docscanner.presentation.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.docscanner.data.camera.DocumentDetector
import com.example.docscanner.domain.model.DocumentCorners
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val mainHandler = Handler(Looper.getMainLooper())

private data class NormCorners(
    val tl: PointF, val tr: PointF,
    val bl: PointF, val br: PointF
) {
    fun avgDrift(other: NormCorners): Float = (
            dist(tl, other.tl) + dist(tr, other.tr) +
                    dist(bl, other.bl) + dist(br, other.br)
            ) / 4f

    private fun dist(a: PointF, b: PointF): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

// ── Frame sampling ─────────────────────────────────────────────────────────────
// Analyse every 2nd frame → ~15 fps at 30fps camera.
private const val FRAME_SKIP = 2

// ── Two-stage lock-on ──────────────────────────────────────────────────────────
// Each stable detection frame adds +3 to hitScore (unstable adds 0 — no penalty).
//
// Stage 1 — PRE_LOCK  (score ≥ 6,  ~2 stable frames):
//   Overlay FREEZES at its current position. Yellow border + "Locking on…".
//   The overlay will NEVER chase a new detection again until a full reset.
//   This is what prevents the box from "going to another direction" mid-fill.
//
// Stage 2 — FULL_LOCK (score ≥ 12, ~4 stable frames):
//   Green border + "Ready / Hold steady".  Auto-capture countdown starts.
private const val PRE_LOCK_THRESHOLD = 6
private const val LOCK_THRESHOLD     = 12

// ── Miss tolerance — very forgiving ───────────────────────────────────────────
// Only start decaying hitScore after 6 consecutive missed frames (~0.4 s at 15fps).
// This means passing a hand in front of the camera, or a brief shadow, will NOT
// break the detection at all.
private const val MISS_TOLERANCE        = 6
// Require 10 consecutive misses before fully resetting (~0.65 s).
// The document must genuinely leave the frame to trigger a reset.
private const val FRAMES_TO_UNLOCK_MISS = 10

// Scene-change threshold — unchanged, works well.
private const val SCENE_CHANGE_THRESHOLD = 12f

// ── EMA alphas ─────────────────────────────────────────────────────────────────
// ALPHA_DISPLAY_HUNT: how fast the overlay tracks the document while hunting.
//   0.20 → very smooth glide. Eliminates the jitter / vibrating effect.
//   The overlay appears to "settle" onto the document edge rather than snap.
private const val ALPHA_DISPLAY_HUNT = 0.20f

// ALPHA_LOCKED: tiny continuous correction while locked. Keeps overlay glued.
private const val ALPHA_LOCKED = 0.04f

// ALPHA_SMOOTH: internal EMA for stability comparison only (never drawn).
//   0.35 → converges faster than old 0.25 while still absorbing Canny noise.
private const val ALPHA_SMOOTH = 0.35f

// ── Stability gate ─────────────────────────────────────────────────────────────
// avgDrift on the SMOOTHED signal must be < this to score as "stable".
// 0.10 = 10% of frame — forgives all normal hand tremor.
private const val STABILITY_THRESHOLD = 0.10f

// ── Auto-capture ───────────────────────────────────────────────────────────────
// 5 consecutive steady analysis-frames ≈ 0.33 s.  Short but not accidental.
private const val STEADY_FRAMES_TO_CAPTURE = 5
private const val STEADY_DRIFT_THRESHOLD   = 0.030f

private const val POST_CAPTURE_COOLDOWN = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    pageCount: Int,
    lastCapturedBitmap: Bitmap?,
    onPhotoCaptured: (Bitmap, DocumentCorners?) -> Unit,
    onPreview: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzerExec   = remember { Executors.newSingleThreadExecutor() }
    val frameCounter   = remember { AtomicInteger(0) }
    val analyzing      = remember { AtomicBoolean(false) }

    var isFlashOn     by remember { mutableStateOf(false) }
    var isCapturing   by remember { mutableStateOf(false) }
    var autoScanOn    by remember { mutableStateOf(true) }
    var autoCaptureOn by remember { mutableStateOf(true) }

    // displayNorm — what the overlay draws.
    //   HUNTING    : updated at ALPHA_DISPLAY_HUNT speed (smooth glide).
    //   PRE-LOCKED : FROZEN — never updated from new raw detections.
    //   LOCKED     : tiny drift via ALPHA_LOCKED, effectively frozen.
    var displayNorm      by remember { mutableStateOf<NormCorners?>(null) }

    // smoothedNorm — internal heavy EMA, NEVER drawn.  Used only for stability check.
    var smoothedNorm     by remember { mutableStateOf<NormCorners?>(null) }
    var prevSmoothedNorm by remember { mutableStateOf<NormCorners?>(null) }

    var hitScore          by remember { mutableIntStateOf(0) }
    var consecutiveMisses by remember { mutableIntStateOf(0) }
    var isLocked          by remember { mutableStateOf(false) }
    var isPreLocked       by remember { mutableStateOf(false) }
    var frozenNorm        by remember { mutableStateOf<NormCorners?>(null) }
    var cooldownFrames    by remember { mutableIntStateOf(0) }
    var lockedSceneSig    by remember { mutableStateOf<FloatArray?>(null) }
    var steadyFrames      by remember { mutableIntStateOf(0) }

    val autoScanRef     = rememberUpdatedState(autoScanOn)
    val autoCaptureRef  = rememberUpdatedState(autoCaptureOn)
    val isLockedRef     = rememberUpdatedState(isLocked)
    val isPreLockedRef  = rememberUpdatedState(isPreLocked)
    val cooldownRef     = rememberUpdatedState(cooldownFrames)
    val isCapturingRef  = rememberUpdatedState(isCapturing)

    fun resetDetection() {
        displayNorm = null; smoothedNorm = null; prevSmoothedNorm = null
        frozenNorm = null; hitScore = 0; consecutiveMisses = 0
        isLocked = false; isPreLocked = false
        lockedSceneSig = null; steadyFrames = 0
    }

    val huntProgress by remember {
        derivedStateOf { (hitScore.toFloat() / LOCK_THRESHOLD).coerceIn(0f, 1f) }
    }
    val steadyProgress by remember {
        derivedStateOf {
            if (isLocked && autoCaptureOn)
                (steadyFrames.toFloat() / STEADY_FRAMES_TO_CAPTURE).coerceIn(0f, 1f)
            else 0f
        }
    }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (displayNorm != null) 1f else 0f,
        animationSpec = tween(if (displayNorm != null) 120 else 60),
        label = "overlayAlpha"
    )

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val reusableBitmapRef = remember { mutableStateOf<Bitmap?>(null) }

    fun doCapture() {
        if (isCapturing) return
        isCapturing = true
        val capturedNorm = frozenNorm ?: displayNorm
        captureImage(imageCapture, context) { bmp ->
            isCapturing = false
            if (bmp != null) {
                val corners = capturedNorm?.let { n ->
                    DocumentCorners(
                        topLeft     = PointF(n.tl.x * bmp.width,  n.tl.y * bmp.height),
                        topRight    = PointF(n.tr.x * bmp.width,  n.tr.y * bmp.height),
                        bottomLeft  = PointF(n.bl.x * bmp.width,  n.bl.y * bmp.height),
                        bottomRight = PointF(n.br.x * bmp.width,  n.br.y * bmp.height)
                    )
                }
                onPhotoCaptured(bmp, corners)
            }
            resetDetection()
            cooldownFrames = POST_CAPTURE_COOLDOWN
        }
    }

    val imageAnalyzer = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build().also { analysis ->
                analysis.setAnalyzer(analyzerExec) analyzer@{ proxy ->
                    if (!autoScanRef.value) { proxy.close(); return@analyzer }
                    if (frameCounter.incrementAndGet() % FRAME_SKIP != 0) {
                        proxy.close(); return@analyzer
                    }
                    if (!analyzing.compareAndSet(false, true)) { proxy.close(); return@analyzer }

                    val upright = try { extractBitmap(proxy, reusableBitmapRef) }
                    catch (_: Exception) {
                        proxy.close(); analyzing.set(false); return@analyzer
                    }
                    proxy.close()

                    val longSide = maxOf(upright.width, upright.height)
                    val target   = DocumentDetector.RECOMMENDED_RESOLUTION
                    val frame = if (longSide > target) {
                        val s = target.toFloat() / longSide
                        Bitmap.createScaledBitmap(upright,
                            (upright.width * s).toInt(), (upright.height * s).toInt(), false)
                    } else upright

                    val result = DocumentDetector.detect(frame)
                    if (frame !== upright && !frame.isRecycled) frame.recycle()
                    analyzing.set(false)

                    val detected: NormCorners? =
                        if (result.isConfident && result.corners != null) {
                            val c = result.corners
                            NormCorners(
                                tl = PointF(c.topLeft.x  / frame.width,  c.topLeft.y  / frame.height),
                                tr = PointF(c.topRight.x / frame.width,  c.topRight.y / frame.height),
                                bl = PointF(c.bottomLeft.x / frame.width,c.bottomLeft.y / frame.height),
                                br = PointF(c.bottomRight.x/ frame.width,c.bottomRight.y/ frame.height)
                            )
                        } else null

                    val sceneSig = result.sceneSig

                    mainHandler.post {
                        if (cooldownRef.value > 0) { cooldownFrames--; return@post }
                        if (isCapturingRef.value) return@post

                        val wasLocked    = isLockedRef.value
                        val wasPreLocked = isPreLockedRef.value

                        // Scene-change → instant unlock
                        if ((wasLocked || wasPreLocked) && lockedSceneSig != null) {
                            val d = DocumentDetector.sceneDistance(lockedSceneSig, sceneSig)
                            if (d > SCENE_CHANGE_THRESHOLD) { resetDetection(); return@post }
                        }

                        if (detected != null) {
                            consecutiveMisses = 0

                            // Update internal smoothed corners (never drawn)
                            val prevSm = smoothedNorm
                            val newSmoothed = if (prevSm == null) detected
                            else lerp(detected, prevSm, ALPHA_SMOOTH)
                            smoothedNorm = newSmoothed

                            // Stability check on smoothed signal
                            val prevSm2 = prevSmoothedNorm
                            val isStable = prevSm2 == null ||
                                    newSmoothed.avgDrift(prevSm2) < STABILITY_THRESHOLD
                            prevSmoothedNorm = newSmoothed

                            // Hit scoring:
                            //   Stable   → +3 (makes progress toward lock)
                            //   Unstable →  0 (pause, NOT a penalty — bar never drops on tremor)
                            if (isStable) {
                                hitScore = (hitScore + 3).coerceAtMost(LOCK_THRESHOLD + 3)
                            }
                            // Intentionally no else — unstable = no-op on score

                            when {
                                wasLocked -> {
                                    // Tiny drift so the overlay stays glued to the document
                                    displayNorm = frozenNorm?.let { lerp(detected, it, ALPHA_LOCKED) }
                                    frozenNorm  = displayNorm

                                    if (autoCaptureRef.value) {
                                        val drift = prevSm2?.let { newSmoothed.avgDrift(it) } ?: 1f
                                        if (drift < STEADY_DRIFT_THRESHOLD) {
                                            steadyFrames++
                                            if (steadyFrames >= STEADY_FRAMES_TO_CAPTURE) {
                                                steadyFrames = 0
                                                doCapture()
                                            }
                                        } else {
                                            steadyFrames = (steadyFrames - 1).coerceAtLeast(0)
                                        }
                                    }
                                }

                                wasPreLocked -> {
                                    // Overlay stays FROZEN — do not chase new detections.
                                    // This is the key fix: the box never moves while the
                                    // progress bar is filling.
                                    if (hitScore >= LOCK_THRESHOLD) {
                                        isLocked    = true
                                        isPreLocked = false
                                        lockedSceneSig = sceneSig
                                        steadyFrames   = 0
                                        Log.d("CameraScreen", "LOCKED (score=$hitScore)")
                                    }
                                }

                                else -> {
                                    // HUNTING — overlay glides smoothly toward the document.
                                    // ALPHA_DISPLAY_HUNT = 0.20 means a slow, gentle drift,
                                    // not a jittery snap.
                                    val prevDisp = displayNorm
                                    displayNorm = if (prevDisp == null) detected
                                    else lerp(detected, prevDisp, ALPHA_DISPLAY_HUNT)

                                    if (hitScore >= PRE_LOCK_THRESHOLD) {
                                        // Freeze the overlay RIGHT NOW at its current position.
                                        isPreLocked    = true
                                        frozenNorm     = displayNorm
                                        lockedSceneSig = sceneSig
                                        Log.d("CameraScreen", "PRE-LOCKED (score=$hitScore)")
                                    }
                                }
                            }

                        } else {
                            // No detection this frame
                            consecutiveMisses++

                            // Forgive brief dropouts completely — only start penalizing
                            // after MISS_TOLERANCE consecutive misses.
                            if (consecutiveMisses > MISS_TOLERANCE) {
                                // Gentle -1 per frame.  At 15fps this takes several seconds
                                // to fully reset — document must really be gone.
                                hitScore = (hitScore - 1).coerceAtLeast(0)
                            }

                            steadyFrames = (steadyFrames - 1).coerceAtLeast(0)

                            when {
                                wasLocked || wasPreLocked -> {
                                    // Keep the frozen overlay visible
                                    displayNorm = frozenNorm
                                    if (consecutiveMisses >= FRAMES_TO_UNLOCK_MISS) {
                                        resetDetection()
                                    }
                                }
                                else -> {
                                    // While hunting: hold the display for MISS_TOLERANCE frames
                                    // before clearing — avoids flicker on brief detection gaps.
                                    if (consecutiveMisses >= MISS_TOLERANCE) {
                                        displayNorm = null
                                    }
                                }
                            }
                            // Keep smoothedNorm alive — when detection resumes, stability
                            // passes immediately because smoothed position is still close.
                        }
                    }
                }
            }
    }

    LaunchedEffect(autoScanOn)    { if (!autoScanOn) resetDetection() }
    LaunchedEffect(autoCaptureOn) { steadyFrames = 0 }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> uris.forEach { uri -> uriToBitmap(context, uri)?.let { onPhotoCaptured(it, null) } } }

    // ══════════════════════════════════════════════════════════════════════════
    // UI
    // ══════════════════════════════════════════════════════════════════════════

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {

        // ── TOP BAR ──────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().height(56.dp).background(Color.Black).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                if (pageCount == 0) "Scan Document"
                else "$pageCount ${if (pageCount == 1) "page" else "pages"}",
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium
            )
            Row {
                IconButton(onClick = { autoScanOn = !autoScanOn }) {
                    Icon(
                        if (autoScanOn) Icons.Default.DocumentScanner else Icons.Default.CropFree,
                        "Auto Scan",
                        tint = if (autoScanOn) Color(0xFF00E676) else Color.White)
                }
                IconButton(onClick = { autoCaptureOn = !autoCaptureOn }) {
                    Icon(
                        if (autoCaptureOn) Icons.Default.Timer else Icons.Default.TimerOff,
                        "Auto Capture",
                        tint = if (autoCaptureOn) Color(0xFF00E676) else Color.White)
                }
                IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                    Icon(Icons.Default.PhotoLibrary, "Gallery", tint = Color.White)
                }
                IconButton(onClick = { isFlashOn = !isFlashOn }) {
                    Icon(
                        if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        "Flash", tint = Color.White)
                }
            }
        }

        // ── CAMERA + OVERLAY ─────────────────────────────────────────────────
        Box(Modifier.weight(1f).fillMaxWidth()) {

            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }.also { pv ->
                        ProcessCameraProvider.getInstance(ctx).addListener({
                            val provider = ProcessCameraProvider.getInstance(ctx).get()
                            val preview  = Preview.Builder().build()
                                .also { it.setSurfaceProvider(pv.surfaceProvider) }
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview, imageCapture, imageAnalyzer)
                            } catch (e: Exception) { e.printStackTrace() }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── Document outline overlay ─────────────────────────────────────
            val norm = displayNorm
            if (autoScanOn && norm != null && overlayAlpha > 0.01f) {
                Canvas(Modifier.fillMaxSize()) {
                    val pw = size.width; val ph = size.height
                    val tl = Offset(norm.tl.x * pw, norm.tl.y * ph)
                    val tr = Offset(norm.tr.x * pw, norm.tr.y * ph)
                    val br = Offset(norm.br.x * pw, norm.br.y * ph)
                    val bl = Offset(norm.bl.x * pw, norm.bl.y * ph)
                    val poly = Path().apply {
                        moveTo(tl.x, tl.y); lineTo(tr.x, tr.y)
                        lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
                    }
                    // White → Yellow (pre-lock) → Green (locked)
                    val color = when {
                        isLocked    -> Color(0xFF00E676)
                        isPreLocked -> Color(0xFFFFD600)
                        else        -> Color.White
                    }
                    drawPath(poly, color.copy(alpha = overlayAlpha * 0.15f))
                    drawPath(poly, color.copy(alpha = overlayAlpha),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                    val bLen = 24.dp.toPx(); val bW = 3.5.dp.toPx()
                    fun bracket(pt: Offset, hd: Float, vd: Float) {
                        drawLine(color.copy(alpha = overlayAlpha), pt,
                            Offset(pt.x + hd * bLen, pt.y), bW, StrokeCap.Round)
                        drawLine(color.copy(alpha = overlayAlpha), pt,
                            Offset(pt.x, pt.y + vd * bLen), bW, StrokeCap.Round)
                    }
                    bracket(tl, 1f, 1f); bracket(tr, -1f, 1f)
                    bracket(bl, 1f, -1f); bracket(br, -1f, -1f)
                }
            }

            // ── Status badge ─────────────────────────────────────────────────
            if (autoScanOn && (hitScore > 0 || isLocked || isPreLocked)) {
                Box(
                    Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(0.65f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when {
                            isLocked && autoCaptureOn && steadyProgress > 0.05f -> {
                                CircularProgressIndicator(
                                    progress = { steadyProgress },
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF00E676),
                                    trackColor = Color.White.copy(0.3f),
                                    strokeWidth = 2.dp)
                                Text("Hold steady…", color = Color(0xFF00E676),
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            isLocked -> {
                                Icon(Icons.Default.Lock, null,
                                    tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                                Text(
                                    if (autoCaptureOn) "Keep steady to capture"
                                    else "Ready to capture",
                                    color = Color(0xFF00E676),
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            isPreLocked -> {
                                CircularProgressIndicator(
                                    progress = { huntProgress },
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFFFFD600),
                                    trackColor = Color.White.copy(0.3f),
                                    strokeWidth = 2.dp)
                                Text("Locking on…", color = Color(0xFFFFD600),
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            else -> {
                                CircularProgressIndicator(
                                    progress = { huntProgress },
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White, strokeWidth = 2.dp)
                                Text("Detecting…", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ── Capture flash ────────────────────────────────────────────────
            val flashAlpha by animateFloatAsState(
                targetValue = if (isCapturing) 0.3f else 0f,
                animationSpec = if (isCapturing) tween(50) else tween(200),
                label = "flash"
            )
            if (flashAlpha > 0.01f) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))
            }
        }

        // ── BOTTOM BAR ───────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Thumbnail
            Box(Modifier.size(width = 50.dp, height = 64.dp), contentAlignment = Alignment.Center) {
                if (lastCapturedBitmap != null) {
                    Box(
                        Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, Color.White.copy(0.7f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onPreview)
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val bmp = lastCapturedBitmap
                            val s = minOf(size.width / bmp.width, size.height / bmp.height)
                            val w = bmp.width * s; val h = bmp.height * s
                            val x = (size.width - w) / 2f; val y = (size.height - h) / 2f
                            drawContext.canvas.nativeCanvas.drawBitmap(
                                bmp, null, android.graphics.RectF(x, y, x + w, y + h), null)
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(2.dp).size(16.dp)
                                .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$pageCount", color = Color.White,
                                fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Shutter — border colour signals detection state
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .border(3.dp, when {
                        isLocked    -> Color(0xFF00E676)
                        isPreLocked -> Color(0xFFFFD600)
                        else        -> Color.White
                    }, CircleShape)
                    .padding(5.dp).clip(CircleShape)
                    .background(if (isCapturing) Color.Gray else Color.White)
                    .clickable(enabled = !isCapturing) { doCapture() },
                contentAlignment = Alignment.Center
            ) {
                if (isCapturing) CircularProgressIndicator(
                    Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
            }

            // Done
            Box(Modifier.size(width = 50.dp, height = 64.dp), contentAlignment = Alignment.Center) {
                if (pageCount > 0) {
                    Box(
                        Modifier.size(50.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(onClick = onDone),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, "Done",
                            tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// HELPERS
// ══════════════════════════════════════════════════════════════════════════════

private fun lerp(new: NormCorners, old: NormCorners, alpha: Float): NormCorners {
    fun f(n: Float, o: Float) = alpha * n + (1f - alpha) * o
    return NormCorners(
        tl = PointF(f(new.tl.x, old.tl.x), f(new.tl.y, old.tl.y)),
        tr = PointF(f(new.tr.x, old.tr.x), f(new.tr.y, old.tr.y)),
        bl = PointF(f(new.bl.x, old.bl.x), f(new.bl.y, old.bl.y)),
        br = PointF(f(new.br.x, old.br.x), f(new.br.y, old.br.y))
    )
}

private fun extractBitmap(proxy: ImageProxy, ref: MutableState<Bitmap?>): Bitmap {
    val buffer = proxy.planes[0].buffer
    val w = proxy.width; val h = proxy.height; val rot = proxy.imageInfo.rotationDegrees
    var raw = ref.value
    if (raw == null || raw.width != w || raw.height != h || raw.isRecycled) {
        raw?.recycle()
        raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        ref.value = raw
    }
    buffer.rewind(); raw.copyPixelsFromBuffer(buffer)
    return if (rot != 0) Bitmap.createBitmap(raw, 0, 0, w, h,
        Matrix().apply { postRotate(rot.toFloat()) }, false)
    else raw
}

private fun captureImage(cap: ImageCapture, ctx: Context, cb: (Bitmap?) -> Unit) {
    cap.takePicture(ContextCompat.getMainExecutor(ctx),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(p: ImageProxy) { cb(proxyToBitmap(p)); p.close() }
            override fun onError(e: ImageCaptureException) { e.printStackTrace(); cb(null) }
        })
}

private fun proxyToBitmap(p: ImageProxy): Bitmap? = try {
    val buf = p.planes[0].buffer
    val bytes = ByteArray(buf.remaining()); buf.get(bytes)
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val rot = p.imageInfo.rotationDegrees
    if (rot != 0) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height,
        Matrix().apply { postRotate(rot.toFloat()) }, true)
    else bmp
} catch (_: Exception) { null }

private fun uriToBitmap(ctx: Context, uri: Uri): Bitmap? = try {
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
} catch (_: Exception) { null }