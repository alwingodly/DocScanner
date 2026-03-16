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
import com.example.docscanner.domain.model.FilterType
import com.example.docscanner.domain.model.FolderExportType
import com.example.docscanner.domain.model.ScannedPage
import com.example.docscanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var brightnessJob: Job? = null
    private var originalCornersHash: Int = 0

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

    fun onSaveToFolder() {
        val state = _state.value
        if (state.pages.isEmpty()) return
        _state.value = state.copy(isSaving = true)

        // Get the classification label from the first page (for PDF) or per-page (for images)
        val firstPageLabel = state.pages.firstOrNull()?.docClassType?.displayName

        viewModelScope.launch {
            try {
                when (state.targetExportType) {

                    FolderExportType.PDF -> {
                        val pdfUri = exporter.exportToPdf(state.pages, state.fileName)
                        val thumbnailUri = exporter.exportAsImage(
                            state.pages.first(),
                            "${state.fileName}_thumb",
                            ExportFormat.JPEG
                        )

                        documentRepository.saveDocument(
                            Document(
                                folderId      = state.targetFolderId,
                                name          = state.fileName,
                                pageCount     = state.pages.size,
                                thumbnailPath = thumbnailUri?.toString(),
                                pdfPath       = pdfUri?.toString(),
                                docClassLabel = firstPageLabel,
                                createdAt     = System.currentTimeMillis()
                            )
                        )
                    }

                    FolderExportType.IMAGES -> {
                        val imageUris = exporter.exportAllAsImages(
                            state.pages,
                            state.fileName,
                            ExportFormat.JPEG
                        )

                        imageUris.forEachIndexed { index, uri ->
                            val page = state.pages.getOrNull(index)
                            val pageName = if (state.pages.size == 1) state.fileName
                            else "${state.fileName}_p${index + 1}"
                            documentRepository.saveDocument(
                                Document(
                                    folderId      = state.targetFolderId,
                                    name          = pageName,
                                    pageCount     = 1,
                                    thumbnailPath = uri?.toString(),
                                    pdfPath       = null,
                                    docClassLabel = page?.docClassType?.displayName,
                                    createdAt     = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }

                _state.value = _state.value.copy(isSaving = false, saveSuccess = true)
            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    fun onSaveToFolderAsPdf() {
        _state.value = _state.value.copy(targetExportType = FolderExportType.PDF)
        onSaveToFolder()
    }

    fun onSaveToFolderAsImages() {
        _state.value = _state.value.copy(targetExportType = FolderExportType.IMAGES)
        onSaveToFolder()
    }

    fun onSaveNavigated() {
        _state.value = _state.value.copy(saveSuccess = false)
    }

    fun onUpdatePageCorners(index: Int, corners: DocumentCorners) {
        val page = _state.value.pages.getOrNull(index) ?: return
        updatePageAtIndex(index) { it.copy(corners = corners) }
        viewModelScope.launch {
            val cropped = documentProcessor.perspectiveTransform(page.originalBitmap, corners)
            updatePageAtIndex(index) { it.copy(croppedBitmap = cropped, enhancedBitmap = cropped) }
        }
    }

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

    fun onDeletePage(index: Int) {
        val updated = _state.value.pages.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            _state.value = _state.value.copy(pages = updated)
        }
    }

    fun onReorderPage(fromIndex: Int, toIndex: Int) {
        val updated = _state.value.pages.toMutableList()
        if (fromIndex in updated.indices && toIndex in updated.indices && fromIndex != toIndex) {
            val page = updated.removeAt(fromIndex)
            updated.add(toIndex, page)
            _state.value = _state.value.copy(pages = updated)
        }
    }

    fun onSelectPageForEdit(index: Int) {
        if (index in _state.value.pages.indices) {
            val page = _state.value.pages[index]
            originalCornersHash = page.corners.hashCode()
            _state.value = _state.value.copy(
                editingPageIndex = index, editingFilter = page.filterType,
                showApplyToAllPrompt = false, editDone = false
            )
        }
    }

    fun onEditCornersChanged(corners: DocumentCorners) {
        val index = _state.value.editingPageIndex ?: return
        updatePageAtIndex(index) { it.copy(corners = corners) }
    }

    fun onEditFilterSelected(filterType: FilterType) {
        val index = _state.value.editingPageIndex ?: return
        val page = _state.value.pages.getOrNull(index) ?: return
        val source = page.croppedBitmap ?: page.originalBitmap
        _state.value = _state.value.copy(isProcessing = true, editingFilter = filterType)
        viewModelScope.launch {
            val enhanced = documentProcessor.applyFilter(source, filterType)
            updatePageAtIndex(index) { it.copy(enhancedBitmap = enhanced, filterType = filterType) }
            _state.value = _state.value.copy(isProcessing = false)
        }
    }

    fun onEditBrightnessContrast(brightness: Double, contrast: Double) {
        val index = _state.value.editingPageIndex ?: return
        val page = _state.value.pages.getOrNull(index) ?: return
        val source = page.croppedBitmap ?: page.originalBitmap
        brightnessJob?.cancel()
        brightnessJob = viewModelScope.launch {
            delay(200)
            val adjusted = documentProcessor.adjustBrightnessContrast(source, brightness, contrast)
            updatePageAtIndex(index) { it.copy(enhancedBitmap = adjusted) }
        }
    }

    fun onEditDone() {
        val index = _state.value.editingPageIndex ?: return
        val page = _state.value.pages.getOrNull(index) ?: return
        val cornersChanged = page.corners.hashCode() != originalCornersHash

        if (cornersChanged && page.corners != null) {
            _state.value = _state.value.copy(isProcessing = true)
            viewModelScope.launch {
                val cropped = documentProcessor.perspectiveTransform(page.originalBitmap, page.corners!!)
                val enhanced = documentProcessor.applyFilter(cropped, page.filterType)
                updatePageAtIndex(index) { it.copy(croppedBitmap = cropped, enhancedBitmap = enhanced) }
                _state.value = _state.value.copy(isProcessing = false)
                afterEditProcessing()
            }
        } else {
            afterEditProcessing()
        }
    }

    private fun afterEditProcessing() {
        val index = _state.value.editingPageIndex ?: return
        val page = _state.value.pages.getOrNull(index) ?: return
        val otherFilters = _state.value.pages.filterIndexed { i, _ -> i != index }.map { it.filterType }.toSet()
        val shouldPrompt = _state.value.pages.size > 1 && otherFilters.isNotEmpty() && page.filterType !in otherFilters
        if (shouldPrompt) {
            _state.value = _state.value.copy(showApplyToAllPrompt = true)
        } else {
            finishEdit()
        }
    }

    fun onApplyEditingFilterToAll() {
        val index = _state.value.editingPageIndex ?: return
        val page = _state.value.pages.getOrNull(index) ?: return
        val filterType = page.filterType
        _state.value = _state.value.copy(isProcessing = true, showApplyToAllPrompt = false)
        viewModelScope.launch {
            val updatedPages = _state.value.pages.map { p ->
                if (p.id == page.id) p
                else {
                    val source = p.croppedBitmap ?: p.originalBitmap
                    val enhanced = documentProcessor.applyFilter(source, filterType)
                    p.copy(enhancedBitmap = enhanced, filterType = filterType)
                }
            }
            _state.value = _state.value.copy(pages = updatedPages, isProcessing = false)
            finishEdit()
        }
    }

    fun onDismissApplyToAll() {
        _state.value = _state.value.copy(showApplyToAllPrompt = false)
        finishEdit()
    }

    private fun finishEdit() {
        _state.value = _state.value.copy(
            editingPageIndex = null, editingFilter = FilterType.ORIGINAL,
            showApplyToAllPrompt = false, editDone = true
        )
    }

    fun onEditNavigated() { _state.value = _state.value.copy(editDone = false) }

    fun onBatchFilterApply(filterType: FilterType) {
        _state.value = _state.value.copy(isProcessing = true)
        viewModelScope.launch {
            val updated = _state.value.pages.map { p ->
                val source = p.croppedBitmap ?: p.originalBitmap
                val enhanced = documentProcessor.applyFilter(source, filterType)
                p.copy(enhancedBitmap = enhanced, filterType = filterType)
            }
            _state.value = _state.value.copy(pages = updated, isProcessing = false)
        }
    }

    fun onFileNameChanged(name: String) { _state.value = _state.value.copy(fileName = name) }
    fun onReset() { _state.value = ScannerState() }

    private fun updatePage(pageId: String, transform: (ScannedPage) -> ScannedPage) {
        _state.value = _state.value.copy(
            pages = _state.value.pages.map { if (it.id == pageId) transform(it) else it }
        )
    }

    private fun updatePageAtIndex(index: Int, transform: (ScannedPage) -> ScannedPage) {
        val updated = _state.value.pages.toMutableList()
        if (index in updated.indices) {
            updated[index] = transform(updated[index])
            _state.value = _state.value.copy(pages = updated)
        }
    }
}

data class ScannerState(
    val pages: List<ScannedPage> = emptyList(),
    val isProcessing: Boolean = false,
    val editingPageIndex: Int? = null,
    val editingFilter: FilterType = FilterType.ORIGINAL,
    val showApplyToAllPrompt: Boolean = false,
    val editDone: Boolean = false,
    val fileName: String = "DocScan_${System.currentTimeMillis()}",
    val targetFolderId: String = "",
    val targetFolderName: String = "",
    val targetExportType: FolderExportType = FolderExportType.PDF,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)