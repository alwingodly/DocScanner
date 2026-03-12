package com.example.docscanner.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.domain.model.Folder
import com.example.docscanner.domain.repository.FolderRepository
import com.example.docscanner.domain.repository.FolderResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading    : Boolean = false,
    val isRefreshing : Boolean = false,
    val errorMessage : String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val folderRepository: FolderRepository
) : ViewModel() {

    val folders: StateFlow<List<Folder>> = folderRepository.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        syncFolders(isInitial = true)
    }

    fun refresh() = syncFolders(isInitial = false)

    private fun syncFolders(isInitial: Boolean) {
        viewModelScope.launch {
            _uiState.value = if (isInitial) HomeUiState(isLoading = true)
            else HomeUiState(isRefreshing = true)

            when (val result = folderRepository.syncFolders()) {
                is FolderResult.Loading -> Unit
                is FolderResult.Success -> _uiState.value = HomeUiState()
                is FolderResult.Error   -> _uiState.value = HomeUiState(
                    errorMessage = "Couldn't refresh: ${result.message}"
                )
            }
        }
    }

    /**
     * Called by the sidebar when the user drags a folder to a new position.
     * Uses the *current* snapshot of [folders] so the indices stay consistent.
     */
    fun reorderFolder(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            folderRepository.reorderFolders(
                currentFolders = folders.value,
                fromIndex      = fromIndex,
                toIndex        = toIndex
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}