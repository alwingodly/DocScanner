package com.example.docscanner.presentation.alldocuments

import android.Manifest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.docscanner.domain.model.Document
import com.example.docscanner.presentation.folder.DragDeleteZone

// ─── Paper Studio tokens ──────────────────────────────────────────────────────
internal val BgBase      = Color(0xFFFAF8F5)
internal val BgSurface   = Color(0xFFF3F0EB)
internal val BgCard      = Color(0xFFFFFFFF)
internal val BgElevated  = Color(0xFFEFECE6)
internal val StrokeLight = Color(0xFFE2DDD8)
internal val StrokeMid   = Color(0xFFCDC8C0)
internal val Coral       = Color(0xFFE8603C)
internal val CoralSoft   = Color(0x1AE8603C)
internal val Ink         = Color(0xFF1A1A2E)
internal val InkMid      = Color(0xFF6B6878)
internal val InkDim      = Color(0xFFB8B4BC)
internal val GreenAccent = Color(0xFF2E9E6B)
internal val DangerRed   = Color(0xFFD94040)

private val DocTypeBadgeBg = Color(0xFF2E6BE6)

@Composable
fun AllDocumentsScreen(
    dragState       : SidebarDragState,
    onDocumentClick : (Document) -> Unit,
    onScanClick     : () -> Unit,
    viewModel       : AllDocumentsViewModel = hiltViewModel()
) {
    val documents by viewModel.documents.collectAsState()
    val context = LocalContext.current

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun haptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    val wasOverDelete = remember { mutableStateOf(false) }
    LaunchedEffect(dragState.isOverDeleteZone) {
        if (dragState.isOverDeleteZone && !wasOverDelete.value) haptic()
        wasOverDelete.value = dragState.isOverDeleteZone
    }

    var pendingDeleteDoc by remember { mutableStateOf<Document?>(null) }
    var documentToDelete by remember { mutableStateOf<Document?>(null) }

    val configuration  = LocalConfiguration.current
    val density        = LocalDensity.current
    val screenWidthPx  = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val currentDragX   = dragState.startX + dragState.dragOffsetX
    val currentDragY   = dragState.startY + dragState.dragOffsetY
    val isNearCorner   = dragState.isDragging &&
            currentDragX > screenWidthPx  * 0.75f &&
            currentDragY > screenHeightPx * 0.75f

    Box(Modifier.fillMaxSize().background(BgBase)) {
        if (documents.isEmpty()) {
            Column(
                Modifier.align(Alignment.Center).padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Description, null, tint = InkDim, modifier = Modifier.size(36.dp)) }
                Spacer(Modifier.height(4.dp))
                Text("No documents yet", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Tap Scan Document to get started", color = InkMid, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp).padding(top = 10.dp, bottom = 100.dp)
            ) {
                documents.chunked(2).forEach { rowDocs ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowDocs.forEach { doc ->
                            val isDragging = dragState.draggingDocumentId == doc.id
                            DocCard(
                                document         = doc,
                                isDragging       = isDragging,
                                modifier         = Modifier.weight(1f),
                                onTap            = { onDocumentClick(doc) },
                                onLongPressStart = { wx, wy ->
                                    pendingDeleteDoc = doc
                                    dragState.onDragStart(doc.id, wx, wy, doc.thumbnailPath, doc.name, doc.pageCount)
                                    haptic()
                                },
                                onDrag       = { dx, dy -> dragState.onDrag(dx, dy) },
                                onDragEnd    = {
                                    val overDelete = dragState.isOverDeleteZone
                                    val target     = dragState.onDragEnd()
                                    when {
                                        overDelete -> { documentToDelete = pendingDeleteDoc; pendingDeleteDoc = null }
                                        target != null -> {
                                            viewModel.moveDocumentToFolder(doc.id, target)
                                            haptic()
                                            pendingDeleteDoc = null
                                        }
                                        else -> pendingDeleteDoc = null
                                    }
                                },
                                onDragCancel = { dragState.onDragCancel(); pendingDeleteDoc = null }
                            )
                        }
                        if (rowDocs.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (!dragState.isDragging) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(16.dp).navigationBarsPadding()
                    .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x22E8603C), spotColor = Color(0x22E8603C))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Coral, Color(0xFFD94860))))
                    .clickable(onClick = onScanClick).padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Scan Document", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        DragDeleteZone(
            visible   = isNearCorner,
            isHovered = dragState.isOverDeleteZone,
            modifier  = Modifier
                .align(Alignment.BottomEnd)
                .zIndex(10f)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    dragState.deleteZoneLeft   = pos.x
                    dragState.deleteZoneTop    = pos.y
                    dragState.deleteZoneRight  = pos.x + coords.size.width
                    dragState.deleteZoneBottom = pos.y + coords.size.height
                }
        )
    }

    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(18.dp),
            title   = { Text("Delete Document", color = Ink, fontWeight = FontWeight.Bold) },
            text    = { Text("Delete \"${doc.name}\"? This cannot be undone.", color = InkMid, fontSize = 14.sp) },
            confirmButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(DangerRed.copy(0.10f))
                        .border(1.dp, DangerRed.copy(0.30f), RoundedCornerShape(10.dp))
                        .clickable { viewModel.deleteDocument(doc); documentToDelete = null }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) { Text("Delete", color = DangerRed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            },
            dismissButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
                        .clickable { documentToDelete = null }.padding(horizontal = 16.dp, vertical = 9.dp)
                ) { Text("Cancel", color = InkMid, fontSize = 14.sp) }
            }
        )
    }
}

