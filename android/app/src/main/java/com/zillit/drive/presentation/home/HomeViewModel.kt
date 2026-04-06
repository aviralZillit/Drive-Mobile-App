package com.zillit.drive.presentation.home

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zillit.drive.BuildConfig
import com.zillit.drive.domain.model.DriveContents
import com.zillit.drive.domain.model.DriveItem
import com.zillit.drive.domain.model.DriveSection
import com.zillit.drive.domain.model.DriveTag
import com.zillit.drive.domain.model.StorageUsage
import com.zillit.drive.domain.repository.DriveRepository
import com.zillit.drive.data.local.prefs.SessionManager
import com.zillit.drive.data.remote.socket.DriveSocketManager
import org.json.JSONObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val fileBadges: Set<String> = emptySet(),
    // Multi-select
    val isSelecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    // Storage
    val storageUsage: StorageUsage? = null,
    // Tags
    val allTags: List<DriveTag> = emptyList(),
    val selectedTag: DriveTag? = null,
    // Folder list for move picker
    val rootFolders: List<Pair<String, String>> = emptyList() // id to name
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DriveRepository,
    private val sessionManager: SessionManager,
    private val socketManager: DriveSocketManager,
    private val application: Application
) : ViewModel() {

    val currentUserId: String?
        get() = sessionManager.getCachedSession()?.userId

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState
    private var loadJob: Job? = null
    private var loadId: Long = 0
    /** Maps folderId → parent_folder_id for ancestor badge bubbling */
    private var parentMap: Map<String, String> = emptyMap()

    init {
        loadContents()
        loadFavoriteIds()
        loadStorageUsage()
        loadTags()
        setupSocket()
    }

    fun loadContents(folderId: String? = null) {
        loadJob?.cancel()
        loadId++
        val myLoadId = loadId

        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Build separate options for files and folders (matching web/iOS)
            val fileOptions = mutableMapOf<String, String>()
            val folderOptions = mutableMapOf<String, String>()
            val sortOpts = mapOf("sort_by" to _uiState.value.sortBy, "sort_order" to _uiState.value.sortOrder)
            fileOptions.putAll(sortOpts)
            folderOptions.putAll(sortOpts)

            if (folderId != null) {
                fileOptions["folder_id"] = folderId
                folderOptions["parent_folder_id"] = folderId  // folders use parent_folder_id!
            } else {
                // Root level: apply section filter (mine / shared)
                val qf = _uiState.value.driveSection.apiValue
                fileOptions["quick_filter"] = qf
                folderOptions["quick_filter"] = qf
            }

            _uiState.value.selectedTag?.let { tag ->
                fileOptions["tag_id"] = tag.id
                folderOptions["tag_id"] = tag.id
            }

            try {
                // Two separate API calls (matches web behavior)
                val filesResult = repository.getFiles(fileOptions)
                val foldersResult = repository.getFolders(folderOptions)

                // Stale check — discard if a newer load started
                if (myLoadId != loadId) return@launch

                val files = filesResult.getOrDefault(emptyList())
                val folders = foldersResult.getOrDefault(emptyList())

                // Build parent map for ancestor badge bubbling
                parentMap = folders
                    .filter { !it.parentFolderId.isNullOrEmpty() }
                    .associate { it.id to it.parentFolderId!! }

                // Client-side filtering (matches web combinedData useMemo)
                val filteredFiles = if (folderId != null) {
                    files.filter { it.folderId == folderId }
                } else {
                    files.filter { it.folderId == null || it.folderId.isNullOrEmpty() }
                }
                val filteredFolders = if (folderId != null) {
                    folders.filter { it.parentFolderId == folderId }
                } else {
                    folders.filter { it.parentFolderId == null || it.parentFolderId.isNullOrEmpty() }
                }

                val items = buildList {
                    addAll(filteredFolders.map { folder ->
                        DriveItem.Folder(folder.copy(
                            isFavorite = folder.id in _uiState.value.favoriteFolderIds
                        ))
                    })
                    addAll(filteredFiles.map { file ->
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (myLoadId != loadId) return@launch
                _uiState.update { it.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load contents"
                ) }
            }
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

    // ─── Move Item ───

    fun moveItem(itemId: String, itemType: String, targetFolderId: String?) {
        viewModelScope.launch {
            val result = when (itemType) {
                "file" -> repository.moveFile(itemId, targetFolderId)
                "folder" -> repository.moveFolder(itemId, targetFolderId)
                else -> return@launch
            }
            result.fold(
                onSuccess = {
                    // Remove from current list since it moved
                    _uiState.update { state ->
                        state.copy(items = state.items.filter { it.id != itemId })
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(error = error.message ?: "Failed to move item") }
                }
            )
        }
    }

    // ─── Rename Item ───

    fun renameItem(itemId: String, itemType: String, newName: String) {
        viewModelScope.launch {
            val result = when (itemType) {
                "file" -> repository.updateFile(itemId, mapOf("file_name" to newName))
                "folder" -> repository.updateFolder(itemId, mapOf("folder_name" to newName))
                else -> return@launch
            }
            result.fold(
                onSuccess = {
                    // Update item name in local list
                    _uiState.update { state ->
                        state.copy(items = state.items.map { item ->
                            when {
                                item.id == itemId && item is DriveItem.File ->
                                    DriveItem.File(item.file.copy(fileName = newName))
                                item.id == itemId && item is DriveItem.Folder ->
                                    DriveItem.Folder(item.folder.copy(folderName = newName))
                                else -> item
                            }
                        })
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(error = error.message ?: "Failed to rename item") }
                }
            )
        }
    }

    // ─── Multi-Select ───

    fun enterSelectMode(initialItem: DriveItem? = null) {
        _uiState.update { state ->
            state.copy(
                isSelecting = true,
                selectedIds = if (initialItem != null) setOf(initialItem.id) else emptySet()
            )
        }
    }

    fun exitSelectMode() {
        _uiState.update { it.copy(isSelecting = false, selectedIds = emptySet()) }
    }

    fun toggleSelection(item: DriveItem) {
        _uiState.update { state ->
            val newIds = state.selectedIds.toMutableSet()
            if (item.id in newIds) newIds.remove(item.id) else newIds.add(item.id)
            state.copy(selectedIds = newIds)
        }
    }

    fun bulkDelete() {
        val state = _uiState.value
        if (state.selectedIds.isEmpty()) return

        viewModelScope.launch {
            val items = state.items.filter { it.id in state.selectedIds }
            val bulkItems = items.map { item ->
                val type = when (item) {
                    is DriveItem.File -> "file"
                    is DriveItem.Folder -> "folder"
                }
                item.id to type
            }

            repository.bulkDelete(bulkItems).fold(
                onSuccess = {
                    _uiState.update { s ->
                        s.copy(
                            items = s.items.filter { it.id !in state.selectedIds },
                            isSelecting = false,
                            selectedIds = emptySet()
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(error = error.message ?: "Failed to delete selected items") }
                }
            )
        }
    }

    fun bulkMove(targetFolderId: String?) {
        val state = _uiState.value
        if (state.selectedIds.isEmpty()) return

        viewModelScope.launch {
            val items = state.items.filter { it.id in state.selectedIds }
            val bulkItems = items.map { item ->
                val type = when (item) {
                    is DriveItem.File -> "file"
                    is DriveItem.Folder -> "folder"
                }
                item.id to type
            }

            repository.bulkMove(bulkItems, targetFolderId).fold(
                onSuccess = {
                    _uiState.update { s ->
                        s.copy(
                            items = s.items.filter { it.id !in state.selectedIds },
                            isSelecting = false,
                            selectedIds = emptySet()
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(error = error.message ?: "Failed to move selected items") }
                }
            )
        }
    }

    // ─── Storage ───

    private fun loadStorageUsage() {
        viewModelScope.launch {
            repository.getStorageUsage().onSuccess { usage ->
                _uiState.update { it.copy(storageUsage = usage) }
            }
        }
    }

    // ─── Tags ───

    fun loadTags() {
        viewModelScope.launch {
            repository.getTags().onSuccess { tags ->
                _uiState.update { it.copy(allTags = tags) }
            }
        }
    }

    fun setTagFilter(tag: DriveTag?) {
        _uiState.update { it.copy(selectedTag = tag) }
        loadContents(_uiState.value.currentFolderId)
    }

    // ─── Folder Picker ───

    fun loadRootFolders() {
        viewModelScope.launch {
            val options = mapOf("root" to "true", "quick_filter" to "mine")
            repository.getFolderContents(options).onSuccess { contents ->
                _uiState.update { it.copy(
                    rootFolders = contents.folders.map { f -> f.id to f.folderName }
                ) }
            }
        }
    }

    // ─── Sorting / View ───

    fun setSortBy(sortBy: String) {
        _uiState.update { it.copy(sortBy = sortBy) }
        loadContents(_uiState.value.currentFolderId)
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun switchSection(section: DriveSection) {
        if (section == _uiState.value.driveSection) return
        loadJob?.cancel()  // Cancel in-flight request to prevent race condition
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
        loadStorageUsage()
    }

    /** Force-fetches from network (pull-to-refresh / cache bypass) */
    private fun forceLoadContents(folderId: String?) {
        loadJob?.cancel()
        loadId++
        val myLoadId = loadId

        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val fileOptions = mutableMapOf<String, String>()
            val folderOptions = mutableMapOf<String, String>()
            val sortOpts = mapOf("sort_by" to _uiState.value.sortBy, "sort_order" to _uiState.value.sortOrder)
            fileOptions.putAll(sortOpts)
            folderOptions.putAll(sortOpts)

            if (folderId != null) {
                fileOptions["folder_id"] = folderId
                folderOptions["parent_folder_id"] = folderId
            } else {
                val qf = _uiState.value.driveSection.apiValue
                fileOptions["quick_filter"] = qf
                folderOptions["quick_filter"] = qf
            }

            _uiState.value.selectedTag?.let { tag ->
                fileOptions["tag_id"] = tag.id
                folderOptions["tag_id"] = tag.id
            }

            try {
                val filesResult = repository.getFiles(fileOptions)
                val foldersResult = repository.getFolders(folderOptions)

                if (myLoadId != loadId) return@launch

                val files = filesResult.getOrDefault(emptyList())
                val folders = foldersResult.getOrDefault(emptyList())

                val filteredFiles = if (folderId != null) {
                    files.filter { it.folderId == folderId }
                } else {
                    files.filter { it.folderId == null || it.folderId.isNullOrEmpty() }
                }
                val filteredFolders = if (folderId != null) {
                    folders.filter { it.parentFolderId == folderId }
                } else {
                    folders.filter { it.parentFolderId == null || it.parentFolderId.isNullOrEmpty() }
                }

                val items = buildList {
                    addAll(filteredFolders.map { folder ->
                        DriveItem.Folder(folder.copy(
                            isFavorite = folder.id in _uiState.value.favoriteFolderIds
                        ))
                    })
                    addAll(filteredFiles.map { file ->
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (myLoadId != loadId) return@launch
                _uiState.update { it.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load contents"
                ) }
            }
        }
    }

    // ─── Batch Download as ZIP ───

    fun downloadAllAsZip() {
        val state = _uiState.value
        val fileIds = if (state.isSelecting && state.selectedIds.isNotEmpty()) {
            // Download selected files only
            state.items
                .filterIsInstance<DriveItem.File>()
                .filter { it.id in state.selectedIds }
                .map { it.id }
        } else {
            // Download all files in current view
            state.items.filterIsInstance<DriveItem.File>().map { it.id }
        }

        if (fileIds.isEmpty()) {
            _uiState.update { it.copy(error = "No files to download") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            repository.bulkDownloadUrls(fileIds).fold(
                onSuccess = { urls ->
                    _uiState.update { it.copy(isLoading = false) }
                    if (urls.isNotEmpty()) {
                        // Use DownloadManager for each URL (typically the API returns a single ZIP URL)
                        urls.forEach { url ->
                            enqueueDownload(url, "zillit_drive_files.zip")
                        }
                    }
                    // Exit select mode after download
                    if (state.isSelecting) {
                        exitSelectMode()
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to download files"
                        )
                    }
                }
            )
        }
    }

    private fun enqueueDownload(url: String, fileName: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("Zillit Drive - $fileName")
                setDescription("Downloading files from Zillit Drive")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }

            val downloadManager = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to start download: ${e.message}") }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ─── Socket.IO Real-Time Events ───

    private fun setupSocket() {
        val session = sessionManager.getCachedSession() ?: return
        socketManager.connect(BuildConfig.SOCKET_URL, session.projectId)

        viewModelScope.launch {
            socketManager.events.collect { event ->
                when (event.event) {
                    // Drive data changes -> refetch
                    "drive:file:added", "drive:file:updated", "drive:file:deleted",
                    "drive:folder:created", "drive:folder:updated", "drive:folder:deleted" -> {
                        refresh()
                    }
                    // Sharing events -> check if relevant to current user
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
                    // New badge received -> update counts
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
                // Bubble up to ancestor folders
                var pid = parentMap[level1]
                while (pid != null) {
                    newFolderBadges[pid] = (newFolderBadges[pid] ?: 0) + 1
                    pid = parentMap[pid]
                }
            }
            if (level2 != null) {
                newFileBadges.add(level2)
            }
            state.copy(folderBadges = newFolderBadges, fileBadges = newFileBadges)
        }
    }

    private fun markFolderRead(folderId: String) {
        val clearedCount = _uiState.value.folderBadges[folderId] ?: 0
        _uiState.update { state ->
            val newBadges = state.folderBadges.toMutableMap()
            newBadges.remove(folderId)
            // Decrement ancestor badge counts
            if (clearedCount > 0) {
                var pid = parentMap[folderId]
                while (pid != null) {
                    val current = newBadges[pid] ?: 0
                    val newCount = current - clearedCount
                    if (newCount <= 0) newBadges.remove(pid) else newBadges[pid] = newCount
                    pid = parentMap[pid]
                }
            }
            state.copy(folderBadges = newBadges)
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
