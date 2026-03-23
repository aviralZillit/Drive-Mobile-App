package com.zillit.drive.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zillit.drive.BuildConfig
import com.zillit.drive.data.local.prefs.SessionManager
import com.zillit.drive.data.local.prefs.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data class Success(val session: UserSession) : LoginState()
    data class Error(val message: String) : LoginState()
    data object AlreadyLoggedIn : LoginState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun checkExistingSession() {
        viewModelScope.launch {
            val isLoggedIn = sessionManager.isLoggedIn().first()
            if (isLoggedIn) {
                _loginState.value = LoginState.AlreadyLoggedIn
            }
        }
    }

    fun login(userId: String, projectId: String, deviceId: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val session = UserSession(
                    userId = userId,
                    projectId = projectId,
                    deviceId = deviceId,
                    encryptionKey = BuildConfig.ENCRYPTION_KEY,
                    encryptionIv = BuildConfig.ENCRYPTION_IV,
                    environment = BuildConfig.ENVIRONMENT
                )
                sessionManager.saveSession(session)
                _loginState.value = LoginState.Success(session)
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionManager.clearSession()
            _loginState.value = LoginState.Idle
        }
    }
}
