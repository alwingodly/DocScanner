package com.example.docscanner.presentation.alldocuments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.docscanner.domain.model.Document

internal val BgBase = Color(0xFFFAF8F5)
internal val BgSurface = Color(0xFFF3F0EB)
internal val BgCard = Color(0xFFFFFFFF)
internal val BgElevated = Color(0xFFEFECE6)
internal val StrokeLight = Color(0xFFE2DDD8)
internal val StrokeMid = Color(0xFFCDC8C0)
internal val Coral = Color(0xFFE8603C)
internal val CoralSoft = Color(0x1AE8603C)
internal val Ink = Color(0xFF1A1A2E)
internal val InkMid = Color(0xFF6B6878)
internal val InkDim = Color(0xFFB8B4BC)
internal val GreenAccent = Color(0xFF2E9E6B)
internal val DangerRed = Color(0xFFD94040)

private val DocTypeBadgeBg = Color(0xFF2E6BE6)
private val SelectBlue = Color(0xFF2E6BE6)
private val PdfBadgeBg = Color(0xFFD94040)
private const val FILTER_ALL = "All"

@Composable
fun AllDocumentsScreen(
    dragState: SidebarDragState,
    isSelectMode: Boolean,
    selectAllTrigger: Int,
    onSelectToggle: () -> Unit,
    onDocumentClick: (Document) -> Unit,
    onScanClick: () -> Unit,
    onSelectedCountChanged: (Int) -> Unit = {},
    onDocumentCountChanged: (Int) -> Unit = {},
    viewModel: AllDocumentsViewModel = hiltViewModel()
) {
    val documents by viewModel.documents.collectAsState()
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var activeFilter by remember { mutableStateOf(FILTER_ALL) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isSelectMode) { if (!isSelectMode) selectedIds = emptySet() }
    LaunchedEffect(documents) {
        selectedIds = selectedIds.filter { id -> documents.any { it.id == id } }.toSet()
    }
    LaunchedEffect(selectedIds.size) { onSelectedCountChanged(selectedIds.size) }
    LaunchedEffect(documents.size) { onDocumentCountChanged(documents.size) }

    val availableFilters = remember(documents) {
        val labels =
            mutableListOf(FILTER_ALL); labels.addAll(documents.mapNotNull { it.docClassLabel?.takeIf { l -> l != "Document" } }
        .distinct().sorted()); labels
    }
    LaunchedEffect(availableFilters) {
        if (activeFilter !in availableFilters) activeFilter = FILTER_ALL
    }
    val filteredDocuments = remember(
        documents,
        activeFilter
    ) { if (activeFilter == FILTER_ALL) documents else documents.filter { it.docClassLabel == activeFilter } }

    LaunchedEffect(selectAllTrigger) {
        if (selectAllTrigger > 0 && isSelectMode) {
            val allSelected =
                filteredDocuments.isNotEmpty() && filteredDocuments.all { it.id in selectedIds }
            selectedIds = if (allSelected) emptySet() else filteredDocuments.map { it.id }.toSet()
        }
    }

    Box(Modifier
        .fillMaxSize()
        .background(BgBase)) {
        Column(Modifier.fillMaxSize()) {
            if (!isSelectMode && availableFilters.size > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableFilters.forEach { filter ->
                        val isActive = activeFilter == filter;
                        val count =
                            if (filter == FILTER_ALL) documents.size else documents.count { it.docClassLabel == filter }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isActive) Ink else BgSurface)
                                .border(
                                    1.dp,
                                    if (isActive) Ink else StrokeLight,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    activeFilter =
                                        if (isActive && filter != FILTER_ALL) FILTER_ALL else filter
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    filter,
                                    color = if (isActive) Color.White else Ink,
                                    fontSize = 11.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                                ); Text(
                                "$count",
                                color = if (isActive) Color.White.copy(0.7f) else InkDim,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                            }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                if (documents.isEmpty()) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(BgSurface)
                                .border(1.dp, StrokeLight, RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Description,
                                null,
                                tint = InkDim,
                                modifier = Modifier.size(36.dp)
                            )
                        }; Spacer(Modifier.height(4.dp)); Text(
                        "No documents yet",
                        color = Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ); Text(
                        "Tap Scan Document to get started",
                        color = InkMid,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    }
                } else if (filteredDocuments.isEmpty()) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            null,
                            tint = InkDim,
                            modifier = Modifier.size(36.dp)
                        ); Text("No $activeFilter documents", color = InkMid, fontSize = 14.sp)
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp)
                            .padding(top = 4.dp, bottom = if (isSelectMode) 110.dp else 100.dp)
                    ) {
                        filteredDocuments.chunked(2).forEach { rowDocs ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowDocs.forEach { doc ->
                                    if (isSelectMode) SelectableDocCard(
                                        document = doc,
                                        isSelected = doc.id in selectedIds,
                                        modifier = Modifier.weight(1f),
                                        onTap = {
                                            selectedIds =
                                                if (doc.id in selectedIds) selectedIds - doc.id else selectedIds + doc.id
                                        }) else DraggableDocCard(
                                        document = doc,
                                        dragState = dragState,
                                        modifier = Modifier.weight(1f),
                                        onTap = { onDocumentClick(doc) },
                                        onDragEnd = { target ->
                                            if (target != null) viewModel.moveDocumentToFolder(
                                                doc.id,
                                                target
                                            )
                                        })
                                }; if (rowDocs.size == 1) Spacer(Modifier.weight(1f))
                            }; Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = isSelectMode && selectedIds.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .navigationBarsPadding()
                    .shadow(
                        12.dp,
                        RoundedCornerShape(20.dp),
                        ambientColor = Color(0x22E8603C),
                        spotColor = Color(0x22E8603C)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
                    .border(1.dp, StrokeLight, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DangerRed.copy(0.10f))
                        .border(1.dp, DangerRed.copy(0.25f), RoundedCornerShape(12.dp))
                        .clickable { showDeleteSelectedDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            null,
                            tint = DangerRed,
                            modifier = Modifier.size(16.dp)
                        ); Text(
                        "Delete ${selectedIds.size} ${if (selectedIds.size == 1) "document" else "documents"}",
                        color = DangerRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    }
                }
            }
        }
        if (!isSelectMode && !dragState.isDragging) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .shadow(
                        12.dp,
                        RoundedCornerShape(16.dp),
                        ambientColor = Color(0x22E8603C),
                        spotColor = Color(0x22E8603C)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Coral, Color(0xFFD94860))))
                    .clickable(onClick = onScanClick)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    ); Text(
                    "Scan Document",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                }
            }
        }
    }
    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            containerColor = BgCard,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    "Delete ${selectedIds.size} Documents",
                    color = Ink,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Delete ${selectedIds.size} selected documents? This cannot be undone.",
                    color = InkMid,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DangerRed.copy(0.10f))
                        .border(1.dp, DangerRed.copy(0.30f), RoundedCornerShape(10.dp))
                        .clickable {
                            documents.filter { it.id in selectedIds }
                                .forEach { viewModel.deleteDocument(it) }; selectedIds =
                            emptySet(); onSelectToggle(); showDeleteSelectedDialog = false
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        "Delete",
                        color = DangerRed,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            },
            dismissButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
                        .clickable { showDeleteSelectedDialog = false }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) { Text("Cancel", color = InkMid, fontSize = 14.sp) }
            })
    }
}

