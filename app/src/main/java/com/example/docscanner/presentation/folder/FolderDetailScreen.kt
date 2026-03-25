package com.example.docscanner.presentation.folder

import android.widget.Toast
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.docscanner.domain.model.Document
import com.example.docscanner.navigation.ALL_DOCUMENTS_ID
import com.example.docscanner.presentation.alldocuments.*

private fun Document.isMergeable(): Boolean = pdfPath == null && !isMergedSource

@Composable
fun FolderDetailScreen(
    folderId: String,
    folderName: String,
    folderIcon: String,
    showTopBar: Boolean = true,
    dragState: SidebarDragState,
    isSelectMode: Boolean,
    selectAllTrigger: Int,
    onSelectToggle: () -> Unit,
    onStartScan: () -> Unit,
    onOpenDocument: (Document) -> Unit,
    onMergeSelected: (List<Document>, String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onSelectedCountChanged: (Int) -> Unit = {},
    onDocumentCountChanged: (Int) -> Unit = {},
    viewModel: FolderViewModel = hiltViewModel()
) {
    LaunchedEffect(folderId) { viewModel.loadFolder(folderId) }
    val documents by viewModel.documents.collectAsState()
    val context = LocalContext.current

    // Local reorder state — maintains display order independent of DB
    var localOrder by remember(documents) { mutableStateOf(documents.map { it.id }) }
    val orderedDocuments = remember(localOrder, documents) {
        val docMap = documents.associateBy { it.id }
        localOrder.mapNotNull { docMap[it] }
    }

    // Numbered selection for merge
    var selectedOrder by remember { mutableStateOf(listOf<String>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val selectedIds = selectedOrder.toSet()

    LaunchedEffect(isSelectMode) { if (!isSelectMode) selectedOrder = emptyList() }
    LaunchedEffect(documents) {
        selectedOrder = selectedOrder.filter { id -> documents.any { it.id == id } }
        // Sync local order with any new/removed docs
        val currentIds = documents.map { it.id }.toSet()
        val newOrder = localOrder.filter { it in currentIds }.toMutableList()
        documents.forEach { if (it.id !in newOrder) newOrder.add(it.id) }
        localOrder = newOrder
    }
    LaunchedEffect(selectedOrder.size) { onSelectedCountChanged(selectedOrder.size) }
    LaunchedEffect(documents.size) { onDocumentCountChanged(documents.size) }
    LaunchedEffect(selectAllTrigger) {
        if (selectAllTrigger > 0 && isSelectMode) {
            val all =
                documents.isNotEmpty() && documents.all { it.id in selectedIds }; selectedOrder =
                if (all) emptyList() else orderedDocuments.map { it.id }
        }
    }

    val mergeableDocs = selectedOrder.mapNotNull { id -> documents.find { it.id == id } }
        .filter { it.isMergeable() }
    val sharedCategory = if (mergeableDocs.size >= 2) {
        val labels = mergeableDocs.map { it.docClassLabel ?: "Other" }
            .toSet(); if (labels.size == 1) labels.first() else null
    } else null
    val canMerge = sharedCategory != null

    // Reorder drag state
    var reorderDragIndex by remember { mutableIntStateOf(-1) }
    val cardBounds =
        remember { mutableStateMapOf<Int, Pair<Float, Float>>() } // index → (top, bottom) in window

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBase)
    ) {
        Column(Modifier.fillMaxSize()) {
            if (!showTopBar) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(folderIcon, fontSize = 18.sp); Spacer(Modifier.width(6.dp)); Text(
                    folderName,
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                }
            }

            Box(Modifier.weight(1f)) {
                if (orderedDocuments.isEmpty()) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            null,
                            tint = StrokeMid,
                            modifier = Modifier.size(48.dp)
                        ); Text(
                        "Empty folder",
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ); Text("Scan or drag documents here", color = InkMid, fontSize = 13.sp)
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp)
                            .padding(top = 4.dp, bottom = if (isSelectMode) 140.dp else 100.dp)
                    ) {
                        orderedDocuments.chunked(2).forEachIndexed { rowIndex, rowDocs ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowDocs.forEachIndexed { colIndex, doc ->
                                    val flatIndex = rowIndex * 2 + colIndex
                                    if (isSelectMode) {
                                        val orderNum = selectedOrder.indexOf(doc.id)
                                        NumberedSelectCard(
                                            doc,
                                            if (orderNum >= 0) orderNum + 1 else null,
                                            Modifier.weight(1f)
                                        ) {
                                            selectedOrder =
                                                if (doc.id in selectedIds) selectedOrder - doc.id else selectedOrder + doc.id
                                        }
                                    } else {
                                        ReorderableDragCard(
                                            document = doc,
                                            index = flatIndex,
                                            dragState = dragState,
                                            isBeingReordered = reorderDragIndex == flatIndex,
                                            modifier = Modifier.weight(1f),
                                            onTap = { onOpenDocument(doc) },
                                            onReorderStart = { reorderDragIndex = flatIndex },
                                            onReorderDrop = { targetIdx ->
                                                if (targetIdx >= 0 && targetIdx != flatIndex && targetIdx < localOrder.size) {
                                                    val mutable = localOrder.toMutableList()
                                                    val item = mutable.removeAt(flatIndex)
                                                    mutable.add(targetIdx, item)
                                                    localOrder = mutable
                                                }
                                                reorderDragIndex = -1
                                            },
                                            onSidebarDrop = { target ->
                                                if (target != null && target != folderId) viewModel.moveDocument(
                                                    doc.id,
                                                    folderId,
                                                    if (target == ALL_DOCUMENTS_ID) "" else target
                                                )
                                            },
                                            onRegisterBounds = { idx, top, bottom ->
                                                cardBounds[idx] = Pair(top, bottom)
                                            },
                                            cardBounds = cardBounds
                                        )
                                    }
                                }
                                if (rowDocs.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // Select bottom bar
        AnimatedVisibility(
            visible = isSelectMode && selectedOrder.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgCard)
                    .border(1.dp, StrokeLight, RoundedCornerShape(16.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DangerRed.copy(0.08f))
                        .border(1.dp, DangerRed.copy(0.20f), RoundedCornerShape(10.dp))
                        .clickable { showDeleteDialog = true }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            null,
                            tint = DangerRed,
                            modifier = Modifier.size(15.dp)
                        ); Text(
                        "Delete",
                        color = DangerRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (canMerge) Brush.horizontalGradient(
                                listOf(
                                    Coral,
                                    Color(0xFFD94860)
                                )
                            ) else Brush.horizontalGradient(listOf(BgSurface, BgSurface))
                        )
                        .clickable(enabled = canMerge) {
                            val docsInOrder =
                                selectedOrder.mapNotNull { id -> documents.find { it.id == id && it.isMergeable() } }
                            onMergeSelected(docsInOrder, folderId); selectedOrder =
                            emptyList(); onSelectToggle()
                        }, contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            null,
                            tint = if (canMerge) Color.White else InkDim,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            when {
                                canMerge -> "Merge ${mergeableDocs.size} → PDF"; mergeableDocs.size >= 2 -> "Same type needed"; mergeableDocs.size == 1 -> "Select 2+ images"; else -> "Select images"
                            },
                            color = if (canMerge) Color.White else InkDim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Scan FAB
        if (!isSelectMode && !dragState.isDragging) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .navigationBarsPadding()
                    .shadow(
                        12.dp,
                        RoundedCornerShape(14.dp),
                        ambientColor = Color(0x22E8603C),
                        spotColor = Color(0x22E8603C)
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(Coral, Color(0xFFD94860))))
                    .clickable(onClick = onStartScan)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    ); Text(
                    "Scan",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    "Delete ${selectedOrder.size} documents?",
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            },
            text = { Text("This cannot be undone.", color = InkMid, fontSize = 14.sp) },
            confirmButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DangerRed.copy(0.10f))
                        .border(1.dp, DangerRed.copy(0.30f), RoundedCornerShape(10.dp))
                        .clickable {
                            documents.filter { it.id in selectedIds }
                                .forEach { viewModel.deleteDocument(it) }; selectedOrder =
                            emptyList(); onSelectToggle(); showDeleteDialog = false
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
                        .clickable { showDeleteDialog = false }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) { Text("Cancel", color = InkMid, fontSize = 14.sp) }
            })
    }
}

