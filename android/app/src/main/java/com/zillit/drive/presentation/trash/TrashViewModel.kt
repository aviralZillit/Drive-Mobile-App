package com.zillit.drive.presentation.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zillit.drive.domain.model.DriveItem
import com.zillit.drive.domain.repository.DriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashUiState(
    val items: List<DriveItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: DriveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState

    init {
        loadTrash()
    }

    fun loadTrash() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.getTrash().fold(
                onSuccess = { items ->
                    _uiState.update { it.copy(items = items, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load trash"
                        )
                    }
                }
            )
        }
    }

    fun restoreItem(item: DriveItem) {
        val type = when (item) {
            is DriveItem.File -> "file"
            is DriveItem.Folder -> "folder"
        }
        viewModelScope.launch {
            repository.restoreTrashItem(type, item.id).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(items = state.items.filter { it.id != item.id })
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(error = error.message ?: "Failed to restore item")
                    }
                }
            )
        }
    }

    fun permanentDelete(item: DriveItem) {
        val type = when (item) {
            is DriveItem.File -> "file"
            is DriveItem.Folder -> "folder"
        }
        viewModelScope.launch {
            repository.permanentDeleteTrashItem(type, item.id).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(items = state.items.filter { it.id != item.id })
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(error = error.message ?: "Failed to delete item")
                    }
                }
            )
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.emptyTrash().fold(
                onSuccess = {
                    _uiState.update { it.copy(items = emptyList(), isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to empty trash"
                        )
                    }
                }
            )
        }
    }
}
