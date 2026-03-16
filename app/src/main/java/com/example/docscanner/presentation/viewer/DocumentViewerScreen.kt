package com.example.docscanner.presentation.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.example.docscanner.presentation.alldocuments.BgBase
import com.example.docscanner.presentation.alldocuments.BgCard
import com.example.docscanner.presentation.alldocuments.BgSurface
import com.example.docscanner.presentation.alldocuments.Coral
import com.example.docscanner.presentation.alldocuments.CoralSoft
import com.example.docscanner.presentation.alldocuments.DangerRed
import com.example.docscanner.presentation.alldocuments.GreenAccent
import com.example.docscanner.presentation.alldocuments.Ink
import com.example.docscanner.presentation.alldocuments.InkDim
import com.example.docscanner.presentation.alldocuments.InkMid
import com.example.docscanner.presentation.alldocuments.StrokeLight
import com.example.docscanner.presentation.alldocuments.StrokeMid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DocumentType { PDF, IMAGE }
private enum class OcrState { IDLE, LOADING, SUCCESS, ERROR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    documentName: String,
    documentUri : String,
    documentType: DocumentType,
    onBack      : () -> Unit
) {
    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()
    val uri       = Uri.parse(documentUri)
    val ocrHelper = remember { MlKitOcrHelper(context) }

    var pdfPages     by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading    by remember { mutableStateOf(documentType == DocumentType.PDF) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(documentUri) {
        if (documentType == DocumentType.PDF) {
            isLoading = true
            try { pdfPages = renderPdfPages(context, uri) }
            catch (e: Exception) { errorMessage = "Cannot open PDF: ${e.message}" }
            isLoading = false
        }
    }

    val listState   = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    val totalPages  = if (documentType == DocumentType.PDF) pdfPages.size else 1

    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var ocrState     by remember { mutableStateOf(OcrState.IDLE) }
    var ocrText      by remember { mutableStateOf("") }
    var ocrError     by remember { mutableStateOf("") }
    var showOcrSheet by remember { mutableStateOf(false) }

    fun runOcrOnCurrentPage() {
        showOcrSheet = true
        ocrState     = OcrState.LOADING
        ocrText      = ""; ocrError = ""
        scope.launch {
            val bitmap: Bitmap? = when (documentType) {
                DocumentType.PDF   -> pdfPages.getOrNull(currentPage - 1)
                DocumentType.IMAGE -> loadBitmapFromUri(context, uri)
            }
            if (bitmap == null) { ocrError = "Could not load page image."; ocrState = OcrState.ERROR; return@launch }
            ocrHelper.extractText(bitmap)
                .onSuccess { t -> ocrText = t.ifBlank { "No text found on this page." }; ocrState = OcrState.SUCCESS }
                .onFailure { e -> ocrError = e.message ?: "Unknown error"; ocrState = OcrState.ERROR }
        }
    }

    if (showOcrSheet) {
        OcrBottomSheet(
            state     = ocrState,
            text      = ocrText,
            error     = ocrError,
            onDismiss = { showOcrSheet = false },
            onCopy    = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Extracted Text", ocrText))
            }
        )
    }

    Box(Modifier.fillMaxSize().background(BgBase)) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().background(BgBase).statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(documentName, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (totalPages > 1) Text("Page $currentPage of $totalPages", color = InkMid, fontSize = 11.sp)
                    }

                    val contentReady = !isLoading && errorMessage == null &&
                            (documentType == DocumentType.IMAGE || (documentType == DocumentType.PDF && pdfPages.isNotEmpty()))
                    if (contentReady) {
                        val scanning = ocrState == OcrState.LOADING
                        Box(
                            Modifier.padding(end = 10.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (scanning) BgSurface else CoralSoft)
                                .border(1.dp, if (scanning) StrokeLight else Coral.copy(0.25f), RoundedCornerShape(10.dp))
                                .then(if (!scanning) Modifier.pointerInput(Unit) { detectTapGestures { runOcrOnCurrentPage() } } else Modifier)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (scanning) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CircularProgressIndicator(Modifier.size(13.dp), color = Coral, strokeWidth = 2.dp)
                                    Text("Reading…", color = InkMid, fontSize = 12.sp)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Icon(Icons.Default.DocumentScanner, null, tint = Coral, modifier = Modifier.size(15.dp))
                                    Text("Extract Text", color = Coral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(StrokeLight))
            }

            // ── Content ───────────────────────────────────────────────────────
            Box(Modifier.weight(1f).fillMaxWidth().background(BgBase)) {
                when {
                    isLoading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = Coral)
                        Text("Rendering PDF…", color = InkMid, fontSize = 13.sp)
                    }

                    errorMessage != null -> Text(errorMessage!!, color = DangerRed, modifier = Modifier.align(Alignment.Center).padding(24.dp))

                    documentType == DocumentType.PDF && pdfPages.isNotEmpty() -> {
                        Box(
                            Modifier.fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                                        scale = newScale
                                        if (newScale > 1f) {
                                            val maxX = (size.width * (newScale - 1f)) / 2f
                                            val maxY = (size.height * (newScale - 1f)) / 2f
                                            offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                                        } else offset = Offset.Zero
                                    }
                                }
                                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1.1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) }
                        ) {
                            LazyColumn(
                                state               = listState,
                                modifier            = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                                contentPadding      = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                userScrollEnabled   = scale <= 1.05f
                            ) {
                                itemsIndexed(pdfPages) { idx, bmp -> PdfPageItem(bmp, idx + 1, pdfPages.size) }
                            }
                        }
                        if (pdfPages.size > 1) PageBadge(currentPage, pdfPages.size, Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp))
                        if (scale > 1.05f) {
                            Box(Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(BgCard).border(1.dp, StrokeLight, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                Text("${(scale * 100).toInt()}%", color = InkMid, fontSize = 12.sp)
                            }
                        }
                    }

                    documentType == DocumentType.IMAGE -> ZoomableImage(uri = uri)

                    else -> Text("No content to display", color = InkMid, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

// ── OCR sheet ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrBottomSheet(state: OcrState, text: String, error: String, onDismiss: () -> Unit, onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = BgCard,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 36.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(StrokeMid)) }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Extracted Text", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(when (state) { OcrState.LOADING -> "Scanning page…"; OcrState.SUCCESS -> "${text.length} characters"; OcrState.ERROR -> "Failed to read"; OcrState.IDLE -> "" }, color = InkMid, fontSize = 12.sp)
                }
                if (state == OcrState.SUCCESS && text.isNotBlank()) {
                    Box(
                        Modifier.clip(RoundedCornerShape(9.dp)).background(if (copied) GreenAccent.copy(0.10f) else CoralSoft)
                            .border(1.dp, if (copied) GreenAccent.copy(0.30f) else Coral.copy(0.25f), RoundedCornerShape(9.dp))
                            .pointerInput(Unit) { detectTapGestures { onCopy(); copied = true } }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null, Modifier.size(14.dp), tint = if (copied) GreenAccent else Coral)
                            Text(if (copied) "Copied!" else "Copy", color = if (copied) GreenAccent else Coral, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            AnimatedContent(state, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "ocr") { s ->
                when (s) {
                    OcrState.LOADING -> Box(
                        Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(BgSurface).border(1.dp, StrokeLight, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(28.dp), color = Coral, strokeWidth = 2.5.dp)
                            Text("Reading all text on page…", color = InkMid, fontSize = 13.sp)
                        }
                    }
                    OcrState.ERROR -> Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DangerRed.copy(0.08f)).border(1.dp, DangerRed.copy(0.20f), RoundedCornerShape(12.dp)).padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, Modifier.size(20.dp), tint = DangerRed)
                        Text("Error: $error", color = DangerRed, fontSize = 13.sp)
                    }
                    OcrState.SUCCESS -> Box(
                        Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 360.dp)
                            .clip(RoundedCornerShape(12.dp)).background(BgSurface).border(1.dp, StrokeLight, RoundedCornerShape(12.dp)).padding(16.dp)
                    ) {
                        Text(text, color = Ink, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
                    }
                    OcrState.IDLE -> Unit
                }
            }
        }
    }
}

