package com.example.docscanner.presentation.merge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.docscanner.domain.model.Document

// Paper Studio tokens
private val BgCard      = Color(0xFFFFFFFF)
private val BgSurface   = Color(0xFFF3F0EB)
private val StrokeLight = Color(0xFFE2DDD8)
private val StrokeMid   = Color(0xFFCDC8C0)
private val Coral       = Color(0xFFE8603C)
private val Ink         = Color(0xFF1A1A2E)
private val InkMid      = Color(0xFF6B6878)
private val AmberAccent = Color(0xFFE6A23C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergedDocumentSheet(
    document   : Document,
    onView     : () -> Unit,
    onUnmerge  : () -> Unit,
    onDismiss  : () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(18.dp),
            title = {
                Text("Unmerge Document", color = Ink, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will restore the ${document.sourceDocumentIds.size} original images and remove the merged PDF. Continue?",
                    color = InkMid, fontSize = 14.sp
                )
            },
            confirmButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(AmberAccent.copy(0.12f))
                        .border(1.dp, AmberAccent.copy(0.30f), RoundedCornerShape(10.dp))
                        .clickable { showConfirm = false; onUnmerge(); onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text("Unmerge", color = AmberAccent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            },
            dismissButton = {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
                        .clickable { showConfirm = false }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text("Cancel", color = InkMid, fontSize = 14.sp)
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgCard,
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(StrokeMid)
            )
        }
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Document info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(document.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Text(
                "${document.pageCount} pages  •  Merged PDF",
                color = InkMid, fontSize = 13.sp
            )

            Spacer(Modifier.height(20.dp))

            // View PDF
            SheetAction(
                icon = Icons.Default.Visibility,
                title = "View PDF",
                subtitle = "Open the merged document",
                accentColor = Coral,
                onClick = { onView(); onDismiss() }
            )

            Spacer(Modifier.height(10.dp))

            // Unmerge
            SheetAction(
                icon = Icons.Default.CallSplit,
                title = "Unmerge",
                subtitle = "Restore ${document.sourceDocumentIds.size} original images",
                accentColor = AmberAccent,
                onClick = { showConfirm = true }
            )
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    val BgSurface   = Color(0xFFF3F0EB)
    val StrokeLight = Color(0xFFE2DDD8)
    val Ink         = Color(0xFF1A1A2E)
    val InkMid      = Color(0xFF6B6878)
    val InkDim      = Color(0xFFB8B4BC)

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgSurface)
            .border(1.dp, StrokeLight, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = InkMid, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, null, tint = InkDim, modifier = Modifier.size(18.dp))
    }
}