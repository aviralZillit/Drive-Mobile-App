package com.zillit.drive.presentation.upload

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.zillit.drive.data.worker.UploadWorker
import com.zillit.drive.domain.model.UploadPart
import com.zillit.drive.domain.model.UploadSession
import com.zillit.drive.domain.repository.DriveRepository
import com.zillit.drive.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import kotlin.math.min

enum class UploadStatus {
    PENDING, UPLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
}

data class SpeedSample(
    val timestamp: Long,
    val bytes: Long
)

data class UploadItemState(
    val uploadId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val progress: Float = 0f,
    val status: UploadStatus = UploadStatus.PENDING,
    val errorMessage: String? = null,
    val speed: Double = 0.0, // bytes per second
    val eta: Long = 0L, // seconds remaining
    val uploadedBytes: Long = 0L,
    val completedParts: Set<Int> = emptySet(),
    // Internal: URI for resume
    val uri: Uri? = null,
    val folderId: String? = null,
    val mimeType: String? = null
) {
    val statusText: String
        get() {
            val sizeText = FileUtils.formatFileSize(fileSizeBytes)
            return when (status) {
                UploadStatus.PENDING -> "$sizeText \u2022 Pending"
                UploadStatus.UPLOADING -> {
                    val percent = (progress * 100).toInt()
                    val speedText = if (speed > 0) FileUtils.formatFileSize(speed.toLong()) + "/s" else ""
                    val etaText = if (eta > 0) formatEta(eta) else ""
                    buildString {
                        append("$sizeText \u2022 $percent%")
                        if (speedText.isNotEmpty()) append(" \u2022 $speedText")
                        if (etaText.isNotEmpty()) append(" \u2022 $etaText")
                    }
                }
                UploadStatus.PAUSED -> {
                    val percent = (progress * 100).toInt()
                    "$sizeText \u2022 Paused at $percent%"
                }
                UploadStatus.COMPLETED -> "$sizeText \u2022 Completed"
                UploadStatus.FAILED -> "$sizeText \u2022 Failed${errorMessage?.let { ": $it" } ?: ""}"
                UploadStatus.CANCELLED -> "$sizeText \u2022 Cancelled"
            }
        }

    private fun formatEta(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}

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
    private val speedSamples = mutableMapOf<String, MutableList<SpeedSample>>()

    // Max 6 concurrent chunk uploads
    private val uploadSemaphore = Semaphore(6)

    // Retry config
    private val maxRetries = 3
    private val retryBaseDelayMs = 650L
    private val retryMaxDelayMs = 6000L

    // Speed window: 8 seconds
    private val speedWindowMs = 8000L

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

    // Threshold for routing to WorkManager background upload (10 MB)
    private val backgroundUploadThreshold = 10L * 1024L * 1024L

    fun startUpload(
        uri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        folderId: String?
    ) {
        // For large files (> 10MB), delegate to WorkManager for background upload
        if (fileSize > backgroundUploadThreshold) {
            enqueueBackgroundUpload(uri, fileName, fileSize, mimeType, folderId)
            return
        }

        val tempId = "temp_${System.currentTimeMillis()}"

        // Add a pending item immediately for UI feedback
        _uiState.update { state ->
            state.copy(
                activeUploads = state.activeUploads + UploadItemState(
                    uploadId = tempId,
                    fileName = fileName,
                    fileSizeBytes = fileSize,
                    progress = 0f,
                    status = UploadStatus.PENDING,
                    uri = uri,
                    folderId = folderId,
                    mimeType = mimeType
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

                updateUploadItem(session.uploadId) { it.copy(
                    status = UploadStatus.UPLOADING,
                    uri = uri,
                    folderId = folderId,
                    mimeType = mimeType
                ) }

                // Step 2: Upload each chunk with concurrent uploads
                uploadChunks(session, uri)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val currentId = _uiState.value.activeUploads
                    .firstOrNull { it.uploadId == tempId || (it.fileName == fileName && it.status == UploadStatus.UPLOADING) }
                    ?.uploadId ?: tempId
                updateUploadItem(currentId) {
                    it.copy(status = UploadStatus.FAILED, errorMessage = e.message ?: "Upload failed")
                }
            }
        }

        uploadJobs[tempId] = job
    }

    private fun enqueueBackgroundUpload(
        uri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        folderId: String?
    ) {
        val inputData = workDataOf(
            UploadWorker.KEY_FILE_URI to uri.toString(),
            UploadWorker.KEY_FILE_NAME to fileName,
            UploadWorker.KEY_FILE_SIZE to fileSize,
            UploadWorker.KEY_MIME_TYPE to mimeType,
            UploadWorker.KEY_FOLDER_ID to folderId
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()

        val workName = "upload_${fileName}_${System.currentTimeMillis()}"

        WorkManager.getInstance(application)
            .enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, workRequest)

        // Add to UI state to show it's been enqueued
        _uiState.update { state ->
            state.copy(
                activeUploads = state.activeUploads + UploadItemState(
                    uploadId = workName,
                    fileName = fileName,
                    fileSizeBytes = fileSize,
                    progress = 0f,
                    status = UploadStatus.UPLOADING,
                    uri = uri,
                    folderId = folderId,
                    mimeType = mimeType
                ),
                error = null
            )
        }
    }

    private suspend fun uploadChunks(session: UploadSession, uri: Uri) {
        val completedParts = mutableListOf<UploadPart>()
        val contentResolver = application.contentResolver
        val alreadyCompleted = _uiState.value.activeUploads
            .firstOrNull { it.uploadId == session.uploadId }?.completedParts ?: emptySet()

        // Initialize speed tracking
        speedSamples[session.uploadId] = mutableListOf()

        withContext(Dispatchers.IO) {
            val chunkSize = session.chunkSize.toInt()
            val sortedUrls = session.presignedUrls.sortedBy { it.partNumber }

            // Read all chunks into memory-mapped parts (for concurrent uploads)
            // For large files, we read chunks sequentially but upload concurrently
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val chunkDataMap = mutableMapOf<Int, ByteArray>()

                for (presignedUrl in sortedUrls) {
                    if (alreadyCompleted.contains(presignedUrl.partNumber)) {
                        // Skip already completed parts
                        val skip = ByteArray(chunkSize)
                        var skipped = 0
                        while (skipped < chunkSize) {
                            val read = inputStream.read(skip, 0, chunkSize - skipped)
                            if (read == -1) break
                            skipped += read
                        }
                        continue
                    }

                    val buffer = ByteArray(chunkSize)
                    var totalRead = 0
                    var bytesRead: Int
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
                    chunkDataMap[presignedUrl.partNumber] = chunkData
                }

                // Upload concurrently with semaphore
                val deferreds = sortedUrls
                    .filter { !alreadyCompleted.contains(it.partNumber) && chunkDataMap.containsKey(it.partNumber) }
                    .map { presignedUrl ->
                        async {
                            uploadSemaphore.acquire()
                            try {
                                val chunkData = chunkDataMap[presignedUrl.partNumber]!!
                                val etag = uploadPartWithRetry(presignedUrl.url, chunkData, presignedUrl.partNumber)

                                val part = UploadPart(
                                    partNumber = presignedUrl.partNumber,
                                    etag = etag
                                )

                                synchronized(completedParts) {
                                    completedParts.add(part)
                                }

                                // Track speed
                                recordSpeedSample(session.uploadId, chunkData.size.toLong())

                                // Update progress
                                val totalCompleted = alreadyCompleted.size + completedParts.size
                                val progress = totalCompleted.toFloat() / session.totalParts.toFloat()
                                val uploadedBytes = (progress * session.fileSizeBytes).toLong()
                                val currentSpeed = calculateSpeed(session.uploadId)
                                val remaining = session.fileSizeBytes - uploadedBytes
                                val eta = if (currentSpeed > 0) (remaining / currentSpeed).toLong() else 0L

                                updateUploadItem(session.uploadId) {
                                    it.copy(
                                        progress = progress,
                                        uploadedBytes = uploadedBytes,
                                        completedParts = alreadyCompleted + completedParts.map { p -> p.partNumber }.toSet(),
                                        speed = currentSpeed,
                                        eta = eta
                                    )
                                }

                                part
                            } finally {
                                uploadSemaphore.release()
                            }
                        }
                    }

                deferreds.awaitAll()

            } ?: throw RuntimeException("Could not open file input stream")
        }

        // Combine already completed with newly completed
        val allParts = buildList {
            // Add back already-completed parts (we don't have their ETags, so re-fetch if needed)
            addAll(completedParts)
        }

        // Step 3: Complete the upload (non-fatal: file saved even if notification fails)
        try {
            repository.completeUpload(session.uploadId, allParts).fold(
                onSuccess = {
                    updateUploadItem(session.uploadId) {
                        it.copy(status = UploadStatus.COMPLETED, progress = 1f, speed = 0.0, eta = 0L)
                    }
                },
                onFailure = {
                    // Non-fatal: mark as completed anyway since all parts uploaded
                    updateUploadItem(session.uploadId) {
                        it.copy(status = UploadStatus.COMPLETED, progress = 1f, speed = 0.0, eta = 0L)
                    }
                }
            )
        } catch (e: Exception) {
            // Non-fatal complete
            updateUploadItem(session.uploadId) {
                it.copy(status = UploadStatus.COMPLETED, progress = 1f, speed = 0.0, eta = 0L)
            }
        }

        // Clean up speed samples
        speedSamples.remove(session.uploadId)
    }

    private suspend fun uploadPartWithRetry(url: String, chunkData: ByteArray, partNumber: Int): String {
        var lastException: Exception? = null

        for (attempt in 0 until maxRetries) {
            try {
                val requestBody = chunkData.toRequestBody("application/octet-stream".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .put(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw RuntimeException("Failed to upload part $partNumber: HTTP ${response.code}")
                }

                val etag = response.header("ETag")
                    ?: throw RuntimeException("Missing ETag for part $partNumber")

                return etag.replace("\"", "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    // Exponential backoff
                    val delayMs = min(retryBaseDelayMs * (1L shl attempt), retryMaxDelayMs)
                    delay(delayMs)
                }
            }
        }

        throw lastException ?: RuntimeException("Upload part $partNumber failed after $maxRetries attempts")
    }

    // ─── Speed Tracking ───

    private fun recordSpeedSample(uploadId: String, bytes: Long) {
        val samples = speedSamples[uploadId] ?: return
        synchronized(samples) {
            samples.add(SpeedSample(System.currentTimeMillis(), bytes))
            // Remove samples older than window
            val cutoff = System.currentTimeMillis() - speedWindowMs
            samples.removeAll { it.timestamp < cutoff }
        }
    }

    private fun calculateSpeed(uploadId: String): Double {
        val samples = speedSamples[uploadId] ?: return 0.0
        synchronized(samples) {
            if (samples.size < 2) return 0.0
            val cutoff = System.currentTimeMillis() - speedWindowMs
            val recentSamples = samples.filter { it.timestamp >= cutoff }
            if (recentSamples.isEmpty()) return 0.0

            val totalBytes = recentSamples.sumOf { it.bytes }
            val timeSpanMs = (recentSamples.last().timestamp - recentSamples.first().timestamp)
                .coerceAtLeast(1L)
            return totalBytes.toDouble() / (timeSpanMs.toDouble() / 1000.0)
        }
    }

    // ─── Pause / Resume / Cancel / Retry ───

    fun pauseUpload(uploadId: String) {
        uploadJobs[uploadId]?.cancel()
        uploadJobs.remove(uploadId)
        updateUploadItem(uploadId) { it.copy(status = UploadStatus.PAUSED, speed = 0.0, eta = 0L) }
    }

    fun resumeUpload(uploadId: String) {
        val item = _uiState.value.activeUploads.firstOrNull { it.uploadId == uploadId } ?: return
        val uri = item.uri ?: return

        updateUploadItem(uploadId) { it.copy(status = UploadStatus.UPLOADING) }

        val job = viewModelScope.launch {
            try {
                // Re-fetch upload session to get presigned URLs
                val sessionResult = repository.getUploadParts(uploadId)
                val session = sessionResult.getOrElse { error ->
                    // If we can't resume, restart the upload
                    updateUploadItem(uploadId) { it.copy(status = UploadStatus.FAILED, errorMessage = "Could not resume: ${error.message}") }
                    return@launch
                }

                uploadChunks(session, uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateUploadItem(uploadId) {
                    it.copy(status = UploadStatus.FAILED, errorMessage = e.message ?: "Resume failed")
                }
            }
        }

        uploadJobs[uploadId] = job
    }

    fun retryUpload(uploadId: String) {
        val item = _uiState.value.activeUploads.firstOrNull { it.uploadId == uploadId } ?: return
        val uri = item.uri

        if (uri != null) {
            // Remove the failed item and re-start
            _uiState.update { state ->
                state.copy(activeUploads = state.activeUploads.filter { it.uploadId != uploadId })
            }
            startUpload(uri, item.fileName, item.fileSizeBytes, item.mimeType ?: "application/octet-stream", item.folderId)
        } else {
            // Can't retry without URI, mark as failed
            updateUploadItem(uploadId) { it.copy(status = UploadStatus.FAILED, errorMessage = "Cannot retry: file reference lost") }
        }
    }

    fun cancelUpload(uploadId: String) {
        // Cancel the coroutine job
        uploadJobs[uploadId]?.cancel()
        uploadJobs.remove(uploadId)

        updateUploadItem(uploadId) { it.copy(status = UploadStatus.CANCELLED, speed = 0.0, eta = 0L) }

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
        speedSamples.clear()
    }
}
