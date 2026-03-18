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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val SelectBlue = Color(0xFF2E6BE6)
private fun Document.isMergeable(): Boolean = pdfPath == null && !isMergedSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folderId        : String,
    folderName      : String,
    folderIcon      : String,
    showTopBar      : Boolean = true,
    dragState       : SidebarDragState,
    isSelectMode    : Boolean,
    selectAllTrigger: Int,
    onSelectToggle  : () -> Unit,
    onStartScan     : () -> Unit,
    onOpenDocument  : (Document) -> Unit,
    onMergeSelected : (List<Document>, String) -> Unit = { _, _ -> },
    onBack          : () -> Unit,
    onSelectedCountChanged: (Int) -> Unit = {},
    onDocumentCountChanged: (Int) -> Unit = {},
    viewModel       : FolderViewModel = hiltViewModel()
) {
    LaunchedEffect(folderId) { viewModel.loadFolder(folderId) }

    val documents by viewModel.documents.collectAsState()
    val context   = LocalContext.current

    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    val mergeableIds = documents.filter { it.isMergeable() }.map { it.id }.toSet()
    val selectedMergeableCount = selectedIds.count { it in mergeableIds }
    val selectedMergeableDocs = documents.filter { it.id in selectedIds && it.isMergeable() }
    val sharedCategory = if (selectedMergeableDocs.size >= 2) {
        val labels = selectedMergeableDocs.map { it.docClassLabel ?: "Document" }.toSet()
        if (labels.size == 1) labels.first() else null
    } else null
    val canMerge = sharedCategory != null

    LaunchedEffect(isSelectMode) { if (!isSelectMode) selectedIds = emptySet() }
    LaunchedEffect(documents) { selectedIds = selectedIds.filter { id -> documents.any { it.id == id } }.toSet() }
    LaunchedEffect(selectedIds.size) { onSelectedCountChanged(selectedIds.size) }
    LaunchedEffect(documents.size) { onDocumentCountChanged(documents.size) }

    // ── React to "Select all" from header ─────────────────────────────────────
    LaunchedEffect(selectAllTrigger) {
        if (selectAllTrigger > 0 && isSelectMode) {
            val allSelected = documents.isNotEmpty() && documents.all { it.id in selectedIds }
            selectedIds = if (allSelected) emptySet() else documents.map { it.id }.toSet()
        }
    }

    Box(Modifier.fillMaxSize().background(BgBase)) {
        Column(Modifier.fillMaxSize()) {

            // ── Folder name header (when no top bar from parent) ──────────────
            if (!showTopBar) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(folderIcon, fontSize = 18.sp); Spacer(Modifier.width(6.dp))
                    Text(folderName, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ── Grid ──────────────────────────────────────────────────────────
            Box(Modifier.weight(1f)) {
                if (documents.isEmpty()) {
                    Column(Modifier.align(Alignment.Center).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(80.dp).clip(RoundedCornerShape(22.dp)).background(BgSurface).border(1.dp, StrokeLight, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { Text(folderIcon, fontSize = 34.sp) }
                        Spacer(Modifier.height(4.dp)); Text("No documents yet", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text("Tap Scan Document to add one", color = InkMid, fontSize = 13.sp)
                    }
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = if (isSelectMode) 140.dp else 100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(documents, key = { it.id }) { document ->
                            val isSelected = document.id in selectedIds
                            val isMergeableDoc = document.isMergeable()
                            if (isSelectMode) {
                                SelectableFolderDocCard(document = document, isSelected = isSelected, isMergeable = isMergeableDoc, onTap = {
                                    if (!isSelected && !isMergeableDoc) Toast.makeText(context, "PDFs can only be deleted, not merged", Toast.LENGTH_SHORT).show()
                                    selectedIds = if (isSelected) selectedIds - document.id else selectedIds + document.id
                                })
                            } else {
                                DraggableFolderDocCard(document = document, dragState = dragState, onTap = { onOpenDocument(document) }, onDragEnd = { target ->
                                    if (target != null && target != folderId) viewModel.moveDocument(document.id, folderId, if (target == ALL_DOCUMENTS_ID) "" else target)
                                })
                            }
                        }
                    }
                }
            }
        }

        // ── Select mode bottom bar ────────────────────────────────────────────
        AnimatedVisibility(visible = isSelectMode && selectedIds.isNotEmpty(), enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).navigationBarsPadding().shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x22E8603C), spotColor = Color(0x22E8603C)).clip(RoundedCornerShape(20.dp)).background(BgCard).border(1.dp, StrokeLight, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(DangerRed.copy(0.10f)).border(1.dp, DangerRed.copy(0.25f), RoundedCornerShape(12.dp)).clickable { showDeleteSelectedDialog = true }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Default.Delete, null, tint = DangerRed, modifier = Modifier.size(16.dp)); Text("Delete", color = DangerRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (canMerge) Brush.horizontalGradient(listOf(Coral, Color(0xFFD94860))) else Brush.horizontalGradient(listOf(BgSurface, BgSurface))).clickable(enabled = canMerge) { val mergeableDocs = documents.filter { it.id in selectedIds && it.isMergeable() }; onMergeSelected(mergeableDocs, folderId); selectedIds = emptySet(); onSelectToggle() }, contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = if (canMerge) Color.White else InkDim, modifier = Modifier.size(16.dp))
                        Text(when {
                            canMerge -> "Merge as ${sharedCategory} PDF"
                            selectedMergeableCount >= 2 -> "Same type required"
                            selectedMergeableCount == 1 -> "Need 2+ images"
                            else -> "Select images to merge"
                        }, color = if (canMerge) Color.White else InkDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── Scan FAB ──────────────────────────────────────────────────────────
        if (!isSelectMode && !dragState.isDragging) {
            Box(Modifier.align(Alignment.BottomEnd).padding(14.dp).navigationBarsPadding().shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x22E8603C), spotColor = Color(0x22E8603C)).clip(RoundedCornerShape(14.dp)).background(Brush.horizontalGradient(listOf(Coral, Color(0xFFD94860)))).clickable(onClick = onStartScan).padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp)); Text("Scan Document", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(onDismissRequest = { showDeleteSelectedDialog = false }, containerColor = BgCard, shape = RoundedCornerShape(18.dp),
            title = { Text("Delete ${selectedIds.size} Documents", color = Ink, fontWeight = FontWeight.Bold) },
            text = { Text("Delete ${selectedIds.size} selected documents? This cannot be undone.", color = InkMid, fontSize = 14.sp) },
            confirmButton = { Box(Modifier.clip(RoundedCornerShape(10.dp)).background(DangerRed.copy(0.10f)).border(1.dp, DangerRed.copy(0.30f), RoundedCornerShape(10.dp)).clickable { documents.filter { it.id in selectedIds }.forEach { viewModel.deleteDocument(it) }; selectedIds = emptySet(); onSelectToggle(); showDeleteSelectedDialog = false }.padding(horizontal = 16.dp, vertical = 9.dp)) { Text("Delete", color = DangerRed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) } },
            dismissButton = { Box(Modifier.clip(RoundedCornerShape(10.dp)).background(BgSurface).border(1.dp, StrokeLight, RoundedCornerShape(10.dp)).clickable { showDeleteSelectedDialog = false }.padding(horizontal = 16.dp, vertical = 9.dp)) { Text("Cancel", color = InkMid, fontSize = 14.sp) } }
        )
    }
}

