package com.example.docscanner.presentation.alldocuments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.domain.model.Document
import com.example.docscanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AllDocumentsViewModel @Inject constructor(
    private val documentRepository: DocumentRepository
) : ViewModel() {

    val documents: StateFlow<List<Document>> = documentRepository
        .getAllDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun moveDocumentToFolder(documentId: String, targetFolderId: String) {
        viewModelScope.launch {
            documentRepository.moveDocumentToFolder(documentId, targetFolderId)
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            documentRepository.deleteDocument(document)
        }
    }

    fun renameDocument(document: Document, newName: String) {
        viewModelScope.launch {
            documentRepository.renameDocument(document.id, newName)
        }
    }

    fun changeDocumentType(document: Document, newLabel: String) {
        viewModelScope.launch {
            documentRepository.updateDocClassLabel(document.id, newLabel)
        }
    }

    fun updateClassification(document: Document, label: String) {
        viewModelScope.launch {
            documentRepository.updateClassification(document.id, label)
        }
    }
}