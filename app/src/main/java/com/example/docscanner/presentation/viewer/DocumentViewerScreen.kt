package com.example.docscanner.presentation.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.docscanner.data.ocr.MlKitOcrHelper
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.Document
import com.example.docscanner.presentation.alldocuments.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DocumentType { PDF, IMAGE }
private enum class OcrState { IDLE, LOADING, SUCCESS, ERROR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    documentName: String,
    documentUri : String,
    documentType: DocumentType,
    document    : Document? = null,
    onBack      : () -> Unit,
    onRename    : ((String) -> Unit)? = null,
    onChangeType: ((String) -> Unit)? = null,
    onDelete    : (() -> Unit)? = null,
    onUnmerge   : (() -> Unit)? = null
) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val uri = Uri.parse(documentUri); val ocrHelper = remember { MlKitOcrHelper(context) }

    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(documentType == DocumentType.PDF) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(documentUri) { if (documentType == DocumentType.PDF) { isLoading = true; try { pdfPages = renderPdfPages(context, uri) } catch (e: Exception) { errorMessage = "Cannot open PDF: ${e.message}" }; isLoading = false } }

    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    val totalPages = if (documentType == DocumentType.PDF) pdfPages.size else 1

    var scale by remember { mutableFloatStateOf(1f) }; var offset by remember { mutableStateOf(Offset.Zero) }

    var ocrState by remember { mutableStateOf(OcrState.IDLE) }; var ocrText by remember { mutableStateOf("") }; var ocrError by remember { mutableStateOf("") }; var showOcrSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }; var showRenameDialog by remember { mutableStateOf(false) }; var showInfoSheet by remember { mutableStateOf(false) }; var showChangeTypeSheet by remember { mutableStateOf(false) }; var showDeleteDialog by remember { mutableStateOf(false) }

    fun runOcr() {
        showOcrSheet = true; ocrState = OcrState.LOADING; ocrText = ""; ocrError = ""
        scope.launch {
            when (documentType) {
                DocumentType.IMAGE -> { val bmp = loadBitmapFromUri(context, uri); if (bmp == null) { ocrError = "Could not load image."; ocrState = OcrState.ERROR; return@launch }; ocrHelper.extractText(bmp).onSuccess { t -> ocrText = t.ifBlank { "No text found." }; ocrState = OcrState.SUCCESS }.onFailure { e -> ocrError = e.message ?: "Unknown error"; ocrState = OcrState.ERROR } }
                DocumentType.PDF -> { if (pdfPages.isEmpty()) { ocrError = "No PDF pages loaded."; ocrState = OcrState.ERROR; return@launch }; val all = StringBuilder(); for ((idx, bmp) in pdfPages.withIndex()) { ocrHelper.extractText(bmp).onSuccess { t -> if (t.isNotBlank()) { if (pdfPages.size > 1) all.append("── Page ${idx + 1} ──\n"); all.append(t.trim()).append("\n\n") } } }; ocrText = all.toString().trim().ifBlank { "No text found in any page." }; ocrState = OcrState.SUCCESS }
            }
        }
    }

    if (showOcrSheet) OcrBottomSheet(state = ocrState, text = ocrText, error = ocrError, onDismiss = { showOcrSheet = false }, onCopy = { val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText("Extracted Text", ocrText)) })
    if (showRenameDialog && onRename != null) RenameDialog(currentName = documentName, onConfirm = { newName -> onRename(newName); showRenameDialog = false }, onDismiss = { showRenameDialog = false })
    if (showInfoSheet) InfoBottomSheet(documentName = documentName, documentType = documentType, document = document, totalPages = totalPages, uri = documentUri, onDismiss = { showInfoSheet = false })
    if (showChangeTypeSheet && onChangeType != null) ChangeTypeBottomSheet(currentLabel = document?.docClassLabel, onSelect = { label -> onChangeType(label); showChangeTypeSheet = false }, onDismiss = { showChangeTypeSheet = false })

    // ── Delete confirmation dialog ────────────────────────────────────────────
    if (showDeleteDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false }, containerColor = BgCard, shape = RoundedCornerShape(18.dp),
            title = { Text("Delete Document", color = Ink, fontWeight = FontWeight.Bold) },
            text = { Text("Delete \"$documentName\"? This cannot be undone.", color = InkMid, fontSize = 14.sp) },
            confirmButton = {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(DangerRed.copy(0.10f)).border(1.dp, DangerRed.copy(0.30f), RoundedCornerShape(10.dp)).clickable { showDeleteDialog = false; onDelete() }.padding(horizontal = 16.dp, vertical = 9.dp)) { Text("Delete", color = DangerRed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            },
            dismissButton = {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(BgSurface).border(1.dp, StrokeLight, RoundedCornerShape(10.dp)).clickable { showDeleteDialog = false }.padding(horizontal = 16.dp, vertical = 9.dp)) { Text("Cancel", color = InkMid, fontSize = 14.sp) }
            }
        )
    }

    Box(Modifier.fillMaxSize().background(BgBase)) {
        Column(Modifier.fillMaxSize()) {
            // ── Top bar ───────────────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().background(BgBase).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                    Column(Modifier.weight(1f)) {
                        Text(documentName, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (totalPages > 1) Text("Page $currentPage of $totalPages", color = InkMid, fontSize = 11.sp)
                    }
                    val contentReady = !isLoading && errorMessage == null && (documentType == DocumentType.IMAGE || (documentType == DocumentType.PDF && pdfPages.isNotEmpty()))
                    if (contentReady) {
                        val scanning = ocrState == OcrState.LOADING
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (scanning) BgSurface else CoralSoft).border(1.dp, if (scanning) StrokeLight else Coral.copy(0.25f), RoundedCornerShape(10.dp)).then(if (!scanning) Modifier.pointerInput(Unit) { detectTapGestures { runOcr() } } else Modifier).padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            if (scanning) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { CircularProgressIndicator(Modifier.size(13.dp), color = Coral, strokeWidth = 2.dp); Text("Reading…", color = InkMid, fontSize = 12.sp) }
                            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Default.DocumentScanner, null, tint = Coral, modifier = Modifier.size(15.dp)); Text("Extract", color = Coral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, "More", tint = Ink) }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }, containerColor = BgCard) {
                            if (onRename != null) DropdownMenuItem(text = { Text("Rename", color = Ink, fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = InkMid, modifier = Modifier.size(18.dp)) }, onClick = { showMoreMenu = false; showRenameDialog = true })
                            if (onChangeType != null) DropdownMenuItem(text = { Text("Change Type", color = Ink, fontSize = 14.sp) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null, tint = InkMid, modifier = Modifier.size(18.dp)) }, onClick = { showMoreMenu = false; showChangeTypeSheet = true })
                            DropdownMenuItem(text = { Text("Info", color = Ink, fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Default.Info, null, tint = InkMid, modifier = Modifier.size(18.dp)) }, onClick = { showMoreMenu = false; showInfoSheet = true })
                            if (document?.isMergedPdf == true && onUnmerge != null) { HorizontalDivider(color = StrokeLight); DropdownMenuItem(text = { Text("Unmerge", color = Color(0xFFE6A23C), fontSize = 14.sp) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, null, tint = Color(0xFFE6A23C), modifier = Modifier.size(18.dp)) }, onClick = { showMoreMenu = false; onUnmerge() }) }
                            if (onDelete != null) { HorizontalDivider(color = StrokeLight); DropdownMenuItem(text = { Text("Delete", color = DangerRed, fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = DangerRed, modifier = Modifier.size(18.dp)) }, onClick = { showMoreMenu = false; showDeleteDialog = true }) }
                        }
                    }
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(StrokeLight))
            }
            // ── Content ───────────────────────────────────────────────────────
            Box(Modifier.weight(1f).fillMaxWidth().background(BgBase)) {
                when {
                    isLoading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(color = Coral); Text("Rendering PDF…", color = InkMid, fontSize = 13.sp) }
                    errorMessage != null -> Text(errorMessage!!, color = DangerRed, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                    documentType == DocumentType.PDF && pdfPages.isNotEmpty() -> {
                        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> val ns = (scale * zoom).coerceIn(1f, 5f); scale = ns; if (ns > 1f) { val mx = (size.width * (ns - 1f)) / 2f; val my = (size.height * (ns - 1f)) / 2f; offset = Offset((offset.x + pan.x).coerceIn(-mx, mx), (offset.y + pan.y).coerceIn(-my, my)) } else offset = Offset.Zero } }.pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1.1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) }) {
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), userScrollEnabled = scale <= 1.05f) { itemsIndexed(pdfPages) { idx, bmp -> PdfPageItem(bmp, idx + 1, pdfPages.size) } }
                        }
                        if (pdfPages.size > 1) PageBadge(currentPage, pdfPages.size, Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp))
                        if (scale > 1.05f) Box(Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(BgCard).border(1.dp, StrokeLight, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) { Text("${(scale * 100).toInt()}%", color = InkMid, fontSize = 12.sp) }
                    }
                    documentType == DocumentType.IMAGE -> ZoomableImage(uri = uri)
                    else -> Text("No content to display", color = InkMid, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

// ── Change Type sheet ─────────────────────────────────────────────────────────
private val SelectBlue = Color(0xFF2E6BE6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeTypeBottomSheet(currentLabel: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val types = listOf(DocClassType.AADHAAR, DocClassType.PAN, DocClassType.VOTER_ID, DocClassType.DRIVING_LICENSE, DocClassType.PASSPORT, DocClassType.OTHER)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BgCard, dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 36.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(StrokeMid)) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Change Type", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Select the correct document type", color = InkMid, fontSize = 13.sp); Spacer(Modifier.height(10.dp))
            types.forEach { docType ->
                val isSelected = currentLabel == docType.displayName
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (isSelected) SelectBlue.copy(0.08f) else BgSurface).border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) SelectBlue.copy(0.40f) else StrokeLight, RoundedCornerShape(12.dp)).clickable { onSelect(docType.displayName) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(20.dp).clip(CircleShape).border(if (isSelected) 0.dp else 1.5.dp, if (isSelected) Color.Transparent else StrokeMid, CircleShape).background(if (isSelected) SelectBlue else Color.Transparent), contentAlignment = Alignment.Center) { if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                    Text(docType.displayName, color = if (isSelected) SelectBlue else Ink, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

// ── Rename dialog ─────────────────────────────────────────────────────────────
@Composable
private fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = BgCard, shape = RoundedCornerShape(18.dp), title = { Text("Rename Document", color = Ink, fontWeight = FontWeight.Bold) }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Coral, unfocusedBorderColor = StrokeLight, cursorColor = Coral, focusedTextColor = Ink, unfocusedTextColor = Ink), shape = RoundedCornerShape(10.dp)) },
        confirmButton = { Box(Modifier.clip(RoundedCornerShape(10.dp)).background(CoralSoft).border(1.dp, Coral.copy(0.25f), RoundedCornerShape(10.dp)).clickable(enabled = name.isNotBlank() && name != currentName) { onConfirm(name.trim()) }.padding(horizontal = 16.dp, vertical = 9.dp)) { Text("Rename", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (name.isNotBlank() && name != currentName) Coral else InkDim) } },
        dismissButton = { Box(Modifier.clip(RoundedCornerShape(10.dp)).background(BgSurface).border(1.dp, StrokeLight, RoundedCornerShape(10.dp)).clickable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 9.dp)) { Text("Cancel", color = InkMid, fontSize = 14.sp) } }
    )
}

