package com.zillit.drive.presentation.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zillit.drive.domain.model.DriveComment
import com.zillit.drive.domain.model.DriveFile
import com.zillit.drive.domain.model.DriveTag
import com.zillit.drive.domain.model.DriveVersion
import com.zillit.drive.domain.model.ShareLink
import com.zillit.drive.domain.repository.DriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import javax.inject.Inject

data class FileDetailUiState(
    val file: DriveFile? = null,
    val comments: List<DriveComment> = emptyList(),
    val versions: List<DriveVersion> = emptyList(),
    val tags: List<DriveTag> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val shareLink: ShareLink? = null,
    val isDeleted: Boolean = false,
    val isAddingComment: Boolean = false,
    val isGeneratingLink: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedFileUri: Uri? = null,
    val downloadError: String? = null
)

@HiltViewModel
class FileDetailViewModel @Inject constructor(
    private val repository: DriveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileDetailUiState())
    val uiState: StateFlow<FileDetailUiState> = _uiState

    private var fileId: String? = null

    fun loadFile(fileId: String) {
        this.fileId = fileId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.getFile(fileId).fold(
                onSuccess = { file ->
                    _uiState.update { it.copy(file = file, isLoading = false) }
                    // Load related data in parallel after file loads
                    loadComments()
                    loadVersions()
                    loadTags()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load file details"
                        )
                    }
                }
            )
        }
    }

    fun loadComments() {
        val id = fileId ?: return
        viewModelScope.launch {
            repository.getComments(id).fold(
                onSuccess = { comments ->
                    _uiState.update { it.copy(comments = comments) }
                },
                onFailure = { /* silently fail for secondary data */ }
            )
        }
    }

    fun loadVersions() {
        val id = fileId ?: return
        viewModelScope.launch {
            repository.getFileVersions(id).fold(
                onSuccess = { versions ->
                    _uiState.update { it.copy(versions = versions) }
                },
                onFailure = { /* silently fail for secondary data */ }
            )
        }
    }

    fun loadTags() {
        val id = fileId ?: return
        viewModelScope.launch {
            repository.getItemTags(id, "file").fold(
                onSuccess = { tags ->
                    _uiState.update { it.copy(tags = tags) }
                },
                onFailure = { /* silently fail for secondary data */ }
            )
        }
    }

    fun addComment(text: String) {
        val id = fileId ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAddingComment = true) }

            repository.addComment(id, text.trim()).fold(
                onSuccess = { comment ->
                    _uiState.update {
                        it.copy(
                            comments = it.comments + comment,
                            isAddingComment = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isAddingComment = false,
                            error = error.message ?: "Failed to add comment"
                        )
                    }
                }
            )
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            repository.deleteComment(commentId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(comments = it.comments.filter { c -> c.id != commentId })
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(error = error.message ?: "Failed to delete comment")
                    }
                }
            )
        }
    }

    fun generateShareLink() {
        val id = fileId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingLink = true) }

            repository.generateShareLink(id).fold(
                onSuccess = { link ->
                    _uiState.update {
                        it.copy(shareLink = link, isGeneratingLink = false)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isGeneratingLink = false,
                            error = error.message ?: "Failed to generate share link"
                        )
                    }
                }
            )
        }
    }

    fun deleteFile() {
        val id = fileId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            repository.deleteFile(id).fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, isDeleted = true) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to delete file"
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearDownloadError() {
        _uiState.update { it.copy(downloadError = null) }
    }

    fun downloadFile(context: Context) {
        val id = fileId ?: return
        val fileName = _uiState.value.file?.fileName ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f, downloadError = null) }

            try {
                val streamResult = repository.getFileStreamUrl(id)
                streamResult.fold(
                    onSuccess = { streamUrl ->
                        withContext(Dispatchers.IO) {
                            val downloadDir = File(context.cacheDir, "drive_downloads")
                            if (!downloadDir.exists()) downloadDir.mkdirs()
                            val targetFile = File(downloadDir, fileName)

                            val url = URL(streamUrl)
                            val connection = url.openConnection()
                            connection.connect()
                            val totalSize = connection.contentLength.toLong()

                            connection.getInputStream().use { input ->
                                targetFile.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    var bytesRead: Long = 0
                                    var read: Int
                                    while (input.read(buffer).also { read = it } != -1) {
                                        output.write(buffer, 0, read)
                                        bytesRead += read
                                        if (totalSize > 0) {
                                            val progress = bytesRead.toFloat() / totalSize.toFloat()
                                            withContext(Dispatchers.Main) {
                                                _uiState.update { it.copy(downloadProgress = progress) }
                                            }
                                        }
                                    }
                                }
                            }

                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                targetFile
                            )

                            withContext(Dispatchers.Main) {
                                _uiState.update {
                                    it.copy(
                                        isDownloading = false,
                                        downloadProgress = 1f,
                                        downloadedFileUri = uri
                                    )
                                }
                            }
                        }
                    },
                    onFailure = { error ->
                        val msg = error.message ?: "Failed to get download URL"
                        val userMsg = if (msg.contains("insufficient_permissions") || msg.contains("403") || msg.contains("Forbidden")) {
                            "You don't have download permission for this file"
                        } else {
                            msg
                        }
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                downloadError = userMsg
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadError = e.message ?: "Download failed"
                    )
                }
            }
        }
    }

    fun openFile(context: Context) {
        val uri = _uiState.value.downloadedFileUri ?: return
        val file = _uiState.value.file ?: return

        val mimeType = file.mimeType.ifBlank {
            val ext = MimeTypeMap.getFileExtensionFromUrl(file.fileName)
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(error = "No app available to open this file type")
            }
        }
    }

    /**
     * Preview/Open uses the /preview endpoint which only requires VIEW permission.
     * This is separate from downloadFile() which uses /stream and requires DOWNLOAD permission.
     */
    fun downloadAndOpen(context: Context) {
        val id = fileId ?: return
        val fileName = _uiState.value.file?.fileName ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f, downloadError = null) }

            try {
                // Use preview endpoint - only requires VIEW permission
                val previewResult = repository.getFilePreviewUrl(id)
                previewResult.fold(
                    onSuccess = { previewUrl ->
                        withContext(Dispatchers.IO) {
                            val downloadDir = File(context.cacheDir, "drive_downloads")
                            if (!downloadDir.exists()) downloadDir.mkdirs()
                            val targetFile = File(downloadDir, "preview_$fileName")

                            val url = URL(previewUrl)
                            val connection = url.openConnection()
                            connection.connect()
                            val totalSize = connection.contentLength.toLong()

                            connection.getInputStream().use { input ->
                                targetFile.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    var bytesRead: Long = 0
                                    var read: Int
                                    while (input.read(buffer).also { read = it } != -1) {
                                        output.write(buffer, 0, read)
                                        bytesRead += read
                                        if (totalSize > 0) {
                                            val progress = bytesRead.toFloat() / totalSize.toFloat()
                                            withContext(Dispatchers.Main) {
                                                _uiState.update { it.copy(downloadProgress = progress) }
                                            }
                                        }
                                    }
                                }
                            }

                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                targetFile
                            )

                            withContext(Dispatchers.Main) {
                                _uiState.update {
                                    it.copy(
                                        isDownloading = false,
                                        downloadProgress = 1f,
                                        downloadedFileUri = uri
                                    )
                                }
                                openFile(context)
                            }
                        }
                    },
                    onFailure = { error ->
                        val msg = error.message ?: "Failed to preview file"
                        val userMsg = if (msg.contains("insufficient_permissions") || msg.contains("403") || msg.contains("Forbidden")) {
                            "You don't have permission to view this file"
                        } else {
                            msg
                        }
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                downloadError = userMsg
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadError = e.message ?: "Preview failed"
                    )
                }
            }
        }
    }
}
