package com.example.docscanner.presentation.alldocuments

import android.Manifest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.docscanner.domain.model.Document

@Composable
fun AllDocumentsScreen(
    dragState       : SidebarDragState,
    onDocumentClick : (Document) -> Unit,
    onScanClick     : () -> Unit,
    viewModel       : AllDocumentsViewModel = hiltViewModel()
) {
    val documents by viewModel.documents.collectAsState()
    val context = LocalContext.current

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun haptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)
                    ?.defaultVibrator?.vibrate(
                        VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
                    ?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    if (documents.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📄", fontSize = 64.sp)
                Spacer(Modifier.height(16.dp))
                Text("No documents yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap Scan Document to get started",
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            ExtendedFloatingActionButton(
                onClick  = onScanClick,
                icon     = { Icon(Icons.Default.Add, contentDescription = null) },
                text     = { Text("Scan Document") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .padding(bottom = 80.dp)   // space for FAB
        ) {
            if (dragState.isDragging) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color    = MaterialTheme.colorScheme.primaryContainer,
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "⬅  Drop on a folder to move",
                        modifier  = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    "Tap to open  •  Long-press & drag ⬅ to move to folder",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }

            val rows = documents.chunked(2)
            rows.forEach { rowDocs ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowDocs.forEach { doc ->
                        val isDragging = dragState.draggingDocumentId == doc.id
                        DocCard(
                            document         = doc,
                            isDragging       = isDragging,
                            modifier         = Modifier.weight(1f),
                            onTap            = { onDocumentClick(doc) },
                            onLongPressStart = { windowX, windowY ->
                                dragState.onDragStart(
                                    documentId    = doc.id,
                                    windowStartX  = windowX,
                                    windowStartY  = windowY,
                                    thumbnailPath = doc.thumbnailPath,
                                    documentName  = doc.name,
                                    pageCount     = doc.pageCount
                                )
                                haptic()
                            },
                            onDrag       = { dx, dy -> dragState.onDrag(dx, dy) },
                            onDragEnd    = {
                                val target = dragState.onDragEnd()
                                if (target != null) {
                                    viewModel.moveDocumentToFolder(doc.id, target)
                                    haptic()
                                }
                            },
                            onDragCancel = { dragState.onDragCancel() }
                        )
                    }
                    if (rowDocs.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(80.dp))
        }  // end Column

        ExtendedFloatingActionButton(
            onClick  = onScanClick,
            icon     = { Icon(Icons.Default.Add, contentDescription = null) },
            text     = { Text("Scan Document") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }  // end Box
}

@Composable
private fun DocCard(
    document        : Document,
    isDragging      : Boolean,
    modifier        : Modifier,
    onTap           : () -> Unit,
    onLongPressStart: (windowX: Float, windowY: Float) -> Unit,
    onDrag          : (Float, Float) -> Unit,
    onDragEnd       : () -> Unit,
    onDragCancel    : () -> Unit
) {
    var cardWindowX by remember { mutableFloatStateOf(0f) }
    var cardWindowY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier
            .aspectRatio(0.75f)
            // When dragging this card, make it invisible in-place —
            // the ghost rendered at root level takes over visually
            .then(if (isDragging) Modifier.alpha(0f) else Modifier)            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                cardWindowX = pos.x
                cardWindowY = pos.y
            }
            .pointerInput(document.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { localOffset ->
                        onLongPressStart(
                            cardWindowX + localOffset.x,
                            cardWindowY + localOffset.y
                        )
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount.x, amount.y)
                    },
                    onDragEnd    = onDragEnd,
                    onDragCancel = onDragCancel
                )
            }
    ) {
        Card(
            modifier  = Modifier
                .fillMaxSize()
                .clickable(enabled = !isDragging, onClick = onTap),
            shape     = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(Modifier.fillMaxSize()) {
                if (document.thumbnailPath != null) {
                    AsyncImage(
                        model              = document.thumbnailPath,
                        contentDescription = document.name,
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
                            modifier = Modifier.size(36.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        document.name,
                        style     = MaterialTheme.typography.labelSmall,
                        color     = Color.White,
                        maxLines  = 1,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth()
                    )
                }

                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(Color.Black.copy(0.6f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${document.pageCount}p",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}