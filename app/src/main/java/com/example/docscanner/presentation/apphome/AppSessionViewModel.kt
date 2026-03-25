package com.example.docscanner.presentation.apphome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.domain.model.ApplicationSession
import com.example.docscanner.domain.model.ApplicationStatus
import com.example.docscanner.domain.model.ApplicationType
import com.example.docscanner.domain.repository.ApplicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

data class AppHomeUiState(
    val showTypePicker: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showQrScanner: Boolean = false,       // ← new
    val selectedType: ApplicationType? = null,
    val sessionName: String = "",
    val referenceId: String = "",
    val isCreating: Boolean = false,
    val createdSession: ApplicationSession? = null
)

@HiltViewModel
class AppSessionViewModel @Inject constructor(
    private val applicationRepository: ApplicationRepository
) : ViewModel() {

    val sessions = applicationRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(AppHomeUiState())
    val uiState: StateFlow<AppHomeUiState> = _uiState.asStateFlow()

    // ── Existing ──────────────────────────────────────────────────────────────

    fun onFabClick() {
        _uiState.value = _uiState.value.copy(showTypePicker = true)
    }

    fun onTypePicked(type: ApplicationType) {
        _uiState.value = _uiState.value.copy(
            showTypePicker = false,
            showCreateDialog = true,
            selectedType = type,
            sessionName = "",
            referenceId = ""
        )
    }

    fun onSessionNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(sessionName = value)
    }

    fun onReferenceIdChanged(value: String) {
        _uiState.value = _uiState.value.copy(referenceId = value)
    }

    fun onDismiss() {
        _uiState.value = AppHomeUiState()
    }

    fun onSessionNavigated() {
        _uiState.value = _uiState.value.copy(createdSession = null)
    }

    fun onCreateSession() {
        val state = _uiState.value
        val type = state.selectedType ?: return
        val name = state.sessionName.trim()
        if (name.isEmpty()) return

        _uiState.value = _uiState.value.copy(isCreating = true)
        viewModelScope.launch {
            val session = ApplicationSession(
                id = UUID.randomUUID().toString(),
                name = name,
                applicationType = type,
                referenceNumber = state.referenceId.trim().ifEmpty { null },
                status = ApplicationStatus.PENDING
            )
            applicationRepository.createSession(session)
            _uiState.value = _uiState.value.copy(
                isCreating = false,
                showCreateDialog = false,
                createdSession = session
            )
        }
    }

    // ── QR Scanner ────────────────────────────────────────────────────────────

    fun onQrScanClick() {
        _uiState.value = _uiState.value.copy(showQrScanner = true)
    }

    fun onQrScanDismiss() {
        _uiState.value = _uiState.value.copy(showQrScanner = false)
    }

    /**
     * Called when QR is successfully scanned.
     * Expected JSON: {"referenceId":"ABC123","applicationType":"PERSONAL_LOAN","name":"Aswan Loan"}
     * Falls back gracefully if fields are missing.
     */
    fun onQrScanned(rawValue: String) {
        val parsed = parseQrPayload(rawValue)
        _uiState.value = _uiState.value.copy(
            showQrScanner = false,
            showTypePicker = parsed.applicationType == null, // show picker if type not in QR
            showCreateDialog = parsed.applicationType != null,
            selectedType = parsed.applicationType,
            referenceId = parsed.referenceId ?: "",
            sessionName = parsed.name ?: ""
        )
    }

    private data class QrPayload(
        val referenceId: String?,
        val applicationType: ApplicationType?,
        val name: String?
    )

    private fun parseQrPayload(raw: String): QrPayload {
        return try {
            val json = JSONObject(raw)
            val typeStr = json.optString("applicationType", "")
            val type = runCatching {
                ApplicationType.valueOf(typeStr)
            }.getOrNull()
            QrPayload(
                referenceId = json.optString("referenceId", "").ifEmpty { null },
                applicationType = type,
                name = json.optString("name", "").ifEmpty { null }
            )
        } catch (e: Exception) {
            // If QR is just a plain string, treat it as referenceId only
            QrPayload(referenceId = raw.ifEmpty { null }, applicationType = null, name = null)
        }
    }
}