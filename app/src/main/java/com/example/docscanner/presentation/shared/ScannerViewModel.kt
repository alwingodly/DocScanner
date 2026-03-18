package com.example.docscanner.presentation.shared

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.data.classifier.DocumentClassifier
import com.example.docscanner.data.export.DocumentExporter
import com.example.docscanner.data.processor.DocumentProcessor
import com.example.docscanner.domain.model.Document
import com.example.docscanner.domain.model.DocumentCorners
import com.example.docscanner.domain.model.ExportFormat
import com.example.docscanner.domain.model.FolderExportType
import com.example.docscanner.domain.model.ScannedPage
import com.example.docscanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val documentProcessor: DocumentProcessor,
    val exporter: DocumentExporter,
    private val documentRepository: DocumentRepository,
    private val documentClassifier: DocumentClassifier
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerState())
    val state: StateFlow<ScannerState> = _state.asStateFlow()

    override fun onCleared() {
        super.onCleared()
        documentClassifier.close()
    }

    fun setTargetFolder(folderId: String, folderName: String, exportType: FolderExportType) {
        _state.value = _state.value.copy(
            targetFolderId = folderId,
            targetFolderName = folderName,
            targetExportType = exportType
        )
    }

    // ── Capture: add page with basic processing ──────────────────────────────

    fun onPhotoCaptured(bitmap: Bitmap, detectedCorners: DocumentCorners? = null) {
        val pageId = System.currentTimeMillis().toString()
        val newPage = ScannedPage(id = pageId, originalBitmap = bitmap)

        _state.value = _state.value.copy(pages = _state.value.pages + newPage)

        viewModelScope.launch {
            val corners = detectedCorners ?: documentProcessor.detectEdges(bitmap)
            val cropped = documentProcessor.perspectiveTransform(bitmap, corners)

            Log.d("DocClassify", "──────────────────────────────────────")
            Log.d("DocClassify", "Cropped bitmap: ${cropped.width}x${cropped.height}")

            val docType = try {
                val result = documentClassifier.classify(cropped)
                Log.d("DocClassify", "Result: ${result.name}, displayName: ${result.displayName}")
                result
            } catch (e: Exception) {
                Log.e("DocClassify", "Classification FAILED", e)
                null
            }

            Log.d("DocClassify", "Final docType: ${docType?.name ?: "null"}")
            Log.d("DocClassify", "──────────────────────────────────────")

            updatePage(pageId) {
                it.copy(
                    corners = corners,
                    croppedBitmap = cropped,
                    enhancedBitmap = cropped,
                    docClassType = docType
                )
            }
        }
    }

    // ── Auto-save: classify each page and save as individual image documents ─

    fun onAutoSavePages() {
        val currentState = _state.value
        if (currentState.pages.isEmpty()) return
        _state.value = currentState.copy(isSaving = true)

        viewModelScope.launch {
            try {
                val timestamp = System.currentTimeMillis()

                currentState.pages.forEachIndexed { index, page ->
                    // Wait for cropping to be ready (it may still be processing)
                    val bitmap = page.enhancedBitmap ?: page.croppedBitmap ?: page.originalBitmap

                    // Classify if not already done
                    val docType = page.docClassType ?: try {
                        documentClassifier.classify(bitmap)
                    } catch (e: Exception) {
                        Log.e("DocClassify", "Auto-save classification failed", e)
                        null
                    }

                    val pageName = if (currentState.pages.size == 1) {
                        "Scan_${timestamp}"
                    } else {
                        "Scan_${timestamp}_p${index + 1}"
                    }

                    // Export as image
                    val imageUri = exporter.exportAsImage(
                        page.copy(enhancedBitmap = bitmap),
                        pageName,
                        ExportFormat.JPEG
                    )

                    // Save document record
                    documentRepository.saveDocument(
                        Document(
                            folderId = currentState.targetFolderId,
                            name = pageName,
                            pageCount = 1,
                            thumbnailPath = imageUri?.toString(),
                            pdfPath = null,
                            docClassLabel = docType?.displayName,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                _state.value = _state.value.copy(isSaving = false, saveSuccess = true)
            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    // ── Navigation helpers ───────────────────────────────────────────────────

    fun onSaveNavigated() {
        _state.value = _state.value.copy(saveSuccess = false)
    }

    fun onReset() {
        _state.value = ScannerState()
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private fun updatePage(pageId: String, transform: (ScannedPage) -> ScannedPage) {
        _state.value = _state.value.copy(
            pages = _state.value.pages.map { if (it.id == pageId) transform(it) else it }
        )
    }
}

data class ScannerState(
    val pages: List<ScannedPage> = emptyList(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val targetFolderId: String = "",
    val targetFolderName: String = "",
    val targetExportType: FolderExportType = FolderExportType.PDF
)