// ── Cards ─────────────────────────────────────────────────────────────────────

@Composable
private fun DraggableFolderDocCard(document: Document, dragState: SidebarDragState, onTap: () -> Unit, onDragEnd: (String?) -> Unit) {
    var cardWindowX by remember { mutableFloatStateOf(0f) }; var cardWindowY by remember { mutableFloatStateOf(0f) }
    val isDragging = dragState.draggingDocumentId == document.id
    val cardAlpha by animateFloatAsState(if (isDragging) 0f else 1f, tween(150), label = "a"); val cardScale by animateFloatAsState(if (isDragging) 0.94f else 1f, spring(stiffness = 400f), label = "s")
    Box(Modifier.fillMaxWidth().graphicsLayer { alpha = cardAlpha; scaleX = cardScale; scaleY = cardScale }.onGloballyPositioned { coords -> val p = coords.positionInWindow(); cardWindowX = p.x; cardWindowY = p.y }.pointerInput(document.id) { detectDragGesturesAfterLongPress(onDragStart = { offset -> dragState.onDragStart(document.id, cardWindowX + offset.x, cardWindowY + offset.y, document.thumbnailPath, document.name, document.pageCount) }, onDrag = { change, amount -> change.consume(); dragState.onDrag(amount.x, amount.y) }, onDragEnd = { onDragEnd(dragState.onDragEnd()) }, onDragCancel = { dragState.onDragCancel() }) }.shadow(1.dp, RoundedCornerShape(12.dp), ambientColor = Color(0x0E000000), spotColor = Color(0x0E000000)).clip(RoundedCornerShape(12.dp)).border(1.dp, StrokeLight, RoundedCornerShape(12.dp)).background(BgCard).clickable(enabled = !isDragging, onClick = onTap)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(BgSurface), contentAlignment = Alignment.Center) {
                if (document.thumbnailPath != null) AsyncImage(model = document.thumbnailPath, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(if (document.pdfPath != null) Icons.Default.Description else Icons.Default.Image, null, tint = InkDim, modifier = Modifier.size(32.dp))
                ClassificationBadge(document, Modifier.align(Alignment.TopStart))
            }
            DocumentNameRow(document)
        }
    }
}

@Composable
private fun SelectableFolderDocCard(document: Document, isSelected: Boolean, isMergeable: Boolean, onTap: () -> Unit) {
    val borderColor = when { isSelected && isMergeable -> SelectBlue; isSelected && !isMergeable -> InkMid; else -> StrokeLight }
    Box(Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(12.dp), ambientColor = Color(0x0E000000), spotColor = Color(0x0E000000)).clip(RoundedCornerShape(12.dp)).border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp)).background(BgCard).clickable(onClick = onTap)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(BgSurface), contentAlignment = Alignment.Center) {
                if (document.thumbnailPath != null) AsyncImage(model = document.thumbnailPath, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(if (document.pdfPath != null) Icons.Default.Description else Icons.Default.Image, null, tint = InkDim, modifier = Modifier.size(32.dp))
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(when { isSelected && isMergeable -> SelectBlue; isSelected && !isMergeable -> InkMid; else -> Color.White.copy(0.85f) }).border(if (isSelected) 0.dp else 1.5.dp, if (isSelected) Color.Transparent else StrokeMid, CircleShape), contentAlignment = Alignment.Center) { if (isSelected) Icon(Icons.Default.Check, "Selected", tint = Color.White, modifier = Modifier.size(14.dp)) }
                ClassificationBadge(document, Modifier.align(Alignment.TopStart))
            }
            DocumentNameRow(document)
        }
    }
}