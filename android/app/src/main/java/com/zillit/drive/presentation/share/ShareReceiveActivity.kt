package com.zillit.drive.presentation.share

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.zillit.drive.domain.repository.DriveRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiveActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: DriveRepository

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build a simple progress UI
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
        }

        statusText = TextView(this).apply {
            text = "Uploading to Zillit Drive..."
            textSize = 18f
            gravity = Gravity.CENTER
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 32
            }
            isIndeterminate = true
        }

        layout.addView(statusText)
        layout.addView(progressBar)
        setContentView(layout)

        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) {
            finishWithError("No data received")
            return
        }

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    uploadFiles(listOf(uri))
                } else {
                    finishWithError("No file found in shared content")
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (!uris.isNullOrEmpty()) {
                    uploadFiles(uris)
                } else {
                    finishWithError("No files found in shared content")
                }
            }
            else -> {
                finishWithError("Unsupported share action")
            }
        }
    }

    private fun uploadFiles(uris: List<Uri>) {
        val totalFiles = uris.size
        var completedFiles = 0
        var failedFiles = 0

        statusText.text = "Uploading $totalFiles file${if (totalFiles != 1) "s" else ""} to Zillit Drive..."
        progressBar.isIndeterminate = false
        progressBar.max = totalFiles
        progressBar.progress = 0

        lifecycleScope.launch {
            for (uri in uris) {
                try {
                    val fileInfo = getFileInfo(uri)
                    val result = withContext(Dispatchers.IO) {
                        repository.initiateUpload(
                            fileName = fileInfo.name,
                            fileSizeBytes = fileInfo.size,
                            folderId = null,
                            mimeType = fileInfo.mimeType
                        )
                    }

                    result.fold(
                        onSuccess = {
                            completedFiles++
                            statusText.text = "Uploaded $completedFiles of $totalFiles..."
                            progressBar.progress = completedFiles
                        },
                        onFailure = {
                            failedFiles++
                            completedFiles++
                            progressBar.progress = completedFiles
                        }
                    )
                } catch (e: Exception) {
                    failedFiles++
                    completedFiles++
                    progressBar.progress = completedFiles
                }
            }

            // All done
            val message = when {
                failedFiles == 0 -> "Successfully queued $totalFiles file${if (totalFiles != 1) "s" else ""} for upload"
                failedFiles == totalFiles -> "Failed to upload all files"
                else -> "Uploaded ${totalFiles - failedFiles} of $totalFiles files"
            }

            Toast.makeText(this@ShareReceiveActivity, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun getFileInfo(uri: Uri): FileInfo {
        var name = "shared_file_${System.currentTimeMillis()}"
        var size = 0L

        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = it.getString(nameIndex) ?: name
                }
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0) {
                    size = it.getLong(sizeIndex)
                }
            }
        }

        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

        return FileInfo(name, size, mimeType)
    }

    private fun finishWithError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private data class FileInfo(
        val name: String,
        val size: Long,
        val mimeType: String
    )
}
