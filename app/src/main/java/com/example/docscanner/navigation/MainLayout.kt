package com.example.docscanner.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.example.docscanner.presentation.alldocuments.*
import kotlin.math.roundToInt

const val ALL_DOCUMENTS_ID = "__all__"

enum class BottomTab { ALL_DOCS, PROFILE }

@Composable
fun MainLayout(
    folders: List<Folder>, selectedSidebarId: String, dragState: SidebarDragState,
    onAllDocumentsSelected: () -> Unit, onFolderSelected: (Folder) -> Unit,
    onDropToFolder: (String, String) -> Unit, onFolderReorder: (Int, Int) -> Unit = { _, _ -> },
    selectedTab: BottomTab, onTabSelected: (BottomTab) -> Unit,
    isSelectMode: Boolean = false, selectedCount: Int = 0, hasDocuments: Boolean = false,
    onSelectToggle: () -> Unit = {}, onSelectAll: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    // Show select icon on AllDocs or FolderDetail (not Profile)
    val showSelectIcon =
        (selectedTab == BottomTab.ALL_DOCS || selectedSidebarId != ALL_DOCUMENTS_ID) && hasDocuments

    Box(Modifier
        .fillMaxSize()
        .background(BgBase)) {
        Column(Modifier.fillMaxSize()) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(BgBase)
            )

            // ── Header ────────────────────────────────────────────────────────
            Box(Modifier
                .fillMaxWidth()
                .background(BgBase)) {
                AnimatedContent(
                    targetState = isSelectMode,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "header"
                ) { selectActive ->
                    if (selectActive) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onSelectToggle() }) {
                                Icon(
                                    Icons.Default.Close,
                                    "Cancel",
                                    tint = Ink,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "$selectedCount selected",
                                color = Ink,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.weight(1f))
                            Box(Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectAll() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    if (selectedCount > 0) "Deselect all" else "Select all",
                                    color = Coral,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Coral,
                                                Color(0xFFD94860)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DocumentScanner,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "DocScanner",
                                color = Ink,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            )
                            Spacer(Modifier.weight(1f))
                            if (showSelectIcon) {
                                IconButton(onClick = { onSelectToggle() }) {
                                    Icon(
                                        Icons.Default.CheckCircleOutline,
                                        "Select",
                                        tint = InkMid,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(StrokeLight)
                )
            }

            Row(Modifier
                .weight(1f)
                .fillMaxWidth()) {
                SidebarPanel(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.18f),
                    folders = folders,
                    selectedId = selectedSidebarId,
                    dragState = dragState,
                    onAllDocs = onAllDocumentsSelected,
                    onFolder = onFolderSelected,
                    onDrop = onDropToFolder,
                    onFolderReorder = onFolderReorder
                )
                Box(Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(StrokeLight))
                Box(Modifier
                    .weight(0.75f)
                    .fillMaxHeight(), content = content)
            }
            Box(Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(StrokeLight))
            BottomNavBar(selectedTab = selectedTab, onTabSelected = onTabSelected)
        }

        // ── Ghost drag card ──────────────────────────────────────────────────
        if (dragState.isDragging) {
            val ghostW = 90.dp;
            val ghostH = 120.dp;
            val ghostWx = with(density) { ghostW.toPx() }
            val offsetX = (dragState.startX + dragState.dragOffsetX - ghostWx / 2f).roundToInt();
            val offsetY =
                (dragState.startY + dragState.dragOffsetY - with(density) { ghostH.toPx() } / 2f).roundToInt()
            Box(
                Modifier
                    .offset { IntOffset(offsetX, offsetY) }
                    .size(ghostW, ghostH)
                    .zIndex(999f)
                    .graphicsLayer {
                        scaleX = 1.08f; scaleY = 1.08f; shadowElevation = 32f; alpha = 0.96f
                    }
                    .shadow(
                        16.dp,
                        RoundedCornerShape(14.dp),
                        ambientColor = Color(0x22E8603C),
                        spotColor = Color(0x22E8603C)
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, Coral.copy(0.6f), RoundedCornerShape(14.dp))
                    .background(BgCard)
            ) {
                if (dragState.draggingThumbnailPath != null) AsyncImage(
                    model = dragState.draggingThumbnailPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                ) else Box(
                    Modifier
                        .fillMaxSize()
                        .background(BgSurface),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Image, null, Modifier.size(28.dp), tint = InkDim) }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xEEFFFFFF)
                                )
                            )
                        )
                        .padding(horizontal = 5.dp, vertical = 5.dp)
                ) {
                    Text(
                        dragState.draggingDocumentName,
                        color = Ink,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Coral)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        "← folder",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Sidebar ──────────────────────────────────────────────────────────────────

@Composable
private fun SidebarPanel(
    modifier: Modifier,
    folders: List<Folder>,
    selectedId: String,
    dragState: SidebarDragState,
    onAllDocs: () -> Unit,
    onFolder: (Folder) -> Unit,
    onDrop: (String, String) -> Unit,
    onFolderReorder: (Int, Int) -> Unit
) {
    var reorderDragIndex by remember { mutableIntStateOf(-1) };
    var reorderDragDy by remember { mutableFloatStateOf(0f) };
    val folderCentreY = remember { mutableStateMapOf<String, Float>() }
    Column(
        modifier
            .background(BgSurface)
            .onGloballyPositioned { coords ->
                dragState.sidebarRightEdge = coords.boundsInWindow().right
            }) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInWindow(); dragState.updateFolderBounds(
                        ALL_DOCUMENTS_ID,
                        b.top,
                        b.bottom
                    )
                    }) {
                SidebarItem(
                    icon = Icons.Default.Description,
                    label = "All",
                    selected = selectedId == ALL_DOCUMENTS_ID,
                    isDropTarget = dragState.isDragging && dragState.hoveredFolderId == ALL_DOCUMENTS_ID,
                    onClick = onAllDocs
                )
            }
            Spacer(Modifier.height(4.dp)); HorizontalDivider(
            Modifier.padding(horizontal = 8.dp),
            thickness = 0.5.dp,
            color = StrokeLight
        ); Spacer(Modifier.height(4.dp))
            Text(
                "FOLDERS",
                color = InkDim,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                textAlign = TextAlign.Center
            ); Spacer(Modifier.height(4.dp))
            if (folders.isEmpty()) Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(Modifier.size(16.dp), color = Coral, strokeWidth = 2.dp) }
            else folders.forEachIndexed { index, folder ->
                val isBeingReordered = reorderDragIndex == index;
                val isDocDropHovered =
                    dragState.isDragging && dragState.hoveredFolderId == folder.id
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(if (isBeingReordered) Modifier.zIndex(10f) else Modifier)
                        .graphicsLayer {
                            if (isBeingReordered) {
                                translationY = reorderDragDy; shadowElevation = 16f; alpha = 0.95f
                            }
                        }
                        .onGloballyPositioned { coords ->
                            val b = coords.boundsInWindow(); dragState.updateFolderBounds(
                            folder.id,
                            b.top,
                            b.bottom
                        ); folderCentreY[folder.id] = (b.top + b.bottom) / 2f
                        }
                        .pointerInput(index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    if (!dragState.isDragging) {
                                        reorderDragIndex = index; reorderDragDy = 0f
                                    }
                                },
                                onDrag = { change, amount -> change.consume(); if (reorderDragIndex >= 0) reorderDragDy += amount.y },
                                onDragEnd = {
                                    if (reorderDragIndex >= 0) {
                                        val draggedId = folders[reorderDragIndex].id;
                                        val draggedCentre =
                                            (folderCentreY[draggedId] ?: 0f) + reorderDragDy;
                                        val targetIndex = folders.indexOfMinBy { f ->
                                            kotlin.math.abs(
                                                (folderCentreY[f.id] ?: 0f) - draggedCentre
                                            )
                                        }; if (targetIndex >= 0 && targetIndex != reorderDragIndex) onFolderReorder(
                                            reorderDragIndex,
                                            targetIndex
                                        ); reorderDragIndex = -1; reorderDragDy = 0f
                                    }
                                },
                                onDragCancel = { reorderDragIndex = -1; reorderDragDy = 0f })
                        }) {
                    SidebarFolderItem(
                        folder = folder,
                        selected = selectedId == folder.id,
                        isHovered = isDocDropHovered,
                        isReordering = isBeingReordered,
                        onClick = {
                            if (dragState.isDragging && isDocDropHovered) {
                                dragState.draggingDocumentId?.let { onDrop(it, folder.id) }
                            } else onFolder(folder)
                        })
                }
            }
        }
    }
}

