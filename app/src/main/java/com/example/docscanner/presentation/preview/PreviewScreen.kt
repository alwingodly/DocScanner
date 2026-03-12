package com.example.docscanner.presentation.preview

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.example.docscanner.domain.model.DocumentCorners
import com.example.docscanner.domain.model.ScannedPage
import kotlin.math.sqrt

// ══════════════════════════════════════════════════════════════
// Corner normalisation
// ══════════════════════════════════════════════════════════════

private fun normalizeCorners(c: DocumentCorners): DocumentCorners {
    val pts = listOf(c.topLeft, c.topRight, c.bottomLeft, c.bottomRight)
    val cx = pts.map { it.x }.average().toFloat()
    val cy = pts.map { it.y }.average().toFloat()
    val tl = pts.filter { it.x < cx && it.y < cy }
    val tr = pts.filter { it.x >= cx && it.y < cy }
    val bl = pts.filter { it.x < cx && it.y >= cy }
    val br = pts.filter { it.x >= cx && it.y >= cy }
    if (listOf(tl, tr, bl, br).all { it.size == 1 }) {
        return DocumentCorners(tl.first(), tr.first(), bl.first(), br.first())
    }
    val sorted = pts.sortedBy { pt ->
        val angle = kotlin.math.atan2((pt.y - cy).toDouble(), (pt.x - cx).toDouble())
        val shifted = angle - kotlin.math.PI / 4
        if (shifted < 0) shifted + 2 * kotlin.math.PI else shifted
    }
    return DocumentCorners(sorted[3], sorted[0], sorted[2], sorted[1])
}

// ══════════════════════════════════════════════════════════════
// PREVIEW SCREEN
// ══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PreviewScreen(
    pages: List<ScannedPage>,
    onCornersChanged: (Int, DocumentCorners) -> Unit,
    onDeletePage: (Int) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    if (pages.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val pagerState  = rememberPagerState(pageCount = { pages.size })
    val currentPage = pagerState.currentPage
    var cropMode    by remember { mutableStateOf(false) }

    // Reset crop mode when swiping to a new page
    LaunchedEffect(currentPage) { cropMode = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Page ${currentPage + 1} of ${pages.size}",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { onDeletePage(currentPage) }) {
                        Icon(Icons.Default.Delete, "Delete page", tint = Color(0xFFEF5350))
                    }
                    // Crop toggle — ✓ icon while in crop mode signals "tap to confirm"
                    IconButton(onClick = { cropMode = !cropMode }) {
                        Icon(
                            if (cropMode) Icons.Default.Check else Icons.Default.Crop,
                            "Crop",
                            tint = if (cropMode) Color(0xFF00E676) else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        bottomBar = {
            BottomBar(
                pageCount   = pages.size,
                currentPage = currentPage,
                cropMode    = cropMode,
                onDone      = onDone
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->

        HorizontalPager(
            state             = pagerState,
            modifier          = Modifier.fillMaxSize().padding(innerPadding),
            userScrollEnabled = !cropMode,  // block swipe while adjusting corners
            key               = { it }
        ) { pageIndex ->
            val page = pages[pageIndex]

            if (cropMode) {
                // ── Crop mode: show ORIGINAL image with draggable handles ──
                CropCanvas(
                    bitmap           = page.originalBitmap,
                    corners          = page.corners,
                    onCornersChanged = { corners -> onCornersChanged(pageIndex, corners) },
                    modifier         = Modifier.fillMaxSize()
                )
            } else {
                // ── Normal mode: show only the CROPPED result ─────────────
                // croppedBitmap is the perspective-corrected output;
                // fall back to displayBitmap / originalBitmap if not yet processed
                val bitmapToShow = page.croppedBitmap
                    ?: page.displayBitmap
                    ?: page.originalBitmap
                CroppedView(
                    bitmap   = bitmapToShow,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// CROPPED VIEW — fit-to-screen display of the cropped bitmap
// ══════════════════════════════════════════════════════════════

@Composable
private fun CroppedView(bitmap: Bitmap, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color.Black).clipToBounds()) {
        val iw    = bitmap.width.toFloat()
        val ih    = bitmap.height.toFloat()
        val scale = minOf(size.width / iw, size.height / ih)
        val ox    = (size.width  - iw * scale) / 2f
        val oy    = (size.height - ih * scale) / 2f
        drawContext.canvas.nativeCanvas.drawBitmap(
            bitmap, null,
            android.graphics.RectF(ox, oy, ox + iw * scale, oy + ih * scale), null
        )
    }
}

// ══════════════════════════════════════════════════════════════
// BOTTOM BAR
// ══════════════════════════════════════════════════════════════

@Composable
private fun BottomBar(
    pageCount: Int,
    currentPage: Int,
    cropMode: Boolean,
    onDone: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .navigationBarsPadding()
    ) {
        // Page dots
        if (pageCount > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                val visible = minOf(pageCount, 9)
                val start   = if (pageCount <= 9) 0
                else (currentPage - 4).coerceIn(0, pageCount - 9)
                repeat(visible) { i ->
                    val idx    = start + i
                    val active = idx == currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (active) 8.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (active) Color.White else Color.White.copy(0.3f))
                    )
                }
            }
        }

        // Hint while in crop mode
        AnimatedVisibility(visible = cropMode, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Drag corners to adjust  ·  tap ✓ to confirm",
                    color = Color.White.copy(0.5f),
                    fontSize = 12.sp
                )
            }
        }

        Button(
            onClick  = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
        ) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Done  ·  $pageCount ${if (pageCount == 1) "page" else "pages"}")
        }
    }
}

