package com.example.docscanner.presentation.viewer

import android.content.ClipData
import android.widget.Toast
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.docscanner.data.ocr.MlKitOcrClient
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.Document
import com.example.docscanner.domain.model.Folder
import com.example.docscanner.presentation.alldocuments.AadhaarDetailRow
import com.example.docscanner.presentation.alldocuments.BgBase
import com.example.docscanner.presentation.alldocuments.BgCard
import com.example.docscanner.presentation.alldocuments.BgSurface
import com.example.docscanner.presentation.alldocuments.Coral
import com.example.docscanner.presentation.alldocuments.CoralSoft
import com.example.docscanner.presentation.alldocuments.ContextAction
import com.example.docscanner.presentation.alldocuments.DangerRed
import com.example.docscanner.presentation.alldocuments.GreenAccent
import com.example.docscanner.presentation.alldocuments.Ink
import com.example.docscanner.presentation.alldocuments.InkDim
import com.example.docscanner.presentation.alldocuments.InkMid
import com.example.docscanner.presentation.alldocuments.StrokeLight
import com.example.docscanner.presentation.alldocuments.StrokeMid
import com.example.docscanner.presentation.alldocuments.TypeColors
import com.example.docscanner.presentation.alldocuments.groupDetailRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DocumentType { PDF, IMAGE }
private enum class OcrState { IDLE, LOADING, SUCCESS, ERROR }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun DocumentViewerScreen(
    documentName         : String,
    documentUri          : String,
    documentType         : DocumentType,
    document             : Document?          = null,
    documents            : List<Document>     = emptyList(),
    initialIndex         : Int                = 0,
    allFolders           : List<Folder>       = emptyList(),
    onBack               : () -> Unit,
    onRename             : ((Document, String) -> Unit)?  = null,
    onChangeType         : ((Document, String) -> Unit)?  = null,
    onDelete             : ((Document) -> Unit)?          = null,
    onUnmerge            : ((Document) -> Unit)?          = null,
    onRenameAadhaarPair  : ((Document, String) -> Unit)?  = null,
    onUngroupAadhaarPair : ((Document) -> Unit)?          = null,
    onPairPassport       : ((Document) -> Unit)?          = null,
    onUngroupPassport    : ((Document) -> Unit)?          = null,
    onEditDetails        : ((Document) -> Unit)?          = null
) {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val density  = LocalDensity.current
    val ocrHelper = remember { MlKitOcrClient() }

    val galleryDocs = remember(documents, document) {
        if (documents.isNotEmpty()) documents else listOfNotNull(document)
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, galleryDocs.lastIndex.coerceAtLeast(0)),
        pageCount   = { galleryDocs.size.coerceAtLeast(1) }
    )

    // Tracks the zoom level of whichever page is currently visible.
    // Reset to 1 whenever the user swipes to a different page so the
    // pager stays scrollable when viewing the next page at normal scale.
    var pageScale by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(pagerState.currentPage) { pageScale = 1f }

    val currentDoc           = galleryDocs.getOrNull(pagerState.currentPage) ?: document
    val isAadhaarGroupedDoc   = currentDoc?.aadhaarGroupId != null
    val isPassportDoc         = currentDoc?.docClassLabel == "Passport"
    val isPassportPaired      = currentDoc?.passportGroupId != null
    val currentDocumentType  = currentDoc?.let {
        if (it.pdfPath != null) DocumentType.PDF else DocumentType.IMAGE
    } ?: documentType
    val currentUriString = currentDoc?.pdfPath ?: currentDoc?.thumbnailPath ?: documentUri
    val currentUri       = remember(currentUriString) { Uri.parse(currentUriString) }

    // ── PDF state ──────────────────────────────────────────────────────────
    var pdfPages     by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading    by remember { mutableStateOf(currentDocumentType == DocumentType.PDF) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUriString, currentDocumentType) {
        pdfPages     = emptyList()
        errorMessage = null
        if (currentDocumentType == DocumentType.PDF) {
            isLoading = true
            try { pdfPages = renderPdfPages(context, currentUri) }
            catch (e: Exception) { errorMessage = "Cannot open PDF: ${e.message}" }
            isLoading = false
        } else {
            isLoading = false
        }
    }

    val listState   = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    val totalPages  = if (currentDocumentType == DocumentType.PDF) pdfPages.size else 1

    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // ── UI state ───────────────────────────────────────────────────────────
    var ocrState           by remember { mutableStateOf(OcrState.IDLE) }
    var ocrText            by remember { mutableStateOf("") }
    var ocrError           by remember { mutableStateOf("") }
    var showOcrSheet       by remember { mutableStateOf(false) }
    var showMoreMenu       by remember { mutableStateOf(false) }
    var showRenameDialog   by remember { mutableStateOf(false) }
    var showRenamePairDialog by remember { mutableStateOf(false) }
    var renamePairText     by remember { mutableStateOf("") }
    var showInfoSheet      by remember { mutableStateOf(false) }
    var showChangeTypeSheet by remember { mutableStateOf(false) }
    var showMoveSheet      by remember { mutableStateOf(false) }
    var showDeleteDialog   by remember { mutableStateOf(false) }
    val shouldShowViewerMenu = currentDoc != null

    // ── Split-view state ───────────────────────────────────────────────────
    val detailRows  = remember(currentDoc) { currentDoc?.groupDetailRows() ?: emptyList() }
    val hasDetails  = detailRows.isNotEmpty() && currentDocumentType == DocumentType.IMAGE
    var imageHeightDp  by remember { mutableStateOf(220.dp) }
    var isImageExpanded by remember { mutableStateOf(false) }
    var expandedImageHeightDp by remember { mutableStateOf(460.dp) }
    val animatedImageHeight by animateDpAsState(
        targetValue   = if (isImageExpanded) expandedImageHeightDp else imageHeightDp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "imgHeight"
    )

    // ── OCR runner ─────────────────────────────────────────────────────────
    fun runOcr() {
        showOcrSheet = true; ocrState = OcrState.LOADING; ocrText = ""; ocrError = ""
        scope.launch {
            when (currentDocumentType) {
                DocumentType.IMAGE -> {
                    val bmp = loadBitmapFromUri(context, currentUri)
                    if (bmp == null) { ocrError = "Could not load image."; ocrState = OcrState.ERROR; return@launch }
                    ocrHelper.extractText(bmp)
                        .onSuccess { t -> ocrText = t.ifBlank { "No text found." }; ocrState = OcrState.SUCCESS }
                        .onFailure { e -> ocrError = e.message ?: "Unknown error"; ocrState = OcrState.ERROR }
                }
                DocumentType.PDF -> {
                    if (pdfPages.isEmpty()) { ocrError = "No PDF pages loaded."; ocrState = OcrState.ERROR; return@launch }
                    val all = StringBuilder()
                    for ((idx, bmp) in pdfPages.withIndex()) {
                        ocrHelper.extractText(bmp).onSuccess { t ->
                            if (t.isNotBlank()) {
                                if (pdfPages.size > 1) all.append("── Page ${idx + 1} ──\n")
                                all.append(t.trim()).append("\n\n")
                            }
                        }
                    }
                    ocrText  = all.toString().trim().ifBlank { "No text found in any page." }
                    ocrState = OcrState.SUCCESS
                }
            }
        }
    }

    // ── Overlays ───────────────────────────────────────────────────────────
    if (showOcrSheet) OcrBottomSheet(
        state     = ocrState,
        text      = ocrText,
        error     = ocrError,
        onDismiss = { showOcrSheet = false },
        onCopy    = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Extracted Text", ocrText))
        }
    )
    if (showRenameDialog && !isAadhaarGroupedDoc && onRename != null && currentDoc != null)
        RenameDialog(
            currentName = currentDoc.name,
            onConfirm   = { newName -> onRename(currentDoc, newName); showRenameDialog = false },
            onDismiss   = { showRenameDialog = false }
        )
    if (showRenamePairDialog && isAadhaarGroupedDoc && onRenameAadhaarPair != null && currentDoc != null)
        RenameDialog(
            currentName = renamePairText,
            onConfirm   = { newName -> onRenameAadhaarPair(currentDoc, newName); showRenamePairDialog = false },
            onDismiss   = { showRenamePairDialog = false }
        )
    if (showInfoSheet) InfoBottomSheet(
        documentName = currentDoc?.name ?: documentName,
        documentType = currentDocumentType,
        document     = currentDoc ?: document,
        totalPages   = totalPages,
        uri          = currentUriString,
        onDismiss    = { showInfoSheet = false }
    )
    if (showChangeTypeSheet && !isAadhaarGroupedDoc && onChangeType != null && currentDoc != null)
        ChangeTypeBottomSheet(
            currentLabel = currentDoc.docClassLabel,
            onSelect     = { label -> onChangeType(currentDoc, label); showChangeTypeSheet = false },
            onDismiss    = { showChangeTypeSheet = false }
        )
    if (showMoveSheet && !isAadhaarGroupedDoc && currentDoc != null && onChangeType != null)
        MoveToFolderBottomSheet(
            document  = currentDoc,
            allFolders = allFolders,
            onSelect  = { label -> onChangeType(currentDoc, label); showMoveSheet = false },
            onDismiss = { showMoveSheet = false }
        )
    if (showDeleteDialog && !isAadhaarGroupedDoc && onDelete != null && currentDoc != null)
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(18.dp),
            title = { Text("Delete Document", color = Ink, fontWeight = FontWeight.Bold) },
            text  = { Text("Delete \"$documentName\"? This cannot be undone.", color = InkMid, fontSize = 14.sp) },
            confirmButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DangerRed.copy(0.10f))
                        .border(1.dp, DangerRed.copy(0.30f), RoundedCornerShape(10.dp))
                        .clickable { showDeleteDialog = false; onDelete(currentDoc) }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) { Text("Delete", color = DangerRed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            },
            dismissButton = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgSurface)
                        .border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
                        .clickable { showDeleteDialog = false }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) { Text("Cancel", color = InkMid, fontSize = 14.sp) }
            }
        )

    // ══════════════════════════════════════════════════════════════════════
    // MAIN LAYOUT
    // ══════════════════════════════════════════════════════════════════════
    Box(Modifier.fillMaxSize().background(BgBase)) {
        Column(Modifier.fillMaxSize()) {

            // Status bar
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility)
                    .background(Coral)
            )

            // ── Top bar ───────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().background(BgBase)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            currentDoc?.name ?: documentName,
                            color      = Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Text(
                            when {
                                galleryDocs.size > 1 -> "${pagerState.currentPage + 1} of ${galleryDocs.size}"
                                totalPages > 1       -> "Page $currentPage of $totalPages"
                                else                 -> currentDoc?.docClassLabel ?: ""
                            },
                            color    = InkMid,
                            fontSize = 11.sp
                        )
                    }
                    val contentReady = !isLoading && errorMessage == null &&
                            (currentDocumentType == DocumentType.IMAGE ||
                                    (currentDocumentType == DocumentType.PDF && pdfPages.isNotEmpty()))
                    if (contentReady) {
                        val scanning = ocrState == OcrState.LOADING
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (scanning) BgSurface else CoralSoft)
                                .border(1.dp, if (scanning) StrokeLight else Coral.copy(0.25f), RoundedCornerShape(10.dp))
                                .then(if (!scanning) Modifier.pointerInput(Unit) { detectTapGestures { runOcr() } } else Modifier)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (scanning) Row(
                                verticalAlignment    = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(Modifier.size(13.dp), color = Coral, strokeWidth = 2.dp)
                                Text("Reading…", color = InkMid, fontSize = 12.sp)
                            } else Row(
                                verticalAlignment    = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.DocumentScanner, null, tint = Coral, modifier = Modifier.size(15.dp))
                                Text("Extract", color = Coral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    if (shouldShowViewerMenu) {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More", tint = Ink)
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

            // ── Content ───────────────────────────────────────────────
            if (hasDetails) {
                // ── SPLIT VIEW: image + details panel ─────────────────
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(BgBase)
                ) {
                    LaunchedEffect(maxHeight) {
                        expandedImageHeightDp = maxHeight
                    }
                    Column(Modifier.fillMaxSize()) {
                    // Image slot
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(animatedImageHeight)
                            .background(Color(0xFF111111))
                    ) {
                        if (galleryDocs.size > 1) {
                            HorizontalPager(
                                state             = pagerState,
                                modifier          = Modifier.fillMaxSize(),
                                userScrollEnabled = pageScale <= 1.01f
                            ) { page ->
                                val pageUri = galleryDocs.getOrNull(page)?.thumbnailPath?.let(Uri::parse) ?: currentUri
                                ZoomablePagerImage(uri = pageUri, onScaleChange = { pageScale = it })
                            }
                            if (!isImageExpanded) PageBadge(
                                current  = pagerState.currentPage + 1,
                                total    = galleryDocs.size,
                                modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 46.dp)
                            )
                        } else {
                            ZoomableImage(uri = currentUri)
                        }

                        // Fullscreen toggle
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.40f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .clickable {
                                    isImageExpanded = !isImageExpanded
                                    if (!isImageExpanded) imageHeightDp = 220.dp
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = if (isImageExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = null,
                                tint               = Color.White,
                                modifier           = Modifier.size(16.dp)
                            )
                        }

                        // Hint when collapsed
                        if (!isImageExpanded) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "pinch to zoom",
                                    color      = Color.White.copy(alpha = 0.75f),
                                    fontSize   = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Drag handle (hidden when expanded)
                    if (!isImageExpanded) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .background(BgCard)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaDp = with(density) { dragAmount.y.toDp() }
                                        imageHeightDp = (imageHeightDp + deltaDp).coerceIn(56.dp, 420.dp)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(StrokeLight)
                                    .align(Alignment.TopCenter)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                repeat(5) {
                                    Box(Modifier.size(3.dp).clip(CircleShape).background(StrokeMid))
                                }
                            }
                        }
                    }

                    // Details panel
                    if (!isImageExpanded) {
                        LazyColumn(
                            modifier        = Modifier.weight(1f).fillMaxWidth().background(BgBase),
                            contentPadding  = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            item(key = "details_header") {
                                Row(
                                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                    verticalAlignment    = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Extracted details",
                                        color      = Ink,
                                        fontSize   = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(
                                        verticalAlignment    = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        currentDoc?.docClassLabel?.let { label ->
                                            Row(
                                                verticalAlignment    = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(
                                                    Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(TypeColors[label] ?: InkMid)
                                                )
                                                Text(label, color = InkMid, fontSize = 11.sp)
                                            }
                                        }
                                        // Edit button
                                        if (onEditDetails != null && currentDoc != null) {
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(CoralSoft)
                                                    .clickable { onEditDetails(currentDoc) }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    "Edit",
                                                    color      = Coral,
                                                    fontSize   = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            item(key = "details_card") {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(BgCard)
                                        .border(1.dp, StrokeLight, RoundedCornerShape(16.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    detailRows.forEachIndexed { index, item ->
                                        AadhaarDetailRow(
                                            label       = item.label,
                                            value       = item.value,
                                            multiline   = item.multiline,
                                            emphasize   = item.label.equals("Aadhaar", true)
                                                    || item.label.equals("PAN", true),
                                            showDivider = index != detailRows.lastIndex
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }

            } else {
                // ── STANDARD VIEW: PDF / no-details image ─────────────
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(BgBase)
                ) {
                    when {
                        isLoading -> Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = Coral)
                            Text("Rendering PDF…", color = InkMid, fontSize = 13.sp)
                        }

                        errorMessage != null -> Text(
                            errorMessage!!,
                            color    = DangerRed,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        )

                        currentDocumentType == DocumentType.PDF && pdfPages.isNotEmpty() -> {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val ns = (scale * zoom).coerceIn(1f, 5f); scale = ns
                                            if (ns > 1f) {
                                                val mx = (size.width  * (ns - 1f)) / 2f
                                                val my = (size.height * (ns - 1f)) / 2f
                                                offset = Offset(
                                                    (offset.x + pan.x).coerceIn(-mx, mx),
                                                    (offset.y + pan.y).coerceIn(-my, my)
                                                )
                                            } else offset = Offset.Zero
                                        }
                                    }
                                    .pointerInput(Unit) {
                                        detectTapGestures(onDoubleTap = {
                                            if (scale > 1.1f) { scale = 1f; offset = Offset.Zero }
                                            else scale = 2.5f
                                        })
                                    }
                            ) {
                                LazyColumn(
                                    state   = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX       = scale,
                                            scaleY       = scale,
                                            translationX = offset.x,
                                            translationY = offset.y
                                        ),
                                    contentPadding      = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    userScrollEnabled   = scale <= 1.05f
                                ) {
                                    itemsIndexed(pdfPages) { idx, bmp ->
                                        PdfPageItem(bmp, idx + 1, pdfPages.size)
                                    }
                                }
                            }
                            if (pdfPages.size > 1) PageBadge(
                                currentPage, pdfPages.size,
                                Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp)
                            )
                            if (scale > 1.05f) Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BgCard)
                                    .border(1.dp, StrokeLight, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) { Text("${(scale * 100).toInt()}%", color = InkMid, fontSize = 12.sp) }
                        }

                        currentDocumentType == DocumentType.IMAGE && galleryDocs.size > 1 -> {
                            HorizontalPager(
                                state             = pagerState,
                                modifier          = Modifier.fillMaxSize(),
                                userScrollEnabled = pageScale <= 1.01f
                            ) { page ->
                                val pageUri = galleryDocs.getOrNull(page)?.thumbnailPath?.let(Uri::parse) ?: currentUri
                                ZoomablePagerImage(uri = pageUri, onScaleChange = { pageScale = it })
                            }
                            PageBadge(
                                pagerState.currentPage + 1, galleryDocs.size,
                                Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
                                        .background(BgCard.copy(alpha = 0.92f))
                                        .border(1.dp, StrokeLight, CircleShape)
                                        .clickable(enabled = pagerState.currentPage > 0) {
                                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft, null,
                                        tint = if (pagerState.currentPage > 0) Ink else InkDim
                                    )
                                }
                                Box(
                                    Modifier.clip(RoundedCornerShape(12.dp))
                                        .background(BgCard.copy(alpha = 0.92f))
                                        .border(1.dp, StrokeLight, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text("Swipe", color = InkMid, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
                                        .background(BgCard.copy(alpha = 0.92f))
                                        .border(1.dp, StrokeLight, CircleShape)
                                        .clickable(enabled = pagerState.currentPage < galleryDocs.lastIndex) {
                                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                                        tint = if (pagerState.currentPage < galleryDocs.lastIndex) Ink else InkDim
                                    )
                                }
                            }
                        }

                        currentDocumentType == DocumentType.IMAGE -> ZoomableImage(uri = currentUri)

                        else -> Text(
                            "No content to display",
                            color    = InkMid,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // MORE MENU BOTTOM SHEET
    // ══════════════════════════════════════════════════════════════════════
    if (shouldShowViewerMenu && showMoreMenu && currentDoc != null) {
        val currentLabel = currentDoc.docClassLabel ?: "Other"
        ModalBottomSheet(
            onDismissRequest = { showMoreMenu = false },
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
                    .padding(bottom = 16.dp)
            ) {
                // Doc header
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
                        if (currentDoc.thumbnailPath != null)
                            AsyncImage(
                                model              = currentDoc.thumbnailPath,
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize()
                            )
                        else Icon(
                            Icons.Default.Image, null,
                            tint     = InkDim,
                            modifier = Modifier.align(Alignment.Center).size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            currentDoc.name,
                            color      = Ink,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(
                            verticalAlignment    = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(TypeColors[currentLabel] ?: InkMid))
                            Text(currentLabel, color = InkMid, fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)

                // ── Rename ────────────────────────────────────────────
                if (isAadhaarGroupedDoc && onRenameAadhaarPair != null) {
                    ContextAction(icon = Icons.Default.DriveFileRenameOutline, label = "Rename pair", color = Color(0xFF2563EB)) {
                        renamePairText = currentDoc.name.substringBefore("_Aadhaar").replace("_", " ")
                        showMoreMenu = false; showRenamePairDialog = true
                    }
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
                }
                if (!isAadhaarGroupedDoc && onRename != null) {
                    ContextAction(icon = Icons.Default.DriveFileRenameOutline, label = "Rename", color = Color(0xFF2563EB)) {
                        showMoreMenu = false; showRenameDialog = true
                    }
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
                }

                // ── Edit extracted details ─────────────────────────────
                if (onEditDetails != null) {
                    ContextAction(
                        icon  = Icons.Default.CreditCard,
                        label = "Edit extracted details",
                        color = Color(0xFF7C3AED)
                    ) {
                        showMoreMenu = false
                        onEditDetails(currentDoc)
                    }
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
                }

                // ── Move / Change type ─────────────────────────────────
                if (!isAadhaarGroupedDoc && onChangeType != null) {
                    ContextAction(
                        icon     = Icons.Default.DriveFileMove,
                        label    = "Move to folder",
                        color    = Coral,
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = InkDim, modifier = Modifier.size(18.dp)) }
                    ) { showMoreMenu = false; showMoveSheet = true }
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
                    ContextAction(icon = Icons.Default.Label, label = "Change type", color = Color(0xFF7C3AED)) {
                        showMoreMenu = false; showChangeTypeSheet = true
                    }
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
                }

                ContextAction(icon = Icons.Default.Info, label = "Info", color = InkMid) {
                    showMoreMenu = false; showInfoSheet = true
                }

                if (isAadhaarGroupedDoc && onUngroupAadhaarPair != null) {
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
                    ContextAction(icon = Icons.Default.LinkOff, label = "Ungroup pair", color = InkMid) {
                        showMoreMenu = false; onUngroupAadhaarPair(currentDoc)
                    }
                }

                // ── Passport pair / ungroup ────────────────────────────────
                if (isPassportDoc && !isPassportPaired && onPairPassport != null) {
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
                    ContextAction(icon = Icons.Default.Link, label = "Pair with another passport", color = Color(0xFF7C3AED)) {
                        showMoreMenu = false
                        Toast.makeText(context, "Now long-press another passport page to pair", Toast.LENGTH_LONG).show()
                        onPairPassport(currentDoc)
                    }
                }
                if (isPassportDoc && isPassportPaired && onUngroupPassport != null) {
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
                    ContextAction(icon = Icons.Default.LinkOff, label = "Ungroup passport pair", color = InkMid) {
                        showMoreMenu = false; onUngroupPassport(currentDoc)
                    }
                }

                if (currentDoc.isMergedPdf && onUnmerge != null) {
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
                    ContextAction(icon = Icons.AutoMirrored.Filled.CallSplit, label = "Unmerge", color = Color(0xFFE6A23C)) {
                        showMoreMenu = false; onUnmerge(currentDoc)
                    }
                }
                if (!isAadhaarGroupedDoc && onDelete != null) {
                    HorizontalDivider(color = StrokeLight, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
                    ContextAction(icon = Icons.Default.DeleteOutline, label = "Delete", color = DangerRed) {
                        showMoreMenu = false; showDeleteDialog = true
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// PRIVATE COMPOSABLES
// ════════════════════════════════════════════════════════════════════════════

private val SelectBlue = Color(0xFF2E6BE6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveToFolderBottomSheet(
    document  : Document,
    allFolders: List<Folder>,
    onSelect  : (String) -> Unit,
    onDismiss : () -> Unit
) {
    val currentLabel = document.docClassLabel ?: "Other"
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = BgCard,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).width(40.dp).height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(StrokeMid))
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 40.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, null, tint = Ink,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onDismiss() }
                )
                Spacer(Modifier.width(12.dp))
                Text("Move to folder", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = StrokeLight, thickness = 0.5.dp)
            Text("FOLDERS", color = InkDim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            allFolders.forEach { folder ->
                val isCurrent   = folder.docType == currentLabel
                val folderColor = TypeColors[folder.docType] ?: InkMid
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (isCurrent) Modifier.background(folderColor.copy(0.07f)) else Modifier)
                        .clickable { if (!isCurrent) onSelect(folder.docType) else onDismiss() }
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(folderColor.copy(0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Text(folder.icon, fontSize = 17.sp) }
                    Text(
                        folder.name,
                        color      = if (isCurrent) folderColor else Ink,
                        fontSize   = 15.sp,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        modifier   = Modifier.weight(1f)
                    )
                    if (isCurrent) Box(
                        Modifier.size(22.dp).clip(CircleShape).background(folderColor.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Check, null, tint = folderColor, modifier = Modifier.size(13.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeTypeBottomSheet(
    currentLabel: String?,
    onSelect    : (String) -> Unit,
    onDismiss   : () -> Unit
) {
    val types = listOf(
        DocClassType.AADHAAR_BACK, DocClassType.AADHAAR_FRONT,
        DocClassType.PAN, DocClassType.VOTER_ID, DocClassType.PASSPORT, DocClassType.OTHER
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = BgCard,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 36.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp)).background(StrokeMid))
        }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Change Type", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Select the correct document type", color = InkMid, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            types.forEach { docType ->
                val isSelected = currentLabel == docType.displayName
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) SelectBlue.copy(0.08f) else BgSurface)
                        .border(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) SelectBlue.copy(0.40f) else StrokeLight,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelect(docType.displayName) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(20.dp).clip(CircleShape)
                            .border(if (isSelected) 0.dp else 1.5.dp, if (isSelected) Color.Transparent else StrokeMid, CircleShape)
                            .background(if (isSelected) SelectBlue else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    Text(
                        docType.displayName,
                        color      = if (isSelected) SelectBlue else Ink,
                        fontSize   = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        shape            = RoundedCornerShape(18.dp),
        title = { Text("Rename Document", color = Ink, fontWeight = FontWeight.Bold) },
        text  = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Coral,
                    unfocusedBorderColor = StrokeLight,
                    cursorColor          = Coral,
                    focusedTextColor     = Ink,
                    unfocusedTextColor   = Ink
                ),
                shape = RoundedCornerShape(10.dp)
            )
        },
        confirmButton = {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CoralSoft)
                    .border(1.dp, Coral.copy(0.25f), RoundedCornerShape(10.dp))
                    .clickable(enabled = name.isNotBlank() && name != currentName) { onConfirm(name.trim()) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    "Rename",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (name.isNotBlank() && name != currentName) Coral else InkDim
                )
            }
        },
        dismissButton = {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgSurface)
                    .border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) { Text("Cancel", color = InkMid, fontSize = 14.sp) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoBottomSheet(
    documentName: String,
    documentType: DocumentType,
    document    : Document?,
    totalPages  : Int,
    uri         : String,
    onDismiss   : () -> Unit
) {
    val context    = LocalContext.current
    val fileSize   = remember(uri) { getFileSize(context, uri) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy  •  hh:mm a", Locale.getDefault()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = BgCard,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 36.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp)).background(StrokeMid))
        }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Document Info", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            InfoRow("Name", documentName)
            InfoRow("Type", if (documentType == DocumentType.PDF) "PDF Document" else "Image")
            InfoRow("Pages", "$totalPages")
            fileSize?.let { InfoRow("Size", it) }
            document?.let { doc ->
                InfoRow("Created", dateFormat.format(Date(doc.createdAt)))
                doc.docClassLabel?.let { label ->
                    if (label != "Document") InfoRow("Classification", label)
                }
                if (doc.isMergedPdf) InfoRow("Merged from", "${doc.sourceDocumentIds.size} documents")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgSurface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, color = InkMid, fontSize = 13.sp)
        Text(value, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrBottomSheet(
    state    : OcrState,
    text     : String,
    error    : String,
    onDismiss: () -> Unit,
    onCopy   : () -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = BgCard,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 36.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp)).background(StrokeMid))
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text("Extracted Text", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        when (state) {
                            OcrState.LOADING -> "Scanning all pages…"
                            OcrState.SUCCESS -> "${text.length} characters"
                            OcrState.ERROR   -> "Failed to read"
                            OcrState.IDLE    -> ""
                        }, color = InkMid, fontSize = 12.sp
                    )
                }
                if (state == OcrState.SUCCESS && text.isNotBlank()) Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (copied) GreenAccent.copy(0.10f) else CoralSoft)
                        .border(1.dp, if (copied) GreenAccent.copy(0.30f) else Coral.copy(0.25f), RoundedCornerShape(9.dp))
                        .pointerInput(Unit) { detectTapGestures { onCopy(); copied = true } }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(
                            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            null, Modifier.size(14.dp),
                            tint = if (copied) GreenAccent else Coral
                        )
                        Text(
                            if (copied) "Copied!" else "Copy",
                            color = if (copied) GreenAccent else Coral,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                state,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "ocr"
            ) { s ->
                when (s) {
                    OcrState.LOADING -> Box(
                        Modifier.fillMaxWidth().height(160.dp)
                            .clip(RoundedCornerShape(12.dp)).background(BgSurface)
                            .border(1.dp, StrokeLight, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(28.dp), color = Coral, strokeWidth = 2.5.dp)
                            Text("Reading all pages…", color = InkMid, fontSize = 13.sp)
                        }
                    }
                    OcrState.ERROR -> Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)).background(DangerRed.copy(0.08f))
                            .border(1.dp, DangerRed.copy(0.20f), RoundedCornerShape(12.dp)).padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, Modifier.size(20.dp), tint = DangerRed)
                        Text("Error: $error", color = DangerRed, fontSize = 13.sp)
                    }
                    OcrState.SUCCESS -> Box(
                        Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 360.dp)
                            .clip(RoundedCornerShape(12.dp)).background(BgSurface)
                            .border(1.dp, StrokeLight, RoundedCornerShape(12.dp)).padding(16.dp)
                    ) {
                        Text(
                            text, color = Ink, fontSize = 14.sp, lineHeight = 22.sp,
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        )
                    }
                    OcrState.IDLE -> Unit
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(bitmap: Bitmap, pageNumber: Int, totalPages: Int) {
    Box(
        Modifier.fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(10.dp), ambientColor = Color(0x12000000), spotColor = Color(0x12000000))
            .clip(RoundedCornerShape(10.dp)).background(Color.White)
            .border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
    ) {
        Image(
            bitmap           = bitmap.asImageBitmap(),
            contentDescription = "Page $pageNumber",
            contentScale     = ContentScale.FillWidth,
            modifier         = Modifier.fillMaxWidth()
        )
        Box(
            Modifier.align(Alignment.BottomEnd).padding(8.dp)
                .clip(RoundedCornerShape(6.dp)).background(Ink.copy(0.78f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) { Text("$pageNumber / $totalPages", color = Color.White, fontSize = 11.sp) }
    }
}

@Composable
private fun ZoomableImage(uri: Uri) {
    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val ns = (scale * zoom).coerceIn(1f, 5f); scale = ns
                    if (ns > 1f) {
                        val mx = (size.width  * (ns - 1f)) / 2f
                        val my = (size.height * (ns - 1f)) / 2f
                        offset = Offset(
                            (offset.x + pan.x).coerceIn(-mx, mx),
                            (offset.y + pan.y).coerceIn(-my, my)
                        )
                    } else offset = Offset.Zero
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1.1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model              = uri,
            contentDescription = null,
            contentScale       = ContentScale.Fit,
            modifier           = Modifier.fillMaxSize().graphicsLayer(
                scaleX       = scale, scaleY = scale,
                translationX = offset.x, translationY = offset.y
            )
        )
        if (scale > 1.05f) Box(
            Modifier.align(Alignment.BottomStart).padding(12.dp)
                .clip(RoundedCornerShape(8.dp)).background(BgCard)
                .border(1.dp, StrokeLight, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) { Text("${(scale * 100).toInt()}%", color = InkMid, fontSize = 12.sp) }
    }
}

