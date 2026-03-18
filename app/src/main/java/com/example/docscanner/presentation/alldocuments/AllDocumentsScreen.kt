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
private val SelectBlue     = Color(0xFF2E6BE6)
private val PdfBadgeBg     = Color(0xFFD94040)

@Composable
fun AllDocumentsScreen(
    dragState       : SidebarDragState,
    onDocumentClick : (Document) -> Unit,
    onScanClick     : () -> Unit,
    viewModel       : AllDocumentsViewModel = hiltViewModel()
) {
    val documents by viewModel.documents.collectAsState()

    var isSelectMode by remember { mutableStateOf(false) }
    var selectedIds  by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(documents) { selectedIds = selectedIds.filter { id -> documents.any { it.id == id } }.toSet() }
    fun exitSelectMode() { isSelectMode = false; selectedIds = emptySet() }

    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(BgBase)) {
        Column(Modifier.fillMaxSize()) {

            // ── Select mode top bar ───────────────────────────────────────────
            AnimatedVisibility(visible = isSelectMode) {
                Box(Modifier.fillMaxWidth().background(BgBase).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { exitSelectMode() }.padding(8.dp)) { Icon(Icons.Default.Close, "Cancel", tint = Ink, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.width(8.dp))
                        Text("${selectedIds.size} selected", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            val allSelected = documents.isNotEmpty() && documents.all { it.id in selectedIds }
                            selectedIds = if (allSelected) emptySet() else documents.map { it.id }.toSet()
                        }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(if (documents.isNotEmpty() && documents.all { it.id in selectedIds }) "Deselect all" else "Select all", color = Coral, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(StrokeLight))
                }
            }

            // ── Select button ─────────────────────────────────────────────────
            if (!isSelectMode && documents.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(BgSurface).border(1.dp, StrokeLight, RoundedCornerShape(8.dp)).clickable { isSelectMode = true }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Default.CheckCircleOutline, null, tint = InkMid, modifier = Modifier.size(14.dp)); Text("Select", color = InkMid, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }

            // ── Document grid ─────────────────────────────────────────────────
            Box(Modifier.weight(1f)) {
                if (documents.isEmpty()) {
                    Column(Modifier.align(Alignment.Center).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(BgSurface).border(1.dp, StrokeLight, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Description, null, tint = InkDim, modifier = Modifier.size(36.dp)) }
                        Spacer(Modifier.height(4.dp)); Text("No documents yet", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Tap Scan Document to get started", color = InkMid, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(top = 4.dp, bottom = if (isSelectMode) 110.dp else 100.dp)) {
                        documents.chunked(2).forEach { rowDocs ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowDocs.forEach { doc ->
                                    if (isSelectMode) {
                                        SelectableDocCard(document = doc, isSelected = doc.id in selectedIds, modifier = Modifier.weight(1f), onTap = { selectedIds = if (doc.id in selectedIds) selectedIds - doc.id else selectedIds + doc.id })
                                    } else {
                                        DraggableDocCard(document = doc, dragState = dragState, modifier = Modifier.weight(1f), onTap = { onDocumentClick(doc) }, onDragEnd = { target -> if (target != null) viewModel.moveDocumentToFolder(doc.id, target) })
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

        // ── Select mode bottom bar (Delete only) ──────────────────────────────
        AnimatedVisibility(visible = isSelectMode && selectedIds.isNotEmpty(), enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).navigationBarsPadding().shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x22E8603C), spotColor = Color(0x22E8603C)).clip(RoundedCornerShape(20.dp)).background(BgCard).border(1.dp, StrokeLight, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(DangerRed.copy(0.10f)).border(1.dp, DangerRed.copy(0.25f), RoundedCornerShape(12.dp)).clickable { showDeleteSelectedDialog = true }, contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Default.Delete, null, tint = DangerRed, modifier = Modifier.size(16.dp)); Text("Delete ${selectedIds.size} ${if (selectedIds.size == 1) "document" else "documents"}", color = DangerRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        // ── Scan FAB ──────────────────────────────────────────────────────────
        if (!isSelectMode && !dragState.isDragging) {
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp).navigationBarsPadding().shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x22E8603C), spotColor = Color(0x22E8603C)).clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(Coral, Color(0xFFD94860)))).clickable(onClick = onScanClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp)); Text("Scan Document", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(onDismissRequest = { showDeleteSelectedDialog = false }, containerColor = BgCard, shape = RoundedCornerShape(18.dp),
            title = { Text("Delete ${selectedIds.size} Documents", color = Ink, fontWeight = FontWeight.Bold) },
            text = { Text("Delete ${selectedIds.size} selected documents? This cannot be undone.", color = InkMid, fontSize = 14.sp) },
            confirmButton = { Box(Modifier.clip(RoundedCornerShape(10.dp)).background(DangerRed.copy(0.10f)).border(1.dp, DangerRed.copy(0.30f), RoundedCornerShape(10.dp)).clickable { documents.filter { it.id in selectedIds }.forEach { viewModel.deleteDocument(it) }; exitSelectMode(); showDeleteSelectedDialog = false }.padding(horizontal = 16.dp, vertical = 9.dp)) { Text("Delete", color = DangerRed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) } },
            dismissButton = { Box(Modifier.clip(RoundedCornerShape(10.dp)).background(BgSurface).border(1.dp, StrokeLight, RoundedCornerShape(10.dp)).clickable { showDeleteSelectedDialog = false }.padding(horizontal = 16.dp, vertical = 9.dp)) { Text("Cancel", color = InkMid, fontSize = 14.sp) } }
        )
    }
}

// ── Document badges ───────────────────────────────────────────────────────────

@Composable
internal fun DocumentNameRow(document: Document, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (document.pdfPath != null) {
            Icon(Icons.Default.PictureAsPdf, null, tint = PdfBadgeBg, modifier = Modifier.size(14.dp))
        }
        Text(document.name, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ClassificationBadge(document: Document, modifier: Modifier = Modifier) {
    // Classification badge (top-start) — only for non-"Document" and non-"Merged" labels
    document.docClassLabel?.let { label ->
        if (label != "Document" && label != "Merged") {
            Box(modifier.padding(6.dp).clip(RoundedCornerShape(6.dp)).background(DocTypeBadgeBg).padding(horizontal = 7.dp, vertical = 3.dp)) {
                Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Draggable doc card ────────────────────────────────────────────────────────

@Composable
private fun DraggableDocCard(document: Document, dragState: SidebarDragState, modifier: Modifier, onTap: () -> Unit, onDragEnd: (String?) -> Unit) {
    var cardWindowX by remember { mutableFloatStateOf(0f) }; var cardWindowY by remember { mutableFloatStateOf(0f) }
    val isDragging = dragState.draggingDocumentId == document.id
    val cardAlpha by animateFloatAsState(if (isDragging) 0f else 1f, tween(150), label = "a")
    val cardScale by animateFloatAsState(if (isDragging) 0.94f else 1f, spring(stiffness = 400f), label = "s")

    Column(
        modifier.graphicsLayer { alpha = cardAlpha; scaleX = cardScale; scaleY = cardScale }
            .onGloballyPositioned { coords -> val p = coords.positionInWindow(); cardWindowX = p.x; cardWindowY = p.y }
            .pointerInput(document.id) { detectDragGesturesAfterLongPress(onDragStart = { offset -> dragState.onDragStart(document.id, cardWindowX + offset.x, cardWindowY + offset.y, document.thumbnailPath, document.name, document.pageCount) }, onDrag = { change, amount -> change.consume(); dragState.onDrag(amount.x, amount.y) }, onDragEnd = { onDragEnd(dragState.onDragEnd()) }, onDragCancel = { dragState.onDragCancel() }) }
            .shadow(1.dp, RoundedCornerShape(12.dp), ambientColor = Color(0x0E000000), spotColor = Color(0x0E000000))
            .clip(RoundedCornerShape(12.dp)).border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
            .background(BgCard).clickable(enabled = !isDragging, onClick = onTap)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(BgSurface), contentAlignment = Alignment.Center) {
            if (document.thumbnailPath != null) AsyncImage(model = document.thumbnailPath, contentDescription = document.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Default.Image, null, Modifier.size(36.dp), tint = InkDim)
            ClassificationBadge(document, Modifier.align(Alignment.TopStart))
        }
        DocumentNameRow(document)
    }
}

// ── Selectable doc card ───────────────────────────────────────────────────────

@Composable
private fun SelectableDocCard(document: Document, isSelected: Boolean, modifier: Modifier, onTap: () -> Unit) {
    Column(
        modifier.shadow(1.dp, RoundedCornerShape(12.dp), ambientColor = Color(0x0E000000), spotColor = Color(0x0E000000))
            .clip(RoundedCornerShape(12.dp)).border(if (isSelected) 2.dp else 1.dp, if (isSelected) SelectBlue else StrokeLight, RoundedCornerShape(12.dp))
            .background(BgCard).clickable(onClick = onTap)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(BgSurface), contentAlignment = Alignment.Center) {
            if (document.thumbnailPath != null) AsyncImage(model = document.thumbnailPath, contentDescription = document.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Default.Image, null, Modifier.size(36.dp), tint = InkDim)
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(if (isSelected) SelectBlue else Color.White.copy(0.85f)).border(if (isSelected) 0.dp else 1.5.dp, if (isSelected) Color.Transparent else StrokeMid, CircleShape), contentAlignment = Alignment.Center) { if (isSelected) Icon(Icons.Default.Check, "Selected", tint = Color.White, modifier = Modifier.size(14.dp)) }
            ClassificationBadge(document, Modifier.align(Alignment.TopStart))
        }
        DocumentNameRow(document)
    }
}