// ── Reorderable drag card — in normal mode, drag to reorder within grid OR to sidebar folder ──

@Composable
private fun ReorderableDragCard(
    document: Document,
    index: Int,
    dragState: SidebarDragState,
    isBeingReordered: Boolean,
    modifier: Modifier,
    onTap: () -> Unit,
    onReorderStart: () -> Unit,
    onReorderDrop: (Int) -> Unit,
    onSidebarDrop: (String?) -> Unit,
    onRegisterBounds: (Int, Float, Float) -> Unit,
    cardBounds: Map<Int, Pair<Float, Float>>
) {
    var wx by remember { mutableFloatStateOf(0f) };
    var wy by remember { mutableFloatStateOf(0f) }
    val isDragging = dragState.draggingDocumentId == document.id
    val a by animateFloatAsState(if (isDragging) 0f else 1f, tween(150), label = "a")
    val s by animateFloatAsState(
        if (isDragging) 0.96f else 1f,
        spring(stiffness = 400f),
        label = "s"
    )

    Box(
        modifier
            .graphicsLayer { alpha = a; scaleX = s; scaleY = s }
            .onGloballyPositioned { c ->
                val p = c.positionInWindow(); wx = p.x; wy = p.y
                val b = c.boundsInWindow(); onRegisterBounds(index, b.top, b.bottom)
            }
            .pointerInput(document.id, index) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { o ->
                        onReorderStart()
                        dragState.onDragStart(
                            document.id,
                            wx + o.x,
                            wy + o.y,
                            document.thumbnailPath,
                            document.name,
                            document.pageCount
                        )
                    },
                    onDrag = { c, am -> c.consume(); dragState.onDrag(am.x, am.y) },
                    onDragEnd = {
                        val dragY = dragState.startY + dragState.dragOffsetY
                        val dragX = dragState.startX + dragState.dragOffsetX

                        // Check if dropped on sidebar
                        if (dragX < dragState.sidebarRightEdge) {
                            val target = dragState.onDragEnd()
                            onSidebarDrop(target)
                            onReorderDrop(-1) // reset reorder
                        } else {
                            // Find which card position the drag landed on
                            val targetIdx = cardBounds.entries.filter { it.key != index }
                                .minByOrNull { (_, bounds) ->
                                    val mid = (bounds.first + bounds.second) / 2f
                                    kotlin.math.abs(dragY - mid)
                                }?.key ?: -1
                            dragState.onDragEnd()
                            onReorderDrop(targetIdx)
                        }
                    },
                    onDragCancel = { dragState.onDragCancel(); onReorderDrop(-1) }
                )
            }
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
            .background(BgCard)
            .clickable(enabled = !isDragging, onClick = onTap)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(BgSurface),
                contentAlignment = Alignment.Center
            ) {
                if (document.thumbnailPath != null) AsyncImage(
                    model = document.thumbnailPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                else Icon(
                    if (document.pdfPath != null) Icons.Default.Description else Icons.Default.Image,
                    null,
                    tint = InkDim,
                    modifier = Modifier.size(32.dp)
                )
                ClassificationBadge(document, Modifier.align(Alignment.TopStart))
            }
            DocumentNameRow(document)
        }
    }
}

// ── Numbered selection card ───────────────────────────────────────────────────

@Composable
private fun NumberedSelectCard(
    document: Document,
    orderNumber: Int?,
    modifier: Modifier,
    onTap: () -> Unit
) {
    val isSelected = orderNumber != null
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .border(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) Ink else StrokeLight,
                RoundedCornerShape(10.dp)
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
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            else Icon(
                if (document.pdfPath != null) Icons.Default.Description else Icons.Default.Image,
                null,
                tint = InkDim,
                modifier = Modifier.size(32.dp)
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Ink else Color.White.copy(0.85f))
                    .border(
                        if (isSelected) 0.dp else 1.dp,
                        if (isSelected) Color.Transparent else StrokeMid,
                        CircleShape
                    ), contentAlignment = Alignment.Center
            ) {
                if (isSelected) Text(
                    "$orderNumber",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            ClassificationBadge(document, Modifier.align(Alignment.TopStart))
            if (!document.isMergeable()) Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Ink.copy(0.6f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) { Text("PDF", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
        }
        DocumentNameRow(document)
    }
}