/**
 * A page composable for use inside [HorizontalPager] that supports
 * pinch-to-zoom and double-tap zoom without conflicting with the pager's
 * horizontal swipe gesture.
 *
 * Conflict resolution strategy (using [awaitEachGesture]):
 *  - 2+ fingers (pinch)         → consume events, handle zoom/pan
 *  - 1 finger  + scale > 1      → consume events, pan the zoomed image
 *  - 1 finger  + scale == 1     → do NOT consume, let HorizontalPager swipe
 *
 * [onScaleChange] lets the parent set [HorizontalPager.userScrollEnabled]
 * to false while the image is zoomed in, providing an extra safety net.
 */
@Composable
private fun ZoomablePagerImage(uri: Uri, onScaleChange: (Float) -> Unit = {}) {
    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            // Double-tap: toggle 1× ↔ 2.5×
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1.1f) {
                        scale = 1f; offset = Offset.Zero; onScaleChange(1f)
                    } else {
                        scale = 2.5f; onScaleChange(2.5f)
                    }
                })
            }
            // Pinch-to-zoom + pan — only consumes when needed
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event        = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        val zoom         = event.calculateZoom()
                        val pan          = event.calculatePan()
                        val cur          = scale

                        when {
                            pointerCount >= 2 -> {
                                // Pinch: update zoom, pan during pinch
                                val ns = (cur * zoom).coerceIn(1f, 5f)
                                scale = ns
                                onScaleChange(ns)
                                if (ns > 1f) {
                                    val mx = (size.width  * (ns - 1f)) / 2f
                                    val my = (size.height * (ns - 1f)) / 2f
                                    offset = Offset(
                                        (offset.x + pan.x).coerceIn(-mx, mx),
                                        (offset.y + pan.y).coerceIn(-my, my)
                                    )
                                } else {
                                    offset = Offset.Zero
                                    onScaleChange(1f)
                                }
                                event.changes.forEach { it.consume() }
                            }
                            pointerCount == 1 && cur > 1f -> {
                                // Single-finger pan while zoomed in
                                val mx = (size.width  * (cur - 1f)) / 2f
                                val my = (size.height * (cur - 1f)) / 2f
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-mx, mx),
                                    (offset.y + pan.y).coerceIn(-my, my)
                                )
                                event.changes.forEach { it.consume() }
                            }
                            // Single-finger + scale == 1: don't consume →
                            // HorizontalPager receives the swipe normally.
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model              = uri,
            contentDescription = null,
            contentScale       = ContentScale.Fit,
            modifier           = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX       = scale,
                    scaleY       = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
        if (scale > 1.05f) Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BgCard)
                .border(1.dp, StrokeLight, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) { Text("${(scale * 100).toInt()}%", color = InkMid, fontSize = 12.sp) }
    }
}

@Composable
private fun PageBadge(current: Int, total: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp)).background(BgCard)
            .border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) { Text("$current / $total", color = InkMid, fontSize = 12.sp) }
}

private fun getFileSize(context: Context, uriString: String): String? {
    return try {
        val size = if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
            val u = Uri.parse(uriString)
            context.contentResolver.openFileDescriptor(u, "r")?.use { it.statSize } ?: return null
        } else {
            val f = File(uriString); if (f.exists()) f.length() else return null
        }
        when {
            size < 1024            -> "$size B"
            size < 1024 * 1024     -> "${size / 1024} KB"
            else                   -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
        }
    } catch (_: Exception) { null }
}

private suspend fun renderPdfPages(context: Context, uri: Uri): List<Bitmap> =
    withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val fd       = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext bitmaps
            val renderer = PdfRenderer(fd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bmp  = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close(); bitmaps.add(bmp)
            }
            renderer.close(); fd.close()
        } catch (e: Exception) { e.printStackTrace() }
        bitmaps
    }

private suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val loader = coil.ImageLoader(context)
            val req    = ImageRequest.Builder(context).data(uri).allowHardware(false).build()
            (loader.execute(req).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        } catch (e: Exception) { null }
    }
