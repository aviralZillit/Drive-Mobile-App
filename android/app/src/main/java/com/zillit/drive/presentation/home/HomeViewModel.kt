package com.zillit.drive.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zillit.drive.BuildConfig
import com.zillit.drive.domain.model.DriveContents
import com.zillit.drive.domain.model.DriveItem
import com.zillit.drive.domain.model.DriveSection
import com.zillit.drive.domain.repository.DriveRepository
import com.zillit.drive.data.local.prefs.SessionManager
import com.zillit.drive.data.remote.socket.DriveSocketManager
import org.json.JSONObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val items: List<DriveItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentFolderId: String? = null,
    val folderPath: List<Pair<String?, String>> = listOf(null to "My Drive"), // id to name
    val favoriteFileIds: Set<String> = emptySet(),
    val favoriteFolderIds: Set<String> = emptySet(),
    val sortBy: String = "name",
    val sortOrder: String = "asc",
    val isGridView: Boolean = false,
    val driveSection: DriveSection = DriveSection.MY_DRIVE,
    val folderBadges: Map<String, Int> = emptyMap(),
    val fileBadges: Set<String> = emptySet()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DriveRepository,
    private val sessionManager: SessionManager,
    private val socketManager: DriveSocketManager
) : ViewModel() {

    val currentUserId: String?
        get() = sessionManager.getCachedSession()?.userId

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadContents()
        loadFavoriteIds()
        setupSocket()
    }

    fun loadContents(folderId: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val options = mutableMapOf<String, String>()
            if (folderId != null) {
                options["folder_id"] = folderId
            } else {
                options["root"] = "true"
                // Apply section filter only at root (matches web behavior)
                options["quick_filter"] = _uiState.value.driveSection.apiValue
            }
            options["sort_by"] = _uiState.value.sortBy
            options["sort_order"] = _uiState.value.sortOrder

            val result = repository.getFolderContents(options)
            result.fold(
                onSuccess = { contents ->
                    val items = buildList {
                        addAll(contents.folders.map { folder ->
                            DriveItem.Folder(folder.copy(
                                isFavorite = folder.id in _uiState.value.favoriteFolderIds
                            ))
                        })
                        addAll(contents.files.map { file ->
                            DriveItem.File(file.copy(
                                isFavorite = file.id in _uiState.value.favoriteFileIds
                            ))
                        })
                    }
                    _uiState.update { it.copy(
                        items = items,
                        isLoading = false,
                        currentFolderId = folderId
                    ) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load contents"
                    ) }
                }
            )
        }
    }

    fun navigateToFolder(folderId: String, folderName: String) {
        _uiState.update { state ->
            state.copy(
                folderPath = state.folderPath + (folderId to folderName)
            )
        }
        markFolderRead(folderId)
        loadContents(folderId)
    }

    fun navigateBack(): Boolean {
        val path = _uiState.value.folderPath
        if (path.size <= 1) return false

        val newPath = path.dropLast(1)
        _uiState.update { it.copy(folderPath = newPath) }
        loadContents(newPath.last().first)
        return true
    }

    fun navigateToPathIndex(index: Int) {
        val path = _uiState.value.folderPath
        if (index >= path.size) return

        val newPath = path.take(index + 1)
        _uiState.update { it.copy(folderPath = newPath) }
        loadContents(newPath.last().first)
    }

    fun toggleFavorite(itemId: String, itemType: String) {
        viewModelScope.launch {
            repository.toggleFavorite(itemId, itemType)
            // Update local state
            _uiState.update { state ->
                val newFileIds = state.favoriteFileIds.toMutableSet()
                val newFolderIds = state.favoriteFolderIds.toMutableSet()
                when (itemType) {
                    "file" -> if (itemId in newFileIds) newFileIds.remove(itemId) else newFileIds.add(itemId)
                    "folder" -> if (itemId in newFolderIds) newFolderIds.remove(itemId) else newFolderIds.add(itemId)
                }
                state.copy(
                    favoriteFileIds = newFileIds,
                    favoriteFolderIds = newFolderIds,
                    items = state.items.map { item ->
                        when (item) {
                            is DriveItem.File -> if (item.id == itemId) DriveItem.File(item.file.copy(isFavorite = !item.isFavorite)) else item
                            is DriveItem.Folder -> if (item.id == itemId) DriveItem.Folder(item.folder.copy(isFavorite = !item.isFavorite)) else item
                        }
                    }
                )
            }
        }
    }

    fun deleteItem(itemId: String, itemType: String) {
        viewModelScope.launch {
            val result = when (itemType) {
                "file" -> repository.deleteFile(itemId)
                "folder" -> repository.deleteFolder(itemId)
                else -> return@launch
            }
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(items = state.items.filter { it.id != itemId })
                }
            }
        }
    }

    fun setSortBy(sortBy: String) {
        _uiState.update { it.copy(sortBy = sortBy) }
        loadContents(_uiState.value.currentFolderId)
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun switchSection(section: DriveSection) {
        if (section == _uiState.value.driveSection) return
        _uiState.update { it.copy(
            driveSection = section,
            folderPath = listOf(null to section.displayName),
            currentFolderId = null,
            items = emptyList()
        ) }
        loadContents()
    }

    fun refresh() {
        forceLoadContents(_uiState.value.currentFolderId)
        loadFavoriteIds()
    }

    /** Force-fetches from network (pull-to-refresh / cache bypass) */
    private fun forceLoadContents(folderId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val options = mutableMapOf<String, String>()
            if (folderId != null) {
                options["folder_id"] = folderId
            } else {
                options["root"] = "true"
                options["quick_filter"] = _uiState.value.driveSection.apiValue
            }
            options["sort_by"] = _uiState.value.sortBy
            options["sort_order"] = _uiState.value.sortOrder

            val result = repository.forceGetFolderContents(options)
            result.fold(
                onSuccess = { contents ->
                    val items = buildList {
                        addAll(contents.folders.map { folder ->
                            DriveItem.Folder(folder.copy(
                                isFavorite = folder.id in _uiState.value.favoriteFolderIds
                            ))
                        })
                        addAll(contents.files.map { file ->
                            DriveItem.File(file.copy(
                                isFavorite = file.id in _uiState.value.favoriteFileIds
                            ))
                        })
                    }
                    _uiState.update { it.copy(
                        items = items,
                        isLoading = false,
                        currentFolderId = folderId
                    ) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load contents"
                    ) }
                }
            )
        }
    }

    // ─── Socket.IO Real-Time Events ───

    private fun setupSocket() {
        val session = sessionManager.getCachedSession() ?: return
        socketManager.connect(BuildConfig.SOCKET_URL, session.projectId)

        viewModelScope.launch {
            socketManager.events.collect { event ->
                when (event.event) {
                    // Drive data changes → refetch
                    "drive:file:added", "drive:file:updated", "drive:file:deleted",
                    "drive:folder:created", "drive:folder:updated", "drive:folder:deleted" -> {
                        refresh()
                    }
                    // Sharing events → check if relevant to current user
                    "drive:folder:shared", "drive:file:shared" -> {
                        val json = event.data as? JSONObject
                        val sharedWith = json?.optJSONArray("shared_with")
                        val userId = currentUserId
                        if (userId != null && sharedWith != null) {
                            val ids = (0 until sharedWith.length()).map { sharedWith.getString(it) }
                            if (ids.contains(userId)) {
                                refresh()
                            }
                        }
                    }
                    // New badge received → update counts
                    "notification:save" -> {
                        val json = event.data as? JSONObject ?: return@collect
                        val tool = json.optString("tool")
                        if (tool != "drive_label") return@collect
                        val level1 = json.optString("level_1", "")
                        val level2 = json.optString("level_2", "")
                        handleNewBadge(level1.ifEmpty { null }, level2.ifEmpty { null })
                    }
                    // Badge read sync from other devices
                    "notification:level:read" -> {
                        val json = event.data as? JSONObject ?: return@collect
                        val tool = json.optString("tool")
                        if (tool != "drive_label") return@collect
                        val level1 = json.optString("level_1", "")
                        if (level1.isNotEmpty()) {
                            _uiState.update { state ->
                                state.copy(folderBadges = state.folderBadges - level1)
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── Badge Management ───

    private fun handleNewBadge(level1: String?, level2: String?) {
        _uiState.update { state ->
            val newFolderBadges = state.folderBadges.toMutableMap()
            val newFileBadges = state.fileBadges.toMutableSet()
            if (level1 != null) {
                newFolderBadges[level1] = (newFolderBadges[level1] ?: 0) + 1
            }
            if (level2 != null) {
                newFileBadges.add(level2)
            }
            state.copy(folderBadges = newFolderBadges, fileBadges = newFileBadges)
        }
    }

    private fun markFolderRead(folderId: String) {
        _uiState.update { state ->
            state.copy(folderBadges = state.folderBadges - folderId)
        }
        // Emit socket to sync with backend (matches web connectSocket.js)
        val session = sessionManager.getCachedSession() ?: return
        socketManager.emit("notification:level:read", JSONObject().apply {
            put("project_id", session.projectId)
            put("tool", "drive_label")
            put("unit", "drive_file_label")
            put("level_1", folderId)
        })
    }

    private fun loadFavoriteIds() {
        viewModelScope.launch {
            repository.getFavoriteIds().onSuccess { (fileIds, folderIds) ->
                _uiState.update { it.copy(
                    favoriteFileIds = fileIds.toSet(),
                    favoriteFolderIds = folderIds.toSet()
                ) }
            }
        }
    }
}