private fun <T> List<T>.indexOfMinBy(selector: (T) -> Float): Int {
    if (isEmpty()) return -1;
    var minIdx = 0;
    var minVal = selector(this[0]); for (i in 1..lastIndex) {
        val v = selector(this[i]); if (v < minVal) {
            minVal = v; minIdx = i
        }
    }; return minIdx
}

@Composable
private fun SidebarItem(
    label: String,
    selected: Boolean,
    isDropTarget: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    val bg by animateColorAsState(
        when {
            isDropTarget -> GreenAccent.copy(0.12f); selected -> Coral.copy(0.10f); else -> Color.Transparent
        }, tween(200), label = "bg"
    );
    val bd by animateColorAsState(
        when {
            isDropTarget -> GreenAccent.copy(0.40f); selected -> Coral.copy(0.30f); else -> Color.Transparent
        }, tween(200), label = "bd"
    );
    val tint by animateColorAsState(
        when {
            isDropTarget -> GreenAccent; selected -> Coral; else -> InkMid
        }, tween(200), label = "t"
    ); Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, bd, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) Icon(
            icon,
            label,
            Modifier.size(24.dp),
            tint = tint
        ); Spacer(Modifier.height(3.dp)); Text(
        label,
        color = tint,
        fontSize = 10.sp,
        fontWeight = if (selected || isDropTarget) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    }
}