@Composable
internal fun ClassificationBadge(document: Document, modifier: Modifier = Modifier) {
    document.docClassLabel?.let { label ->
        if (label != "Document") Box(
            modifier
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DocTypeBadgeBg)
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) { Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
internal fun DocumentNameRow(document: Document, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (document.pdfPath != null) Icon(
            Icons.Default.PictureAsPdf,
            null,
            tint = PdfBadgeBg,
            modifier = Modifier.size(14.dp)
        ); Text(
        document.name,
        color = Ink,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    }
}

@Composable
private fun DraggableDocCard(
    document: Document,
    dragState: SidebarDragState,
    modifier: Modifier,
    onTap: () -> Unit,
    onDragEnd: (String?) -> Unit
) {
    var cardWindowX by remember { mutableFloatStateOf(0f) };
    var cardWindowY by remember { mutableFloatStateOf(0f) };
    val isDragging = dragState.draggingDocumentId == document.id;
    val cardAlpha by animateFloatAsState(if (isDragging) 0f else 1f, tween(150), label = "a");
    val cardScale by animateFloatAsState(
        if (isDragging) 0.94f else 1f,
        spring(stiffness = 400f),
        label = "s"
    ); Column(
        modifier
            .graphicsLayer { alpha = cardAlpha; scaleX = cardScale; scaleY = cardScale }
            .onGloballyPositioned { coords ->
                val p = coords.positionInWindow(); cardWindowX = p.x; cardWindowY = p.y
            }
            .pointerInput(document.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        dragState.onDragStart(
                            document.id,
                            cardWindowX + offset.x,
                            cardWindowY + offset.y,
                            document.thumbnailPath,
                            document.name,
                            document.pageCount
                        )
                    },
                    onDrag = { change, amount ->
                        change.consume(); dragState.onDrag(
                        amount.x,
                        amount.y
                    )
                    },
                    onDragEnd = { onDragEnd(dragState.onDragEnd()) },
                    onDragCancel = { dragState.onDragCancel() })
            }
            .shadow(
                1.dp,
                RoundedCornerShape(12.dp),
                ambientColor = Color(0x0E000000),
                spotColor = Color(0x0E000000)
            )
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
            .background(BgCard)
            .clickable(enabled = !isDragging, onClick = onTap)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(BgSurface),
            contentAlignment = Alignment.Center
        ) {
            if (document.thumbnailPath != null) AsyncImage(
                model = document.thumbnailPath,
                contentDescription = document.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) else Icon(
                Icons.Default.Image,
                null,
                Modifier.size(36.dp),
                tint = InkDim
            ); ClassificationBadge(document, Modifier.align(Alignment.TopStart))
        }; DocumentNameRow(document)
    }
}

@Composable
private fun SelectableDocCard(
    document: Document,
    isSelected: Boolean,
    modifier: Modifier,
    onTap: () -> Unit
) {
    Column(
        modifier
            .shadow(
                1.dp,
                RoundedCornerShape(12.dp),
                ambientColor = Color(0x0E000000),
                spotColor = Color(0x0E000000)
            )
            .clip(RoundedCornerShape(12.dp))
            .border(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) SelectBlue else StrokeLight,
                RoundedCornerShape(12.dp)
            )
            .background(BgCard)
            .clickable(onClick = onTap)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(BgSurface),
            contentAlignment = Alignment.Center
        ) {
            if (document.thumbnailPath != null) AsyncImage(
                model = document.thumbnailPath,
                contentDescription = document.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) else Icon(Icons.Default.Image, null, Modifier.size(36.dp), tint = InkDim); Box(
            Modifier
                .align(
                    Alignment.TopEnd
                )
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isSelected) SelectBlue else Color.White.copy(0.85f))
                .border(
                    if (isSelected) 0.dp else 1.5.dp,
                    if (isSelected) Color.Transparent else StrokeMid,
                    CircleShape
                ), contentAlignment = Alignment.Center
        ) {
            if (isSelected) Icon(
                Icons.Default.Check,
                "Selected",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }; ClassificationBadge(document, Modifier.align(Alignment.TopStart))
        }; DocumentNameRow(document)
    }
}