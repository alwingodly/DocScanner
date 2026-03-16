package com.example.docscanner.presentation.edit

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PointF
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import com.example.docscanner.domain.model.FilterType
import com.example.docscanner.domain.model.ScannedPage
import com.example.docscanner.presentation.alldocuments.BgBase
import com.example.docscanner.presentation.alldocuments.BgCard
import com.example.docscanner.presentation.alldocuments.BgSurface
import com.example.docscanner.presentation.alldocuments.Coral
import com.example.docscanner.presentation.alldocuments.GreenAccent
import com.example.docscanner.presentation.alldocuments.Ink
import com.example.docscanner.presentation.alldocuments.InkDim
import com.example.docscanner.presentation.alldocuments.InkMid
import com.example.docscanner.presentation.alldocuments.StrokeLight
import com.example.docscanner.presentation.alldocuments.StrokeMid
import kotlin.math.sqrt

// ─── Shared extra tokens for edit screen ─────────────────────────────────────
private val BgPreview = Color(0xFFF0EDE8)   // warm grey canvas for image preview
private val CropBlue  = Coral               // reuse coral as the crop handle accent

// ──────────────────────────────────────────────────────────────
// Corner normalisation (unchanged logic)
// ──────────────────────────────────────────────────────────────

private fun normalizeCorners(c: DocumentCorners): DocumentCorners {
    val pts = listOf(c.topLeft, c.topRight, c.bottomLeft, c.bottomRight)
    val cx  = pts.map { it.x }.average().toFloat()
    val cy  = pts.map { it.y }.average().toFloat()
    val tl  = pts.filter { it.x < cx && it.y < cy }
    val tr  = pts.filter { it.x >= cx && it.y < cy }
    val bl  = pts.filter { it.x < cx && it.y >= cy }
    val br  = pts.filter { it.x >= cx && it.y >= cy }
    if (listOf(tl, tr, bl, br).all { it.size == 1 })
        return DocumentCorners(tl.first(), tr.first(), bl.first(), br.first())
    val sorted = pts.sortedBy { pt ->
        val angle   = kotlin.math.atan2((pt.y - cy).toDouble(), (pt.x - cx).toDouble())
        val shifted = angle - kotlin.math.PI / 4
        if (shifted < 0) shifted + 2 * kotlin.math.PI else shifted
    }
    return DocumentCorners(sorted[3], sorted[0], sorted[2], sorted[1])
}

// ──────────────────────────────────────────────────────────────
// Live colour-matrix helper (unchanged logic)
// ──────────────────────────────────────────────────────────────

private fun buildLivePaint(brightness: Double, contrast: Double, filterType: FilterType): Paint {
    val c         = contrast.toFloat()
    val b         = brightness.toFloat() * 2.55f
    val translate = 128f * (1f - c) + b
    val cm        = ColorMatrix(floatArrayOf(
        c,  0f, 0f, 0f, translate,
        0f, c,  0f, 0f, translate,
        0f, 0f, c,  0f, translate,
        0f, 0f, 0f, 1f, 0f
    ))
    when (filterType) {
        FilterType.ORIGINAL  -> { }
        FilterType.ENHANCED  -> {
            val sat  = ColorMatrix().also { it.setSaturation(1.3f) }
            val warm = ColorMatrix(floatArrayOf(1.05f, 0f, 0f, 0f, 8f, 0f, 1.0f, 0f, 0f, 2f, 0f, 0f, 0.95f, 0f, -4f, 0f, 0f, 0f, 1f, 0f))
            cm.postConcat(sat); cm.postConcat(warm)
        }
        FilterType.GRAYSCALE  -> cm.postConcat(ColorMatrix().also { it.setSaturation(0f) })
        FilterType.BLACK_WHITE -> {
            cm.postConcat(ColorMatrix().also { it.setSaturation(0f) })
            cm.postConcat(ColorMatrix(floatArrayOf(2.5f, 0f, 0f, 0f, -160f, 0f, 2.5f, 0f, 0f, -160f, 0f, 0f, 2.5f, 0f, -160f, 0f, 0f, 0f, 1f, 0f)))
        }
        FilterType.MAGIC -> {
            cm.postConcat(ColorMatrix().also { it.setSaturation(0f) })
            cm.postConcat(ColorMatrix(floatArrayOf(3.0f, 0f, 0f, 0f, -200f, 0f, 3.0f, 0f, 0f, -200f, 0f, 0f, 3.0f, 0f, -200f, 0f, 0f, 0f, 1f, 0f)))
        }
    }
    return Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
}

