package com.example.docscanner.presentation.edit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PointF
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.example.docscanner.data.ocr.MlKitOcrHelper
import com.example.docscanner.domain.model.DocumentCorners
import com.example.docscanner.domain.model.FilterType
import com.example.docscanner.domain.model.ScannedPage
import kotlinx.coroutines.launch
import kotlin.math.sqrt

// ──────────────────────────────────────────────────────────────
// Corner normalisation (centroid-based)
// ──────────────────────────────────────────────────────────────

private fun normalizeCorners(c: DocumentCorners): DocumentCorners {
    val pts = listOf(c.topLeft, c.topRight, c.bottomLeft, c.bottomRight)
    val cx = pts.map { it.x }.average().toFloat()
    val cy = pts.map { it.y }.average().toFloat()

    val tl = pts.filter { it.x < cx && it.y < cy }
    val tr = pts.filter { it.x >= cx && it.y < cy }
    val bl = pts.filter { it.x < cx && it.y >= cy }
    val br = pts.filter { it.x >= cx && it.y >= cy }

    if (listOf(tl, tr, bl, br).all { it.size == 1 })
        return DocumentCorners(tl.first(), tr.first(), bl.first(), br.first())

    val sorted = pts.sortedBy { pt ->
        val angle = kotlin.math.atan2((pt.y - cy).toDouble(), (pt.x - cx).toDouble())
        val shifted = angle - kotlin.math.PI / 4
        if (shifted < 0) shifted + 2 * kotlin.math.PI else shifted
    }
    return DocumentCorners(sorted[3], sorted[0], sorted[2], sorted[1])
}

// ──────────────────────────────────────────────────────────────
// Live colour-matrix helper  (brightness + contrast in Canvas)
// ──────────────────────────────────────────────────────────────

/**
 * Builds an Android [Paint] whose [ColorMatrixColorFilter] encodes
 * brightness (-100..100), contrast (0.5..2.0), and the chosen [FilterType].
 *
 * This runs on the UI thread inside Canvas – kept allocation-light.
 *
 * Filter mappings (matching your OpenCV pipeline so the preview matches
 * the final processed result as closely as possible):
 *
 *   ORIGINAL   → just brightness + contrast, full colour
 *   ENHANCED   → brightness/contrast + saturation boost + mild sharpen sim
 *   GRAYSCALE  → desaturate (setSaturation(0))
 *   BLACK_WHITE → desaturate + very high contrast (Otsu-style simulation)
 *   MAGIC      → desaturate + adaptive-threshold look (high local contrast)
 */
private fun buildLivePaint(
    brightness: Double,
    contrast: Double,
    filterType: FilterType
): Paint {
    val c         = contrast.toFloat()
    val b         = brightness.toFloat() * 2.55f   // map -100..100 → -255..255
    val translate = 128f * (1f - c) + b            // anchor mid-grey

    // ── Base brightness + contrast ──
    val cm = ColorMatrix(floatArrayOf(
        c,  0f, 0f, 0f, translate,
        0f, c,  0f, 0f, translate,
        0f, 0f, c,  0f, translate,
        0f, 0f, 0f, 1f, 0f
    ))

    // ── Filter-specific layer (postConcat so brightness/contrast still applies) ──
    when (filterType) {

        FilterType.ORIGINAL -> { /* no extra transform */ }

        FilterType.ENHANCED -> {
            // Slight saturation boost (1.3×) + warm lift on highlights
            val sat = ColorMatrix()
            sat.setSaturation(1.3f)
            val warm = ColorMatrix(floatArrayOf(
                1.05f, 0f,    0f,    0f,  8f,
                0f,    1.0f,  0f,    0f,  2f,
                0f,    0f,    0.95f, 0f, -4f,
                0f,    0f,    0f,    1f,  0f
            ))
            cm.postConcat(sat)
            cm.postConcat(warm)
        }

        FilterType.GRAYSCALE -> {
            val gray = ColorMatrix()
            gray.setSaturation(0f)
            cm.postConcat(gray)
        }

        FilterType.BLACK_WHITE -> {
            // Desaturate + crush blacks and blow out whites (Otsu feel)
            val gray = ColorMatrix()
            gray.setSaturation(0f)
            cm.postConcat(gray)
            val bw = ColorMatrix(floatArrayOf(
                2.5f, 0f,   0f,   0f, -160f,
                0f,   2.5f, 0f,   0f, -160f,
                0f,   0f,   2.5f, 0f, -160f,
                0f,   0f,   0f,   1f,   0f
            ))
            cm.postConcat(bw)
        }

        FilterType.MAGIC -> {
            // Desaturate + adaptive-threshold approximation:
            // high local contrast, slightly lifted shadows
            val gray = ColorMatrix()
            gray.setSaturation(0f)
            cm.postConcat(gray)
            val adaptive = ColorMatrix(floatArrayOf(
                3.0f, 0f,   0f,   0f, -200f,
                0f,   3.0f, 0f,   0f, -200f,
                0f,   0f,   3.0f, 0f, -200f,
                0f,   0f,   0f,   1f,   0f
            ))
            cm.postConcat(adaptive)
        }
    }

    return Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
}

