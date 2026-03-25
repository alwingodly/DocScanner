package com.example.docscanner.presentation.shared

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.data.classifier.DocumentClassifier
import com.example.docscanner.data.export.DocumentExporter
import com.example.docscanner.data.masking.AadhaarMasker
import com.example.docscanner.data.masking.PanMasker
import com.example.docscanner.data.ocr.MlKitOcrHelper
import com.example.docscanner.data.processor.DocumentProcessor
import com.example.docscanner.domain.model.DocClassType
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
    private val documentClassifier: DocumentClassifier,
    private val ocrHelper: MlKitOcrHelper,
    private val aadhaarMasker: AadhaarMasker,
    private val panMasker: PanMasker
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerState())
    val state: StateFlow<ScannerState> = _state.asStateFlow()

    override fun onCleared() {
        super.onCleared()
        documentClassifier.close()
    }

    fun setSessionId(sessionId: String?) {
        _state.value = _state.value.copy(sessionId = sessionId)
    }

    fun setTargetFolder(folderId: String, folderName: String, exportType: FolderExportType) {
        _state.value = _state.value.copy(
            targetFolderId = folderId,
            targetFolderName = folderName,
            targetExportType = exportType
        )
    }

    fun onPhotoCaptured(bitmap: Bitmap, detectedCorners: DocumentCorners? = null) {
        val pageId = System.currentTimeMillis().toString()
        _state.value = _state.value.copy(
            pages = _state.value.pages + ScannedPage(
                id = pageId,
                originalBitmap = bitmap
            )
        )

        viewModelScope.launch {
            val corners = detectedCorners ?: documentProcessor.detectEdges(bitmap)
            val cropped = documentProcessor.perspectiveTransform(bitmap, corners)
            val docType = try {
                documentClassifier.classify(cropped)
            } catch (_: Exception) {
                null
            }
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

    fun onAutoSavePages() {
        val currentState = _state.value
        if (currentState.pages.isEmpty()) return
        _state.value = currentState.copy(isSaving = true)

        viewModelScope.launch {
            try {
                currentState.pages.forEachIndexed { index, page ->
                    val originalBitmap =
                        page.enhancedBitmap ?: page.croppedBitmap ?: page.originalBitmap

                    // 1. ALWAYS classify
                    val docType = try {
                        documentClassifier.classify(originalBitmap)
                    } catch (e: Exception) {
                        Log.e("ScannerVM", "Classification failed", e)
                        page.docClassType ?: DocClassType.OTHER
                    }

                    // 2. OCR on ORIGINAL bitmap for accurate naming (before masking)
                    val smartName = try {
                        val fields = ocrHelper.extractFields(originalBitmap, docType)
                        buildSmartName(docType, fields.name, fields.idNumber)
                    } catch (e: Exception) {
                        Log.e("ScannerVM", "OCR naming failed", e)
                        null
                    }

                    // 3. Mask Aadhaar AFTER OCR (so saved image is masked)
                    val saveBitmap = when (docType) {
                        DocClassType.AADHAAR -> try {
                            aadhaarMasker.mask(originalBitmap)
                        } catch (e: Exception) {
                            Log.e("ScannerVM", "Aadhaar masking failed", e)
                            originalBitmap
                        }
                        DocClassType.PAN -> try {
                            panMasker.mask(originalBitmap)
                        } catch (e: Exception) {
                            Log.e("ScannerVM", "PAN masking failed", e)
                            originalBitmap
                        }
                        else -> originalBitmap
                    }

                    val timestamp = System.currentTimeMillis()
                    val name = smartName
                        ?: if (currentState.pages.size == 1) "Scan_$timestamp"
                        else "Scan_${timestamp}_p${index + 1}"

                    val imageUri = exporter.exportAsImage(
                        page.copy(enhancedBitmap = saveBitmap),
                        name,
                        ExportFormat.JPEG
                    )

                    documentRepository.saveDocument(
                        Document(
                            folderId      = currentState.targetFolderId,
                            name          = name,
                            pageCount     = 1,
                            thumbnailPath = imageUri?.toString(),
                            pdfPath       = null,
                            docClassLabel = docType.displayName,
                            createdAt     = System.currentTimeMillis(),
                            sessionId     = currentState.sessionId  // ← session-scoped
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

    private fun buildSmartName(
        type: DocClassType,
        personName: String?,
        idNumber: String?
    ): String? {
        if (personName == null && idNumber == null) return null
        val parts = mutableListOf<String>()
        personName?.let { parts.add(it) }
        if (type != DocClassType.OTHER) parts.add(type.displayName.replace(" ", "_"))
        idNumber?.let { parts.add(it.replace("\\s".toRegex(), "").takeLast(6)) }
        return if (parts.isEmpty()) null else parts.joinToString("_")
    }

    fun onSaveNavigated() {
        _state.value = _state.value.copy(saveSuccess = false)
    }

    fun onReset() {
        // ← preserve sessionId so docs after reset still belong to the active session
        _state.value = ScannerState(sessionId = _state.value.sessionId)
    }

    private fun updatePage(pageId: String, transform: (ScannedPage) -> ScannedPage) {
        _state.value = _state.value.copy(
            pages = _state.value.pages.map {
                if (it.id == pageId) transform(it) else it
            }
        )
    }
}

data class ScannerState(
    val pages: List<ScannedPage> = emptyList(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val targetFolderId: String = "",
    val targetFolderName: String = "",
    val targetExportType: FolderExportType = FolderExportType.PDF,
    val sessionId: String? = null
)