// ── Info sheet ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoBottomSheet(documentName: String, documentType: DocumentType, document: Document?, totalPages: Int, uri: String, onDismiss: () -> Unit) {
    val context = LocalContext.current; val fileSize = remember(uri) { getFileSize(context, uri) }; val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy  •  hh:mm a", Locale.getDefault()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BgCard, dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 36.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(StrokeMid)) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Document Info", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(8.dp))
            InfoRow("Name", documentName); InfoRow("Type", if (documentType == DocumentType.PDF) "PDF Document" else "Image"); InfoRow("Pages", "$totalPages")
            fileSize?.let { InfoRow("Size", it) }
            document?.let { doc -> InfoRow("Created", dateFormat.format(Date(doc.createdAt))); doc.docClassLabel?.let { label -> if (label != "Document") InfoRow("Classification", label) }; if (doc.isMergedPdf) InfoRow("Merged from", "${doc.sourceDocumentIds.size} documents") }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BgSurface).padding(horizontal = 14.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, color = InkMid, fontSize = 13.sp); Text(value, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp)) } }

private fun getFileSize(context: Context, uriString: String): String? { return try { val size = if (uriString.startsWith("content://") || uriString.startsWith("file://")) { val u = Uri.parse(uriString); context.contentResolver.openFileDescriptor(u, "r")?.use { it.statSize } ?: return null } else { val f = File(uriString); if (f.exists()) f.length() else return null }; when { size < 1024 -> "$size B"; size < 1024 * 1024 -> "${size / 1024} KB"; else -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0)) } } catch (_: Exception) { null } }

