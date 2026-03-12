package com.example.docscanner.presentation.edit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
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
import com.example.docscanner.data.ocr.TesseractOcrHelper
import com.example.docscanner.domain.model.DocumentCorners
import com.example.docscanner.domain.model.FilterType
import com.example.docscanner.domain.model.ScannedPage
import kotlinx.coroutines.launch
import kotlin.math.sqrt

// ══════════════════════════════════════════════════════════════
// Corner normalisation (centroid-based)
// ══════════════════════════════════════════════════════════════

private fun normalizeCorners(c: DocumentCorners): DocumentCorners {
    val pts = listOf(c.topLeft, c.topRight, c.bottomLeft, c.bottomRight)
    val cx = pts.map { it.x }.average().toFloat()
    val cy = pts.map { it.y }.average().toFloat()

    val tl = pts.filter { it.x <  cx && it.y <  cy }
    val tr = pts.filter { it.x >= cx && it.y <  cy }
    val bl = pts.filter { it.x <  cx && it.y >= cy }
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
// OCR state
// ══════════════════════════════════════════════════════════════

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
    onBrightnessContrastChanged: (Double, Double) -> Unit,
    onCornersChanged: (DocumentCorners) -> Unit,
    onDone: () -> Unit,
    onApplyToAll: () -> Unit,
    onDismissApplyToAll: () -> Unit
) {
    if (page == null) return

    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()
    val ocrHelper = remember { TesseractOcrHelper(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var brightness  by remember { mutableDoubleStateOf(0.0) }
    var contrast    by remember { mutableDoubleStateOf(1.0) }

    // OCR state
    var ocrState     by remember { mutableStateOf(OcrState.IDLE) }
    var ocrText      by remember { mutableStateOf("") }
    var ocrError     by remember { mutableStateOf("") }
    var showOcrSheet by remember { mutableStateOf(false) }

    // Apply-to-all dialog
    if (showApplyToAllPrompt) {
        AlertDialog(
            onDismissRequest = onDismissApplyToAll,
            title = { Text("Apply to all pages?") },
            text  = { Text("Apply \"${currentFilter.displayName}\" filter to all $totalPages pages?") },
            confirmButton = { Button(onClick = onApplyToAll) { Text("Apply to all") } },
            dismissButton = { TextButton(onClick = onDismissApplyToAll) { Text("Just this page") } }
        )
    }

    // OCR bottom sheet
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

    Column(Modifier.fillMaxSize().background(Color.Black)) {

        TopAppBar(
            title = { Text("Edit Page") },
            navigationIcon = {
                IconButton(onClick = onDone, enabled = !isProcessing) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = onDone, enabled = !isProcessing) {
                    Icon(Icons.Default.Check, "Done", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor    = Color.Black,
                titleContentColor = Color.White
            )
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor   = Color(0xFF1A1A1A),
            contentColor     = Color.White
        ) {
            Tab(
                selected = selectedTab == 0, onClick = { selectedTab = 0 },
                text = { Text("Crop") },
                icon = { Icon(Icons.Default.Crop, null, Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1, onClick = { selectedTab = 1 },
                text = { Text("Enhance") },
                icon = { Icon(Icons.Default.Tune, null, Modifier.size(18.dp)) }
            )
        }

        when (selectedTab) {
            0 -> CropTab(
                bitmap           = page.originalBitmap,
                corners          = page.corners,
                isProcessing     = isProcessing,
                onCornersChanged = onCornersChanged,
                modifier         = Modifier.weight(1f)
            )
            1 -> EnhanceTab(
                bitmap              = page.displayBitmap,
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

        // ── Bottom action bar ──────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Extract Text button (OCR)
            OutlinedButton(
                onClick = {
                    showOcrSheet = true
                    ocrState     = OcrState.LOADING
                    ocrText      = ""
                    ocrError     = ""
                    scope.launch {
                        ocrHelper.extractText(page.displayBitmap)
                            .onSuccess { text ->
                                ocrText  = text.ifBlank { "No text found in this image." }
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
                border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444))
            ) {
                Icon(Icons.Default.TextFields, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Extract Text")
            }

            // Done button
            Button(
                onClick  = onDone,
                enabled  = !isProcessing,
                modifier = Modifier.weight(1f)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Processing...")
                } else {
                    Text("Done")
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
        containerColor   = Color(0xFF1A1A1A),
        dragHandle       = { BottomSheetDefaults.DragHandle(color = Color(0xFF555555)) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Extracted Text",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                if (state == OcrState.SUCCESS && text.isNotBlank()) {
                    TextButton(onClick = { onCopy(); copied = true }) {
                        Icon(
                            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            null,
                            Modifier.size(18.dp),
                            tint = if (copied) Color(0xFF4CAF50) else Color(0xFF2196F3)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (copied) "Copied!" else "Copy",
                            color = if (copied) Color(0xFF4CAF50) else Color(0xFF2196F3)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when (state) {
                OcrState.LOADING -> {
                    Box(
                        Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF2196F3))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Reading text…",
                                color = Color.White.copy(0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                OcrState.ERROR -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF3A1A1A))
                            .padding(16.dp)
                    ) {
                        Text(
                            "Error: $error",
                            color = Color(0xFFEF5350),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                OcrState.SUCCESS -> {
                    val scrollState = rememberScrollState()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 360.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A2A2A))
                            .padding(16.dp)
                    ) {
                        Text(
                            text     = text,
                            color    = Color.White,
                            style    = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)
                        )
                    }
                }

                OcrState.IDLE -> Unit
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// CROP TAB  —  pinch-to-zoom + corner drag + normalisation
// ══════════════════════════════════════════════════════════════

@Composable
private fun CropTab(
    bitmap: Bitmap,
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

    // ── Transform helpers ─────────────────────────────────────

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
        val ratio   = newZoom / zoom
        val newPanX = focalX + (panX - focalX) * ratio
        val newPanY = focalY + (panY - focalY) * ratio
        zoom = newZoom
        if (newZoom <= 1f) { panX = 0f; panY = 0f }
        else { val (cx, cy) = clampPan(newPanX, newPanY, newZoom); panX = cx; panY = cy }
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
            val d = sqrt(
                (touch.x - p.x) * (touch.x - p.x) +
                        (touch.y - p.y) * (touch.y - p.y)
            )
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    Box(
        modifier.fillMaxWidth().clipToBounds()
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val firstId   = firstDown.id
                        val firstPos  = firstDown.position
                        val cornerHit = hitTest(firstPos)
                        var isDragging = cornerHit >= 0
                        draggingIdx    = cornerHit

                        var secondId: PointerId? = null
                        var prevA    = firstPos
                        var prevB    = firstPos
                        var prevDist = 0f

                        fun fingerDist(a: Offset, b: Offset) =
                            sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
                                .coerceAtLeast(0.1f)

                        do {
                            val event: PointerEvent = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }

                            when {
                                // Second finger appeared → switch to pinch zoom
                                pressed.size >= 2 && secondId == null -> {
                                    isDragging  = false
                                    draggingIdx = -1
                                    secondId    = pressed.first { it.id != firstId }.id
                                    val a = pressed.first { it.id == firstId }.position
                                    val b = pressed.first { it.id == secondId!! }.position
                                    prevA = a; prevB = b
                                    prevDist = fingerDist(a, b)
                                    pressed.fastForEach { it.consume() }
                                }

                                // Two fingers → pinch zoom + pan
                                pressed.size >= 2 && secondId != null -> {
                                    val pA = pressed.firstOrNull { it.id == firstId }
                                    val pB = pressed.firstOrNull { it.id == secondId }
                                    if (pA != null && pB != null) {
                                        val curA    = pA.position
                                        val curB    = pB.position
                                        val curDist = fingerDist(curA, curB)
                                        val focalX  = (curA.x + curB.x) / 2f
                                        val focalY  = (curA.y + curB.y) / 2f
                                        if (prevDist > 0f) applyZoom(curDist / prevDist, focalX, focalY)
                                        if (zoom > 1f) {
                                            val prevMidX = (prevA.x + prevB.x) / 2f
                                            val prevMidY = (prevA.y + prevB.y) / 2f
                                            val (cx, cy) = clampPan(
                                                panX + (focalX - prevMidX),
                                                panY + (focalY - prevMidY),
                                                zoom
                                            )
                                            panX = cx; panY = cy
                                        }
                                        prevA = curA; prevB = curB; prevDist = curDist
                                        pressed.fastForEach { it.consume() }
                                    }
                                }

                                // One finger → drag corner handle or pan image
                                pressed.size == 1 && secondId == null -> {
                                    val ptr = pressed.first()
                                    if (ptr.positionChanged()) {
                                        if (isDragging && draggingIdx >= 0) {
                                            val c = localCorners
                                            if (c != null) {
                                                val zs  = baseSf * zoom
                                                val dx  = (ptr.position.x - ptr.previousPosition.x) / zs
                                                val dy  = (ptr.position.y - ptr.previousPosition.y) / zs
                                                val cur = when (draggingIdx) {
                                                    0 -> c.topLeft;    1 -> c.topRight
                                                    2 -> c.bottomLeft; 3 -> c.bottomRight
                                                    else -> null
                                                }
                                                if (cur != null) {
                                                    val np = PointF(
                                                        (cur.x + dx).coerceIn(0f, bitmap.width.toFloat()),
                                                        (cur.y + dy).coerceIn(0f, bitmap.height.toFloat())
                                                    )
                                                    localCorners = when (draggingIdx) {
                                                        0 -> c.copy(topLeft     = np)
                                                        1 -> c.copy(topRight    = np)
                                                        2 -> c.copy(bottomLeft  = np)
                                                        3 -> c.copy(bottomRight = np)
                                                        else -> c
                                                    }
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

                        // ── Finger(s) lifted — normalise corner slots ──────
                        if (isDragging) {
                            val normalized = localCorners?.let { normalizeCorners(it) }
                            localCorners = normalized
                            normalized?.let { onCornersChanged(it) }
                        }
                        draggingIdx = -1
                    }
                }
        ) {
            canvasW = size.width
            canvasH = size.height

            val iw = bitmap.width.toFloat()
            val ih = bitmap.height.toFloat()
            baseSf = minOf(canvasW / iw, canvasH / ih)

            val zs = baseSf * zoom
            val ox = imgLeft()
            val oy = imgTop()

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
                drawPath(poly, Color(0x332196F3))
                drawPath(poly, Color(0xFF2196F3), style = Stroke(3.dp.toPx()))

                val hr = 14.dp.toPx()
                listOf(tl, tr, bl, br).forEachIndexed { i, pt ->
                    val active = i == draggingIdx
                    val r = if (active) hr * 1.4f else hr
                    drawCircle(Color.White, r, pt)
                    if (active) {
                        drawCircle(Color(0xFF2196F3), r, pt)
                        drawCircle(Color.White, r * 0.45f, pt)
                    } else {
                        drawCircle(Color(0xFF2196F3), r, pt, style = Stroke(3.dp.toPx()))
                    }
                }
            }
        }

        if (isProcessing) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Color.White) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// ENHANCE TAB
// ══════════════════════════════════════════════════════════════

@Composable
private fun EnhanceTab(
    bitmap: Bitmap,
    currentFilter: FilterType,
    isProcessing: Boolean,
    brightness: Double,
    contrast: Double,
    onFilterSelected: (FilterType) -> Unit,
    onBrightnessChanged: (Double) -> Unit,
    onContrastChanged: (Double) -> Unit,
    onSliderFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().weight(1f).clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cw = size.width; val ch = size.height
                val iw = bitmap.width.toFloat(); val ih = bitmap.height.toFloat()
                val s  = minOf(cw / iw, ch / ih)
                val sw = iw * s; val sh = ih * s
                val oX = (cw - sw) / 2f; val oY = (ch - sh) / 2f
                drawContext.canvas.nativeCanvas.drawBitmap(
                    bitmap, null,
                    android.graphics.RectF(oX, oY, oX + sw, oY + sh), null
                )
            }
            if (isProcessing) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Color.White) }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(vertical = 12.dp)
        ) {
            Text(
                "Filter",
                style    = MaterialTheme.typography.labelLarge,
                color    = Color.White.copy(0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterType.entries.forEach { f ->
                    val sel = f == currentFilter
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) Color(0xFF2196F3) else Color(0xFF2A2A2A))
                            .border(1.dp, if (sel) Color(0xFF2196F3) else Color(0xFF444444), RoundedCornerShape(8.dp))
                            .clickable { onFilterSelected(f) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            f.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (sel) Color.White else Color.White.copy(0.7f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BrightnessHigh, null,
                    tint = Color.White.copy(0.7f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Brightness", style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(0.7f), modifier = Modifier.width(72.dp))
                Slider(
                    value                = brightness.toFloat(),
                    onValueChange        = { onBrightnessChanged(it.toDouble()) },
                    onValueChangeFinished = onSliderFinished,
                    valueRange           = -100f..100f,
                    modifier             = Modifier.weight(1f)
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Contrast, null,
                    tint = Color.White.copy(0.7f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Contrast", style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(0.7f), modifier = Modifier.width(72.dp))
                Slider(
                    value                = contrast.toFloat(),
                    onValueChange        = { onContrastChanged(it.toDouble()) },
                    onValueChangeFinished = onSliderFinished,
                    valueRange           = 0.5f..2.0f,
                    modifier             = Modifier.weight(1f)
                )
            }
        }
    }
}