package com.example.docscanner.presentation.folder

import android.Manifest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.docscanner.domain.model.Document
import com.example.docscanner.navigation.ALL_DOCUMENTS_ID
import com.example.docscanner.presentation.alldocuments.*
import com.example.docscanner.presentation.viewer.DocumentType

private val DocTypeBadgeBg = Color(0xFF2E6BE6)

// ── Quarter-circle delete zone — bottom-right corner ─────────────────────────

@Composable
fun DragDeleteZone(
    visible  : Boolean,
    isHovered: Boolean,
    modifier : Modifier = Modifier,
    size     : Dp = 220.dp
) {
    val revealScale by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "reveal"
    )

    val alpha1 by animateFloatAsState(if (isHovered) 0.25f else 0.12f, tween(180), label = "a1")
    val alpha2 by animateFloatAsState(if (isHovered) 0.45f else 0.25f, tween(180), label = "a2")
    val alpha3 by animateFloatAsState(if (isHovered) 0.92f else 0.72f, tween(180), label = "a3")

    val btnScale by animateFloatAsState(
        targetValue   = if (isHovered) 1.14f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label         = "btn"
    )

    val density = LocalDensity.current
    val sizePx  = with(density) { size.toPx() }
    val iosRed  = Color(0xFFFF3B30)

    Box(
        modifier.size(size).graphicsLayer {
            scaleX = revealScale; scaleY = revealScale
            transformOrigin = TransformOrigin(1f, 1f)
            alpha = revealScale
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            fun arc(radius: Float, alpha: Float) = drawArc(
                color = iosRed.copy(alpha = alpha), startAngle = 180f, sweepAngle = 90f, useCenter = true,
                topLeft = Offset(this.size.width - radius, this.size.height - radius),
                size = Size(radius * 2, radius * 2)
            )
            arc(sizePx, alpha1)
            arc(sizePx * 0.72f, alpha2)
            arc(sizePx * 0.46f, alpha3)
        }

        val btnPad = (size.value * 0.17f).dp
        Column(
            Modifier.align(Alignment.BottomEnd).padding(bottom = btnPad, end = btnPad)
                .graphicsLayer { scaleX = btnScale; scaleY = btnScale },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                Modifier.size(48.dp)
                    .shadow(if (isHovered) 16.dp else 8.dp, CircleShape, ambientColor = iosRed.copy(0.5f), spotColor = iosRed.copy(0.5f))
                    .clip(CircleShape).background(if (isHovered) iosRed else Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, "Delete", tint = if (isHovered) Color.White else iosRed, modifier = Modifier.size(22.dp))
            }
            Text(if (isHovered) "Release" else "Delete", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── FolderDetailScreen ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folderId      : String,
    folderName    : String,
    folderIcon    : String,
    showTopBar    : Boolean = true,
    dragState     : SidebarDragState,
    onStartScan   : () -> Unit,
    onOpenDocument: (uri: String, type: DocumentType, name: String) -> Unit,
    onBack        : () -> Unit,
    viewModel     : FolderViewModel = hiltViewModel()
) {
    LaunchedEffect(folderId) { viewModel.loadFolder(folderId) }

    val documents        by viewModel.documents.collectAsState()
    val context          = LocalContext.current
    var documentToDelete by remember { mutableStateOf<Document?>(null) }
    var pendingDeleteDoc by remember { mutableStateOf<Document?>(null) }

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

    val configuration  = LocalConfiguration.current
    val density        = LocalDensity.current
    val screenWidthPx  = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val currentDragX   = dragState.startX + dragState.dragOffsetX
    val currentDragY   = dragState.startY + dragState.dragOffsetY
    val isNearCorner   = dragState.isDragging &&
            currentDragX > screenWidthPx  * 0.75f &&
            currentDragY > screenHeightPx * 0.75f

    val bodyContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.fillMaxSize().background(BgBase)) {
            if (documents.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center).padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(80.dp).clip(RoundedCornerShape(22.dp)).background(BgSurface)
                            .border(1.dp, StrokeLight, RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text(folderIcon, fontSize = 34.sp) }
                    Spacer(Modifier.height(4.dp))
                    Text("No documents yet", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Tap Scan Document to add one", color = InkMid, fontSize = 13.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!showTopBar) {
                        item(span = { GridItemSpan(2) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp, top = 4.dp)) {
                                Text(folderName, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    items(documents, key = { it.id }) { document ->
                        val isDragging = dragState.draggingDocumentId == document.id
                        DraggableDocumentGridCard(
                            document         = document,
                            isDragging       = isDragging,
                            onOpen           = {
                                val type = if (document.pdfPath != null) DocumentType.PDF else DocumentType.IMAGE
                                val uri  = document.pdfPath ?: document.thumbnailPath ?: return@DraggableDocumentGridCard
                                onOpenDocument(uri, type, document.name)
                            },
                            onDelete         = { documentToDelete = document },
                            onLongPressStart = { wx, wy ->
                                pendingDeleteDoc = document
                                dragState.onDragStart(document.id, wx, wy, document.thumbnailPath, document.name, document.pageCount)
                                haptic()
                            },
                            onDrag       = { dx, dy -> dragState.onDrag(dx, dy) },
                            onDragEnd    = {
                                val overDelete = dragState.isOverDeleteZone
                                val target     = dragState.onDragEnd()
                                when {
                                    overDelete -> { documentToDelete = pendingDeleteDoc; pendingDeleteDoc = null }
                                    target != null && target != folderId -> {
                                        val realTarget = if (target == ALL_DOCUMENTS_ID) "" else target
                                        viewModel.moveDocument(document.id, folderId, realTarget)
                                        haptic()
                                        pendingDeleteDoc = null
                                    }
                                    else -> pendingDeleteDoc = null
                                }
                            },
                            onDragCancel = { dragState.onDragCancel(); pendingDeleteDoc = null }
                        )
                    }
                }
            }

            if (!dragState.isDragging) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(14.dp).navigationBarsPadding()
                        .shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x22E8603C), spotColor = Color(0x22E8603C))
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(Coral, Color(0xFFD94860))))
                        .clickable(onClick = onStartScan)
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Scan Document", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (showTopBar) {
            Box(Modifier.fillMaxSize().background(BgBase)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().background(BgBase).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Text(folderIcon, fontSize = 16.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(folderName, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(Modifier.width(48.dp))
                        }
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(StrokeLight))
                    }
                    bodyContent(Modifier.weight(1f))
                }
            }
        } else {
            bodyContent(Modifier)
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

// ── Grid document card with classification badge ──────────────────────────────

@Composable
private fun DraggableDocumentGridCard(
    document        : Document,
    isDragging      : Boolean,
    onOpen          : () -> Unit,
    onDelete        : () -> Unit,
    onLongPressStart: (Float, Float) -> Unit,
    onDrag          : (Float, Float) -> Unit,
    onDragEnd       : () -> Unit,
    onDragCancel    : () -> Unit
) {
    var cardWindowX by remember { mutableFloatStateOf(0f) }
    var cardWindowY by remember { mutableFloatStateOf(0f) }
    val cardAlpha   by animateFloatAsState(if (isDragging) 0f else 1f, tween(150), label = "a")
    val cardScale   by animateFloatAsState(if (isDragging) 0.94f else 1f, spring(stiffness = 400f), label = "s")

    Box(
        Modifier.fillMaxWidth()
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
            .clickable(enabled = !isDragging, onClick = onOpen)
    ) {
        Column {
            // ── Thumbnail with classification badge ───────────────────────────
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).background(BgSurface),
                contentAlignment = Alignment.Center
            ) {
                if (document.thumbnailPath != null) {
                    AsyncImage(
                        model = document.thumbnailPath, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        if (document.pdfPath != null) Icons.Default.Description else Icons.Default.Image,
                        null, tint = InkDim, modifier = Modifier.size(32.dp)
                    )
                }

                // ── Document classification label badge ───────────────────────
                document.docClassLabel?.let { label ->
                    if (label != "Document") {
                        Box(
                            Modifier.align(Alignment.TopStart).padding(6.dp)
                                .clip(RoundedCornerShape(6.dp)).background(DocTypeBadgeBg)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── Document name ─────────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp)) {
                Text(
                    document.name, color = Ink, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}