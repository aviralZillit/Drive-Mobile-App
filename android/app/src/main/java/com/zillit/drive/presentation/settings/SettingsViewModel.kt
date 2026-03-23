package com.zillit.drive.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zillit.drive.data.local.prefs.SessionManager
import com.zillit.drive.data.local.prefs.UserSession
import com.zillit.drive.domain.model.FolderAccess
import com.zillit.drive.domain.model.StorageUsage
import com.zillit.drive.domain.repository.DriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val storageUsage: StorageUsage? = null,
    val session: UserSession? = null,
    val teamMembers: List<FolderAccess> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedOut: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DriveRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Load session info
            val session = sessionManager.getSession()
            _uiState.update { it.copy(session = session) }

            // Load storage usage
            repository.getStorageUsage().fold(
                onSuccess = { usage ->
                    _uiState.update { it.copy(storageUsage = usage, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load storage info"
                        )
                    }
                }
            )

            // Load team members from root folder access
            loadTeamMembers()
        }
    }

    private suspend fun loadTeamMembers() {
        repository.getFolderAccess("root").fold(
            onSuccess = { members ->
                _uiState.update { it.copy(teamMembers = members) }
            },
            onFailure = {
                // Silently handle - team members section will just not show
            }
        )
    }

    fun logout() {
        viewModelScope.launch {
            sessionManager.clearSession()
            _uiState.update { it.copy(isLoggedOut = true) }
        }
    }
}
