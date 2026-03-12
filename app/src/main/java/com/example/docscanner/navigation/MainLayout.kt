package com.example.docscanner.navigation

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.docscanner.domain.model.Folder
import com.example.docscanner.presentation.alldocuments.SidebarDragState
import kotlin.math.roundToInt

const val ALL_DOCUMENTS_ID = "__all__"

enum class BottomTab { ALL_DOCS, PROFILE }

@Composable
fun MainLayout(
    folders                : List<Folder>,
    selectedSidebarId      : String,
    dragState              : SidebarDragState,
    onAllDocumentsSelected : () -> Unit,
    onFolderSelected       : (Folder) -> Unit,
    onDropToFolder         : (documentId: String, folderId: String) -> Unit,
    onFolderReorder        : (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    selectedTab            : BottomTab,
    onTabSelected          : (BottomTab) -> Unit,
    content                : @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {

            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.primary)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DocumentScanner,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = "DocScanner",
                    style      = MaterialTheme.typography.titleLarge,
                    color      = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(Modifier.weight(1f).fillMaxWidth()) {
                SidebarPanel(
                    modifier        = Modifier.fillMaxHeight().weight(0.18f),
                    folders         = folders,
                    selectedId      = selectedSidebarId,
                    dragState       = dragState,
                    onAllDocs       = onAllDocumentsSelected,
                    onFolder        = onFolderSelected,
                    onDrop          = onDropToFolder,
                    onFolderReorder = onFolderReorder
                )

                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Box(
                    modifier = Modifier.weight(0.75f).fillMaxHeight(),
                    content  = content
                )
            }

            BottomNavBar(
                selectedTab   = selectedTab,
                onTabSelected = onTabSelected
            )
        }

        // ── ROOT-LEVEL GHOST CARD ─────────────────────────────────────────────
        // Rendered here so it floats above BOTH the sidebar and the content pane.
        if (dragState.isDragging) {
            val ghostWidthDp  = 90.dp
            val ghostHeightDp = 120.dp
            val ghostWidthPx  = with(density) { ghostWidthDp.toPx() }
            val ghostHeightPx = with(density) { ghostHeightDp.toPx() }

            // Centre the ghost under the finger
            val offsetX = (dragState.startX + dragState.dragOffsetX - ghostWidthPx  / 2f).roundToInt()
            val offsetY = (dragState.startY + dragState.dragOffsetY - ghostHeightPx / 2f).roundToInt()

            Card(
                modifier = Modifier
                    .offset { IntOffset(offsetX, offsetY) }
                    .size(ghostWidthDp, ghostHeightDp)
                    .zIndex(999f)                          // above everything
                    .graphicsLayer {
                        scaleX          = 1.08f
                        scaleY          = 1.08f
                        shadowElevation = 32f
                        alpha           = 0.95f
                    }
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp)
                    ),
                shape     = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (dragState.draggingThumbnailPath != null) {
                        AsyncImage(
                            model              = dragState.draggingThumbnailPath,
                            contentDescription = dragState.draggingDocumentName,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Name overlay
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                    ) {
                        Text(
                            dragState.draggingDocumentName,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = Color.White,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Page count badge
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(0.6f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "${dragState.draggingPageCount}p",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }

                    // Drop hint badge
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "⬅ folder",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ── Sidebar ───────────────────────────────────────────────────────────────────

@Composable
private fun SidebarPanel(
    modifier        : Modifier,
    folders         : List<Folder>,
    selectedId      : String,
    dragState       : SidebarDragState,
    onAllDocs       : () -> Unit,
    onFolder        : (Folder) -> Unit,
    onDrop          : (documentId: String, folderId: String) -> Unit,
    onFolderReorder : (Int, Int) -> Unit
) {
    var reorderDragIndex by remember { mutableIntStateOf(-1) }
    var reorderDragDy    by remember { mutableFloatStateOf(0f) }
    val folderCentreY    = remember { mutableStateMapOf<String, Float>() }

    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                dragState.sidebarRightEdge = bounds.right
            }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        val bounds = coords.boundsInWindow()
                        dragState.updateFolderBounds(ALL_DOCUMENTS_ID, bounds.top, bounds.bottom)
                    }
            ) {
                val isAllHovered = dragState.isDragging &&
                        dragState.hoveredFolderId == ALL_DOCUMENTS_ID
                SidebarItem(
                    icon         = Icons.Default.Description,
                    label        = "All",
                    selected     = selectedId == ALL_DOCUMENTS_ID,
                    isDropTarget = isAllHovered,
                    onClick      = onAllDocs
                )
            }

            Spacer(Modifier.height(2.dp))
            HorizontalDivider(Modifier.padding(horizontal = 6.dp), thickness = 0.5.dp)
            Spacer(Modifier.height(2.dp))

            Text(
                "FOLDERS",
                style     = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                textAlign = TextAlign.Center
            )

            if (folders.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            } else {
                folders.forEachIndexed { index, folder ->
                    val isBeingReordered = reorderDragIndex == index
                    val isDocDropHovered = dragState.isDragging && dragState.hoveredFolderId == folder.id

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(if (isBeingReordered) Modifier.zIndex(10f) else Modifier)
                            .graphicsLayer {
                                if (isBeingReordered) {
                                    translationY    = reorderDragDy
                                    shadowElevation = 16f
                                    alpha           = 0.93f
                                }
                            }
                            .onGloballyPositioned { coords ->
                                val bounds = coords.boundsInWindow()
                                // Register bounds for document-drag hit testing
                                dragState.updateFolderBounds(folder.id, bounds.top, bounds.bottom)
                                folderCentreY[folder.id] = (bounds.top + bounds.bottom) / 2f
                            }
                            .pointerInput(index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        if (!dragState.isDragging) {
                                            reorderDragIndex = index; reorderDragDy = 0f
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        if (reorderDragIndex >= 0) reorderDragDy += amount.y
                                    },
                                    onDragEnd = {
                                        if (reorderDragIndex >= 0) {
                                            val draggedId     = folders[reorderDragIndex].id
                                            val draggedCentre = (folderCentreY[draggedId] ?: 0f) + reorderDragDy
                                            val targetIndex   = folders.indexOfMinBy { f ->
                                                kotlin.math.abs((folderCentreY[f.id] ?: 0f) - draggedCentre)
                                            }
                                            if (targetIndex >= 0 && targetIndex != reorderDragIndex)
                                                onFolderReorder(reorderDragIndex, targetIndex)
                                            reorderDragIndex = -1; reorderDragDy = 0f
                                        }
                                    },
                                    onDragCancel = { reorderDragIndex = -1; reorderDragDy = 0f }
                                )
                            }
                    ) {
                        SidebarFolderItem(
                            folder       = folder,
                            selected     = selectedId == folder.id,
                            isDragging   = dragState.isDragging,
                            isHovered    = isDocDropHovered,
                            isReordering = isBeingReordered,
                            onClick      = {
                                if (dragState.isDragging && isDocDropHovered) {
                                    val docId = dragState.draggingDocumentId
                                    if (docId != null) onDrop(docId, folder.id)
                                } else {
                                    onFolder(folder)
                                }
                            }
                        )
                    }
                }

                if (reorderDragIndex < 0) {
                    Text(
                        "Hold to reorder",
                        style     = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)
                    )
                }
            }
        }
    }
}

