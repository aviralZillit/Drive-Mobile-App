package com.zillit.drive.presentation.upload

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.zillit.drive.databinding.FragmentUploadBinding
import com.zillit.drive.presentation.adapter.UploadItemAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class UploadFragment : Fragment() {

    private var _binding: FragmentUploadBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UploadViewModel by viewModels()
    private lateinit var adapter: UploadItemAdapter

    private var isFabMenuOpen = false

    // Current camera capture URI (set before launching camera intent)
    private var pendingPhotoUri: Uri? = null
    private var pendingVideoUri: Uri? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { handleSelectedFile(it) }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            pendingPhotoUri?.let { uri ->
                handleCapturedFile(uri, "photo_${System.currentTimeMillis()}.jpg", "image/jpeg")
            }
        }
        pendingPhotoUri = null
    }

    private val captureVideoLauncher = registerForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success: Boolean ->
        if (success) {
            pendingVideoUri?.let { uri ->
                handleCapturedFile(uri, "video_${System.currentTimeMillis()}.mp4", "video/mp4")
            }
        }
        pendingVideoUri = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeState()
    }

    private fun setupRecyclerView() {
        adapter = UploadItemAdapter(
            onCancelClick = { item ->
                viewModel.cancelUpload(item.uploadId)
            },
            onRemoveClick = { item ->
                viewModel.removeCompletedUpload(item.uploadId)
            },
            onPauseClick = { item ->
                viewModel.pauseUpload(item.uploadId)
            },
            onResumeClick = { item ->
                viewModel.resumeUpload(item.uploadId)
            },
            onRetryClick = { item ->
                viewModel.retryUpload(item.uploadId)
            }
        )
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupListeners() {
        binding.fabSelectFile.setOnClickListener {
            if (isFabMenuOpen) {
                closeFabMenu()
                filePickerLauncher.launch(arrayOf("*/*"))
            } else {
                openFabMenu()
            }
        }

        binding.fabCapturePhoto.setOnClickListener {
            closeFabMenu()
            launchPhotoCapture()
        }

        binding.fabCaptureVideo.setOnClickListener {
            closeFabMenu()
            launchVideoCapture()
        }
    }

    private fun openFabMenu() {
        isFabMenuOpen = true
        binding.fabCapturePhoto.visibility = View.VISIBLE
        binding.fabCaptureVideo.visibility = View.VISIBLE
        binding.fabSelectFile.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
    }

    private fun closeFabMenu() {
        isFabMenuOpen = false
        binding.fabCapturePhoto.visibility = View.GONE
        binding.fabCaptureVideo.visibility = View.GONE
        binding.fabSelectFile.setImageResource(android.R.drawable.ic_input_add)
    }

    private fun launchPhotoCapture() {
        val photoFile = createCaptureFile("IMG_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            photoFile
        )
        pendingPhotoUri = uri
        takePhotoLauncher.launch(uri)
    }

    private fun launchVideoCapture() {
        val videoFile = createCaptureFile("VID_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            videoFile
        )
        pendingVideoUri = uri
        captureVideoLauncher.launch(uri)
    }

    private fun createCaptureFile(fileName: String): File {
        val captureDir = File(requireContext().cacheDir, "camera_captures")
        if (!captureDir.exists()) captureDir.mkdirs()
        return File(captureDir, fileName)
    }

    private fun handleCapturedFile(uri: Uri, fileName: String, mimeType: String) {
        val contentResolver = requireContext().contentResolver

        // Get actual file size from the content resolver
        var fileSize = 0L
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0) {
                    fileSize = it.getLong(sizeIndex)
                }
            }
        }

        val folderId = arguments?.getString("folderId")

        viewModel.startUpload(
            uri = uri,
            fileName = fileName,
            fileSize = fileSize,
            mimeType = mimeType,
            folderId = folderId
        )
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility =
                        if (state.isLoading && state.activeUploads.isEmpty()) View.VISIBLE else View.GONE
                    binding.emptyState.visibility =
                        if (!state.isLoading && state.activeUploads.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerView.visibility =
                        if (state.activeUploads.isNotEmpty()) View.VISIBLE else View.GONE

                    adapter.submitList(state.activeUploads.toList())

                    state.error?.let { errorMsg ->
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    private fun handleSelectedFile(uri: Uri) {
        val contentResolver = requireContext().contentResolver

        var fileName = "unknown_file"
        var fileSize = 0L

        // Query file metadata from the content resolver
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = it.getString(nameIndex) ?: fileName
                }
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0) {
                    fileSize = it.getLong(sizeIndex)
                }
            }
        }

        // Determine MIME type
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

        // Get folderId from arguments if navigating from a specific folder
        val folderId = arguments?.getString("folderId")

        viewModel.startUpload(
            uri = uri,
            fileName = fileName,
            fileSize = fileSize,
            mimeType = mimeType,
            folderId = folderId
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
