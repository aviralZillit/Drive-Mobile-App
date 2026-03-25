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

@AndroidEntryPoint
class UploadFragment : Fragment() {

    private var _binding: FragmentUploadBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UploadViewModel by viewModels()
    private lateinit var adapter: UploadItemAdapter

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { handleSelectedFile(it) }
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
            filePickerLauncher.launch(arrayOf("*/*"))
        }
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
