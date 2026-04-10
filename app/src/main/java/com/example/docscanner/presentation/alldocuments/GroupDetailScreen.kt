package com.example.docscanner.presentation.alldocuments

import android.widget.Toast
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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.docscanner.domain.model.AadhaarGroup
import com.example.docscanner.domain.model.Document

private fun Document.groupBadgeLabel(): String = when {
    aadhaarSide == "FRONT" || docClassLabel == "Aadhaar Front" -> "Front"
    aadhaarSide == "BACK"  || docClassLabel == "Aadhaar Back"  -> "Back"
    else -> docClassLabel ?: "Other"
}

private val AadhaarBlue   = Color(0xFF2563EB)
private val AadhaarPurple = Color(0xFF7C3AED)
private val AadhaarGreen  = Color(0xFF16A34A)

// ═══════════════════════════════════════════════════════════════════════════
// GROUP DETAIL SCREEN
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId   : String,
    onBack    : () -> Unit,
    onDocClick: (Document, List<Document>) -> Unit,
    viewModel : AllDocumentsViewModel = hiltViewModel()
) {
    val allDocs       by viewModel.documents.collectAsState()
    val aadhaarGroups by viewModel.aadhaarGroups.collectAsState()
    val docGroupNames by viewModel.docGroupNames.collectAsState()
    val context = LocalContext.current

    val aadhaarGroup = remember(aadhaarGroups, groupId) {
        aadhaarGroups.firstOrNull { it.groupId == groupId }
    }
    val isAadhaarGroup = aadhaarGroup != null

    val rawDocs = remember(allDocs, groupId, aadhaarGroup) {
        if (aadhaarGroup != null)
            listOfNotNull(aadhaarGroup.frontDoc, aadhaarGroup.backDoc)
        else
            allDocs.filter { it.docGroupId == groupId }.sortedByDescending { it.createdAt }
    }

    val sectionOrder by viewModel.sectionDocOrder.collectAsState()
    val docs = remember(rawDocs, sectionOrder) {
        val orderKey  = "group_$groupId"
        val savedIds  = sectionOrder[orderKey]
        if (savedIds.isNullOrEmpty()) return@remember rawDocs
        val byId      = rawDocs.associateBy { it.id }
        val ordered   = savedIds.mapNotNull { byId[it] }
        val remaining = rawDocs.filter { it.id !in savedIds.toSet() }
        ordered + remaining
    }

    val groupDisplayName = remember(docGroupNames, groupId, aadhaarGroup) {
        aadhaarGroup?.displayName ?: docGroupNames[groupId] ?: "Group"
    }

    // ── Dialogs / sheets state ─────────────────────────────────────────────
    var contextDoc          by remember { mutableStateOf<Document?>(null) }
    var actionDoc           by remember { mutableStateOf<Document?>(null) }
    var showRenameDocDialog by remember { mutableStateOf(false) }
    var renameDocText       by remember { mutableStateOf("") }
    var showDeleteDocDialog by remember { mutableStateOf(false) }
    var showRenamePairDialog by remember { mutableStateOf(false) }
    var pairRenameText      by remember { mutableStateOf("") }
    val sheetState          = rememberModalBottomSheetState()

    // ── Drag state (regular groups) ────────────────────────────────────────
    val cardPos   = remember { HashMap<Int, CardPos>() }
    var dragState by remember { mutableStateOf(DragState()) }
    val columnCount = 3
    val gap         = 4.dp

    // ── Layout ────────────────────────────────────────────────────────────
    Column(
        Modifier
            .fillMaxSize()
            .background(BgBase)
    ) {
        // Status bar spacer
        Spacer(Modifier.fillMaxWidth().statusBarsPadding().background(BgCard))

        // ── Top bar ───────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().background(BgCard)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Ink, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        groupDisplayName,
                        color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (isAadhaarGroup) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val statusColor = if (aadhaarGroup?.isComplete == true) AadhaarGreen else Color(0xFFD97706)
                            val statusLabel = if (aadhaarGroup?.isComplete == true) "Pair complete" else "1 side missing"
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(statusColor.copy(alpha = 0.10f))
                                    .border(1.dp, statusColor.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    statusLabel,
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                "${docs.size} page${if (docs.size == 1) "" else "s"}",
                                color = InkDim,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        Text(
                            "${docs.size} document${if (docs.size != 1) "s" else ""} · long-press to reorder",
                            color = InkDim, fontSize = 11.sp
                        )
                    }
                }
                // Rename action in header
                IconButton(onClick = {
                    pairRenameText = if (isAadhaarGroup)
                        aadhaarGroup?.frontDoc?.name
                            ?.substringBefore("_Aadhaar")?.replace("_", " ")
                            ?: aadhaarGroup?.displayName ?: ""
                    else
                        groupDisplayName
                    showRenamePairDialog = true
                }) {
                    Icon(
                        Icons.Default.DriveFileRenameOutline, null,
                        tint = InkMid, modifier = Modifier.size(20.dp)
                    )
                }
            }
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .height(1.dp).background(StrokeLight)
            )
        }

        // ── Content ───────────────────────────────────────────────────────
        if (docs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No documents in this group", color = InkDim, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                contentPadding      = PaddingValues(top = 20.dp, bottom = 60.dp),
                modifier            = Modifier.fillMaxSize()
            ) {

                if (isAadhaarGroup && aadhaarGroup != null) {
                    // ── Aadhaar: full-size card pair ───────────────────────
                    item(key = "aadhaar_cards") {
                        AadhaarCardPair(
                            group        = aadhaarGroup,
                            onFrontTap   = { aadhaarGroup.frontDoc?.let { onDocClick(it, docs) } },
                            onBackTap    = { aadhaarGroup.backDoc?.let  { onDocClick(it, docs) } },
                            onFrontMore  = { aadhaarGroup.frontDoc?.let { contextDoc = it } },
                            onBackMore   = { aadhaarGroup.backDoc?.let  { contextDoc = it } }
                        )
                    }

                    // ── Aadhaar: inline quick actions ──────────────────────
                    item(key = "aadhaar_actions") {
                        AadhaarInlineActions(
                            onSwap    = {
                                viewModel.swapAadhaarSides(aadhaarGroup)
                                Toast.makeText(context, "Sides swapped", Toast.LENGTH_SHORT).show()
                            },
                            onUngroup = {
                                viewModel.ungroupAadhaar(aadhaarGroup)
                                onBack()
                            }
                        )
                    }

                    // ── Aadhaar: details section ───────────────────────────
                    val hasDetails = listOf(
                        aadhaarGroup.frontDoc?.aadhaarName,
                        aadhaarGroup.backDoc?.aadhaarName,
                        aadhaarGroup.dateOfBirth,
                        aadhaarGroup.gender,
                        aadhaarGroup.maskedNumber,
                        aadhaarGroup.address
                    ).any { !it.isNullOrBlank() }

                    if (hasDetails) {
                        item(key = "aadhaar_details") {
                            AadhaarDetailsSection(group = aadhaarGroup)
                        }
                    }

                } else {
                    // ── Regular group: drag-reorderable grid ───────────────
                    val rows = docs.chunked(columnCount)
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
                                        animationSpec = tween(150), label = "a$flatIdx"
                                    )
                                    val scale by animateFloatAsState(
                                        targetValue   = if (isDragging) 1.07f else 1f,
                                        animationSpec = spring(stiffness = 400f), label = "s$flatIdx"
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
                                                    onDragStart = { dragState = DragState(idx = flatIdx) },
                                                    onDrag      = { change, amount ->
                                                        change.consume()
                                                        dragState = dragState.copy(
                                                            deltaX = dragState.deltaX + amount.x,
                                                            deltaY = dragState.deltaY + amount.y
                                                        )
                                                    },
                                                    onDragEnd   = {
                                                        val cur = dragState
                                                        if (cur.idx >= 0) {
                                                            val p = cardPos[cur.idx]
                                                            if (p != null) {
                                                                val dcx    = p.cx + cur.deltaX
                                                                val dcy    = p.cy + cur.deltaY
                                                                val target = cardPos.entries
                                                                    .filter { it.key != cur.idx }
                                                                    .minByOrNull { (_, q) ->
                                                                        val dx = q.cx - dcx
                                                                        val dy = q.cy - dcy
                                                                        dx * dx + dy * dy
                                                                    }?.key ?: cur.idx
                                                                if (target != cur.idx)
                                                                    viewModel.reorderGroupDocs(groupId, cur.idx, target, docs)
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
                                            index     = flatIdx + 1,
                                            onTap     = { onDocClick(doc, docs) },
                                            onMoreTap = { contextDoc = doc }
                                        )
                                    }
                                }
                                repeat(columnCount - rowDocs.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Tile context sheet ─────────────────────────────────────────────────
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
                        .width(40.dp).height(4.dp)
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
                // Doc preview header
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
                                model = doc.thumbnailPath, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        else Icon(Icons.Default.Image, null, tint = InkDim,
                            modifier = Modifier.align(Alignment.Center).size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            doc.name, color = Ink, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        val label = if (isAadhaarGroup) doc.groupBadgeLabel() else (doc.docClassLabel ?: "Other")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(TypeColors[label] ?: InkMid))
                            Text(label, color = InkMid, fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)

                ContextAction(icon = Icons.Default.Visibility, label = "View", color = AadhaarBlue) {
                    onDocClick(doc, docs)
                    contextDoc = null
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 20.dp))

                ContextAction(
                    icon  = Icons.Default.DriveFileRenameOutline,
                    label = "Rename file",
                    color = AadhaarBlue
                ) {
                    actionDoc = doc
                    renameDocText = doc.name
                    showRenameDocDialog = true
                    contextDoc = null
                }

                if (isAadhaarGroup && aadhaarGroup != null) {
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))
                    ContextAction(
                        icon  = Icons.Default.SwapHoriz,
                        label = "Swap Front / Back",
                        color = AadhaarGreen
                    ) {
                        viewModel.swapAadhaarSides(aadhaarGroup)
                        Toast.makeText(context, "Sides swapped", Toast.LENGTH_SHORT).show()
                        contextDoc = null
                    }
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))
                    ContextAction(icon = Icons.Default.DeleteOutline, label = "Delete file", color = DangerRed) {
                        actionDoc = doc
                        showDeleteDocDialog = true
                        contextDoc = null
                    }
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))
                    ContextAction(icon = Icons.Default.LinkOff, label = "Ungroup pair", color = InkMid) {
                        viewModel.ungroupAadhaar(aadhaarGroup)
                        contextDoc = null
                        onBack()
                    }
                } else {
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))
                    ContextAction(icon = Icons.Default.LinkOff, label = "Remove from group", color = InkMid) {
                        viewModel.removeFromGroup(doc.id)
                        contextDoc = null
                    }
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp))
                    ContextAction(icon = Icons.Default.FolderOff, label = "Disband entire group", color = DangerRed) {
                        viewModel.disbandGroup(groupId)
                        contextDoc = null
                        onBack()
                    }
                }
            }
        }
    }

    // ── Rename file dialog ─────────────────────────────────────────────────
    if (showRenameDocDialog) {
        val doc = actionDoc
        AlertDialog(
            onDismissRequest = { showRenameDocDialog = false; actionDoc = null },
            containerColor = BgCard, shape = RoundedCornerShape(20.dp),
            title   = { Text("Rename file", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text    = {
                OutlinedTextField(
                    value = renameDocText, onValueChange = { renameDocText = it },
                    singleLine = true, label = { Text("File name") },
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (renameDocText.isBlank()) BgSurface else AadhaarBlue)
                        .clickable(enabled = renameDocText.isNotBlank()) {
                            doc?.let { viewModel.renameDocument(it, renameDocText.trim()) }
                            showRenameDocDialog = false; actionDoc = null
                        }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text("Rename",
                        color = if (renameDocText.isBlank()) InkDim else Color.White,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            },
            dismissButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
                        .clickable { showRenameDocDialog = false; actionDoc = null }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) { Text("Cancel", color = InkMid, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            }
        )
    }

    // ── Delete file dialog ─────────────────────────────────────────────────
    if (showDeleteDocDialog) {
        val doc = actionDoc
        AlertDialog(
            onDismissRequest = { showDeleteDocDialog = false; actionDoc = null },
            containerColor = BgCard, shape = RoundedCornerShape(20.dp),
            title = { Text("Delete file", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text  = {
                Text("Delete \"${doc?.name ?: ""}\"? This cannot be undone.",
                    color = InkMid, fontSize = 14.sp)
            },
            confirmButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(DangerRed)
                        .clickable {
                            doc?.let { viewModel.deleteDocument(it) }
                            showDeleteDocDialog = false; actionDoc = null
                            if (docs.size <= 1) onBack()
                        }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) { Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            },
            dismissButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
                        .clickable { showDeleteDocDialog = false; actionDoc = null }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) { Text("Cancel", color = InkMid, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            }
        )
    }

    // ── Rename pair / group dialog ─────────────────────────────────────────
    if (showRenamePairDialog) {
        AlertDialog(
            onDismissRequest = { showRenamePairDialog = false },
            containerColor = BgCard, shape = RoundedCornerShape(20.dp),
            title = { Text(if (isAadhaarGroup) "Rename pair" else "Rename group",
                color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text  = {
                OutlinedTextField(
                    value = pairRenameText, onValueChange = { pairRenameText = it },
                    singleLine = true, label = { Text(if (isAadhaarGroup) "Pair name" else "Group name") },
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (pairRenameText.isBlank()) BgSurface else Coral)
                        .clickable(enabled = pairRenameText.isNotBlank()) {
                            if (isAadhaarGroup && aadhaarGroup != null) {
                                val sanitized = pairRenameText.trim().replace(" ", "_")
                                aadhaarGroup.frontDoc?.let { viewModel.renameDocument(it, "${sanitized}_Aadhaar_Front") }
                                aadhaarGroup.backDoc?.let  { viewModel.renameDocument(it, "${sanitized}_Aadhaar_Back")  }
                            } else {
                                viewModel.renameDocGroup(groupId, pairRenameText.trim())
                            }
                            showRenamePairDialog = false
                        }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text("Rename",
                        color = if (pairRenameText.isBlank()) InkDim else Color.White,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            },
            dismissButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
                        .clickable { showRenamePairDialog = false }
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) { Text("Cancel", color = InkMid, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// AADHAAR CARD PAIR — full-width ID card presentation
// Stacked vertically: Front on top, Back below. Each at 1.586:1 (ISO 7810)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun AadhaarCardPair(
    group       : AadhaarGroup,
    onFrontTap  : () -> Unit,
    onBackTap   : () -> Unit,
    onFrontMore : () -> Unit,
    onBackMore  : () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AadhaarCardSlot(
            doc          = group.frontDoc,
            sideLabel    = "Front",
            accentColor  = AadhaarBlue,
            isPresent    = group.frontDoc != null,
            onTap        = onFrontTap,
            onMoreTap    = onFrontMore
        )
        AadhaarCardSlot(
            doc          = group.backDoc,
            sideLabel    = "Back",
            accentColor  = AadhaarPurple,
            isPresent    = group.backDoc != null,
            onTap        = onBackTap,
            onMoreTap    = onBackMore
        )
    }
}

@Composable
private fun AadhaarCardSlot(
    doc         : Document?,
    sideLabel   : String,
    accentColor : Color,
    isPresent   : Boolean,
    onTap       : () -> Unit,
    onMoreTap   : () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            // ISO 7810 ID-1 card ratio (85.6mm × 53.98mm ≈ 1.586:1)
            .aspectRatio(1.586f)
            .shadow(
                elevation    = 6.dp,
                shape        = RoundedCornerShape(18.dp),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor    = Color.Black.copy(alpha = 0.10f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(if (isPresent) BgCard else BgSurface)
            .border(
                width = 1.dp,
                color = if (isPresent) StrokeLight else accentColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp)
            )
            .then(if (isPresent) Modifier.clickable(onClick = onTap) else Modifier)
    ) {
        if (doc?.thumbnailPath != null) {
            // ── Actual scanned image ──────────────────────────────────────
            AsyncImage(
                model              = doc.thumbnailPath,
                contentDescription = sideLabel,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )

            // Gradient overlay — bottom only, for label readability
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1.00f to Color(0xA6000000)
                        )
                    )
            )

            // ── Doc name (bottom-left) ────────────────────────────────────
            Text(
                doc.name,
                color      = Color.White,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 14.dp, end = 46.dp)
            )

            // ── 3-dot menu (bottom-right) ─────────────────────────────────
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.94f))
                    .border(1.dp, Color.Black.copy(alpha = 0.06f), CircleShape)
                    .clickable(onClick = onMoreTap),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MoreVert, null,
                    tint     = Ink,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            // ── Empty slot ────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accentColor.copy(0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CreditCard, null,
                            tint     = accentColor.copy(0.45f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Text(
                        "$sideLabel not added",
                        color      = accentColor.copy(0.5f),
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── Side label chip (top-left) — always visible ───────────────────
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (isPresent) Color.White.copy(alpha = 0.92f)
                    else accentColor.copy(alpha = 0.10f)
                )
                .border(
                    1.dp,
                    if (isPresent) Color.Black.copy(alpha = 0.06f) else accentColor.copy(alpha = 0.16f),
                    RoundedCornerShape(999.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                sideLabel,
                color      = accentColor,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// AADHAAR INLINE ACTIONS — Swap / Ungroup pills below the cards
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun AadhaarInlineActions(
    onSwap    : () -> Unit,
    onUngroup : () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Swap
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
                .border(1.dp, StrokeLight, RoundedCornerShape(14.dp))
                .clickable(onClick = onSwap)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Default.SwapHoriz, null,
                    tint = AadhaarBlue, modifier = Modifier.size(18.dp))
                Text("Swap sides", color = Ink,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        // Ungroup
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
                .border(1.dp, StrokeLight, RoundedCornerShape(14.dp))
                .clickable(onClick = onUngroup)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Default.LinkOff, null,
                    tint = InkMid, modifier = Modifier.size(18.dp))
                Text("Ungroup", color = Ink,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// AADHAAR DETAILS SECTION — clean info hierarchy
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun AadhaarDetailsSection(group: AadhaarGroup) {
    val extractedName = group.frontDoc?.aadhaarName ?: group.backDoc?.aadhaarName
    val detailRows = buildList {
        extractedName?.takeIf { it.isNotBlank() }?.let {
            add(AadhaarDetailItem(label = "Name", value = it))
        }
        group.dateOfBirth?.takeIf { it.isNotBlank() }?.let {
            add(AadhaarDetailItem(label = "DOB", value = it))
        }
        group.gender?.takeIf { it.isNotBlank() }?.let {
            add(AadhaarDetailItem(label = "Gender", value = it))
        }
        group.maskedNumber?.takeIf { it.isNotBlank() }?.let {
            add(AadhaarDetailItem(label = "Aadhaar", value = it, emphasize = true))
        }
        group.address?.takeIf { it.isNotBlank() }?.let {
            add(AadhaarDetailItem(label = "Address", value = it, multiline = true))
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(1.dp, StrokeLight, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ── Section header ────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Aadhaar details",
                color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                "Front + back",
                color = InkDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            "Extracted from the scanned Aadhaar pair.",
            color = InkMid,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)

        detailRows.forEachIndexed { index, item ->
            AadhaarDetailRow(
                label = item.label,
                value = item.value,
                multiline = item.multiline,
                emphasize = item.emphasize,
                showDivider = index != detailRows.lastIndex
            )
        }
    }
}

private data class AadhaarDetailItem(
    val label: String,
    val value: String,
    val multiline: Boolean = false,
    val emphasize: Boolean = false
)

@Composable
private fun AadhaarDetailRow(
    label: String,
    value: String,
    multiline: Boolean = false,
    emphasize: Boolean = false,
    showDivider: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (multiline) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    label,
                    color = InkDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    value,
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Medium,
                    lineHeight = 22.sp
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    label,
                    color = InkDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(76.dp)
                )
                Text(
                    value,
                    color = if (emphasize) AadhaarBlue else Ink,
                    fontSize = 15.sp,
                    fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// GROUP GALLERY TILE — regular (non-Aadhaar) group tile with position badge
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun GroupGalleryTile(
    doc      : Document,
    index    : Int,
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
        if (doc.thumbnailPath != null)
            AsyncImage(
                model = doc.thumbnailPath, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        else Icon(Icons.Default.Image, null,
            Modifier.size(28.dp).align(Alignment.Center), tint = InkDim)

        Box(
            Modifier
                .fillMaxWidth().height(52.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
        )

        Text(
            doc.name, color = Color.White, fontSize = 9.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 6.dp, end = 28.dp)
        )

        val label    = doc.groupBadgeLabel()
        val colorKey = if (label == "Front" || label == "Back") "Aadhaar" else label
        val c        = TypeColors[colorKey] ?: InkMid
        Box(
            Modifier.align(Alignment.TopStart).padding(4.dp)
                .clip(RoundedCornerShape(3.dp)).background(c.copy(0.85f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(label, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.SemiBold)
        }

        // Position number badge
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp)
                .size(20.dp).clip(CircleShape).background(Color.Black.copy(0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$index", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        // 3-dot
        Box(
            Modifier.align(Alignment.BottomEnd).padding(2.dp)
                .size(24.dp).clip(CircleShape).clickable(onClick = onMoreTap),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}
