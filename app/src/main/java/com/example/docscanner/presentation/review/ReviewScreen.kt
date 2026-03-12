package com.example.docscanner.presentation.review

import android.Manifest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.docscanner.domain.model.FilterType
import com.example.docscanner.domain.model.ScannedPage

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
    onSaveAsImages     : () -> Unit,   // save pages as individual image files
    onSaveAsPdf        : () -> Unit,   // merge pages into one PDF
    onBack             : () -> Unit
) {
    val context  = LocalContext.current
    val density  = LocalDensity.current
    var showFilterMenu by remember { mutableStateOf(false) }

    var dragIdx      by remember { mutableIntStateOf(-1) }
    var dragDx       by remember { mutableFloatStateOf(0f) }
    var dragDy       by remember { mutableFloatStateOf(0f) }
    var cardWidthPx  by remember { mutableFloatStateOf(0f) }
    var cardHeightPx by remember { mutableFloatStateOf(0f) }
    val gapPx = with(density) { 10.dp.toPx() }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun haptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)
                    ?.defaultVibrator?.vibrate(
                        VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
                    ?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    fun computeDropIndex(fromIdx: Int, dx: Float, dy: Float): Int {
        if (cardWidthPx <= 0 || cardHeightPx <= 0) return fromIdx
        val fromRow  = fromIdx / 2
        val fromCol  = fromIdx % 2
        val colShift = (dx / (cardWidthPx + gapPx)).let { kotlin.math.round(it).toInt() }
        val rowShift = (dy / (cardHeightPx + gapPx)).let { kotlin.math.round(it).toInt() }
        val newCol   = (fromCol + colShift).coerceIn(0, 1)
        val newRow   = (fromRow + rowShift).coerceAtLeast(0)
        return (newRow * 2 + newCol).coerceIn(0, pages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review & Export") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (pages.size > 1) {
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(Icons.Default.AutoFixHigh, "Filter all")
                            }
                            DropdownMenu(
                                expanded         = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false }
                            ) {
                                Text(
                                    "Apply to all",
                                    style    = MaterialTheme.typography.labelMedium,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                FilterType.entries.forEach { f ->
                                    DropdownMenuItem(
                                        text    = { Text(f.displayName) },
                                        onClick = { showFilterMenu = false; onApplyFilterToAll(f) }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMore) {
                Icon(Icons.Default.Add, "Add more pages")
            }
        },
        bottomBar = {
            if (pages.isNotEmpty()) {
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Processing indicator
                        if (isProcessing) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Processing…", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        val pageLabel = "${pages.size} ${if (pages.size == 1) "page" else "pages"}"

                        // ── Save as PDF ───────────────────────────────────────
                        Button(
                            onClick  = onSaveAsPdf,
                            enabled  = !isProcessing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Save as PDF  ($pageLabel)")
                        }

                        // ── Save as Images ────────────────────────────────────
                        OutlinedButton(
                            onClick  = onSaveAsImages,
                            enabled  = !isProcessing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Save as Images  ($pageLabel)")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (pages.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Description, null,
                    Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text("No pages scanned yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Tap + to scan pages", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    "Hold & drag to reorder  •  Tap to edit",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                val rows = pages.chunked(2)
                rows.forEachIndexed { rowIdx, rowPages ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowPages.forEachIndexed { colIdx, page ->
                            val idx        = rowIdx * 2 + colIdx
                            val isDragging = dragIdx == idx

                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(0.7f)
                                    .then(if (isDragging) Modifier.zIndex(100f) else Modifier)
                                    .graphicsLayer {
                                        if (cardWidthPx == 0f) {
                                            cardWidthPx  = size.width
                                            cardHeightPx = size.height
                                        }
                                        if (isDragging) {
                                            translationX    = dragDx
                                            translationY    = dragDy
                                            scaleX          = 1.1f
                                            scaleY          = 1.1f
                                            shadowElevation = 24f
                                            alpha           = 0.92f
                                        }
                                    }
                                    .pointerInput(idx) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                dragIdx = idx
                                                dragDx  = 0f
                                                dragDy  = 0f
                                                haptic()
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragDx += amount.x
                                                dragDy += amount.y
                                            },
                                            onDragEnd = {
                                                val target = computeDropIndex(dragIdx, dragDx, dragDy)
                                                if (target != dragIdx) {
                                                    onReorderPage(dragIdx, target)
                                                    haptic()
                                                }
                                                dragIdx = -1; dragDx = 0f; dragDy = 0f
                                            },
                                            onDragCancel = {
                                                dragIdx = -1; dragDx = 0f; dragDy = 0f
                                            }
                                        )
                                    }
                            ) {
                                PageCard(
                                    page         = page,
                                    pageNumber   = idx + 1,
                                    isProcessing = page.croppedBitmap == null,
                                    onTap        = { onEditPage(idx) },
                                    onDelete     = { onDeletePage(idx) }
                                )
                            }
                        }
                        if (rowPages.size == 1) Spacer(Modifier.weight(1f).aspectRatio(0.7f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

// ── Page card ─────────────────────────────────────────────────────────────────

@Composable
private fun PageCard(
    page        : ScannedPage,
    pageNumber  : Int,
    isProcessing: Boolean,
    onTap       : () -> Unit,
    onDelete    : () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxSize().clickable(onClick = onTap),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (isProcessing) {
                Box(
                    Modifier.fillMaxSize().background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("Processing…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                val bmp = page.displayBitmap
                Canvas(Modifier.fillMaxSize()) {
                    val s = minOf(size.width / bmp.width, size.height / bmp.height)
                    val w = bmp.width * s;  val h = bmp.height * s
                    val x = (size.width - w) / 2f; val y = (size.height - h) / 2f
                    drawContext.canvas.nativeCanvas.drawBitmap(
                        bmp, null,
                        android.graphics.RectF(x, y, x + w, y + h),
                        null
                    )
                }
            }

            // Delete button
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.6f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
            }

            // Page number badge
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(0.6f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text("$pageNumber", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }

            // Filter badge
            if (page.filterType != FilterType.ORIGINAL) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        page.filterType.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}