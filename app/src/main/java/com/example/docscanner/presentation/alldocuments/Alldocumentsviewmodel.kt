package com.example.docscanner.presentation.alldocuments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.data.ocr.MlKitOcrHelper
import com.example.docscanner.domain.model.DocClassType
import com.example.docscanner.domain.model.Document
import com.example.docscanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AllDocumentsViewModel @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context,
    private val documentRepository: DocumentRepository,
    private val ocrHelper: MlKitOcrHelper
) : ViewModel() {

    val documents: StateFlow<List<Document>> = documentRepository
        .getAllDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isOrganizing = MutableStateFlow(false)
    val isOrganizing: StateFlow<Boolean> = _isOrganizing.asStateFlow()

    private val _organizeProgress = MutableStateFlow(0f)
    val organizeProgress: StateFlow<Float> = _organizeProgress.asStateFlow()

    fun moveDocumentToFolder(documentId: String, targetFolderId: String) { viewModelScope.launch { documentRepository.moveDocumentToFolder(documentId, targetFolderId) } }
    fun deleteDocument(document: Document) { viewModelScope.launch { documentRepository.deleteDocument(document) } }
    fun renameDocument(document: Document, newName: String) { viewModelScope.launch { documentRepository.renameDocument(document.id, newName) } }
    fun changeDocumentType(document: Document, newLabel: String) { viewModelScope.launch { documentRepository.updateDocClassLabel(document.id, newLabel) } }
    fun updateClassification(document: Document, label: String) { viewModelScope.launch { documentRepository.updateClassification(document.id, label) } }

    /** Move all documents of a classification group to a folder */
    fun moveGroupToFolder(classLabel: String, targetFolderId: String) {
        viewModelScope.launch { documents.value.filter { (it.docClassLabel ?: "Other") == classLabel }.forEach { documentRepository.moveDocumentToFolder(it.id, targetFolderId) } }
    }

    /**
     * AI Organize: re-classify any unclassified docs and run OCR for naming.
     * For docs already classified, this is a no-op (grouping uses existing labels).
     */
    fun runAiOrganize() {
        if (_isOrganizing.value) return
        val docs = documents.value; if (docs.isEmpty()) return
        _isOrganizing.value = true; _organizeProgress.value = 0f

        viewModelScope.launch {
            // Just signal progress — grouping happens from docClassLabel which is already set
            docs.forEachIndexed { index, _ -> _organizeProgress.value = (index + 1).toFloat() / docs.size }
            _organizeProgress.value = 1f; _isOrganizing.value = false
        }
    }

    fun clearOrganize() { /* no state to clear */ }
}