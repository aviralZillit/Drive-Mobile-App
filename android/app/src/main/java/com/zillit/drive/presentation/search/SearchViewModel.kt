package com.zillit.drive.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zillit.drive.domain.model.DriveItem
import com.zillit.drive.domain.repository.DriveRepository
import com.zillit.drive.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchFileTypeFilter(val label: String) {
    ALL("All"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    DOCUMENTS("Documents"),
    PDF("PDF")
}

data class SearchUiState(
    val query: String = "",
    val results: List<DriveItem> = emptyList(),
    val filteredResults: List<DriveItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeFilter: SearchFileTypeFilter = SearchFileTypeFilter.ALL,
    val dateFilterStart: Long? = null, // epoch millis
    val dateFilterEnd: Long? = null    // epoch millis
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
            _uiState.update { it.copy(results = emptyList(), filteredResults = emptyList(), isLoading = false, error = null) }
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
                _uiState.update {
                    it.copy(
                        results = items,
                        isLoading = false,
                        error = null
                    )
                }
                applyFilters()
            }
        }
    }

    fun setFileTypeFilter(filter: SearchFileTypeFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
        applyFilters()
    }

    fun setDateRange(startMillis: Long?, endMillis: Long?) {
        _uiState.update { it.copy(dateFilterStart = startMillis, dateFilterEnd = endMillis) }
        applyFilters()
    }

    fun clearDateRange() {
        _uiState.update { it.copy(dateFilterStart = null, dateFilterEnd = null) }
        applyFilters()
    }

    private fun applyFilters() {
        _uiState.update { state ->
            val filtered = state.results.filter { item ->
                // File type filter
                val passesTypeFilter = when (state.activeFilter) {
                    SearchFileTypeFilter.ALL -> true
                    SearchFileTypeFilter.IMAGES -> item is DriveItem.File &&
                        FileUtils.isImageFile(item.file.fileExtension)
                    SearchFileTypeFilter.VIDEOS -> item is DriveItem.File &&
                        FileUtils.isVideoFile(item.file.fileExtension)
                    SearchFileTypeFilter.DOCUMENTS -> item is DriveItem.File &&
                        (FileUtils.isOfficeFile(item.file.fileExtension) || FileUtils.isTextFile(item.file.fileExtension))
                    SearchFileTypeFilter.PDF -> item is DriveItem.File &&
                        FileUtils.isPdfFile(item.file.fileExtension)
                }

                // Date range filter
                val passesDateFilter = when {
                    state.dateFilterStart != null && state.dateFilterEnd != null ->
                        item.createdOn in state.dateFilterStart..state.dateFilterEnd
                    state.dateFilterStart != null ->
                        item.createdOn >= state.dateFilterStart
                    state.dateFilterEnd != null ->
                        item.createdOn <= state.dateFilterEnd
                    else -> true
                }

                passesTypeFilter && passesDateFilter
            }
            state.copy(filteredResults = filtered)
        }
    }

    fun toggleFavorite(itemId: String, itemType: String) {
        viewModelScope.launch {
            repository.toggleFavorite(itemId, itemType)
            _uiState.update { state ->
                val updateItem = { item: DriveItem ->
                    when (item) {
                        is DriveItem.File -> if (item.id == itemId)
                            DriveItem.File(item.file.copy(isFavorite = !item.isFavorite)) else item
                        is DriveItem.Folder -> if (item.id == itemId)
                            DriveItem.Folder(item.folder.copy(isFavorite = !item.isFavorite)) else item
                    }
                }
                state.copy(
                    results = state.results.map(updateItem),
                    filteredResults = state.filteredResults.map(updateItem)
                )
            }
        }
    }
}