@Composable
private fun DocCard(
    document        : Document,
    isDragging      : Boolean,
    modifier        : Modifier,
    onTap           : () -> Unit,
    onLongPressStart: (Float, Float) -> Unit,
    onDrag          : (Float, Float) -> Unit,
    onDragEnd       : () -> Unit,
    onDragCancel    : () -> Unit
) {
    var cardWindowX by remember { mutableFloatStateOf(0f) }
    var cardWindowY by remember { mutableFloatStateOf(0f) }
    val cardAlpha   by animateFloatAsState(if (isDragging) 0f else 1f, tween(150), label = "a")
    val cardScale   by animateFloatAsState(if (isDragging) 0.94f else 1f, spring(stiffness = 400f), label = "s")

    Column(
        modifier
            .graphicsLayer { alpha = cardAlpha; scaleX = cardScale; scaleY = cardScale }
            .onGloballyPositioned { coords -> val p = coords.positionInWindow(); cardWindowX = p.x; cardWindowY = p.y }
            .pointerInput(document.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart  = { offset -> onLongPressStart(cardWindowX + offset.x, cardWindowY + offset.y) },
                    onDrag       = { change, amount -> change.consume(); onDrag(amount.x, amount.y) },
                    onDragEnd    = onDragEnd,
                    onDragCancel = onDragCancel
                )
            }
            .shadow(1.dp, RoundedCornerShape(12.dp), ambientColor = Color(0x0E000000), spotColor = Color(0x0E000000))
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
            .background(BgCard)
            .clickable(enabled = !isDragging, onClick = onTap)
    ) {
        // ── Thumbnail with classification badge ───────────────────────────────
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).background(BgSurface),
            contentAlignment = Alignment.Center
        ) {
            if (document.thumbnailPath != null) {
                AsyncImage(
                    model              = document.thumbnailPath,
                    contentDescription = document.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Image, null, Modifier.size(36.dp), tint = InkDim)
            }

            // ── Document classification label badge ───────────────────────────
            document.docClassLabel?.let { label ->
                if (label != "Document") {  // Don't show badge for "OTHER"
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DocTypeBadgeBg)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── Document name ─────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp)) {
            Text(
                document.name,
                color      = Ink,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
        }
    }
}