// ══════════════════════════════════════════════════════════════
// EDIT SCREEN
// ══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    page                       : ScannedPage?,
    currentFilter              : FilterType,
    isProcessing               : Boolean,
    showApplyToAllPrompt       : Boolean,
    totalPages                 : Int,
    onFilterSelected           : (FilterType) -> Unit,
    onBrightnessContrastChanged: (Double, Double) -> Unit,
    onCornersChanged           : (DocumentCorners) -> Unit,
    onDone                     : () -> Unit,
    onApplyToAll               : () -> Unit,
    onDismissApplyToAll        : () -> Unit
) {
    if (page == null) return

    var selectedTab by remember { mutableIntStateOf(0) }
    var brightness  by remember { mutableDoubleStateOf(0.0) }
    var contrast    by remember { mutableDoubleStateOf(1.0) }

    // ── Apply-to-all dialog ───────────────────────────────────────────────────
    if (showApplyToAllPrompt) {
        AlertDialog(
            onDismissRequest = onDismissApplyToAll,
            containerColor   = BgCard,
            shape            = RoundedCornerShape(18.dp),
            title   = { Text("Apply to all pages?", color = Ink, fontWeight = FontWeight.Bold) },
            text    = { Text("Apply \"${currentFilter.displayName}\" to all $totalPages pages?", color = InkMid, fontSize = 14.sp) },
            confirmButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(Brush.horizontalGradient(listOf(Coral, Color(0xFFD94860))))
                        .clickable(onClick = onApplyToAll).padding(horizontal = 16.dp, vertical = 9.dp)
                ) { Text("Apply to all", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            },
            dismissButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismissApplyToAll).padding(horizontal = 16.dp, vertical = 9.dp)
                ) { Text("This page only", color = InkMid, fontSize = 14.sp) }
            }
        )
    }

    Box(Modifier.fillMaxSize().background(BgBase)) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Surface(
                modifier        = Modifier.fillMaxWidth(),
                color           = BgBase,
                shadowElevation = 0.dp,
                tonalElevation  = 0.dp
            ) {
                Box(Modifier.fillMaxWidth().statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDone, enabled = !isProcessing) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink)
                        }
                        Text(
                            if (selectedTab == 0) "Crop" else "Enhance",
                            color      = Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            modifier   = Modifier.weight(1f)
                        )
                        // Done checkmark button
                        Box(
                            Modifier.padding(end = 10.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (!isProcessing) GreenAccent.copy(0.12f) else BgSurface)
                                .border(1.dp, if (!isProcessing) GreenAccent.copy(0.30f) else StrokeLight, RoundedCornerShape(10.dp))
                                .clickable(enabled = !isProcessing, onClick = onDone)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Default.Check, "Done", tint = if (!isProcessing) GreenAccent else InkDim, modifier = Modifier.size(15.dp))
                                Text("Done", color = if (!isProcessing) GreenAccent else InkDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(StrokeLight))
                }
            }

            // ── Tab pills ─────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(12.dp)).background(BgSurface)
                    .border(1.dp, StrokeLight, RoundedCornerShape(12.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Crop" to Icons.Default.CropFree, "Enhance" to Icons.Default.AutoFixHigh)
                    .forEachIndexed { i, (label, icon) ->
                        val sel = selectedTab == i
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                                .background(Color.Transparent)
                                .border(if (sel) 1.5.dp else 1.dp, if (sel) Coral else Color.Transparent, RoundedCornerShape(9.dp))
                                .clickable { selectedTab = i }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(icon, null, Modifier.size(14.dp), tint = if (sel) Coral else InkDim)
                                Text(label, color = if (sel) Coral else InkMid, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
            }

            // ── Tab content ───────────────────────────────────────────────────
            when (selectedTab) {
                0 -> CropTab(
                    bitmap           = page.originalBitmap,
                    corners          = page.corners,
                    isProcessing     = isProcessing,
                    onCornersChanged = onCornersChanged,
                    modifier         = Modifier.weight(1f)
                )
                1 -> EnhanceTab(
                    bitmap              = page.originalBitmap,
                    processedBitmap     = page.displayBitmap,
                    currentFilter       = currentFilter,
                    isProcessing        = isProcessing,
                    brightness          = brightness,
                    contrast            = contrast,
                    onFilterSelected    = onFilterSelected,
                    onBrightnessChanged = { brightness = it },
                    onContrastChanged   = { contrast   = it },
                    onSliderFinished    = { onBrightnessContrastChanged(brightness, contrast) },
                    modifier            = Modifier.weight(1f)
                )
            }

            // ── Bottom done bar ───────────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth().background(BgBase).navigationBarsPadding()
                    .topDivider(topBorder = true).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp)
                        .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x22E8603C), spotColor = Color(0x22E8603C))
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (!isProcessing) Brush.horizontalGradient(listOf(Coral, Color(0xFFD94860))) else Brush.horizontalGradient(listOf(BgSurface, BgSurface)))
                        .clickable(enabled = !isProcessing, onClick = onDone),
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Coral, strokeWidth = 2.dp)
                            Text("Processing…", color = InkMid, fontSize = 14.sp)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("Done", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// helper to draw top border on a Box
private fun Modifier.topDivider(topBorder: Boolean): Modifier = if (topBorder)
    this.then(Modifier.background(StrokeLight).padding(top = 1.dp))
else this

// ══════════════════════════════════════════════════════════════
// CROP TAB
// ══════════════════════════════════════════════════════════════

@Composable
private fun CropTab(
    bitmap          : Bitmap,
    corners         : DocumentCorners?,
    isProcessing    : Boolean,
    onCornersChanged: (DocumentCorners) -> Unit,
    modifier        : Modifier = Modifier
) {
    var localCorners by remember { mutableStateOf(corners) }
    var lastHash     by remember { mutableIntStateOf(corners.hashCode()) }
    val curHash = corners.hashCode()
    if (curHash != lastHash) { localCorners = corners; lastHash = curHash }

    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var canvasW     by remember { mutableFloatStateOf(1f) }
    var canvasH     by remember { mutableFloatStateOf(1f) }
    var baseSf      by remember { mutableFloatStateOf(1f) }
    var draggingIdx by remember { mutableIntStateOf(-1) }

    fun imgLeft() = (canvasW - bitmap.width  * baseSf * zoom) / 2f + panX
    fun imgTop()  = (canvasH - bitmap.height * baseSf * zoom) / 2f + panY

    fun clampPan(px: Float, py: Float, z: Float): Pair<Float, Float> {
        val hw = ((bitmap.width  * baseSf * z) - canvasW).coerceAtLeast(0f) / 2f
        val hh = ((bitmap.height * baseSf * z) - canvasH).coerceAtLeast(0f) / 2f
        return px.coerceIn(-hw, hw) to py.coerceIn(-hh, hh)
    }

    fun applyZoom(factor: Float, fx: Float, fy: Float) {
        val nz = (zoom * factor).coerceIn(1f, 8f); if (nz == zoom) return
        val r  = nz / zoom; zoom = nz
        if (nz <= 1f) { panX = 0f; panY = 0f }
        else { val (cx, cy) = clampPan(fx + (panX - fx) * r, fy + (panY - fy) * r, nz); panX = cx; panY = cy }
    }

    fun screenHandles(): List<Offset> {
        val c = localCorners ?: return emptyList()
        val zs = baseSf * zoom; val ox = imgLeft(); val oy = imgTop()
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
            val d = sqrt((touch.x - p.x).let { it * it } + (touch.y - p.y).let { it * it })
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    Box(modifier.fillMaxWidth().background(BgPreview).clipToBounds()) {
        Canvas(
            Modifier.fillMaxSize().clipToBounds()
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
                        fun dist(a: Offset, b: Offset) = sqrt((a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y)).coerceAtLeast(0.1f)

                        do {
                            val event: PointerEvent = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            when {
                                pressed.size >= 2 && secondId == null -> {
                                    isDragging = false; draggingIdx = -1
                                    secondId   = pressed.first { it.id != firstId }.id
                                    val a = pressed.first { it.id == firstId }.position
                                    val b = pressed.first { it.id == secondId!! }.position
                                    prevA = a; prevB = b; prevDist = dist(a, b)
                                    pressed.fastForEach { it.consume() }
                                }
                                pressed.size >= 2 && secondId != null -> {
                                    val pA = pressed.firstOrNull { it.id == firstId }
                                    val pB = pressed.firstOrNull { it.id == secondId }
                                    if (pA != null && pB != null) {
                                        val curA = pA.position; val curB = pB.position; val curD = dist(curA, curB)
                                        val fx = (curA.x + curB.x) / 2f; val fy = (curA.y + curB.y) / 2f
                                        if (prevDist > 0f) applyZoom(curD / prevDist, fx, fy)
                                        if (zoom > 1f) {
                                            val (cx, cy) = clampPan(panX + (fx - (prevA.x+prevB.x)/2f), panY + (fy - (prevA.y+prevB.y)/2f), zoom)
                                            panX = cx; panY = cy
                                        }
                                        prevA = curA; prevB = curB; prevDist = curD
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
                                                val cur = when (draggingIdx) { 0 -> c.topLeft; 1 -> c.topRight; 2 -> c.bottomLeft; else -> c.bottomRight }
                                                val np  = PointF((cur.x + dx).coerceIn(0f, bitmap.width.toFloat()), (cur.y + dy).coerceIn(0f, bitmap.height.toFloat()))
                                                localCorners = when (draggingIdx) { 0 -> c.copy(topLeft = np); 1 -> c.copy(topRight = np); 2 -> c.copy(bottomLeft = np); else -> c.copy(bottomRight = np) }
                                            }
                                        } else if (zoom > 1f) {
                                            val (cx, cy) = clampPan(panX + ptr.position.x - ptr.previousPosition.x, panY + ptr.position.y - ptr.previousPosition.y, zoom)
                                            panX = cx; panY = cy
                                        }
                                        ptr.consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (isDragging) {
                            val norm = localCorners?.let { normalizeCorners(it) }
                            localCorners = norm; norm?.let { onCornersChanged(it) }
                        }
                        draggingIdx = -1
                    }
                }
        ) {
            canvasW = size.width; canvasH = size.height
            baseSf  = minOf(canvasW / bitmap.width, canvasH / bitmap.height)
            val zs  = baseSf * zoom; val ox = imgLeft(); val oy = imgTop()

            // Draw bitmap
            drawContext.canvas.nativeCanvas.drawBitmap(
                bitmap, null,
                android.graphics.RectF(ox, oy, ox + bitmap.width * zs, oy + bitmap.height * zs),
                null
            )

            localCorners?.let { c ->
                val tl = Offset(c.topLeft.x     * zs + ox, c.topLeft.y     * zs + oy)
                val tr = Offset(c.topRight.x    * zs + ox, c.topRight.y    * zs + oy)
                val bl = Offset(c.bottomLeft.x  * zs + ox, c.bottomLeft.y  * zs + oy)
                val br = Offset(c.bottomRight.x * zs + ox, c.bottomRight.y * zs + oy)

                // Crop region fill + border
                val poly = Path().apply { moveTo(tl.x, tl.y); lineTo(tr.x, tr.y); lineTo(br.x, br.y); lineTo(bl.x, bl.y); close() }
                drawPath(poly, Coral.copy(alpha = 0.08f))
                drawPath(poly, Coral, style = Stroke(2.dp.toPx()))

                // Mid-edge dots
                listOf(tl to tr, tr to br, br to bl, bl to tl).forEach { (a, b) ->
                    drawCircle(Coral.copy(alpha = 0.30f), 3.dp.toPx(), Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f))
                }

                // Corner handles
                val hr = 13.dp.toPx()
                listOf(tl, tr, bl, br).forEachIndexed { i, pt ->
                    val active = i == draggingIdx
                    val r = if (active) hr * 1.4f else hr
                    drawCircle(Color(0x22000000), r + 2.dp.toPx(), pt)   // subtle shadow
                    drawCircle(Color.White, r, pt)
                    if (active) {
                        drawCircle(Coral, r, pt)
                        drawCircle(Color.White, r * 0.4f, pt)
                    } else {
                        drawCircle(Coral, r, pt, style = Stroke(2.5f.dp.toPx()))
                    }
                }
            }
        }

        // Dim + spinner while processing
        if (isProcessing) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(0.55f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Coral)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// ENHANCE TAB
// ══════════════════════════════════════════════════════════════

@Composable
private fun EnhanceTab(
    bitmap             : Bitmap,
    processedBitmap    : Bitmap,
    currentFilter      : FilterType,
    isProcessing       : Boolean,
    brightness         : Double,
    contrast           : Double,
    onFilterSelected   : (FilterType) -> Unit,
    onBrightnessChanged: (Double) -> Unit,
    onContrastChanged  : (Double) -> Unit,
    onSliderFinished   : () -> Unit,
    modifier           : Modifier = Modifier
) {
    val livePaint by remember(brightness, contrast, currentFilter) {
        mutableStateOf(buildLivePaint(brightness, contrast, currentFilter))
    }

    var showOriginal by remember { mutableStateOf(false) }
    val processedId  = processedBitmap.generationId
    LaunchedEffect(processedId) { showOriginal = false }

    val displayBmp = if (showOriginal) bitmap else processedBitmap

    Column(modifier.fillMaxWidth()) {

        // ── Preview ───────────────────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().weight(1f).clipToBounds().background(BgPreview),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cw = size.width; val ch = size.height
                val iw = displayBmp.width.toFloat(); val ih = displayBmp.height.toFloat()
                val s  = minOf(cw / iw, ch / ih)
                val oX = (cw - iw * s) / 2f; val oY = (ch - ih * s) / 2f

                drawContext.canvas.nativeCanvas.drawBitmap(
                    displayBmp, null,
                    android.graphics.RectF(oX, oY, oX + iw * s, oY + ih * s),
                    if (showOriginal) null else livePaint
                )
            }

            // Processing overlay
            if (isProcessing) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(0.55f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Coral)
                }
            }

            // Before / after toggle
            val hasEnhancement = currentFilter != FilterType.ORIGINAL || brightness != 0.0 || contrast != 1.0
            if (hasEnhancement && !isProcessing) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                        .shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x18000000))
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (showOriginal) BgCard else Coral)
                        .border(1.dp, if (showOriginal) StrokeLight else Color.Transparent, RoundedCornerShape(20.dp))
                        .clickable { showOriginal = !showOriginal }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(if (showOriginal) Icons.Default.ImageSearch else Icons.Default.AutoFixHigh, null, Modifier.size(14.dp), tint = if (showOriginal) InkMid else Color.White)
                        Text(if (showOriginal) "Original" else "Enhanced", color = if (showOriginal) InkMid else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── Controls panel ────────────────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().background(BgBase)
                .topDivider(topBorder = true).padding(top = 14.dp, bottom = 10.dp)
        ) {
            // Filter chips
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterType.entries.forEach { f ->
                    val sel = f == currentFilter
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (sel) Coral else BgSurface)
                            .border(1.dp, if (sel) Coral else StrokeLight, RoundedCornerShape(20.dp))
                            .clickable { onFilterSelected(f) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(f.displayName, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) Color.White else InkMid)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(StrokeLight).padding(horizontal = 14.dp))
            Spacer(Modifier.height(8.dp))

            SliderRow(
                icon        = Icons.Default.BrightnessHigh,
                label       = "Bright",
                value       = brightness.toFloat(),
                range       = -100f..100f,
                neutralMark = 0f,
                onChange    = { onBrightnessChanged(it.toDouble()) },
                onFinished  = onSliderFinished
            )
            SliderRow(
                icon        = Icons.Default.Contrast,
                label       = "Contrast",
                value       = contrast.toFloat(),
                range       = 0.5f..2.0f,
                neutralMark = 1.0f,
                onChange    = { onContrastChanged(it.toDouble()) },
                onFinished  = onSliderFinished
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ── Slider row ────────────────────────────────────────────────────────────────

@Composable
private fun SliderRow(
    icon       : androidx.compose.ui.graphics.vector.ImageVector,
    label      : String,
    value      : Float,
    range      : ClosedFloatingPointRange<Float>,
    neutralMark: Float,
    onChange   : (Float) -> Unit,
    onFinished : () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = InkMid)
        Spacer(Modifier.width(8.dp))
        Text(label, color = InkMid, fontSize = 12.sp, modifier = Modifier.width(52.dp))
        Slider(
            value                 = value,
            onValueChange         = onChange,
            onValueChangeFinished = onFinished,
            valueRange            = range,
            modifier              = Modifier.weight(1f),
            colors                = SliderDefaults.colors(
                thumbColor         = Coral,
                activeTrackColor   = Coral,
                inactiveTrackColor = StrokeLight
            )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                value == neutralMark -> "—"
                value > neutralMark  -> "+%.0f".format((value - neutralMark) / (range.endInclusive - neutralMark) * 100)
                else                 -> "%.0f".format((value - neutralMark) / (neutralMark - range.start) * 100)
            },
            color    = InkDim,
            fontSize = 11.sp,
            modifier = Modifier.width(28.dp)
        )
    }
}