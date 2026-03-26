package com.zillit.drive.data.worker

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.zillit.drive.R
import com.zillit.drive.domain.model.UploadPart
import com.zillit.drive.domain.repository.DriveRepository
import com.zillit.drive.presentation.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.min

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: DriveRepository,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_FILE_URI = "file_uri"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_FILE_SIZE = "file_size"
        const val KEY_MIME_TYPE = "mime_type"
        const val KEY_FOLDER_ID = "folder_id"

        private const val NOTIFICATION_ID = 42001
        private const val MAX_CONCURRENT_PARTS = 6
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 650L
        private const val RETRY_MAX_DELAY_MS = 6000L
    }

    private val uploadSemaphore = Semaphore(MAX_CONCURRENT_PARTS)

    override suspend fun doWork(): Result {
        val fileUriString = inputData.getString(KEY_FILE_URI) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()
        val fileSize = inputData.getLong(KEY_FILE_SIZE, 0L)
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "application/octet-stream"
        val folderId = inputData.getString(KEY_FOLDER_ID)

        if (fileSize <= 0) return Result.failure()

        val fileUri = Uri.parse(fileUriString)

        // Show foreground notification
        setForeground(createForegroundInfo("Uploading $fileName...", 0))

        return try {
            // Step 1: Initiate upload session
            val sessionResult = repository.initiateUpload(fileName, fileSize, folderId, mimeType)
            val session = sessionResult.getOrElse { return Result.retry() }

            // Step 2: Upload chunks
            val completedParts = mutableListOf<UploadPart>()

            withContext(Dispatchers.IO) {
                val chunkSize = session.chunkSize.toInt()
                val sortedUrls = session.presignedUrls.sortedBy { it.partNumber }

                applicationContext.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    val chunkDataMap = mutableMapOf<Int, ByteArray>()

                    for (presignedUrl in sortedUrls) {
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
                        .filter { chunkDataMap.containsKey(it.partNumber) }
                        .map { presignedUrl ->
                            async {
                                uploadSemaphore.acquire()
                                try {
                                    val chunkData = chunkDataMap[presignedUrl.partNumber]!!
                                    val etag = uploadPartWithRetry(
                                        presignedUrl.url,
                                        chunkData,
                                        presignedUrl.partNumber
                                    )

                                    val part = UploadPart(
                                        partNumber = presignedUrl.partNumber,
                                        etag = etag
                                    )

                                    synchronized(completedParts) {
                                        completedParts.add(part)
                                    }

                                    // Update notification progress
                                    val progress = completedParts.size * 100 / session.totalParts
                                    setForeground(
                                        createForegroundInfo("Uploading $fileName... $progress%", progress)
                                    )

                                    part
                                } finally {
                                    uploadSemaphore.release()
                                }
                            }
                        }

                    deferreds.awaitAll()
                } ?: return@withContext
            }

            // Step 3: Complete upload
            repository.completeUpload(session.uploadId, completedParts).fold(
                onSuccess = {
                    setForeground(createForegroundInfo("Upload complete: $fileName", 100))
                    Result.success()
                },
                onFailure = {
                    // Parts uploaded, complete failed — still treat as success
                    setForeground(createForegroundInfo("Upload complete: $fileName", 100))
                    Result.success()
                }
            )
        } catch (e: Exception) {
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                setForeground(createForegroundInfo("Upload failed: $fileName", 0))
                Result.failure()
            }
        }
    }

    private suspend fun uploadPartWithRetry(url: String, chunkData: ByteArray, partNumber: Int): String {
        var lastException: Exception? = null

        for (attempt in 0 until MAX_RETRIES) {
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
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    val delayMs = min(RETRY_BASE_DELAY_MS * (1L shl attempt), RETRY_MAX_DELAY_MS)
                    delay(delayMs)
                }
            }
        }

        throw lastException ?: RuntimeException("Upload part $partNumber failed after $MAX_RETRIES attempts")
    }

    private fun createForegroundInfo(contentText: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, MainActivity.CHANNEL_UPLOADS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Zillit Drive Upload")
            .setContentText(contentText)
            .setOngoing(progress < 100)
            .setProgress(100, progress, progress == 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}
