package com.example.docscanner.presentation.camera

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    isSaving: Boolean,
    onImportPages: (List<Uri>) -> Unit,
    onBack: () -> Unit
) {
    val context  = LocalContext.current
    val activity = context.findActivity()

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ── ML Kit scanner result handler ─────────────────────────────────────────
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        DocumentScanner.handleResult(
            resultCode  = result.resultCode,
            data        = result.data,
            onSuccess   = { pageUris, _ -> onImportPages(pageUris) },
            onCancelled = { onBack() },
            onError     = { e -> errorMessage = "Scanner error: ${e.message}" }
        )
    }

    // ── Gallery picker ────────────────────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) onImportPages(uris) }

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
    // UI host for the external scanner
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
            IconButton(onClick = onBack, enabled = !isSaving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                "Scan Document",
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium
            )
            IconButton(
                onClick = { galleryLauncher.launch("image/*") },
                enabled = !isSaving
            ) {
                Icon(Icons.Default.PhotoLibrary, "Gallery", tint = Color.White)
            }
        }

        // ── CENTRE — calm host state while scanner finishes up ────────────────
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF111111)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(0.06f))
                        .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Scanner opens automatically",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Capture pages or import from gallery when needed.",
                    color = Color.White.copy(0.65f),
                    fontSize = 13.sp
                )
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
