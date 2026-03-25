package com.example.docscanner.presentation.alldocuments

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.data.ocr.MlKitOcrHelper
import com.example.docscanner.domain.model.Document
import com.example.docscanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AllDocumentsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val documentRepository: DocumentRepository,
    private val ocrHelper: MlKitOcrHelper
) : ViewModel() {

    // ── Session context ───────────────────────────────────────────────────────

    private val _activeSessionId = MutableStateFlow<String?>(null)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setSessionId(sessionId: String?) {
        if (_activeSessionId.value == sessionId) return  // no-op if same session
        _isLoading.value = true
        _activeSessionId.value = sessionId
    }

    // ── Documents — switches between session and global ───────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    val documents: StateFlow<List<Document>> = _activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                documentRepository.getDocumentsForSession(sessionId)
            } else {
                documentRepository.getAllDocuments()
            }
        }
        .onEach { _isLoading.value = false }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,   // ← fixed: was WhileSubscribed(5000)
            emptyList()
        )

    // ── Existing (untouched) ──────────────────────────────────────────────────

    private val _isOrganizing = MutableStateFlow(false)
    val isOrganizing: StateFlow<Boolean> = _isOrganizing.asStateFlow()

    private val _organizeProgress = MutableStateFlow(0f)
    val organizeProgress: StateFlow<Float> = _organizeProgress.asStateFlow()

    fun moveDocumentToFolder(documentId: String, targetFolderId: String) {
        viewModelScope.launch {
            documentRepository.moveDocumentToFolder(documentId, targetFolderId)
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch { documentRepository.deleteDocument(document) }
    }

    fun renameDocument(document: Document, newName: String) {
        viewModelScope.launch { documentRepository.renameDocument(document.id, newName) }
    }

    fun changeDocumentType(document: Document, newLabel: String) {
        viewModelScope.launch { documentRepository.updateDocClassLabel(document.id, newLabel) }
    }

    fun updateClassification(document: Document, label: String) {
        viewModelScope.launch { documentRepository.updateClassification(document.id, label) }
    }

    fun moveGroupToFolder(classLabel: String, targetFolderId: String) {
        viewModelScope.launch {
            documents.value
                .filter { (it.docClassLabel ?: "Other") == classLabel }
                .forEach { documentRepository.moveDocumentToFolder(it.id, targetFolderId) }
        }
    }

    fun runAiOrganize() {
        if (_isOrganizing.value) return
        val docs = documents.value
        if (docs.isEmpty()) return
        _isOrganizing.value = true
        _organizeProgress.value = 0f
        viewModelScope.launch {
            docs.forEachIndexed { index, _ ->
                _organizeProgress.value = (index + 1).toFloat() / docs.size
            }
            _organizeProgress.value = 1f
            _isOrganizing.value = false
        }
    }

    fun clearOrganize() { /* no state to clear */ }
}