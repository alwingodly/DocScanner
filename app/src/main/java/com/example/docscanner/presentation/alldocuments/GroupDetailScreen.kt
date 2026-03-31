package com.example.docscanner.presentation.alldocuments

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.docscanner.domain.model.Document

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId   : String,
    onBack    : () -> Unit,
    onDocClick: (Document) -> Unit,
    viewModel : AllDocumentsViewModel = hiltViewModel()
) {
    val rawDocs by viewModel.getGroupDocs(groupId)
        .collectAsState(initial = emptyList())

    val sectionOrder by viewModel.sectionDocOrder.collectAsState()

    // Apply saved order for this group
    val docs = remember(rawDocs, sectionOrder) {
        val orderKey  = "group_$groupId"
        val savedIds  = sectionOrder[orderKey]
        if (savedIds.isNullOrEmpty()) return@remember rawDocs
        val byId      = rawDocs.associateBy { it.id }
        val ordered   = savedIds.mapNotNull { byId[it] }
        val remaining = rawDocs.filter { it.id !in savedIds.toSet() }
        ordered + remaining
    }

    var contextDoc by remember { mutableStateOf<Document?>(null) }
    val sheetState = rememberModalBottomSheetState()

    // ── Drag state ────────────────────────────────────────────────────────
    val cardPos   = remember { HashMap<Int, CardPos>() }
    var dragState by remember { mutableStateOf(DragState()) }

    val columnCount = 3
    val gap         = 4.dp
    val rows        = remember(docs) { docs.chunked(columnCount) }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgBase)
    ) {
        // ── Status bar spacer ─────────────────────────────────────────────
        Spacer(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(BgCard)
        )

        // ── Header ────────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().background(BgCard)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack, null,
                        tint     = Ink,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Group · ${docs.size} doc${if (docs.size != 1) "s" else ""}",
                        color      = Ink,
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Long-press a tile to reorder",
                        color    = InkDim,
                        fontSize = 11.sp
                    )
                }
            }
            // Bottom border line
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(StrokeLight)
            )
        }

        // ── Content ───────────────────────────────────────────────────────
        if (docs.isEmpty()) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No documents in this group", color = InkDim, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                contentPadding      = PaddingValues(top = gap, bottom = 40.dp),
                modifier            = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                rows.forEachIndexed { rowIdx, rowDocs ->
                    item(key = rowDocs.first().id) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            rowDocs.forEachIndexed { colIdx, doc ->
                                val flatIdx = rowIdx * columnCount + colIdx

                                val isDragging by remember(flatIdx) {
                                    derivedStateOf { dragState.idx == flatIdx }
                                }
                                val tx by remember(flatIdx) {
                                    derivedStateOf { if (isDragging) dragState.deltaX else 0f }
                                }
                                val ty by remember(flatIdx) {
                                    derivedStateOf { if (isDragging) dragState.deltaY else 0f }
                                }
                                val alpha by animateFloatAsState(
                                    targetValue   = if (isDragging) 0.45f else 1f,
                                    animationSpec = tween(150),
                                    label         = "a$flatIdx"
                                )
                                val scale by animateFloatAsState(
                                    targetValue   = if (isDragging) 1.07f else 1f,
                                    animationSpec = spring(stiffness = 400f),
                                    label         = "s$flatIdx"
                                )

                                Box(
                                    Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { coords ->
                                            val b = coords.boundsInWindow()
                                            cardPos[flatIdx] = CardPos(
                                                (b.left + b.right) / 2f,
                                                (b.top  + b.bottom) / 2f
                                            )
                                        }
                                        .graphicsLayer {
                                            this.alpha        = alpha
                                            this.scaleX       = scale
                                            this.scaleY       = scale
                                            this.translationX = tx
                                            this.translationY = ty
                                        }
                                        .pointerInput(doc.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    dragState = DragState(idx = flatIdx)
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragState = dragState.copy(
                                                        deltaX = dragState.deltaX + amount.x,
                                                        deltaY = dragState.deltaY + amount.y
                                                    )
                                                },
                                                onDragEnd = {
                                                    val cur = dragState
                                                    if (cur.idx >= 0) {
                                                        val p = cardPos[cur.idx]
                                                        if (p != null) {
                                                            val dcx = p.cx + cur.deltaX
                                                            val dcy = p.cy + cur.deltaY
                                                            val target = cardPos.entries
                                                                .filter { it.key != cur.idx }
                                                                .minByOrNull { (_, q) ->
                                                                    val dx = q.cx - dcx
                                                                    val dy = q.cy - dcy
                                                                    dx * dx + dy * dy
                                                                }?.key ?: cur.idx
                                                            if (target != cur.idx)
                                                                viewModel.reorderGroupDocs(
                                                                    groupId,
                                                                    cur.idx,
                                                                    target,
                                                                    docs
                                                                )
                                                        }
                                                    }
                                                    dragState = DragState()
                                                },
                                                onDragCancel = { dragState = DragState() }
                                            )
                                        }
                                ) {
                                    GroupGalleryTile(
                                        doc       = doc,
                                        index     = flatIdx + 1,   // 1-based position number
                                        onTap     = { onDocClick(doc) },
                                        onMoreTap = { contextDoc = doc }
                                    )
                                }
                            }
                            // Fill remaining columns in last row
                            repeat(columnCount - rowDocs.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Tile context sheet ────────────────────────────────────────────────
    if (contextDoc != null) {
        val doc = contextDoc!!
        ModalBottomSheet(
            onDismissRequest = { contextDoc = null },
            sheetState       = sheetState,
            containerColor   = BgCard,
            shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(StrokeMid)
                )
            }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                // ── Doc preview header ────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgSurface)
                            .border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
                    ) {
                        if (doc.thumbnailPath != null)
                            AsyncImage(
                                model              = doc.thumbnailPath,
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize()
                            )
                        else
                            Icon(
                                Icons.Default.Image, null,
                                tint     = InkDim,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(22.dp)
                            )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            doc.name,
                            color      = Ink,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        val label = doc.docClassLabel ?: "Other"
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(TypeColors[label] ?: InkMid)
                            )
                            Text(label, color = InkMid, fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)

                // View
                ContextAction(
                    icon  = Icons.Default.Visibility,
                    label = "View",
                    color = Color(0xFF2563EB)
                ) {
                    onDocClick(doc)
                    contextDoc = null
                }

                HorizontalDivider(
                    color     = StrokeLight,
                    thickness = 0.5.dp,
                    modifier  = Modifier.padding(horizontal = 20.dp)
                )

                // Remove from group
                ContextAction(
                    icon  = Icons.Default.LinkOff,
                    label = "Remove from group",
                    color = InkMid
                ) {
                    viewModel.removeFromGroup(doc.id)
                    contextDoc = null
                }

                HorizontalDivider(
                    color     = StrokeLight,
                    thickness = 0.5.dp,
                    modifier  = Modifier.padding(horizontal = 20.dp)
                )

                // Disband entire group
                ContextAction(
                    icon  = Icons.Default.FolderOff,
                    label = "Disband entire group",
                    color = DangerRed
                ) {
                    viewModel.disbandGroup(groupId)
                    contextDoc = null
                    onBack()
                }
            }
        }
    }
}

