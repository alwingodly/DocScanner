package com.example.docscanner.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.data.local.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onUsernameChanged(value: String) {
        _state.value = _state.value.copy(username = value, errorMessage = null)
    }

    fun onPasswordChanged(value: String) {
        _state.value = _state.value.copy(password = value, errorMessage = null)
    }

    fun onLogin() {
        val username = _state.value.username.trim()
        val password = _state.value.password.trim()

        if (username.isEmpty() || password.isEmpty()) {
            _state.value = _state.value.copy(errorMessage = "Please enter username and password")
            return
        }

        _state.value = _state.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            delay(800) // simulate auth call
            sessionManager.saveSession(username)
            _state.value = _state.value.copy(isLoading = false, loginSuccess = true)
        }
    }
}

data class LoginState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val loginSuccess: Boolean = false,
    val errorMessage: String? = null
)