// ── OCR sheet ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrBottomSheet(state: OcrState, text: String, error: String, onDismiss: () -> Unit, onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BgCard, dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 36.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(StrokeMid)) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Extracted Text", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(when (state) { OcrState.LOADING -> "Scanning all pages…"; OcrState.SUCCESS -> "${text.length} characters"; OcrState.ERROR -> "Failed to read"; OcrState.IDLE -> "" }, color = InkMid, fontSize = 12.sp) }
                if (state == OcrState.SUCCESS && text.isNotBlank()) Box(Modifier.clip(RoundedCornerShape(9.dp)).background(if (copied) GreenAccent.copy(0.10f) else CoralSoft).border(1.dp, if (copied) GreenAccent.copy(0.30f) else Coral.copy(0.25f), RoundedCornerShape(9.dp)).pointerInput(Unit) { detectTapGestures { onCopy(); copied = true } }.padding(horizontal = 14.dp, vertical = 8.dp)) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null, Modifier.size(14.dp), tint = if (copied) GreenAccent else Coral); Text(if (copied) "Copied!" else "Copy", color = if (copied) GreenAccent else Coral, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) } }
            }
            Spacer(Modifier.height(16.dp))
            AnimatedContent(state, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "ocr") { s ->
                when (s) {
                    OcrState.LOADING -> Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(BgSurface).border(1.dp, StrokeLight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(28.dp), color = Coral, strokeWidth = 2.5.dp); Text("Reading all pages…", color = InkMid, fontSize = 13.sp) } }
                    OcrState.ERROR -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DangerRed.copy(0.08f)).border(1.dp, DangerRed.copy(0.20f), RoundedCornerShape(12.dp)).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ErrorOutline, null, Modifier.size(20.dp), tint = DangerRed); Text("Error: $error", color = DangerRed, fontSize = 13.sp) }
                    OcrState.SUCCESS -> Box(Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 360.dp).clip(RoundedCornerShape(12.dp)).background(BgSurface).border(1.dp, StrokeLight, RoundedCornerShape(12.dp)).padding(16.dp)) { Text(text, color = Ink, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) }
                    OcrState.IDLE -> Unit
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
@Composable private fun PdfPageItem(bitmap: Bitmap, pageNumber: Int, totalPages: Int) { Box(Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(10.dp), ambientColor = Color(0x12000000), spotColor = Color(0x12000000)).clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, StrokeLight, RoundedCornerShape(10.dp))) { Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Page $pageNumber", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth()); Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Ink.copy(0.78f)).padding(horizontal = 8.dp, vertical = 4.dp)) { Text("$pageNumber / $totalPages", color = Color.White, fontSize = 11.sp) } } }

@Composable private fun ZoomableImage(uri: Uri) { var scale by remember { mutableFloatStateOf(1f) }; var offset by remember { mutableStateOf(Offset.Zero) }; Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> val ns = (scale * zoom).coerceIn(1f, 5f); scale = ns; if (ns > 1f) { val mx = (size.width * (ns - 1f)) / 2f; val my = (size.height * (ns - 1f)) / 2f; offset = Offset((offset.x + pan.x).coerceIn(-mx, mx), (offset.y + pan.y).coerceIn(-my, my)) } else offset = Offset.Zero } }.pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1.1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) }, contentAlignment = Alignment.Center) { AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)); if (scale > 1.05f) Box(Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(BgCard).border(1.dp, StrokeLight, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) { Text("${(scale * 100).toInt()}%", color = InkMid, fontSize = 12.sp) } } }

@Composable private fun PageBadge(current: Int, total: Int, modifier: Modifier = Modifier) { Box(modifier.clip(RoundedCornerShape(10.dp)).background(BgCard).border(1.dp, StrokeLight, RoundedCornerShape(10.dp)).padding(horizontal = 11.dp, vertical = 5.dp)) { Text("$current / $total", color = InkMid, fontSize = 12.sp) } }

private suspend fun renderPdfPages(context: Context, uri: Uri): List<Bitmap> = withContext(Dispatchers.IO) { val bitmaps = mutableListOf<Bitmap>(); try { val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext bitmaps; val renderer = PdfRenderer(fd); for (i in 0 until renderer.pageCount) { val page = renderer.openPage(i); val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888); page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); page.close(); bitmaps.add(bmp) }; renderer.close(); fd.close() } catch (e: Exception) { e.printStackTrace() }; bitmaps }
private suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) { try { val loader = coil.ImageLoader(context); val req = ImageRequest.Builder(context).data(uri).allowHardware(false).build(); (loader.execute(req).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap } catch (e: Exception) { null } }