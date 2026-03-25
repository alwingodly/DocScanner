package com.example.docscanner.presentation.apphome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.domain.model.ApplicationType
import com.example.docscanner.domain.model.Folder
import com.example.docscanner.domain.repository.FolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActiveSession(
    val sessionId: String,
    val applicationType: ApplicationType
)

@HiltViewModel
class SessionContextViewModel @Inject constructor(
    private val folderRepository: FolderRepository
) : ViewModel() {

    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSessionId: StateFlow<String?> = MutableStateFlow<String?>(null)
        .also { flow ->
            viewModelScope.launch {
                _activeSession.collect { flow.value = it?.sessionId }
            }
        }

    // ── Session folders — emits empty list when no session active ─────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionFolders: StateFlow<List<Folder>> = _activeSession
        .flatMapLatest { active ->
            if (active != null) {
                folderRepository.getFoldersForSession(active.sessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setActiveSession(sessionId: String, applicationType: ApplicationType) {
        _activeSession.value = ActiveSession(sessionId, applicationType)
        // Sync folders for this session in background
        viewModelScope.launch {
            folderRepository.syncFoldersForSession(sessionId, applicationType)
        }
    }

    fun clearActiveSession() {
        _activeSession.value = null
    }
}