// ── PDF page ──────────────────────────────────────────────────────────────────

@Composable
private fun PdfPageItem(bitmap: Bitmap, pageNumber: Int, totalPages: Int) {
    Box(
        Modifier.fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(10.dp), ambientColor = Color(0x12000000), spotColor = Color(0x12000000))
            .clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, StrokeLight, RoundedCornerShape(10.dp))
    ) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Page $pageNumber", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
        Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Ink.copy(0.78f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text("$pageNumber / $totalPages", color = Color.White, fontSize = 11.sp)
        }
    }
}

// ── Zoomable image ────────────────────────────────────────────────────────────

@Composable
private fun ZoomableImage(uri: Uri) {
    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    if (newScale > 1f) {
                        val maxX = (size.width * (newScale - 1f)) / 2f
                        val maxY = (size.height * (newScale - 1f)) / 2f
                        offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                    } else offset = Offset.Zero
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1.1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = uri, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
        )
        if (scale > 1.05f) {
            Box(Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(BgCard).border(1.dp, StrokeLight, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text("${(scale * 100).toInt()}%", color = InkMid, fontSize = 12.sp)
            }
        }
    }
}

// ── Page badge ────────────────────────────────────────────────────────────────

@Composable
private fun PageBadge(current: Int, total: Int, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(BgCard).border(1.dp, StrokeLight, RoundedCornerShape(10.dp)).padding(horizontal = 11.dp, vertical = 5.dp)) {
        Text("$current / $total", color = InkMid, fontSize = 12.sp)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private suspend fun renderPdfPages(context: Context, uri: Uri): List<Bitmap> = withContext(Dispatchers.IO) {
    val bitmaps = mutableListOf<Bitmap>()
    try {
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext bitmaps
        val renderer = PdfRenderer(fd)
        for (i in 0 until renderer.pageCount) {
            val page   = renderer.openPage(i)
            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close(); bitmaps.add(bitmap)
        }
        renderer.close(); fd.close()
    } catch (e: Exception) { e.printStackTrace() }
    bitmaps
}

private suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val loader = coil.ImageLoader(context)
        val req    = ImageRequest.Builder(context).data(uri).allowHardware(false).build()
        (loader.execute(req).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
    } catch (e: Exception) { null }
}