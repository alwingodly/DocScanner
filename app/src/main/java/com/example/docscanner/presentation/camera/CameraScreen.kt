package com.example.docscanner.presentation.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.docscanner.data.camera.DocumentScanner
import com.example.docscanner.domain.model.DocumentCorners
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    pageCount: Int,
    lastCapturedBitmap: Bitmap?,
    isSaving: Boolean,
    onPhotoCaptured: (Bitmap, DocumentCorners?) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val activity = context.findActivity()

    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ── ML Kit scanner result handler ─────────────────────────────────────────
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        DocumentScanner.handleResult(
            resultCode  = result.resultCode,
            data        = result.data,
            onSuccess   = { pageUris, _ ->
                scope.launch {
                    isProcessing = true
                    pageUris.forEach { uri ->
                        uriToBitmap(context, uri)?.let { onPhotoCaptured(it, null) }
                    }
                    isProcessing = false
                    // Trigger auto-save (navigation handled by NavHost)
                    onDone()
                }
            },
            onCancelled = { onBack() },
            onError     = { e -> errorMessage = "Scanner error: ${e.message}" }
        )
    }

    // ── Gallery picker ────────────────────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        scope.launch {
            isProcessing = true
            uris.forEach { uri ->
                uriToBitmap(context, uri)?.let { onPhotoCaptured(it, null) }
            }
            isProcessing = false
            onDone()
        }
    }

    // ── Open ML Kit scanner immediately on screen entry ───────────────────────
    LaunchedEffect(Unit) {
        if (activity != null) {
            DocumentScanner.launch(
                activity  = activity,
                launcher  = scannerLauncher,
                pageLimit = 20,
                onError   = { e -> errorMessage = "Could not start scanner: ${e.message}" }
            )
        } else {
            errorMessage = "Could not find Activity"
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UI — visible while processing / saving scanned pages
    // ══════════════════════════════════════════════════════════════════════════

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {

        // ── TOP BAR ──────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color.Black)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack, enabled = !isSaving && !isProcessing) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                when {
                    isSaving -> "Saving…"
                    pageCount == 0 -> "Scan Document"
                    else -> "$pageCount ${if (pageCount == 1) "page" else "pages"}"
                },
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium
            )
            IconButton(
                onClick = { galleryLauncher.launch("image/*") },
                enabled = !isSaving && !isProcessing
            ) {
                Icon(Icons.Default.PhotoLibrary, "Gallery", tint = Color.White)
            }
        }

        // ── CENTRE — spinner while processing or saving ───────────────────────
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF111111)),
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing || isSaving) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (isSaving) "Saving documents…" else "Processing pages…",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            errorMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = { errorMessage = null }) {
                            Text("Dismiss", color = Color.White)
                        }
                    }
                ) { Text(msg) }
            }
        }

        // ── BOTTOM BAR ────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Thumbnail
            Box(Modifier.size(width = 50.dp, height = 64.dp), contentAlignment = Alignment.Center) {
                if (lastCapturedBitmap != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, Color.White.copy(0.7f), RoundedCornerShape(8.dp))
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val bmp = lastCapturedBitmap
                            val s = minOf(size.width / bmp.width, size.height / bmp.height)
                            val w = bmp.width * s; val h = bmp.height * s
                            val x = (size.width - w) / 2f; val y = (size.height - h) / 2f
                            drawContext.canvas.nativeCanvas.drawBitmap(
                                bmp, null, android.graphics.RectF(x, y, x + w, y + h), null
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.TopEnd).padding(2.dp).size(16.dp)
                                .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$pageCount", color = Color.White,
                                fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Spacer to balance layout (Done button removed — save is automatic)
            Spacer(Modifier.size(width = 50.dp, height = 64.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// HELPERS
// ══════════════════════════════════════════════════════════════════════════════

private suspend fun uriToBitmap(context: Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream) ?: return@withContext null
                val exif = androidx.exifinterface.media.ExifInterface(
                    context.contentResolver.openInputStream(uri)!!
                )
                val rotation = when (
                    exif.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    )
                ) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (rotation != 0f) Bitmap.createBitmap(
                    bmp, 0, 0, bmp.width, bmp.height,
                    Matrix().apply { postRotate(rotation) }, true
                ) else bmp
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

private fun Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}