@Composable
private fun SidebarFolderItem(
    folder: Folder,
    selected: Boolean,
    isHovered: Boolean,
    isReordering: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        when {
            isHovered -> GreenAccent.copy(0.12f); isReordering -> Coral.copy(0.08f); selected -> Coral.copy(
            0.10f
        ); else -> Color.Transparent
        }, tween(200), label = "bg"
    );
    val bd by animateColorAsState(
        when {
            isHovered -> GreenAccent.copy(0.40f); isReordering -> Coral.copy(0.25f); selected -> Coral.copy(
            0.30f
        ); else -> Color.Transparent
        }, tween(200), label = "bd"
    );
    val tint by animateColorAsState(
        when {
            isHovered -> GreenAccent; isReordering -> Coral; selected -> Coral; else -> InkMid
        }, tween(200), label = "t"
    );
    val scale by animateFloatAsState(
        if (isHovered) 1.06f else 1f,
        spring(stiffness = 500f),
        label = "sc"
    ); Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp, vertical = 2.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, bd, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                Icons.Default.Folder,
                folder.name,
                Modifier
                    .size(28.dp)
                    .padding(end = 4.dp, top = 4.dp),
                tint = tint
            ); when {
            isHovered -> Box(
                Modifier
                    .offset(x = 2.dp, y = (-2).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(GreenAccent)
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            ) {
                Text(
                    "↓",
                    color = Color.White,
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold
                )
            }; folder.documentCount > 0 -> Box(
                Modifier
                    .offset(x = 2.dp, y = (-2).dp)
                    .defaultMinSize(minWidth = 14.dp, minHeight = 14.dp)
                    .clip(CircleShape)
                    .background(if (selected) Coral else StrokeMid)
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (folder.documentCount > 99) "99+" else "${folder.documentCount}",
                    color = if (selected) Color.White else InkMid,
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 7.sp
                )
            }
        }
        }; Spacer(Modifier.height(4.dp)); Text(
        folder.name,
        color = tint,
        fontSize = 9.sp,
        fontWeight = if (selected || isHovered || isReordering) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    }
}

@Composable
fun BottomNavBar(selectedTab: BottomTab, onTabSelected: (BottomTab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(BgBase)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(60.dp)
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            BottomTabItem(
                Modifier.weight(1f),
                Icons.Default.Description,
                "Home",
                selectedTab == BottomTab.ALL_DOCS
            ) { onTabSelected(BottomTab.ALL_DOCS) }; BottomTabItem(
            Modifier.weight(1f),
            Icons.Default.Person,
            "Profile",
            selectedTab == BottomTab.PROFILE
        ) { onTabSelected(BottomTab.PROFILE) }
        }
    }
}

@Composable
private fun BottomTabItem(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(if (selected) Coral else InkMid, tween(200), label = "t");
    val indA by animateFloatAsState(if (selected) 1f else 0f, tween(250), label = "ind");
    val scale by animateFloatAsState(
        if (selected) 1.05f else 1f,
        spring(stiffness = 600f),
        label = "sc"
    ); Column(
        modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .width(28.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Coral.copy(alpha = indA))
        ); Spacer(Modifier.height(5.dp)); Icon(
        icon,
        label,
        Modifier.size(21.dp),
        tint = tint
    ); Spacer(
        Modifier.height(2.dp)
    ); Text(
        label,
        color = tint,
        fontSize = 10.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
    )
    }
}