// ──────────────────────────────────────────────────────────────
// OCR state
// ──────────────────────────────────────────────────────────────

private enum class OcrState { IDLE, LOADING, SUCCESS, ERROR }

// ══════════════════════════════════════════════════════════════
// EDIT SCREEN
// ══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    page: ScannedPage?,
    currentFilter: FilterType,
    isProcessing: Boolean,
    showApplyToAllPrompt: Boolean,
    totalPages: Int,
    onFilterSelected: (FilterType) -> Unit,
    /** Called ONLY when slider interaction ends – triggers ViewModel processing */
    onBrightnessContrastChanged: (Double, Double) -> Unit,
    onCornersChanged: (DocumentCorners) -> Unit,
    onDone: () -> Unit,
    onApplyToAll: () -> Unit,
    onDismissApplyToAll: () -> Unit
) {
    if (page == null) return

    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()
    val ocrHelper = remember { MlKitOcrHelper(context) }

    var selectedTab by remember { mutableIntStateOf(0) }

    // Live slider state – drives the Canvas paint in real time
    var brightness by remember { mutableDoubleStateOf(0.0) }
    var contrast   by remember { mutableDoubleStateOf(1.0) }

    // OCR
    var ocrState     by remember { mutableStateOf(OcrState.IDLE) }
    var ocrText      by remember { mutableStateOf("") }
    var ocrError     by remember { mutableStateOf("") }
    var showOcrSheet by remember { mutableStateOf(false) }

    if (showApplyToAllPrompt) {
        AlertDialog(
            onDismissRequest = onDismissApplyToAll,
            title   = { Text("Apply to all pages?") },
            text    = { Text("Apply \"${currentFilter.displayName}\" to all $totalPages pages?") },
            confirmButton = { Button(onClick = onApplyToAll)               { Text("Apply to all") } },
            dismissButton = { TextButton(onClick = onDismissApplyToAll)    { Text("This page only") } }
        )
    }

    if (showOcrSheet) {
        OcrBottomSheet(
            state     = ocrState,
            text      = ocrText,
            error     = ocrError,
            onDismiss = { showOcrSheet = false },
            onCopy    = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Extracted Text", ocrText))
            }
        )
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {

        // ── Top bar ──────────────────────────────────────────
        TopAppBar(
            title = {
                Text(
                    if (selectedTab == 0) "Crop" else "Enhance",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onDone, enabled = !isProcessing) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = onDone, enabled = !isProcessing) {
                    Icon(Icons.Default.Check, "Done", tint = Color(0xFF4CAF50))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor    = Color(0xFF0D0D0D),
                titleContentColor = Color.White
            )
        )

        // ── Tab selector (minimal pill style) ────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Crop", "Enhance").forEachIndexed { i, label ->
                val sel = selectedTab == i
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Color(0xFF2C2C2C) else Color.Transparent)
                        .clickable { selectedTab = i }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (sel) Color.White else Color.White.copy(alpha = 0.45f)
                    )
                }
            }
        }

        // ── Tab content ──────────────────────────────────────
        when (selectedTab) {
            0 -> CropTab(
                // Always pass the ORIGINAL (pre-crop) bitmap so the user
                // adjusts handles over the untransformed image
                bitmap           = page.originalBitmap,
                corners          = page.corners,
                isProcessing     = isProcessing,
                onCornersChanged = onCornersChanged,
                modifier         = Modifier.weight(1f)
            )
            1 -> EnhanceTab(
                // Also show the original here; live ColorMatrix preview makes
                // it feel responsive without expensive ViewModel processing
                bitmap              = page.originalBitmap,
                currentFilter       = currentFilter,
                isProcessing        = isProcessing,
                brightness          = brightness,
                contrast            = contrast,
                onFilterSelected    = onFilterSelected,
                onBrightnessChanged = { brightness = it },   // instant repaint
                onContrastChanged   = { contrast   = it },   // instant repaint
                onSliderFinished    = { onBrightnessContrastChanged(brightness, contrast) },
                modifier            = Modifier.weight(1f)
            )
        }

        // ── Bottom action row ─────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D0D0D))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    showOcrSheet = true
                    ocrState = OcrState.LOADING
                    ocrText  = ""
                    ocrError = ""
                    scope.launch {
                        ocrHelper.extractText(page.displayBitmap)
                            .onSuccess { t ->
                                ocrText  = t.ifBlank { "No text detected." }
                                ocrState = OcrState.SUCCESS
                            }
                            .onFailure { e ->
                                ocrError = e.message ?: "Unknown error"
                                ocrState = OcrState.ERROR
                            }
                    }
                },
                enabled  = !isProcessing,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
            ) {
                Icon(Icons.Default.TextFields, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Extract Text", style = MaterialTheme.typography.labelLarge)
            }

            Button(
                onClick  = onDone,
                enabled  = !isProcessing,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF))
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Processing…")
                } else {
                    Text("Done", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// OCR BOTTOM SHEET
// ══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrBottomSheet(
    state: OcrState,
    text: String,
    error: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var copied by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Color(0xFF161616),
        dragHandle       = { BottomSheetDefaults.DragHandle(color = Color(0xFF3A3A3A)) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Extracted Text",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White)
                if (state == OcrState.SUCCESS && text.isNotBlank()) {
                    TextButton(onClick = { onCopy(); copied = true }) {
                        Icon(
                            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            null, Modifier.size(16.dp),
                            tint = if (copied) Color(0xFF4CAF50) else Color(0xFF2979FF)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (copied) "Copied" else "Copy",
                            color = if (copied) Color(0xFF4CAF50) else Color(0xFF2979FF),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            when (state) {
                OcrState.LOADING -> Box(
                    Modifier.fillMaxWidth().height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF2979FF))
                        Spacer(Modifier.height(10.dp))
                        Text("Reading text…",
                            color = Color.White.copy(0.5f),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                OcrState.ERROR -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A1A1A))
                        .padding(14.dp)
                ) {
                    Text("Error: $error",
                        color = Color(0xFFEF5350),
                        style = MaterialTheme.typography.bodyMedium)
                }

                OcrState.SUCCESS -> {
                    val scroll = rememberScrollState()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 340.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(14.dp)
                    ) {
                        Text(text,
                            color    = Color.White,
                            style    = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().verticalScroll(scroll))
                    }
                }

                OcrState.IDLE -> Unit
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// CROP TAB  —  shows ORIGINAL bitmap + draggable corner handles
// ══════════════════════════════════════════════════════════════

@Composable
private fun CropTab(
    bitmap: Bitmap,           // ← always the original, unprocessed bitmap
    corners: DocumentCorners?,
    isProcessing: Boolean,
    onCornersChanged: (DocumentCorners) -> Unit,
    modifier: Modifier = Modifier
) {
    var localCorners by remember { mutableStateOf(corners) }
    var lastHash     by remember { mutableIntStateOf(corners.hashCode()) }
    val curHash = corners.hashCode()
    if (curHash != lastHash) { localCorners = corners; lastHash = curHash }

    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

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

    fun applyZoom(factor: Float, fx: Float, fy: Float) {
        val nz = (zoom * factor).coerceIn(1f, 8f)
        if (nz == zoom) return
        val r  = nz / zoom
        zoom   = nz
        if (nz <= 1f) { panX = 0f; panY = 0f }
        else { val (cx, cy) = clampPan(fx + (panX - fx) * r, fy + (panY - fy) * r, nz); panX = cx; panY = cy }
    }

    fun screenHandles(): List<Offset> {
        val c  = localCorners ?: return emptyList()
        val zs = baseSf * zoom
        val ox = imgLeft(); val oy = imgTop()
        return listOf(
            Offset(c.topLeft.x    * zs + ox, c.topLeft.y    * zs + oy),
            Offset(c.topRight.x   * zs + ox, c.topRight.y   * zs + oy),
            Offset(c.bottomLeft.x * zs + ox, c.bottomLeft.y * zs + oy),
            Offset(c.bottomRight.x* zs + ox, c.bottomRight.y* zs + oy)
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

    Box(modifier.fillMaxWidth().clipToBounds()) {
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

                        fun dist(a: Offset, b: Offset) =
                            sqrt((a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y)).coerceAtLeast(0.1f)

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
                                        val curA = pA.position; val curB = pB.position
                                        val curD = dist(curA, curB)
                                        val fx   = (curA.x + curB.x) / 2f
                                        val fy   = (curA.y + curB.y) / 2f
                                        if (prevDist > 0f) applyZoom(curD / prevDist, fx, fy)
                                        if (zoom > 1f) {
                                            val (cx, cy) = clampPan(
                                                panX + (fx - (prevA.x+prevB.x)/2f),
                                                panY + (fy - (prevA.y+prevB.y)/2f), zoom
                                            )
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
                                                val cur = when (draggingIdx) {
                                                    0 -> c.topLeft;    1 -> c.topRight
                                                    2 -> c.bottomLeft; else -> c.bottomRight
                                                }
                                                val np = PointF(
                                                    (cur.x + dx).coerceIn(0f, bitmap.width.toFloat()),
                                                    (cur.y + dy).coerceIn(0f, bitmap.height.toFloat())
                                                )
                                                localCorners = when (draggingIdx) {
                                                    0 -> c.copy(topLeft    = np)
                                                    1 -> c.copy(topRight   = np)
                                                    2 -> c.copy(bottomLeft = np)
                                                    else -> c.copy(bottomRight = np)
                                                }
                                            }
                                        } else if (zoom > 1f) {
                                            val dx = ptr.position.x - ptr.previousPosition.x
                                            val dy = ptr.position.y - ptr.previousPosition.y
                                            val (cx, cy) = clampPan(panX + dx, panY + dy, zoom)
                                            panX = cx; panY = cy
                                        }
                                        ptr.consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (isDragging) {
                            val norm = localCorners?.let { normalizeCorners(it) }
                            localCorners = norm
                            norm?.let { onCornersChanged(it) }
                        }
                        draggingIdx = -1
                    }
                }
        ) {
            canvasW = size.width
            canvasH = size.height
            baseSf  = minOf(canvasW / bitmap.width, canvasH / bitmap.height)

            val zs = baseSf * zoom
            val ox = imgLeft(); val oy = imgTop()

            // Draw the ORIGINAL bitmap (no filter applied in crop view)
            drawContext.canvas.nativeCanvas.drawBitmap(
                bitmap, null,
                android.graphics.RectF(ox, oy, ox + bitmap.width * zs, oy + bitmap.height * zs),
                null
            )

            localCorners?.let { c ->
                val tl = Offset(c.topLeft.x    * zs + ox, c.topLeft.y    * zs + oy)
                val tr = Offset(c.topRight.x   * zs + ox, c.topRight.y   * zs + oy)
                val bl = Offset(c.bottomLeft.x * zs + ox, c.bottomLeft.y * zs + oy)
                val br = Offset(c.bottomRight.x* zs + ox, c.bottomRight.y* zs + oy)

                // Dimmed overlay outside the crop region
                val poly = Path().apply {
                    moveTo(tl.x, tl.y); lineTo(tr.x, tr.y)
                    lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
                }
                drawPath(poly, Color(0x1A2979FF))
                drawPath(poly, Color(0xFF2979FF), style = Stroke(2.dp.toPx()))

                // Edge lines as dashes between corners
                listOf(tl to tr, tr to br, br to bl, bl to tl).forEach { (a, b) ->
                    val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
                    drawCircle(Color(0xFF2979FF).copy(alpha = 0.35f), 3.dp.toPx(), Offset(mx, my))
                }

                // Corner handles
                val hr = 13.dp.toPx()
                listOf(tl, tr, bl, br).forEachIndexed { i, pt ->
                    val active = i == draggingIdx
                    val r = if (active) hr * 1.4f else hr
                    drawCircle(Color(0xFF0D0D0D), r + 2.dp.toPx(), pt)   // shadow ring
                    drawCircle(Color.White, r, pt)
                    if (active) {
                        drawCircle(Color(0xFF2979FF), r, pt)
                        drawCircle(Color.White, r * 0.4f, pt)
                    } else {
                        drawCircle(Color(0xFF2979FF), r, pt, style = Stroke(2.5f.dp.toPx()))
                    }
                }
            }
        }

        if (isProcessing) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)),
                contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// ENHANCE TAB  —  live ColorMatrix preview while slider moves
// ══════════════════════════════════════════════════════════════

@Composable
private fun EnhanceTab(
    bitmap: Bitmap,            // ← original bitmap; ColorMatrix applied live
    currentFilter: FilterType,
    isProcessing: Boolean,
    brightness: Double,
    contrast: Double,
    onFilterSelected: (FilterType) -> Unit,
    onBrightnessChanged: (Double) -> Unit,
    onContrastChanged: (Double) -> Unit,
    /** Triggers ViewModel processing once finger leaves slider */
    onSliderFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Recompute the paint whenever brightness, contrast, or filter changes.
    // Because this is inside `remember`, it only recomputes when keys change —
    // cheap enough for every pointer-move event on a slider.
    val livePaint by remember(brightness, contrast, currentFilter) {
        mutableStateOf(buildLivePaint(brightness, contrast, currentFilter))
    }

    Column(modifier.fillMaxWidth()) {

        // ── Preview ──────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .background(Color(0xFF0D0D0D)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cw = size.width; val ch = size.height
                val iw = bitmap.width.toFloat(); val ih = bitmap.height.toFloat()
                val s  = minOf(cw / iw, ch / ih)
                val sw = iw * s; val sh = ih * s
                val oX = (cw - sw) / 2f; val oY = (ch - sh) / 2f

                // Draw with the live ColorMatrix paint — zero ViewModel calls
                drawContext.canvas.nativeCanvas.drawBitmap(
                    bitmap, null,
                    android.graphics.RectF(oX, oY, oX + sw, oY + sh),
                    livePaint           // ← applied every recompose
                )
            }
            if (isProcessing) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        // ── Controls panel ───────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF131313))
                .padding(top = 14.dp, bottom = 10.dp)
        ) {

            // Filter chips
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterType.entries.forEach { f ->
                    val sel = f == currentFilter
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (sel) Color(0xFF2979FF)
                                else Color(0xFF1E1E1E)
                            )
                            .border(
                                1.dp,
                                if (sel) Color(0xFF2979FF) else Color(0xFF2A2A2A),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { onFilterSelected(f) }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            f.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sel) Color.White else Color.White.copy(0.55f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            // Brightness slider
            SliderRow(
                icon        = Icons.Default.BrightnessHigh,
                label       = "Bright",
                value       = brightness.toFloat(),
                range       = -100f..100f,
                neutralMark = 0f,
                onChange    = { onBrightnessChanged(it.toDouble()) },
                onFinished  = onSliderFinished
            )

            // Contrast slider
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

// ── Shared slider row ─────────────────────────────────────────

@Composable
private fun SliderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    neutralMark: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = Color.White.copy(0.5f))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style    = MaterialTheme.typography.labelSmall,
            color    = Color.White.copy(0.5f),
            modifier = Modifier.width(58.dp)
        )
        Slider(
            value                 = value,
            onValueChange         = onChange,
            onValueChangeFinished = onFinished,   // ← commits to ViewModel
            valueRange            = range,
            modifier              = Modifier.weight(1f),
            colors                = SliderDefaults.colors(
                thumbColor            = Color.White,
                activeTrackColor      = Color(0xFF2979FF),
                inactiveTrackColor    = Color(0xFF2A2A2A)
            )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            // Show delta from neutral so user knows how far off centre they are
            when {
                value == neutralMark -> "—"
                value > neutralMark  -> "+%.0f".format((value - neutralMark) / (range.endInclusive - neutralMark) * 100)
                else                 -> "%.0f".format((value - neutralMark) / (neutralMark - range.start) * 100)
            },
            style    = MaterialTheme.typography.labelSmall,
            color    = Color.White.copy(0.4f),
            modifier = Modifier.width(28.dp)
        )
    }
}