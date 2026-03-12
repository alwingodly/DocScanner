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

    /**
     * All documents across every folder, newest first.
     * Requires [DocumentRepository.getAllDocuments] — see CHANGES_NEEDED.kt.
     */
    val documents: StateFlow<List<Document>> = documentRepository
        .getAllDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Move [documentId] to [targetFolderId].
     * Updates Room: document.folderId, decrements old folder count,
     * increments new folder count.
     */
    fun moveDocumentToFolder(documentId: String, targetFolderId: String) {
        viewModelScope.launch {
            documentRepository.moveDocumentToFolder(documentId, targetFolderId)
        }
    }
}