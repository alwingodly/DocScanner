package com.example.docscanner.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.docscanner.presentation.alldocuments.BgBase
import com.example.docscanner.presentation.alldocuments.BgCard
import com.example.docscanner.presentation.alldocuments.BgSurface
import com.example.docscanner.presentation.alldocuments.Coral
import com.example.docscanner.presentation.alldocuments.CoralDark
import com.example.docscanner.presentation.alldocuments.DocumentTab
import com.example.docscanner.presentation.alldocuments.Ink
import com.example.docscanner.presentation.alldocuments.InkDim
import com.example.docscanner.presentation.alldocuments.InkMid
import com.example.docscanner.presentation.alldocuments.StrokeLight

@Composable
fun MainLayout(
    isSelectMode   : Boolean             = false,
    selectedCount  : Int                 = 0,
    documentCount  : Int                 = 0,
    hasDocuments   : Boolean             = false,
    columnCount    : Int                 = 3,
    selectedTab    : DocumentTab         = DocumentTab.ALL,
    onTabChange    : (DocumentTab) -> Unit = {},
    onSelectToggle : () -> Unit          = {},
    onSelectAll    : () -> Unit          = {},
    onColumnChange : (Int) -> Unit       = {},
    content        : @Composable BoxScope.() -> Unit
) {
    var showColumnMenu by remember { mutableStateOf(false) }
    val allSelected = documentCount > 0 && selectedCount == documentCount

    Column(Modifier.fillMaxSize().background(BgBase)) {

        // ── Status bar tint ───────────────────────────────────────────────────
        Spacer(
            Modifier.fillMaxWidth()
                .statusBarsPadding()
                .background(BgCard)
        )

        // ── Header (animates between normal / select mode) ────────────────────
        Box(Modifier.fillMaxWidth().height(68.dp).background(BgCard)) {
            AnimatedContent(
                targetState    = isSelectMode,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label          = "header"
            ) { inSelect ->
                if (inSelect) {
                    // ── Select mode ───────────────────────────────────────────
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onSelectToggle) {
                            Icon(Icons.Default.Close, "Cancel", tint = Ink, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (selectedCount == 0) "Select documents" else "$selectedCount selected",
                                color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                            )
                            if (documentCount > 0)
                                Text("$documentCount total", color = InkMid, fontSize = 12.sp)
                        }
                        // Select all / Deselect all
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (allSelected) Coral.copy(0.10f) else BgSurface)
                                .border(
                                    1.dp,
                                    if (allSelected) Coral.copy(0.28f) else StrokeLight,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectAll() }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (allSelected) Icons.Default.CheckCircle
                                    else Icons.Default.CheckCircleOutline,
                                    null,
                                    tint = if (allSelected) Coral else InkMid,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    if (allSelected) "Deselect all" else "Select all",
                                    color = if (allSelected) Coral else InkMid,
                                    fontSize = 13.sp,
                                    fontWeight = if (allSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                } else {
                    // ── Normal header ─────────────────────────────────────────
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Brand mark
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Coral, CoralDark))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DocumentScanner, null,
                                tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Text(
                            "DocScanner", color = Ink,
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp,
                            modifier = Modifier.weight(1f)
                        )

                        if (hasDocuments) {
                            // Select pill button
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(BgSurface)
                                    .border(1.dp, StrokeLight, RoundedCornerShape(9.dp))
                                    .clickable(onClick = onSelectToggle),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .border(1.5.dp, InkMid.copy(0.6f), RoundedCornerShape(3.dp))
                                )
                            }
                            Spacer(Modifier.width(4.dp))

                            // Grid size menu
                            Box {
                                IconButton(onClick = { showColumnMenu = true }) {
                                    Icon(Icons.Default.GridView, null,
                                        tint = InkMid, modifier = Modifier.size(20.dp))
                                }
                                DropdownMenu(
                                    expanded         = showColumnMenu,
                                    onDismissRequest = { showColumnMenu = false },
                                    modifier         = Modifier.background(BgCard)
                                ) {
                                    Text(
                                        "GRID SIZE", color = InkDim, fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                    listOf(2, 3, 4).forEach { count ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    GridPreviewIcon(count, columnCount == count)
                                                    Text(
                                                        "$count columns",
                                                        color = if (columnCount == count) Coral else Ink,
                                                        fontSize = 14.sp,
                                                        fontWeight = if (columnCount == count)
                                                            FontWeight.SemiBold else FontWeight.Normal
                                                    )
                                                    if (columnCount == count) {
                                                        Spacer(Modifier.weight(1f))
                                                        Box(Modifier.size(18.dp).clip(CircleShape)
                                                            .background(Coral),
                                                            contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Check, null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(11.dp))
                                                        }
                                                    }
                                                }
                                            },
                                            onClick = { onColumnChange(count); showColumnMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Bottom divider
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(StrokeLight))
        }

        // ── Content ───────────────────────────────────────────────────────────
        Box(Modifier.weight(1f).fillMaxWidth(), content = content)

        // ── Bottom tab bar ────────────────────────────────────────────────────
        BottomTabBar(selectedTab = selectedTab, onTabChange = onTabChange)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BOTTOM TAB BAR
// ═══════════════════════════════════════════════════════════════════════════════

private data class TabItem(val tab: DocumentTab, val icon: ImageVector, val label: String)

private val TABS = listOf(
    TabItem(DocumentTab.ALL,          Icons.Default.FolderCopy,  "All"),
    TabItem(DocumentTab.UNCLASSIFIED, Icons.Default.HelpOutline, "Unclassified")
)

@Composable
private fun BottomTabBar(
    selectedTab : DocumentTab,
    onTabChange : (DocumentTab) -> Unit
) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(StrokeLight))
        Row(
            Modifier.fillMaxWidth().background(BgCard)
                .navigationBarsPadding()
                .height(60.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TABS.forEach { item ->
                val isActive = selectedTab == item.tab
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onTabChange(item.tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Active pill behind icon
                        Box(contentAlignment = Alignment.Center) {
                            if (isActive)
                                Box(
                                    Modifier.width(44.dp).height(26.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(Coral.copy(alpha = 0.11f))
                                )
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint     = if (isActive) Coral else InkDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            item.label,
                            color      = if (isActive) Coral else InkDim,
                            fontSize   = 10.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ─── Mini grid preview ────────────────────────────────────────────────────────

@Composable
private fun GridPreviewIcon(columns: Int, isActive: Boolean) {
    val color = if (isActive) Coral else InkDim.copy(0.5f)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.width(22.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(columns.coerceAtMost(4)) {
                    Box(
                        Modifier
                            .size(if (columns == 4) 3.dp else if (columns == 3) 4.dp else 6.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}