// ── Tile with position-number badge ──────────────────────────────────────────

@Composable
private fun GroupGalleryTile(
    doc      : Document,
    index    : Int,                // 1-based position number
    modifier : Modifier = Modifier,
    onTap    : () -> Unit,
    onMoreTap: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(BgSurface)
            .clickable(onClick = onTap)
    ) {
        // ── Thumbnail ─────────────────────────────────────────────────────
        if (doc.thumbnailPath != null)
            AsyncImage(
                model              = doc.thumbnailPath,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        else
            Icon(
                Icons.Default.Image, null,
                Modifier
                    .size(28.dp)
                    .align(Alignment.Center),
                tint = InkDim
            )

        // ── Bottom scrim ──────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC000000))
                    )
                )
        )

        // ── Doc name ──────────────────────────────────────────────────────
        Text(
            doc.name,
            color      = Color.White,
            fontSize   = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 6.dp, end = 28.dp)
        )

        // ── Type badge (top-left) ─────────────────────────────────────────
        val label = doc.docClassLabel ?: "Other"
        val c     = TypeColors[label] ?: InkMid
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(c.copy(0.85f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                label,
                color      = Color.White,
                fontSize   = 7.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // ── Position number badge (top-right) ─────────────────────────────
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$index",
                color      = Color.White,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ── 3-dot menu (bottom-right) ─────────────────────────────────────
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onMoreTap),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MoreVert, null,
                tint     = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}