package com.zillit.drive.presentation.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zillit.drive.domain.model.FileAccess
import com.zillit.drive.domain.model.FolderAccess
import com.zillit.drive.domain.model.ShareLink
import com.zillit.drive.domain.repository.DriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareUiState(
    val itemId: String = "",
    val itemType: String = "", // "file" or "folder"
    val fileAccessEntries: List<FileAccess> = emptyList(),
    val folderAccessEntries: List<FolderAccess> = emptyList(),
    val shareLink: ShareLink? = null,
    val isLoading: Boolean = false,
    val isGeneratingLink: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val repository: DriveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState

    fun init(itemId: String, itemType: String) {
        _uiState.update { it.copy(itemId = itemId, itemType = itemType) }
        loadAccess()
    }

    private fun loadAccess() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val state = _uiState.value
            if (state.itemType == "file") {
                repository.getFileAccess(state.itemId).fold(
                    onSuccess = { entries ->
                        _uiState.update { it.copy(fileAccessEntries = entries, isLoading = false) }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load access") }
                    }
                )
            } else {
                repository.getFolderAccess(state.itemId).fold(
                    onSuccess = { entries ->
                        _uiState.update { it.copy(folderAccessEntries = entries, isLoading = false) }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load access") }
                    }
                )
            }
        }
    }

    fun generateShareLink() {
        val state = _uiState.value
        if (state.itemType != "file") return

        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingLink = true) }
            repository.generateShareLink(state.itemId).fold(
                onSuccess = { link ->
                    _uiState.update { it.copy(shareLink = link, isGeneratingLink = false) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isGeneratingLink = false, error = error.message ?: "Failed to generate link") }
                }
            )
        }
    }

    fun updateFilePermission(userId: String, canView: Boolean, canEdit: Boolean, canDownload: Boolean) {
        _uiState.update { state ->
            state.copy(
                fileAccessEntries = state.fileAccessEntries.map { entry ->
                    if (entry.userId == userId) {
                        entry.copy(canView = canView, canEdit = canEdit, canDownload = canDownload)
                    } else entry
                }
            )
        }
    }

    fun updateFolderRole(userId: String, role: String) {
        _uiState.update { state ->
            state.copy(
                folderAccessEntries = state.folderAccessEntries.map { entry ->
                    if (entry.userId == userId) entry.copy(role = role) else entry
                }
            )
        }
    }

    fun savePermissions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, saveSuccess = false) }
            val state = _uiState.value

            val result = if (state.itemType == "file") {
                repository.updateFileAccess(state.itemId, state.fileAccessEntries)
            } else {
                repository.updateFolderAccess(state.itemId, state.folderAccessEntries)
            }

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isSaving = false, error = error.message ?: "Failed to save permissions") }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
