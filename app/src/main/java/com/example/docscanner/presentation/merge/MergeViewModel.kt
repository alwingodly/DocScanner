package com.example.docscanner.presentation.merge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.data.export.DocumentExporter
import com.example.docscanner.domain.model.Document
import com.example.docscanner.domain.model.ExportFormat
import com.example.docscanner.domain.model.ScannedPage
import com.example.docscanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class MergeItem(
    val documentId: String,
    val name: String,
    val bitmap: Bitmap? = null,
    val isLoading: Boolean = true
)

data class MergeState(
    val items: List<MergeItem> = emptyList(),
    val isLoading: Boolean = true,
    val isMerging: Boolean = false,
    val mergeSuccess: Boolean = false,
    val folderId: String = "",
    val categoryLabel: String = "Document"
)

@HiltViewModel
class MergeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val exporter: DocumentExporter,
    private val documentRepository: DocumentRepository,
    private val ocrHelper: com.example.docscanner.data.ocr.MlKitOcrHelper
) : ViewModel() {

    private val _state = MutableStateFlow(MergeState())
    val state: StateFlow<MergeState> = _state.asStateFlow()

    fun loadDocuments(documents: List<Document>, folderId: String) {
        // Determine shared category from source documents
        val labels = documents.map { it.docClassLabel ?: "Document" }.toSet()
        val category = if (labels.size == 1) labels.first() else "Document"

        _state.value = MergeState(
            items = documents.map { MergeItem(documentId = it.id, name = it.name) },
            isLoading = true,
            folderId = folderId,
            categoryLabel = category
        )

        viewModelScope.launch {
            val loaded = documents.map { doc ->
                val bitmap = doc.thumbnailPath?.let { path -> loadBitmap(path) }
                MergeItem(
                    documentId = doc.id,
                    name = doc.name,
                    bitmap = bitmap,
                    isLoading = false
                )
            }
            _state.value = _state.value.copy(items = loaded, isLoading = false)
        }
    }

    fun onReorder(fromIndex: Int, toIndex: Int) {
        val items = _state.value.items.toMutableList()
        if (fromIndex in items.indices && toIndex in items.indices && fromIndex != toIndex) {
            val item = items.removeAt(fromIndex)
            items.add(toIndex, item)
            _state.value = _state.value.copy(items = items)
        }
    }

    fun onRemoveItem(index: Int) {
        val items = _state.value.items.toMutableList()
        if (index in items.indices) {
            items.removeAt(index)
            _state.value = _state.value.copy(items = items)
        }
    }

    /**
     * Merge selected documents into a single PDF.
     * - Source image documents are marked as hidden (isMergedSource = true)
     * - The merged PDF stores the source IDs for later unmerge
     */
    fun onMerge() {
        val currentState = _state.value
        val items = currentState.items.filter { it.bitmap != null }
        if (items.isEmpty()) return

        _state.value = currentState.copy(isMerging = true)

        viewModelScope.launch {
            try {
                val timestamp = System.currentTimeMillis()

                // OCR first page for smart naming
                val firstBitmap = items.first().bitmap!!
                val docType =
                    com.example.docscanner.domain.model.DocClassType.entries.find { it.displayName == currentState.categoryLabel }
                        ?: com.example.docscanner.domain.model.DocClassType.OTHER
                val ocrName = try {
                    val fields = ocrHelper.extractFields(firstBitmap, docType)
                    val parts = mutableListOf<String>()
                    fields.name?.let { parts.add(it) }
                    parts.add(currentState.categoryLabel.replace(" ", "_"))
                    fields.idNumber?.let { parts.add(it.replace("\\s".toRegex(), "").takeLast(6)) }
                    if (parts.size > 1) parts.joinToString("_") else null
                } catch (_: Exception) {
                    null
                }

                val fileName = ocrName ?: "PDF_${currentState.categoryLabel}_$timestamp"

                // Convert to ScannedPages for the exporter
                val pages = items.mapNotNull { item ->
                    item.bitmap?.let { bmp ->
                        ScannedPage(
                            id = item.documentId,
                            originalBitmap = bmp,
                            croppedBitmap = bmp,
                            enhancedBitmap = bmp
                        )
                    }
                }

                // Export as PDF
                val pdfUri = exporter.exportToPdf(pages, fileName)

                // Create thumbnail from first page
                val thumbnailUri = exporter.exportAsImage(
                    pages.first(),
                    "${fileName}_thumb",
                    ExportFormat.JPEG
                )

                // Collect source document IDs (in merge order)
                val sourceIds = items.map { it.documentId }
                val mergedFromIds = sourceIds.joinToString(",")

                // Save the merged PDF document with source references
                documentRepository.saveDocument(
                    Document(
                        folderId = currentState.folderId,
                        name = fileName,
                        pageCount = pages.size,
                        thumbnailPath = thumbnailUri?.toString(),
                        pdfPath = pdfUri?.toString(),
                        docClassLabel = currentState.categoryLabel,
                        createdAt = System.currentTimeMillis(),
                        mergedFromDocumentIds = mergedFromIds
                    )
                )

                // Hide the source image documents (keep in DB for unmerge)
                documentRepository.markAsMergedSources(sourceIds)

                _state.value = _state.value.copy(isMerging = false, mergeSuccess = true)
            } catch (e: Exception) {
                Log.e("MergeVM", "Merge failed", e)
                _state.value = _state.value.copy(isMerging = false)
            }
        }
    }

    /**
     * Unmerge a merged PDF document.
     * - Restores the hidden source image documents
     * - Deletes the merged PDF document (and its files)
     */
    fun unmerge(mergedDocument: Document) {
        if (!mergedDocument.isMergedPdf) return

        viewModelScope.launch {
            try {
                val sourceIds = mergedDocument.sourceDocumentIds

                // Restore the source image documents (unhide them)
                documentRepository.restoreMergedSources(sourceIds)

                // Delete the merged PDF's files from disk
                mergedDocument.pdfPath?.let { deleteDiskFile(it) }
                mergedDocument.thumbnailPath?.let { deleteDiskFile(it) }

                // Delete the merged PDF document record
                documentRepository.deleteDocument(mergedDocument)
            } catch (e: Exception) {
                Log.e("MergeVM", "Unmerge failed", e)
            }
        }
    }

    fun onMergeNavigated() {
        _state.value = _state.value.copy(mergeSuccess = false)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun loadBitmap(path: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("content://") || path.startsWith("file://")) {
                val uri = android.net.Uri.parse(path)
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                val file = File(path)
                if (file.exists()) BitmapFactory.decodeFile(path) else null
            }
        } catch (e: Exception) {
            Log.e("MergeVM", "Failed to load bitmap: $path", e)
            null
        }
    }

    private suspend fun deleteDiskFile(path: String) = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("content://") || path.startsWith("file://")) {
                val uri = android.net.Uri.parse(path)
                appContext.contentResolver.delete(uri, null, null)
            } else {
                File(path).delete()
            }
        } catch (e: Exception) {
            Log.e("MergeVM", "Failed to delete file: $path", e)
        }
    }
}