private fun <T> List<T>.indexOfMinBy(selector: (T) -> Float): Int {
    if (isEmpty()) return -1
    var minIdx = 0; var minVal = selector(this[0])
    for (i in 1..lastIndex) { val v = selector(this[i]); if (v < minVal) { minVal = v; minIdx = i } }
    return minIdx
}

// ── Sidebar item: All Documents ───────────────────────────────────────────────

@Composable
private fun SidebarItem(
    label       : String,
    selected    : Boolean,
    isDropTarget: Boolean,
    onClick     : () -> Unit,
    icon        : ImageVector? = null
) {
    val bg = when {
        isDropTarget -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
        selected     -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else         -> Color.Transparent
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) Icon(
            icon, label, Modifier.size(20.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style      = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color      = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign  = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Sidebar folder item ───────────────────────────────────────────────────────

@Composable
private fun SidebarFolderItem(
    folder      : Folder,
    selected    : Boolean,
    isDragging  : Boolean,
    isHovered   : Boolean,
    isReordering: Boolean,
    onClick     : () -> Unit
) {
    val bg = when {
        isHovered    -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
        isReordering -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        selected     -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else         -> Color.Transparent
    }
    val iconTint = when {
        isHovered    -> MaterialTheme.colorScheme.tertiary
        isReordering -> MaterialTheme.colorScheme.primary
        selected     -> MaterialTheme.colorScheme.primary
        else         -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Folder icon with notification badge ───────────────────────────────
        Box(contentAlignment = Alignment.TopEnd) {
            // Slightly larger box when hovered so the glow background fits
            if (isHovered) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = folder.name,
                        modifier = Modifier.size(20.dp),
                        tint     = iconTint
                    )
                }
            } else {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = folder.name,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 4.dp, top = 4.dp), // leave room for badge
                    tint = iconTint
                )
            }

            // Notification badge — only when there are documents and not hovering
            if (folder.documentCount > 0 && !isHovered) {
                Box(
                    Modifier
                        .offset(x = 2.dp, y = (-2).dp)
                        .defaultMinSize(minWidth = 14.dp, minHeight = 14.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = if (folder.documentCount > 99) "99+" else "${folder.documentCount}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // "Drop here" badge replaces the count badge when hovered
            if (isHovered) {
                Box(
                    Modifier
                        .offset(x = 2.dp, y = (-2).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.tertiary)
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "↓",
                        style      = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color      = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            folder.name,
            style      = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color      = iconTint,
            fontWeight = if (selected || isHovered || isReordering) FontWeight.SemiBold
            else FontWeight.Normal,
            textAlign  = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
        )

        if (isReordering) {
            Spacer(Modifier.height(2.dp))
            Text(
                "Moving…",
                style      = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        if (isHovered) {
            Spacer(Modifier.height(2.dp))
            Text(
                "Drop here",
                style      = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color      = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Bottom bar — clean 2-tab nav, no camera bulge ────────────────────────────

@Composable
fun BottomNavBar(
    selectedTab  : BottomTab,
    onTabSelected: (BottomTab) -> Unit
) {
    Surface(
        modifier        = Modifier.fillMaxWidth(),
        tonalElevation  = 4.dp,
        shadowElevation = 8.dp,
        color           = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabItem(
                modifier  = Modifier.weight(1f),
                icon      = Icons.Default.Description,
                label     = "Home",
                selected  = selectedTab == BottomTab.ALL_DOCS,
                onClick   = { onTabSelected(BottomTab.ALL_DOCS) }
            )
            TabItem(
                modifier  = Modifier.weight(1f),
                icon      = Icons.Default.Person,
                label     = "Profile",
                selected  = selectedTab == BottomTab.PROFILE,
                onClick   = { onTabSelected(BottomTab.PROFILE) }
            )
        }
    }
}

@Composable
private fun RowScope.TabItem(
    modifier: Modifier, icon: ImageVector, label: String,
    selected: Boolean, onClick: () -> Unit
) {
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, Modifier.size(22.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}