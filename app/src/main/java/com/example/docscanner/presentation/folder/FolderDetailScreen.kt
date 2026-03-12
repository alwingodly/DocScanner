package com.example.docscanner.presentation.folder

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.docscanner.domain.model.Document
import com.example.docscanner.navigation.ALL_DOCUMENTS_ID
import com.example.docscanner.presentation.alldocuments.SidebarDragState
import com.example.docscanner.presentation.viewer.DocumentType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folderId      : String,
    folderName    : String,
    folderIcon    : String,
    showTopBar    : Boolean = true,
    dragState     : SidebarDragState,           // ← NEW: shared drag state
    onStartScan   : () -> Unit,
    onOpenDocument: (uri: String, type: DocumentType, name: String) -> Unit,
    onBack        : () -> Unit,
    viewModel     : FolderViewModel = hiltViewModel()
) {
    LaunchedEffect(folderId) { viewModel.loadFolder(folderId) }

    val documents        by viewModel.documents.collectAsState()
    val context          = LocalContext.current
    var documentToDelete by remember { mutableStateOf<Document?>(null) }

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

    val bodyContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.fillMaxSize()) {
            if (documents.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(folderIcon, fontSize = 64.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("No documents yet", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap Scan Document to add one",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier             = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement  = Arrangement.spacedBy(10.dp),
                    contentPadding       = PaddingValues(bottom = 80.dp)
                ) {
                    // Drag hint banner
                    item {
                        if (dragState.isDragging) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                color    = MaterialTheme.colorScheme.primaryContainer,
                                shape    = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "⬅  Drop on a sidebar folder to move",
                                    modifier  = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style     = MaterialTheme.typography.bodySmall,
                                    color     = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        } else {
                            Text(
                                "Long-press & drag ⬅ to move to another folder",
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }

                    // Folder header (when no TopAppBar)
                    if (!showTopBar) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier          = Modifier.padding(bottom = 4.dp)
                            ) {
                                Text(folderIcon, fontSize = 28.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(folderName, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }

                    items(documents, key = { it.id }) { document ->
                        val isDragging = dragState.draggingDocumentId == document.id

                        DraggableDocumentCard(
                            document    = document,
                            isDragging  = isDragging,
                            onOpen      = {
                                val type = if (document.pdfPath != null) DocumentType.PDF
                                else DocumentType.IMAGE
                                val uri  = document.pdfPath
                                    ?: document.thumbnailPath
                                    ?: return@DraggableDocumentCard
                                onOpenDocument(uri, type, document.name)
                            },
                            onDelete          = { documentToDelete = document },
                            onLongPressStart  = { windowX, windowY ->
                                dragState.onDragStart(
                                    documentId    = document.id,
                                    windowStartX  = windowX,
                                    windowStartY  = windowY,
                                    thumbnailPath = document.thumbnailPath,
                                    documentName  = document.name,
                                    pageCount     = document.pageCount
                                )
                                haptic()
                            },
                            onDrag            = { dx, dy -> dragState.onDrag(dx, dy) },
                            onDragEnd         = {
                                val target = dragState.onDragEnd()
                                if (target != null && target != folderId) {
                                    // ALL_DOCUMENTS_ID means "move back to unassigned"
                                    val realTarget = if (target == ALL_DOCUMENTS_ID) "" else target
                                    viewModel.moveDocument(document.id, folderId, realTarget)
                                    haptic()
                                }
                            },
                            onDragCancel      = { dragState.onDragCancel() }
                        )
                    }
                }
            }

            // FAB
            ExtendedFloatingActionButton(
                onClick  = onStartScan,
                icon     = { Icon(Icons.Default.Add, "Scan") },
                text     = { Text("Scan Document") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }

    if (showTopBar) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(folderIcon, fontSize = 22.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(folderName)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            bodyContent(Modifier.padding(innerPadding))
        }
    } else {
        bodyContent(Modifier)
    }

    // Delete confirmation
    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title   = { Text("Delete Document") },
            text    = { Text("Delete \"${doc.name}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteDocument(doc); documentToDelete = null },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

// ── Draggable document card ───────────────────────────────────────────────────

@Composable
private fun DraggableDocumentCard(
    document        : Document,
    isDragging      : Boolean,
    onOpen          : () -> Unit,
    onDelete        : () -> Unit,
    onLongPressStart: (windowX: Float, windowY: Float) -> Unit,
    onDrag          : (Float, Float) -> Unit,
    onDragEnd       : () -> Unit,
    onDragCancel    : () -> Unit
) {
    val dateFormat   = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    var cardWindowX  by remember { mutableFloatStateOf(0f) }
    var cardWindowY  by remember { mutableFloatStateOf(0f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isDragging) Modifier.graphicsLayer { alpha = 0f } else Modifier)
            .onGloballyPositioned { coords ->
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
            .then(
                if (isDragging) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .clickable(enabled = !isDragging, onClick = onOpen),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 10.dp else 2.dp
        )
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier         = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (document.thumbnailPath != null) {
                    AsyncImage(
                        model              = document.thumbnailPath,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        if (document.pdfPath != null) Icons.Default.Description
                        else Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = document.name,
                    style    = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = dateFormat.format(Date(document.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Drag handle indicator when dragging
            if (isDragging) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}