package com.example.docscanner.presentation.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.docscanner.data.export.DocumentExporter
import com.example.docscanner.domain.model.ExportFormat
import com.example.docscanner.domain.model.ScannedPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ExportState { CHOOSING, EXPORTING, SENDING, SUCCESS, ERROR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    pages: List<ScannedPage>,
    exporter: DocumentExporter,
    fileName: String,
    onFileNameChanged: (String) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var exportState by remember { mutableStateOf(ExportState.CHOOSING) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }
    var exportedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var errorMessage by remember { mutableStateOf("") }
    var apiResponse by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Export & Send") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (exportState) {

                ExportState.CHOOSING -> {
                    Text("Export & Send", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${pages.size} ${if (pages.size == 1) "page" else "pages"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = fileName,
                        onValueChange = onFileNameChanged,
                        label = { Text("File name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))

                    FormatCard(
                        icon = Icons.Default.PictureAsPdf,
                        title = "PDF Document",
                        subtitle = "All pages in one file",
                        isSelected = selectedFormat == ExportFormat.PDF,
                        onClick = { selectedFormat = ExportFormat.PDF }
                    )
                    Spacer(Modifier.height(10.dp))
                    FormatCard(
                        icon = Icons.Default.Image,
                        title = "JPEG Images",
                        subtitle = "One image per page",
                        isSelected = selectedFormat == ExportFormat.JPEG,
                        onClick = { selectedFormat = ExportFormat.JPEG }
                    )
                    Spacer(Modifier.height(10.dp))
                    FormatCard(
                        icon = Icons.Default.Image,
                        title = "PNG Images",
                        subtitle = "Lossless quality",
                        isSelected = selectedFormat == ExportFormat.PNG,
                        onClick = { selectedFormat = ExportFormat.PNG }
                    )
                    Spacer(Modifier.height(28.dp))

                    Button(
                        onClick = {
                            exportState = ExportState.EXPORTING
                            scope.launch {
                                try {
                                    // Step 1: Export file
                                    val uris = when (selectedFormat) {
                                        ExportFormat.PDF -> {
                                            val uri = exporter.exportToPdf(pages, fileName)
                                            if (uri != null) listOf(uri) else emptyList()
                                        }
                                        else -> exporter.exportAllAsImages(pages, fileName, selectedFormat)
                                    }

                                    if (uris.isEmpty()) {
                                        errorMessage = "Export failed"
                                        exportState = ExportState.ERROR
                                        return@launch
                                    }

                                    exportedUris = uris

                                    // Step 2: Send to API
                                    exportState = ExportState.SENDING
                                    apiResponse = sendToApi(
                                        uris = uris,
                                        fileName = fileName,
                                        format = selectedFormat,
                                        pageCount = pages.size
                                    )

                                    exportState = ExportState.SUCCESS

                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Unknown error"
                                    exportState = ExportState.ERROR
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Export & Send") }
                }

                ExportState.EXPORTING -> {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(Modifier.size(64.dp), strokeWidth = 4.dp)
                    Spacer(Modifier.height(24.dp))
                    Text("Exporting...", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Saving as ${selectedFormat.displayName}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                }

                ExportState.SENDING -> {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(Modifier.size(64.dp), strokeWidth = 4.dp)
                    Spacer(Modifier.height(24.dp))
                    Text("Sending to server...", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Uploading ${selectedFormat.displayName}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                }

                ExportState.SUCCESS -> {
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.CheckCircle, null,
                        Modifier.size(72.dp), tint = Color(0xFF34A853))
                    Spacer(Modifier.height(24.dp))
                    Text("Done!", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Exported & sent successfully",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Show dummy API response
                    if (apiResponse.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Server Response",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text(apiResponse,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = { shareFiles(context, exportedUris, selectedFormat) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.padding(end = 8.dp))
                        Text("Share")
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text("Done")
                    }
                    Spacer(Modifier.weight(1f))
                }

                ExportState.ERROR -> {
                    Spacer(Modifier.weight(1f))
                    Text("Failed", style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { exportState = ExportState.CHOOSING },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Try Again") }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Dummy API Call ─────────────────────────────────────────
// Replace this with your real API call later
private suspend fun sendToApi(
    uris: List<Uri>,
    fileName: String,
    format: ExportFormat,
    pageCount: Int
): String {
    // Simulate network delay
    delay(1500)

    // TODO: Replace with real API call
    // e.g. val response = apiService.uploadDocument(...)

    return """
        status: success
        file: $fileName.${format.extension}
        pages: $pageCount
        uploaded: ${uris.size} file(s)
        id: DOC_${System.currentTimeMillis()}
    """.trimIndent()
}

@Composable
private fun FormatCard(
    icon: ImageVector, title: String, subtitle: String,
    isSelected: Boolean, onClick: () -> Unit
) {
    val border = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val bg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, border, RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun shareFiles(context: Context, uris: List<Uri>, format: ExportFormat) {
    if (uris.isEmpty()) return
    val mimeType = when (format) {
        ExportFormat.PDF  -> "application/pdf"
        ExportFormat.JPEG -> "image/jpeg"
        ExportFormat.PNG  -> "image/png"
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uris.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}