package com.zillit.drive.presentation.file

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.chip.Chip
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zillit.drive.R
import com.zillit.drive.databinding.FragmentFileDetailBinding
import com.zillit.drive.domain.model.DriveComment
import com.zillit.drive.domain.model.DriveVersion
import com.zillit.drive.presentation.editor.OnlyOfficeEditorActivity
import com.zillit.drive.util.FileUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class FileDetailFragment : Fragment() {

    private var _binding: FragmentFileDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FileDetailViewModel by viewModels()

    private lateinit var commentsAdapter: CommentsAdapter
    private lateinit var versionsAdapter: VersionsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupListeners()
        observeState()

        val fileId = arguments?.getString("fileId")
        if (fileId != null) {
            viewModel.loadFile(fileId)
        } else {
            Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    private fun setupRecyclerViews() {
        commentsAdapter = CommentsAdapter { commentId ->
            showDeleteCommentDialog(commentId)
        }
        binding.rvComments.apply {
            adapter = commentsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        versionsAdapter = VersionsAdapter()
        binding.rvVersions.apply {
            adapter = versionsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnShare.setOnClickListener {
            viewModel.generateShareLink()
        }

        binding.btnDelete.setOnClickListener {
            showDeleteFileDialog()
        }

        binding.btnSendComment.setOnClickListener {
            submitComment()
        }

        binding.etComment.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitComment()
                true
            } else {
                false
            }
        }

        binding.btnCopyLink.setOnClickListener {
            val link = viewModel.uiState.value.shareLink?.url ?: return@setOnClickListener
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Share Link", link))
            Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        binding.btnManageAccess.setOnClickListener {
            val fileId = arguments?.getString("fileId") ?: return@setOnClickListener
            findNavController().navigate(
                R.id.action_file_detail_to_share,
                bundleOf("itemId" to fileId, "itemType" to "file")
            )
        }

        binding.btnRetry.setOnClickListener {
            val fileId = arguments?.getString("fileId") ?: return@setOnClickListener
            viewModel.loadFile(fileId)
        }

        binding.btnDownload.setOnClickListener {
            viewModel.downloadFile(requireContext())
        }

        binding.btnOpen.setOnClickListener {
            if (viewModel.uiState.value.downloadedFileUri != null) {
                viewModel.openFile(requireContext())
            } else {
                viewModel.downloadAndOpen(requireContext())
            }
        }

        binding.btnEdit?.let { btn ->
            btn.setOnClickListener {
                val fileId = arguments?.getString("fileId") ?: return@setOnClickListener
                val fileName = viewModel.uiState.value.file?.fileName ?: "Document"
                val intent = Intent(requireContext(), OnlyOfficeEditorActivity::class.java).apply {
                    putExtra(OnlyOfficeEditorActivity.EXTRA_FILE_ID, fileId)
                    putExtra(OnlyOfficeEditorActivity.EXTRA_FILE_NAME, fileName)
                }
                startActivity(intent)
            }
        }
    }

    private fun submitComment() {
        val text = binding.etComment.text.toString()
        if (text.isBlank()) return

        viewModel.addComment(text)
        binding.etComment.text?.clear()
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etComment.windowToken, 0)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Loading
                    binding.progressBar.visibility =
                        if (state.isLoading && state.file == null) View.VISIBLE else View.GONE

                    // Error
                    binding.errorState.visibility =
                        if (state.error != null && state.file == null) View.VISIBLE else View.GONE
                    binding.tvError.text = state.error

                    // Main content
                    binding.scrollView.visibility =
                        if (state.file != null && !state.isLoading) View.VISIBLE else View.GONE

                    // Transient errors shown as toast when file is already displayed
                    if (state.error != null && state.file != null) {
                        Toast.makeText(requireContext(), state.error, Toast.LENGTH_SHORT).show()
                        viewModel.clearError()
                    }

                    // File info
                    state.file?.let { file ->
                        binding.tvFileName.text = file.fileName
                        binding.tvFileSize.text = FileUtils.formatFileSize(file.fileSizeBytes)
                        binding.tvMimeType.text = file.mimeType
                        binding.tvCreatedDate.text = formatDate(file.createdOn)
                        binding.tvModifiedDate.text = formatDate(file.updatedOn)
                        binding.tvCreatedBy.text = file.createdBy

                        // Extension badge
                        val ext = file.fileExtension.uppercase()
                        binding.tvExtBadge.text = ext
                        val badgeBg = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 12f
                            setColor(FileUtils.getExtensionColor(file.fileExtension))
                        }
                        binding.tvExtBadge.background = badgeBg

                        // Show edit button only for editable file types
                        binding.btnEdit?.visibility = if (FileUtils.isEditableFile(file.fileExtension)) View.VISIBLE else View.GONE

                        // Extension label
                        binding.tvFileExtension.text = ext
                        val extLabelBg = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 8f
                            setColor(FileUtils.getExtensionColor(file.fileExtension))
                        }
                        binding.tvFileExtension.background = extLabelBg

                        // Description
                        if (!file.description.isNullOrBlank()) {
                            binding.tvDescriptionLabel.visibility = View.VISIBLE
                            binding.tvDescription.visibility = View.VISIBLE
                            binding.tvDescription.text = file.description
                        } else {
                            binding.tvDescriptionLabel.visibility = View.GONE
                            binding.tvDescription.visibility = View.GONE
                        }
                    }

                    // Share link
                    if (state.shareLink != null) {
                        binding.cardShareLink.visibility = View.VISIBLE
                        binding.tvShareLink.text = state.shareLink.url
                        binding.tvShareExpiry.text = if (state.shareLink.expiresAt != null) {
                            "Expires: ${state.shareLink.expiresAt}"
                        } else {
                            "No expiration"
                        }
                    } else {
                        binding.cardShareLink.visibility = View.GONE
                    }

                    // Share button state
                    binding.btnShare.isEnabled = !state.isGeneratingLink
                    binding.btnShare.text = if (state.isGeneratingLink) "Generating..." else "Share"

                    // Tags
                    binding.tagsContainer.removeAllViews()
                    if (state.tags.isEmpty()) {
                        binding.tvNoTags.visibility = View.VISIBLE
                    } else {
                        binding.tvNoTags.visibility = View.GONE
                        state.tags.forEach { tag ->
                            val tagView = createTagChip(tag.name, tag.color)
                            binding.tagsContainer.addView(tagView)
                        }
                    }

                    // Comments
                    binding.tvCommentsHeader.text = if (state.comments.isNotEmpty()) {
                        "Comments (${state.comments.size})"
                    } else {
                        "Comments"
                    }
                    if (state.comments.isEmpty()) {
                        binding.tvNoComments.visibility = View.VISIBLE
                        binding.rvComments.visibility = View.GONE
                    } else {
                        binding.tvNoComments.visibility = View.GONE
                        binding.rvComments.visibility = View.VISIBLE
                        commentsAdapter.submitList(state.comments)
                    }

                    // Comment input state
                    binding.btnSendComment.isEnabled = !state.isAddingComment
                    binding.etComment.isEnabled = !state.isAddingComment

                    // Versions
                    binding.tvVersionsHeader.text = if (state.versions.isNotEmpty()) {
                        "Versions (${state.versions.size})"
                    } else {
                        "Versions"
                    }
                    if (state.versions.isEmpty()) {
                        binding.tvNoVersions.visibility = View.VISIBLE
                        binding.rvVersions.visibility = View.GONE
                    } else {
                        binding.tvNoVersions.visibility = View.GONE
                        binding.rvVersions.visibility = View.VISIBLE
                        versionsAdapter.submitList(state.versions)
                    }

                    // Download progress
                    if (state.isDownloading) {
                        binding.downloadProgressContainer.visibility = View.VISIBLE
                        binding.downloadProgressBar.progress = (state.downloadProgress * 100).toInt()
                        binding.tvDownloadStatus.text = "Downloading... ${(state.downloadProgress * 100).toInt()}%"
                        binding.btnDownload.isEnabled = false
                        binding.btnDownload.text = "Downloading..."
                        binding.btnOpen.isEnabled = false
                    } else {
                        binding.downloadProgressContainer.visibility = View.GONE
                        binding.btnDownload.isEnabled = true
                        binding.btnDownload.text = if (state.downloadedFileUri != null) "Downloaded \u2713" else "Download"
                        binding.btnOpen.isEnabled = state.downloadedFileUri != null || state.file != null
                    }

                    // Download error
                    if (state.downloadError != null) {
                        Toast.makeText(requireContext(), state.downloadError, Toast.LENGTH_SHORT).show()
                        viewModel.clearDownloadError()
                    }

                    // Deletion - navigate back
                    if (state.isDeleted) {
                        Toast.makeText(requireContext(), "File deleted", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    private fun createTagChip(name: String, color: String): Chip {
        val tagColor = try {
            Color.parseColor(color)
        } catch (e: Exception) {
            Color.parseColor("#78909C")
        }

        return Chip(requireContext()).apply {
            text = name
            setTextColor(Color.WHITE)
            chipBackgroundColor = android.content.res.ColorStateList.valueOf(tagColor)
            isClickable = false
            isCheckable = false
        }
    }

    private fun showDeleteFileDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete File")
            .setMessage("Are you sure you want to delete this file? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteFile()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteCommentDialog(commentId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Comment")
            .setMessage("Are you sure you want to delete this comment?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteComment(commentId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "N/A"
        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---- Inner Adapter: Comments ----

    private class CommentsAdapter(
        private val onDeleteClick: (String) -> Unit
    ) : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {

        private var items: List<DriveComment> = emptyList()

        fun submitList(newItems: List<DriveComment>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
            val layout = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 12
                }
                setPadding(12, 10, 12, 10)
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8f
                    setColor(Color.parseColor("#F5F6FA"))
                }
                background = bg
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val textContainer = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val tvUser = TextView(parent.context).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#8F9BB3"))
            }

            val tvText = TextView(parent.context).apply {
                textSize = 14f
                setTextColor(Color.parseColor("#12213F"))
            }

            val tvDate = TextView(parent.context).apply {
                textSize = 11f
                setTextColor(Color.parseColor("#8F9BB3"))
            }

            textContainer.addView(tvUser)
            textContainer.addView(tvText)
            textContainer.addView(tvDate)
            layout.addView(textContainer)

            val btnDelete = ImageButton(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(36, 36)
                setBackgroundColor(Color.TRANSPARENT)
                setImageResource(android.R.drawable.ic_menu_delete)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                contentDescription = "Delete comment"
            }
            layout.addView(btnDelete)

            return CommentViewHolder(layout, tvUser, tvText, tvDate, btnDelete)
        }

        override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
            val comment = items[position]
            holder.tvUser.text = comment.userId
            holder.tvText.text = comment.text
            holder.tvDate.text = formatTimestamp(comment.createdOn)
            holder.btnDelete.setOnClickListener { onDeleteClick(comment.id) }
        }

        private fun formatTimestamp(timestamp: Long): String {
            if (timestamp <= 0) return ""
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        class CommentViewHolder(
            itemView: View,
            val tvUser: TextView,
            val tvText: TextView,
            val tvDate: TextView,
            val btnDelete: ImageButton
        ) : RecyclerView.ViewHolder(itemView)
    }

    // ---- Inner Adapter: Versions ----

    private class VersionsAdapter : RecyclerView.Adapter<VersionsAdapter.VersionViewHolder>() {

        private var items: List<DriveVersion> = emptyList()

        fun submitList(newItems: List<DriveVersion>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VersionViewHolder {
            val layout = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 10
                }
                setPadding(12, 10, 12, 10)
                gravity = android.view.Gravity.CENTER_VERTICAL
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8f
                    setColor(Color.parseColor("#F5F6FA"))
                }
                background = bg
            }

            val tvVersion = TextView(parent.context).apply {
                textSize = 14f
                setTextColor(Color.parseColor("#12213F"))
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val tvInfo = TextView(parent.context).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#8F9BB3"))
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            }

            layout.addView(tvVersion)
            layout.addView(tvInfo)

            return VersionViewHolder(layout, tvVersion, tvInfo)
        }

        override fun onBindViewHolder(holder: VersionViewHolder, position: Int) {
            val version = items[position]
            holder.tvVersion.text = "v${version.versionNumber}"
            val size = FileUtils.formatFileSize(version.fileSizeBytes)
            val date = formatTimestamp(version.createdOn)
            holder.tvInfo.text = "$size  -  $date"
        }

        private fun formatTimestamp(timestamp: Long): String {
            if (timestamp <= 0) return ""
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        class VersionViewHolder(
            itemView: View,
            val tvVersion: TextView,
            val tvInfo: TextView
        ) : RecyclerView.ViewHolder(itemView)
    }
}
