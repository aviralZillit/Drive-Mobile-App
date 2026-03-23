package com.zillit.drive.presentation.upload

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zillit.drive.domain.model.UploadPart
import com.zillit.drive.domain.model.UploadSession
import com.zillit.drive.domain.repository.DriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

enum class UploadStatus {
    PENDING, UPLOADING, COMPLETED, FAILED, CANCELLED
}

data class UploadItemState(
    val uploadId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val progress: Float = 0f,
    val status: UploadStatus = UploadStatus.PENDING,
    val errorMessage: String? = null
)

data class UploadUiState(
    val activeUploads: List<UploadItemState> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val repository: DriveRepository,
    private val okHttpClient: OkHttpClient,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState

    private val uploadJobs = mutableMapOf<String, Job>()

    init {
        loadActiveUploads()
    }

    fun loadActiveUploads() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getActiveUploads().fold(
                onSuccess = { sessions ->
                    val items = sessions.map { session ->
                        UploadItemState(
                            uploadId = session.uploadId,
                            fileName = session.fileName,
                            fileSizeBytes = session.fileSizeBytes,
                            progress = 0f,
                            status = UploadStatus.PENDING
                        )
                    }
                    _uiState.update { it.copy(activeUploads = items, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load uploads") }
                }
            )
        }
    }

    fun startUpload(
        uri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        folderId: String?
    ) {
        val tempId = "temp_${System.currentTimeMillis()}"

        // Add a pending item immediately for UI feedback
        _uiState.update { state ->
            state.copy(
                activeUploads = state.activeUploads + UploadItemState(
                    uploadId = tempId,
                    fileName = fileName,
                    fileSizeBytes = fileSize,
                    progress = 0f,
                    status = UploadStatus.PENDING
                )
            )
        }

        val job = viewModelScope.launch {
            try {
                // Step 1: Initiate the upload to get presigned URLs
                val sessionResult = repository.initiateUpload(fileName, fileSize, folderId, mimeType)
                val session = sessionResult.getOrElse { error ->
                    updateUploadItem(tempId) { it.copy(status = UploadStatus.FAILED, errorMessage = error.message) }
                    return@launch
                }

                // Replace temp ID with real upload ID
                replaceUploadId(tempId, session.uploadId)

                updateUploadItem(session.uploadId) { it.copy(status = UploadStatus.UPLOADING) }

                // Step 2: Upload each chunk to its presigned URL
                val completedParts = mutableListOf<UploadPart>()
                val contentResolver = application.contentResolver

                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val chunkSize = session.chunkSize.toInt()
                        val buffer = ByteArray(chunkSize)

                        for (presignedUrl in session.presignedUrls.sortedBy { it.partNumber }) {
                            var totalRead = 0
                            var bytesRead: Int

                            // Read exactly one chunk
                            while (totalRead < chunkSize) {
                                bytesRead = inputStream.read(buffer, totalRead, chunkSize - totalRead)
                                if (bytesRead == -1) break
                                totalRead += bytesRead
                            }

                            if (totalRead == 0) break

                            val chunkData = if (totalRead < chunkSize) {
                                buffer.copyOfRange(0, totalRead)
                            } else {
                                buffer
                            }

                            // PUT chunk to presigned S3 URL
                            val requestBody = chunkData.toRequestBody("application/octet-stream".toMediaType())
                            val request = Request.Builder()
                                .url(presignedUrl.url)
                                .put(requestBody)
                                .build()

                            val response = okHttpClient.newCall(request).execute()
                            if (!response.isSuccessful) {
                                throw RuntimeException("Failed to upload part ${presignedUrl.partNumber}: HTTP ${response.code}")
                            }

                            val etag = response.header("ETag")
                                ?: throw RuntimeException("Missing ETag for part ${presignedUrl.partNumber}")

                            completedParts.add(
                                UploadPart(
                                    partNumber = presignedUrl.partNumber,
                                    etag = etag.replace("\"", "")
                                )
                            )

                            // Update progress
                            val progress = completedParts.size.toFloat() / session.totalParts.toFloat()
                            updateUploadItem(session.uploadId) { it.copy(progress = progress) }
                        }
                    } ?: throw RuntimeException("Could not open file input stream")
                }

                // Step 3: Complete the upload
                repository.completeUpload(session.uploadId, completedParts).fold(
                    onSuccess = {
                        updateUploadItem(session.uploadId) {
                            it.copy(status = UploadStatus.COMPLETED, progress = 1f)
                        }
                    },
                    onFailure = { error ->
                        updateUploadItem(session.uploadId) {
                            it.copy(status = UploadStatus.FAILED, errorMessage = error.message)
                        }
                    }
                )
            } catch (e: CancellationException) {
                // Coroutine was cancelled, status already set by cancelUpload
                throw e
            } catch (e: Exception) {
                // Find the current upload ID (could be temp or real)
                val currentId = _uiState.value.activeUploads
                    .firstOrNull { it.uploadId == tempId || it.fileName == fileName && it.status == UploadStatus.UPLOADING }
                    ?.uploadId ?: tempId
                updateUploadItem(currentId) {
                    it.copy(status = UploadStatus.FAILED, errorMessage = e.message ?: "Upload failed")
                }
            }
        }

        uploadJobs[tempId] = job
    }

    fun cancelUpload(uploadId: String) {
        // Cancel the coroutine job
        uploadJobs[uploadId]?.cancel()
        uploadJobs.remove(uploadId)

        updateUploadItem(uploadId) { it.copy(status = UploadStatus.CANCELLED) }

        // Abort on the server side
        viewModelScope.launch {
            repository.abortUpload(uploadId)
        }
    }

    fun removeCompletedUpload(uploadId: String) {
        _uiState.update { state ->
            state.copy(activeUploads = state.activeUploads.filter { it.uploadId != uploadId })
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun updateUploadItem(uploadId: String, transform: (UploadItemState) -> UploadItemState) {
        _uiState.update { state ->
            state.copy(
                activeUploads = state.activeUploads.map { item ->
                    if (item.uploadId == uploadId) transform(item) else item
                }
            )
        }
    }

    private fun replaceUploadId(oldId: String, newId: String) {
        _uiState.update { state ->
            state.copy(
                activeUploads = state.activeUploads.map { item ->
                    if (item.uploadId == oldId) item.copy(uploadId = newId) else item
                }
            )
        }
        // Move the job reference too
        uploadJobs[oldId]?.let { job ->
            uploadJobs[newId] = job
            uploadJobs.remove(oldId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        uploadJobs.values.forEach { it.cancel() }
        uploadJobs.clear()
    }
}
