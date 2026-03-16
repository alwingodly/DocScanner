package com.example.docscanner.presentation.review

import android.Manifest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.FilterType
import com.example.docscanner.domain.model.ScannedPage

// ─── Paper Studio tokens ──────────────────────────────────────────────────────
private val BgBase      = Color(0xFFFAF8F5)
private val BgSurface   = Color(0xFFF3F0EB)
private val BgCard      = Color(0xFFFFFFFF)
private val StrokeLight = Color(0xFFE2DDD8)
private val StrokeMid   = Color(0xFFCDC8C0)
private val Coral       = Color(0xFFE8603C)
private val CoralSoft   = Color(0x1AE8603C)
private val Ink         = Color(0xFF1A1A2E)
private val InkMid      = Color(0xFF6B6878)
private val InkDim      = Color(0xFFB8B4BC)
private val GreenAccent = Color(0xFF2E9E6B)
private val DangerRed   = Color(0xFFD94040)

private val DocTypeBadgeBg = Color(0xFF2E6BE6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    pages              : List<ScannedPage>,
    isProcessing       : Boolean,
    onDeletePage       : (Int) -> Unit,
    onReorderPage      : (fromIndex: Int, toIndex: Int) -> Unit,
    onEditPage         : (Int) -> Unit,
    onApplyFilterToAll : (FilterType) -> Unit,
    onAddMore          : () -> Unit,
    onSaveAsImages     : () -> Unit,
    onSaveAsPdf        : () -> Unit,
    onBack             : () -> Unit
) {
    val context  = LocalContext.current
    val density  = LocalDensity.current

    var showFilterMenu  by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    var dragIdx      by remember { mutableIntStateOf(-1) }
    var dragDx       by remember { mutableFloatStateOf(0f) }
    var dragDy       by remember { mutableFloatStateOf(0f) }
    var cardWidthPx  by remember { mutableFloatStateOf(0f) }
    var cardHeightPx by remember { mutableFloatStateOf(0f) }
    val gapPx = with(density) { 12.dp.toPx() }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun haptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)
                    ?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
                    ?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    fun computeDropIndex(fromIdx: Int, dx: Float, dy: Float): Int {
        if (cardWidthPx <= 0 || cardHeightPx <= 0) return fromIdx
        val colShift = (dx / (cardWidthPx + gapPx)).let { kotlin.math.round(it).toInt() }
        val rowShift = (dy / (cardHeightPx + gapPx)).let { kotlin.math.round(it).toInt() }
        val newCol   = ((fromIdx % 2) + colShift).coerceIn(0, 1)
        val newRow   = ((fromIdx / 2) + rowShift).coerceAtLeast(0)
        return (newRow * 2 + newCol).coerceIn(0, pages.size - 1)
    }

    if (showExportSheet) {
        ExportBottomSheet(
            pageCount    = pages.size,
            isProcessing = isProcessing,
            onSaveAsPdf  = { showExportSheet = false; onSaveAsPdf() },
            onSaveImages = { showExportSheet = false; onSaveAsImages() },
            onDismiss    = { showExportSheet = false }
        )
    }

    Box(Modifier.fillMaxSize().background(BgBase)) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth().background(BgBase).statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Review", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (pages.isNotEmpty())
                            Text("${pages.size} ${if (pages.size == 1) "page" else "pages"}", color = InkMid, fontSize = 11.sp)
                    }
                    Box {
                        IconButton(onClick = { showFilterMenu = true }, enabled = pages.size > 1) {
                            Icon(Icons.Default.AutoFixHigh, "Filter all", tint = if (pages.size > 1) Coral else InkDim)
                        }
                        DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }, containerColor = BgCard) {
                            Text("Apply to all pages", style = MaterialTheme.typography.labelSmall, color = InkMid, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            HorizontalDivider(color = StrokeLight)
                            FilterType.entries.forEach { f ->
                                DropdownMenuItem(text = { Text(f.displayName, color = Ink, fontSize = 14.sp) }, onClick = { showFilterMenu = false; onApplyFilterToAll(f) })
                            }
                        }
                    }
                }
                if (isProcessing) {
                    LinearProgressIndicator(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
                        color = Coral, trackColor = StrokeLight
                    )
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(StrokeLight))
            }

            // ── Grid ─────────────────────────────────────────────────────────
            Box(Modifier.weight(1f)) {
                if (pages.isEmpty()) {
                    EmptyState()
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp).padding(top = 6.dp, bottom = 130.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DragHandle, null, Modifier.size(12.dp), tint = InkDim)
                            Spacer(Modifier.width(4.dp))
                            Text("Hold to reorder", color = InkDim, fontSize = 11.sp)
                            Spacer(Modifier.width(16.dp))
                            Icon(Icons.Default.TouchApp, null, Modifier.size(12.dp), tint = InkDim)
                            Spacer(Modifier.width(4.dp))
                            Text("Tap to edit", color = InkDim, fontSize = 11.sp)
                        }

                        pages.chunked(2).forEachIndexed { rowIdx, rowPages ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowPages.forEachIndexed { colIdx, page ->
                                    val idx = rowIdx * 2 + colIdx
                                    val isDragging = dragIdx == idx
                                    val scale by animateFloatAsState(if (isDragging) 1.06f else 1f, spring(stiffness = 500f), label = "s$idx")
                                    Box(
                                        Modifier.weight(1f).aspectRatio(0.72f)
                                            .then(if (isDragging) Modifier.zIndex(100f) else Modifier)
                                            .graphicsLayer {
                                                if (cardWidthPx == 0f) { cardWidthPx = size.width; cardHeightPx = size.height }
                                                scaleX = scale; scaleY = scale
                                                if (isDragging) { translationX = dragDx; translationY = dragDy; shadowElevation = 24f; alpha = 0.95f }
                                            }
                                            .pointerInput(idx) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { dragIdx = idx; dragDx = 0f; dragDy = 0f; haptic() },
                                                    onDrag = { ch, amt -> ch.consume(); dragDx += amt.x; dragDy += amt.y },
                                                    onDragEnd = {
                                                        val t = computeDropIndex(dragIdx, dragDx, dragDy)
                                                        if (t != dragIdx) { onReorderPage(dragIdx, t); haptic() }
                                                        dragIdx = -1; dragDx = 0f; dragDy = 0f
                                                    },
                                                    onDragCancel = { dragIdx = -1; dragDx = 0f; dragDy = 0f }
                                                )
                                            }
                                    ) {
                                        PageCard(page = page, number = idx + 1, isDragging = isDragging, onTap = { onEditPage(idx) }, onDelete = { onDeletePage(idx) })
                                    }
                                }
                                if (rowPages.size == 1) Spacer(Modifier.weight(1f).aspectRatio(0.72f))
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        // ── Bottom action bar ─────────────────────────────────────────────────
        if (pages.isNotEmpty()) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp).navigationBarsPadding()
                    .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x22E8603C), spotColor = Color(0x22E8603C))
                    .clip(RoundedCornerShape(20.dp)).background(BgCard)
                    .border(1.dp, StrokeLight, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(13.dp)).clickable(onClick = onAddMore),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, "Add", tint = Ink, modifier = Modifier.size(20.dp)) }
                Box(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(13.dp))
                        .background(if (!isProcessing) Brush.horizontalGradient(listOf(Coral, Color(0xFFD94860))) else Brush.horizontalGradient(listOf(BgSurface, BgSurface)))
                        .clickable(enabled = !isProcessing) { showExportSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(15.dp), color = Coral, strokeWidth = 2.dp)
                            Text("Processing…", color = InkMid, fontSize = 13.sp)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(Icons.Default.IosShare, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("Export  •  ${pages.size} ${if (pages.size == 1) "page" else "pages"}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageCard(page: ScannedPage, number: Int, isDragging: Boolean, onTap: () -> Unit, onDelete: () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .shadow(if (isDragging) 16.dp else 2.dp, RoundedCornerShape(14.dp), ambientColor = if (isDragging) Color(0x33E8603C) else Color(0x14000000), spotColor = if (isDragging) Color(0x33E8603C) else Color(0x14000000))
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (isDragging) Coral.copy(0.5f) else StrokeLight, RoundedCornerShape(14.dp))
            .background(BgCard).clickable(onClick = onTap)
    ) {
        if (page.croppedBitmap == null) {
            Box(Modifier.fillMaxSize().background(BgSurface), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Coral, strokeWidth = 2.dp)
                    Text("Processing", color = InkMid, fontSize = 10.sp)
                }
            }
        } else {
            val bmp = page.displayBitmap
            Canvas(Modifier.fillMaxSize()) {
                val s = minOf(size.width / bmp.width, size.height / bmp.height)
                val w = bmp.width * s; val h = bmp.height * s
                val ox = (size.width - w) / 2f; val oy = (size.height - h) / 2f
                drawContext.canvas.nativeCanvas.drawBitmap(bmp, null, android.graphics.RectF(ox, oy, ox + w, oy + h), null)
            }
            Box(Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99FFFFFF)))))
        }

        // ── Page number badge (top-start) ─────────────────────────────────────
        Box(Modifier.align(Alignment.TopStart).padding(7.dp).clip(RoundedCornerShape(6.dp)).background(Ink.copy(0.80f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
            Text("$number", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        // ── Document type badge (top-center) ─────────────────────────────────
        page.docClassType?.let { docType ->
            if (docType != DocClassType.OTHER) {
                Box(
                    Modifier.align(Alignment.TopCenter).padding(top = 7.dp)
                        .clip(RoundedCornerShape(6.dp)).background(DocTypeBadgeBg)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(docType.displayName, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── Delete button (top-end) ───────────────────────────────────────────
        Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(26.dp).clip(CircleShape).background(BgCard).border(1.dp, StrokeLight, CircleShape).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, "Delete", tint = DangerRed, modifier = Modifier.size(13.dp))
        }

        // ── Filter badge (bottom-start) ───────────────────────────────────────
        if (page.filterType != FilterType.ORIGINAL) {
            Box(Modifier.align(Alignment.BottomStart).padding(7.dp).clip(RoundedCornerShape(6.dp)).background(Coral).padding(horizontal = 7.dp, vertical = 3.dp)) {
                Text(page.filterType.displayName, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Edit icon (bottom-end) ────────────────────────────────────────────
        Box(Modifier.align(Alignment.BottomEnd).padding(7.dp).size(24.dp).clip(CircleShape).background(Color.White.copy(0.80f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Edit, null, tint = InkMid, modifier = Modifier.size(11.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportBottomSheet(pageCount: Int, isProcessing: Boolean, onSaveAsPdf: () -> Unit, onSaveImages: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BgCard,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 36.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(StrokeMid)) }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp)) {
            Text("Export", color = Ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("$pageCount ${if (pageCount == 1) "page" else "pages"} ready", color = InkMid, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            ExportOption(Icons.Default.PictureAsPdf, "Save as PDF", "All pages merged into one file", Coral, !isProcessing, onSaveAsPdf)
            Spacer(Modifier.height(10.dp))
            ExportOption(Icons.Default.PhotoLibrary, "Save as Images", "Each page as a separate JPEG", GreenAccent, !isProcessing, onSaveImages)
        }
    }
}

@Composable
private fun ExportOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, accentColor: Color, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgSurface).border(1.dp, StrokeLight, RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accentColor.copy(0.10f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = InkMid, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, null, tint = InkDim, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(40.dp)) {
            Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(BgSurface).border(1.dp, StrokeLight, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DocumentScanner, null, tint = InkDim, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text("No pages yet", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Tap + to start scanning", color = InkMid, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}