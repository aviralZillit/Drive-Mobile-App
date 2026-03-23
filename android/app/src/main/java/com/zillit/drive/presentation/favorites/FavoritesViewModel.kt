package com.zillit.drive.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zillit.drive.domain.model.DriveItem
import com.zillit.drive.domain.repository.DriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val items: List<DriveItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: DriveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val idsResult = repository.getFavoriteIds()
            idsResult.fold(
                onSuccess = { (fileIds, folderIds) ->
                    if (fileIds.isEmpty() && folderIds.isEmpty()) {
                        _uiState.update { it.copy(items = emptyList(), isLoading = false) }
                        return@launch
                    }

                    val items = mutableListOf<DriveItem>()

                    // Fetch all favorite folders in parallel
                    val folderDeferreds = folderIds.map { folderId ->
                        async {
                            repository.getFolder(folderId).getOrNull()
                        }
                    }

                    // Fetch all favorite files in parallel
                    val fileDeferreds = fileIds.map { fileId ->
                        async {
                            repository.getFile(fileId).getOrNull()
                        }
                    }

                    val folders = folderDeferreds.awaitAll().filterNotNull()
                    val files = fileDeferreds.awaitAll().filterNotNull()

                    items.addAll(folders.map { folder ->
                        DriveItem.Folder(folder.copy(isFavorite = true))
                    })
                    items.addAll(files.map { file ->
                        DriveItem.File(file.copy(isFavorite = true))
                    })

                    _uiState.update { it.copy(items = items, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load favorites"
                        )
                    }
                }
            )
        }
    }

    fun toggleFavorite(itemId: String, itemType: String) {
        viewModelScope.launch {
            repository.toggleFavorite(itemId, itemType).onSuccess {
                // Remove the item from the favorites list since it was unfavorited
                _uiState.update { state ->
                    state.copy(items = state.items.filter { it.id != itemId })
                }
            }
        }
    }

    fun refresh() {
        loadFavorites()
    }
}