// ══════════════════════════════════════════════════════════════
// CROP CANVAS — original image with draggable corner handles
// ══════════════════════════════════════════════════════════════

@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    corners: DocumentCorners?,
    onCornersChanged: (DocumentCorners) -> Unit,
    modifier: Modifier = Modifier
) {
    var localCorners by remember { mutableStateOf(corners) }
    var lastHash     by remember { mutableIntStateOf(corners.hashCode()) }
    val  curHash = corners.hashCode()
    if (curHash != lastHash) { localCorners = corners; lastHash = curHash }

    var zoom    by remember { mutableFloatStateOf(1f) }
    var panX    by remember { mutableFloatStateOf(0f) }
    var panY    by remember { mutableFloatStateOf(0f) }
    var canvasW by remember { mutableFloatStateOf(1f) }
    var canvasH by remember { mutableFloatStateOf(1f) }
    var baseSf  by remember { mutableFloatStateOf(1f) }
    var draggingIdx by remember { mutableIntStateOf(-1) }

    fun imgLeft() = (canvasW - bitmap.width  * baseSf * zoom) / 2f + panX
    fun imgTop()  = (canvasH - bitmap.height * baseSf * zoom) / 2f + panY

    fun clampPan(px: Float, py: Float, z: Float): Pair<Float, Float> {
        val hw = ((bitmap.width  * baseSf * z) - canvasW).coerceAtLeast(0f) / 2f
        val hh = ((bitmap.height * baseSf * z) - canvasH).coerceAtLeast(0f) / 2f
        return px.coerceIn(-hw, hw) to py.coerceIn(-hh, hh)
    }

    fun applyZoom(factor: Float, focalX: Float, focalY: Float) {
        val newZoom = (zoom * factor).coerceIn(1f, 8f)
        if (newZoom == zoom) return
        val ratio = newZoom / zoom
        val nx = focalX + (panX - focalX) * ratio
        val ny = focalY + (panY - focalY) * ratio
        zoom = newZoom
        if (newZoom <= 1f) { panX = 0f; panY = 0f }
        else { val (cx, cy) = clampPan(nx, ny, newZoom); panX = cx; panY = cy }
    }

    fun screenHandles(): List<Offset> {
        val c  = localCorners ?: return emptyList()
        val zs = baseSf * zoom
        val ox = imgLeft(); val oy = imgTop()
        return listOf(
            Offset(c.topLeft.x     * zs + ox, c.topLeft.y     * zs + oy),
            Offset(c.topRight.x    * zs + ox, c.topRight.y    * zs + oy),
            Offset(c.bottomLeft.x  * zs + ox, c.bottomLeft.y  * zs + oy),
            Offset(c.bottomRight.x * zs + ox, c.bottomRight.y * zs + oy)
        )
    }

    fun hitTest(touch: Offset, threshold: Float = 80f): Int {
        var best = -1; var bestD = threshold
        screenHandles().forEachIndexed { i, p ->
            val d = sqrt((touch.x - p.x) * (touch.x - p.x) + (touch.y - p.y) * (touch.y - p.y))
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    Box(modifier.clipToBounds()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val firstId   = firstDown.id
                        val firstPos  = firstDown.position
                        val cornerHit  = hitTest(firstPos)
                        var isDragging = cornerHit >= 0
                        draggingIdx    = cornerHit

                        var secondId: PointerId? = null
                        var prevA = firstPos; var prevB = firstPos; var prevDist = 0f

                        fun fingerDist(a: Offset, b: Offset) =
                            sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
                                .coerceAtLeast(0.1f)

                        do {
                            val event: PointerEvent = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            when {
                                pressed.size >= 2 && secondId == null -> {
                                    isDragging = false; draggingIdx = -1
                                    secondId = pressed.first { it.id != firstId }.id
                                    val a = pressed.first { it.id == firstId }.position
                                    val b = pressed.first { it.id == secondId!! }.position
                                    prevA = a; prevB = b; prevDist = fingerDist(a, b)
                                    pressed.fastForEach { it.consume() }
                                }
                                pressed.size >= 2 && secondId != null -> {
                                    val pA = pressed.firstOrNull { it.id == firstId }
                                    val pB = pressed.firstOrNull { it.id == secondId }
                                    if (pA != null && pB != null) {
                                        val curA = pA.position; val curB = pB.position
                                        val curDist = fingerDist(curA, curB)
                                        val fx = (curA.x + curB.x) / 2f
                                        val fy = (curA.y + curB.y) / 2f
                                        if (prevDist > 0f) applyZoom(curDist / prevDist, fx, fy)
                                        if (zoom > 1f) {
                                            val pmx = (prevA.x + prevB.x) / 2f
                                            val pmy = (prevA.y + prevB.y) / 2f
                                            val (cx, cy) = clampPan(panX + (fx - pmx), panY + (fy - pmy), zoom)
                                            panX = cx; panY = cy
                                        }
                                        prevA = curA; prevB = curB; prevDist = curDist
                                        pressed.fastForEach { it.consume() }
                                    }
                                }
                                pressed.size == 1 && secondId == null -> {
                                    val ptr = pressed.first()
                                    if (ptr.positionChanged()) {
                                        if (isDragging && draggingIdx >= 0) {
                                            val c = localCorners
                                            if (c != null) {
                                                val zs = baseSf * zoom
                                                val dx = (ptr.position.x - ptr.previousPosition.x) / zs
                                                val dy = (ptr.position.y - ptr.previousPosition.y) / zs
                                                val cur = when (draggingIdx) {
                                                    0 -> c.topLeft; 1 -> c.topRight
                                                    2 -> c.bottomLeft; else -> c.bottomRight
                                                }
                                                val np = PointF(
                                                    (cur.x + dx).coerceIn(0f, bitmap.width.toFloat()),
                                                    (cur.y + dy).coerceIn(0f, bitmap.height.toFloat())
                                                )
                                                localCorners = when (draggingIdx) {
                                                    0 -> c.copy(topLeft     = np)
                                                    1 -> c.copy(topRight    = np)
                                                    2 -> c.copy(bottomLeft  = np)
                                                    else -> c.copy(bottomRight = np)
                                                }
                                            }
                                        } else if (zoom > 1f) {
                                            val dx = ptr.position.x - ptr.previousPosition.x
                                            val dy = ptr.position.y - ptr.previousPosition.y
                                            val (cx, cy) = clampPan(panX + dx, panY + dy, zoom)
                                            panX = cx; panY = cy
                                        }
                                        if (isDragging || zoom > 1f) ptr.consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (isDragging) {
                            val normalized = localCorners?.let { normalizeCorners(it) }
                            localCorners = normalized
                            normalized?.let { onCornersChanged(it) }
                        }
                        draggingIdx = -1
                    }
                }
        ) {
            canvasW = size.width; canvasH = size.height
            val iw = bitmap.width.toFloat(); val ih = bitmap.height.toFloat()
            baseSf = minOf(canvasW / iw, canvasH / ih)
            val zs = baseSf * zoom
            val ox = imgLeft(); val oy = imgTop()

            drawContext.canvas.nativeCanvas.drawBitmap(
                bitmap, null,
                android.graphics.RectF(ox, oy, ox + iw * zs, oy + ih * zs), null
            )

            localCorners?.let { c ->
                val tl = Offset(c.topLeft.x     * zs + ox, c.topLeft.y     * zs + oy)
                val tr = Offset(c.topRight.x    * zs + ox, c.topRight.y    * zs + oy)
                val bl = Offset(c.bottomLeft.x  * zs + ox, c.bottomLeft.y  * zs + oy)
                val br = Offset(c.bottomRight.x * zs + ox, c.bottomRight.y * zs + oy)

                val poly = Path().apply {
                    moveTo(tl.x, tl.y); lineTo(tr.x, tr.y)
                    lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
                }
                drawPath(poly, Color(0x2200E676))
                drawPath(poly, Color(0xFF00E676), style = Stroke(2.dp.toPx()))

                val midTop   = Offset((tl.x + tr.x) / 2f, (tl.y + tr.y) / 2f)
                val midBot   = Offset((bl.x + br.x) / 2f, (bl.y + br.y) / 2f)
                val midLeft  = Offset((tl.x + bl.x) / 2f, (tl.y + bl.y) / 2f)
                val midRight = Offset((tr.x + br.x) / 2f, (tr.y + br.y) / 2f)
                val guideColor = Color(0xFF00E676).copy(alpha = 0.25f)
                drawLine(guideColor, midTop,  midBot,   strokeWidth = 1.dp.toPx())
                drawLine(guideColor, midLeft, midRight, strokeWidth = 1.dp.toPx())

                val hr = 14.dp.toPx()
                listOf(tl, tr, bl, br).forEachIndexed { i, pt ->
                    val active = i == draggingIdx
                    val r = if (active) hr * 1.45f else hr
                    drawCircle(Color.White, r, pt)
                    if (active) {
                        drawCircle(Color(0xFF00E676), r, pt)
                        drawCircle(Color.White, r * 0.4f, pt)
                    } else {
                        drawCircle(Color(0xFF00E676), r, pt, style = Stroke(3.dp.toPx()))
                    }
                }
            }
        }
    }
}