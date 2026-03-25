package com.example.docscanner.presentation.alldocuments

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.docscanner.domain.model.DocClassType
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

private val PdfBadgeBg = Color(0xFFD94040)

internal val TypeColors = mapOf(
    "Aadhaar" to Color(0xFF2E6BE6),
    "PAN Card" to Color(0xFFD4880F),
    "Passport" to Color(0xFF7C3AED),
    "Voter ID" to Color(0xFF1D8055),
    "DL" to Color(0xFFD94040),
    "Other" to Color(0xFF6B6878)
)

private val ALL_TYPES = DocClassType.entries.map { it.displayName }
private const val FILTER_ALL = "All"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllDocumentsScreen(
    dragState: SidebarDragState, isSelectMode: Boolean, isOrganizeMode: Boolean,
    selectAllTrigger: Int, onSelectToggle: () -> Unit, onOrganizeToggle: () -> Unit,
    onDocumentClick: (Document) -> Unit, onScanClick: () -> Unit,
    onSelectedCountChanged: (Int) -> Unit = {}, onDocumentCountChanged: (Int) -> Unit = {},
    viewModel: AllDocumentsViewModel = hiltViewModel()
) {
    val documents by viewModel.documents.collectAsState()
    val isProcessing by viewModel.isOrganizing.collectAsState()
    val progress by viewModel.organizeProgress.collectAsState()
    val context = LocalContext.current

    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(FILTER_ALL) }
    val isLoading by viewModel.isLoading.collectAsState()
    // Bottom sheet state for type change
    var typeChangeDoc by remember { mutableStateOf<Document?>(null) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(isSelectMode) { if (!isSelectMode) selectedIds = emptySet() }
    LaunchedEffect(documents) {
        selectedIds = selectedIds.filter { id -> documents.any { it.id == id } }.toSet()
    }
    LaunchedEffect(selectedIds.size) { onSelectedCountChanged(selectedIds.size) }
    LaunchedEffect(documents.size) { onDocumentCountChanged(documents.size) }
    LaunchedEffect(selectAllTrigger) {
        if (selectAllTrigger > 0 && isSelectMode) {
            val all =
                documents.isNotEmpty() && documents.all { it.id in selectedIds }; selectedIds =
                if (all) emptySet() else documents.map { it.id }.toSet()
        }
    }
    LaunchedEffect(isOrganizeMode) { if (isOrganizeMode) viewModel.runAiOrganize() }

    val availableFilters = remember(documents) {
        val l =
            mutableListOf(FILTER_ALL); l.addAll(documents.mapNotNull { it.docClassLabel?.takeIf { lb -> lb != "Other" } }
        .distinct().sorted()); l
    }
    LaunchedEffect(availableFilters) {
        if (activeFilter !in availableFilters) activeFilter = FILTER_ALL
    }
    val filteredDocuments = remember(
        documents,
        activeFilter
    ) { if (activeFilter == FILTER_ALL) documents else documents.filter { it.docClassLabel == activeFilter } }
    val grouped = remember(
        documents,
        isOrganizeMode
    ) {
        if (!isOrganizeMode) emptyMap() else documents.groupBy { it.docClassLabel ?: "Other" }
            .toSortedMap()
    }

    Box(Modifier
        .fillMaxSize()
        .background(BgBase)) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = isOrganizeMode && isProcessing) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Analyzing documents...", color = InkMid, fontSize = 12.sp)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = Ink,
                        trackColor = StrokeLight
                    )
                }
            }

            if (!isSelectMode && !isOrganizeMode && availableFilters.size > 1) {
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
                                fontSize = 9.sp
                            )
                            }
                        }
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                when {
                    isLoading -> Box(Modifier.fillMaxSize()) // ← blank during switch, no flash
                    documents.isEmpty() -> EmptyState()
                    isOrganizeMode -> OrganizeView(
                        grouped, dragState, viewModel, context, onDocumentClick
                    ) { typeChangeDoc = it }
                    else -> NormalGrid(
                        if (isSelectMode) documents else filteredDocuments,
                        isSelectMode, selectedIds, dragState, viewModel,
                        onDocumentClick,
                        { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id }
                    ) { typeChangeDoc = it }
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
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgCard)
                    .border(1.dp, StrokeLight, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DangerRed.copy(0.08f))
                        .border(1.dp, DangerRed.copy(0.20f), RoundedCornerShape(10.dp))
                        .clickable { showDeleteDialog = true }, contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            null,
                            tint = DangerRed,
                            modifier = Modifier.size(16.dp)
                        ); Text(
                        "Delete ${selectedIds.size}",
                        color = DangerRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    }
                }
            }
        }

        if (!isSelectMode && !isOrganizeMode && !dragState.isDragging) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x22E8603C))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Coral, Color(0xFFD94860))))
                    .clickable(onClick = onScanClick)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    ); Text(
                    "Scan",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                }
            }
        }
    }

    // ── Type change bottom sheet ──────────────────────────────────────────────
    if (typeChangeDoc != null) {
        val doc = typeChangeDoc!!
        val currentLabel = doc.docClassLabel ?: "Other"

        ModalBottomSheet(
            onDismissRequest = { typeChangeDoc = null },
            sheetState = sheetState,
            containerColor = BgCard,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(StrokeMid)
                )
            }) {
            Column(Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)) {
                // Header with thumbnail
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BgSurface)
                            .border(1.dp, StrokeLight, RoundedCornerShape(8.dp))
                    ) {
                        if (doc.thumbnailPath != null) AsyncImage(
                            model = doc.thumbnailPath,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        else Icon(
                            Icons.Default.Image,
                            null,
                            tint = InkDim,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            doc.name,
                            color = Ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("Current: $currentLabel", color = InkMid, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)
                Spacer(Modifier.height(4.dp))

                // Type options
                ALL_TYPES.forEach { typeName ->
                    val typeColor = TypeColors[typeName] ?: InkMid
                    val isCurrent = typeName == currentLabel

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (isCurrent) Modifier.background(typeColor.copy(0.06f)) else Modifier)
                            .clickable {
                                if (!isCurrent) {
                                    viewModel.changeDocumentType(doc, typeName)
                                    Toast.makeText(
                                        context,
                                        "Changed to $typeName",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                typeChangeDoc = null
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(typeColor))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            typeName,
                            color = if (isCurrent) typeColor else Ink,
                            fontSize = 15.sp,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Spacer(Modifier.weight(1f))
                        if (isCurrent) Icon(
                            Icons.Default.Check,
                            null,
                            tint = typeColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
                    "Delete ${selectedIds.size} documents?",
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
                                .forEach { viewModel.deleteDocument(it) }; selectedIds =
                            emptySet(); onSelectToggle(); showDeleteDialog = false
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

@Composable
private fun EmptyState() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Description,
                null,
                tint = StrokeMid,
                modifier = Modifier.size(48.dp)
            ); Text(
            "No documents",
            color = Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        ); Text("Scan to get started", color = InkMid, fontSize = 13.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ORGANIZE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun OrganizeView(
    grouped: Map<String, List<Document>>,
    dragState: SidebarDragState,
    viewModel: AllDocumentsViewModel,
    context: android.content.Context,
    onDocumentClick: (Document) -> Unit,
    onBadgeTap: (Document) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = 100.dp)
    ) {
        grouped.forEach { (label, docs) ->
            val color = TypeColors[label] ?: InkMid
            val isDraggingGroup = dragState.isGroupDrag && dragState.draggingGroupLabel == label
            val alpha by animateFloatAsState(
                if (isDraggingGroup) 0.25f else 1f,
                tween(150),
                label = "sa_$label"
            )
            var hx by remember { mutableFloatStateOf(0f) };
            var hy by remember { mutableFloatStateOf(0f) }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { val p = it.positionInWindow(); hx = p.x; hy = p.y }
                        .pointerInput(label) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { o ->
                                    dragState.onGroupDragStart(
                                        label,
                                        docs.size,
                                        hx + o.x,
                                        hy + o.y,
                                        docs.firstOrNull()?.thumbnailPath
                                    )
                                },
                                onDrag = { c, a -> c.consume(); dragState.onDrag(a.x, a.y) },
                                onDragEnd = {
                                    val t = dragState.onDragEnd(); if (t != null) {
                                    viewModel.moveGroupToFolder(label, t); Toast.makeText(
                                        context,
                                        "${docs.size} moved",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                },
                                onDragCancel = { dragState.onDragCancel() })
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(color)
                    ); Spacer(Modifier.width(8.dp))
                    Text(
                        label,
                        color = Ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ); Spacer(Modifier.width(6.dp))
                    Text("${docs.size}", color = InkMid, fontSize = 11.sp); Spacer(
                    Modifier.weight(
                        1f
                    )
                )
                    Text("hold to move all", color = InkDim, fontSize = 9.sp)
                }
                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    docs.chunked(2).forEach { rowDocs ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowDocs.forEach { doc ->
                                BadgeTapDocCard(
                                    doc,
                                    dragState,
                                    Modifier.weight(1f),
                                    { onDocumentClick(doc) },
                                    { onBadgeTap(doc) }) { t ->
                                    if (t != null) viewModel.moveDocumentToFolder(
                                        doc.id,
                                        t
                                    )
                                }
                            }; if (rowDocs.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// NORMAL GRID
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NormalGrid(
    documents: List<Document>,
    isSelectMode: Boolean,
    selectedIds: Set<String>,
    dragState: SidebarDragState,
    viewModel: AllDocumentsViewModel,
    onDocumentClick: (Document) -> Unit,
    onSelect: (String) -> Unit,
    onBadgeTap: (Document) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = if (isSelectMode) 110.dp else 100.dp)
    ) {
        documents.chunked(2).forEach { rowDocs ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowDocs.forEach { doc ->
                    if (isSelectMode) SelectableDocCard(
                        doc,
                        doc.id in selectedIds,
                        Modifier.weight(1f)
                    ) { onSelect(doc.id) } else BadgeTapDocCard(
                        doc,
                        dragState,
                        Modifier.weight(1f),
                        { onDocumentClick(doc) },
                        { onBadgeTap(doc) }) { t ->
                        if (t != null) viewModel.moveDocumentToFolder(
                            doc.id,
                            t
                        )
                    }
                }
                if (rowDocs.size == 1) Spacer(Modifier.weight(1f))
            }; Spacer(Modifier.height(8.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CARDS
// ═══════════════════════════════════════════════════════════════════════════════

/** Card with tappable badge that triggers bottom sheet type picker */
@Composable
private fun BadgeTapDocCard(
    document: Document,
    dragState: SidebarDragState,
    modifier: Modifier,
    onTap: () -> Unit,
    onBadgeTap: () -> Unit,
    onDragEnd: (String?) -> Unit
) {
    var wx by remember { mutableFloatStateOf(0f) };
    var wy by remember { mutableFloatStateOf(0f) }
    val isDragging = dragState.draggingDocumentId == document.id
    val a by animateFloatAsState(if (isDragging) 0f else 1f, tween(150), label = "a");
    val s by animateFloatAsState(
        if (isDragging) 0.96f else 1f,
        spring(stiffness = 400f),
        label = "s"
    )

    Column(
        modifier
            .graphicsLayer { alpha = a; scaleX = s; scaleY = s }
            .onGloballyPositioned { val p = it.positionInWindow(); wx = p.x; wy = p.y }
            .pointerInput(document.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { o ->
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
                    onDragEnd = { onDragEnd(dragState.onDragEnd()) },
                    onDragCancel = { dragState.onDragCancel() })
            }
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
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
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            else Icon(Icons.Default.Image, null, Modifier.size(32.dp), tint = InkDim)
            // Tappable badge
            TappableBadge(document, Modifier.align(Alignment.TopStart), onBadgeTap)
        }
        DocumentNameRow(document)
    }
}

@Composable
private fun TappableBadge(document: Document, modifier: Modifier, onTap: () -> Unit) {
    val label = document.docClassLabel ?: "Other"
    val c = TypeColors[label] ?: InkMid
    Box(
        modifier
            .padding(5.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(c)
            .clickable(onClick = onTap)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
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
            ) else Icon(
                Icons.Default.Image,
                null,
                Modifier.size(32.dp),
                tint = InkDim
            ); Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isSelected) Ink else Color.White.copy(0.85f))
                .border(
                    if (isSelected) 0.dp else 1.dp,
                    if (isSelected) Color.Transparent else StrokeMid,
                    CircleShape
                ), contentAlignment = Alignment.Center
        ) {
            if (isSelected) Icon(
                Icons.Default.Check,
                null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }; ClassificationBadge(document, Modifier.align(Alignment.TopStart))
        }
        DocumentNameRow(document)
    }
}

// Non-tappable badge for select mode cards
@Composable
internal fun ClassificationBadge(document: Document, modifier: Modifier = Modifier) {
    val label = document.docClassLabel ?: "Other";
    val c = TypeColors[label] ?: InkMid; Box(
        modifier
            .padding(5.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(c)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) { Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium) }
}

@Composable
internal fun DocumentNameRow(document: Document, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (document.pdfPath != null) Icon(
            Icons.Default.PictureAsPdf,
            null,
            tint = PdfBadgeBg,
            modifier = Modifier.size(13.dp)
        ); Text(
        document.name,
        color = Ink,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    }
}