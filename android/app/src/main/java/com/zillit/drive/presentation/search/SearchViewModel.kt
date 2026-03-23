package com.zillit.drive.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zillit.drive.domain.model.DriveItem
import com.zillit.drive.domain.repository.DriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<DriveItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: DriveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private var searchJob: Job? = null

    fun search(query: String) {
        _uiState.update { it.copy(query = query) }

        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false, error = null) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300L)
            _uiState.update { it.copy(isLoading = true, error = null) }

            val options = mapOf("search" to query)

            val filesResult = repository.getFiles(options)
            val foldersResult = repository.getFolders(options)

            val files = filesResult.getOrNull().orEmpty()
            val folders = foldersResult.getOrNull().orEmpty()

            if (filesResult.isFailure && foldersResult.isFailure) {
                val errorMsg = filesResult.exceptionOrNull()?.message ?: "Search failed"
                _uiState.update { it.copy(isLoading = false, error = errorMsg) }
            } else {
                val items = buildList {
                    addAll(folders.map { DriveItem.Folder(it) })
                    addAll(files.map { DriveItem.File(it) })
                }
                _uiState.update { it.copy(results = items, isLoading = false, error = null) }
            }
        }
    }

    fun toggleFavorite(itemId: String, itemType: String) {
        viewModelScope.launch {
            repository.toggleFavorite(itemId, itemType)
            _uiState.update { state ->
                state.copy(
                    results = state.results.map { item ->
                        when (item) {
                            is DriveItem.File -> if (item.id == itemId)
                                DriveItem.File(item.file.copy(isFavorite = !item.isFavorite)) else item
                            is DriveItem.Folder -> if (item.id == itemId)
                                DriveItem.Folder(item.folder.copy(isFavorite = !item.isFavorite)) else item
                        }
                    }
                )
            }
        }
    }
}
