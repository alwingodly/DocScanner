package com.example.docscanner.presentation.profile

import androidx.lifecycle.ViewModel
import com.example.docscanner.data.local.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ProfileUiState(
    val username: String = "",
    val notificationsEnabled: Boolean = true,
    val darkTheme: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileUiState(username = sessionManager.getUsername() ?: "User")
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun toggleNotifications() {
        _state.value = _state.value.copy(
            notificationsEnabled = !_state.value.notificationsEnabled
        )
    }

    fun toggleDarkTheme() {
        _state.value = _state.value.copy(
            darkTheme = !_state.value.darkTheme
        )
    }

    fun logout(onLoggedOut: () -> Unit) {
        sessionManager.clearSession()
        onLoggedOut()
    }
}