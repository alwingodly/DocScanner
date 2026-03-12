package com.example.docscanner.presentation.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.domain.model.Document
import com.example.docscanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val _documents = MutableStateFlow<List<Document>>(emptyList())
    val documents: StateFlow<List<Document>> = _documents.asStateFlow()

    private var collectJob: Job? = null
    private var currentFolderId: String? = null

    fun loadFolder(folderId: String) {
        // Only skip if same folder AND the collection is still alive.
        // If the composable left composition the job was cancelled, so we must restart.
        if (folderId == currentFolderId && collectJob?.isActive == true) return

        currentFolderId = folderId
        collectJob?.cancel()
        _documents.value = emptyList() // clear stale docs immediately so wrong items never flash

        collectJob = viewModelScope.launch {
            documentRepository.getDocuments(folderId).collect {
                _documents.value = it
            }
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch { documentRepository.deleteDocument(document) }
    }

    fun moveDocument(documentId: String, fromFolderId: String, toFolderId: String) {
        if (fromFolderId == toFolderId) return
        viewModelScope.launch { documentRepository.moveDocumentToFolder(documentId, toFolderId) }
    }
}