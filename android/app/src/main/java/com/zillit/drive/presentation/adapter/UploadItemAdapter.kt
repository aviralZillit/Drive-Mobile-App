package com.zillit.drive.presentation.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zillit.drive.databinding.ItemUploadBinding
import com.zillit.drive.presentation.upload.UploadItemState
import com.zillit.drive.presentation.upload.UploadStatus
import com.zillit.drive.util.FileUtils

class UploadItemAdapter(
    private val onCancelClick: (UploadItemState) -> Unit,
    private val onRemoveClick: (UploadItemState) -> Unit
) : ListAdapter<UploadItemState, UploadItemAdapter.ViewHolder>(UploadItemDiffCallback()) {

    inner class ViewHolder(
        private val binding: ItemUploadBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UploadItemState) {
            binding.tvFileName.text = item.fileName

            // Extension badge
            val extension = item.fileName.substringAfterLast('.', "").uppercase()
            binding.tvExtBadge.text = extension
            if (extension.isNotEmpty()) {
                binding.tvExtBadge.setBackgroundColor(FileUtils.getExtensionColor(extension.lowercase()))
            }

            // Status text
            val sizeText = FileUtils.formatFileSize(item.fileSizeBytes)
            binding.tvStatus.text = when (item.status) {
                UploadStatus.PENDING -> "$sizeText \u2022 Pending"
                UploadStatus.UPLOADING -> {
                    val percent = (item.progress * 100).toInt()
                    "$sizeText \u2022 Uploading $percent%"
                }
                UploadStatus.COMPLETED -> "$sizeText \u2022 Completed"
                UploadStatus.FAILED -> "$sizeText \u2022 Failed${item.errorMessage?.let { ": $it" } ?: ""}"
                UploadStatus.CANCELLED -> "$sizeText \u2022 Cancelled"
            }

            // Status text color
            binding.tvStatus.setTextColor(
                when (item.status) {
                    UploadStatus.COMPLETED -> 0xFF2E7D32.toInt() // green
                    UploadStatus.FAILED -> 0xFFE53935.toInt()    // red
                    UploadStatus.CANCELLED -> 0xFF8F9BB3.toInt() // grey
                    else -> 0xFF5F6D88.toInt()                   // default secondary
                }
            )

            // Progress bar
            when (item.status) {
                UploadStatus.UPLOADING -> {
                    binding.progressBarUpload.visibility = View.VISIBLE
                    binding.progressBarUpload.progress = (item.progress * 1000).toInt()
                }
                UploadStatus.COMPLETED -> {
                    binding.progressBarUpload.visibility = View.VISIBLE
                    binding.progressBarUpload.progress = 1000
                }
                else -> {
                    binding.progressBarUpload.visibility = View.GONE
                }
            }

            // Cancel / Remove button
            when (item.status) {
                UploadStatus.PENDING, UploadStatus.UPLOADING -> {
                    binding.btnCancel.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    binding.btnCancel.contentDescription = "Cancel upload"
                    binding.btnCancel.setOnClickListener { onCancelClick(item) }
                }
                UploadStatus.COMPLETED, UploadStatus.FAILED, UploadStatus.CANCELLED -> {
                    binding.btnCancel.setImageResource(android.R.drawable.ic_menu_delete)
                    binding.btnCancel.contentDescription = "Remove from list"
                    binding.btnCancel.setOnClickListener { onRemoveClick(item) }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUploadBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class UploadItemDiffCallback : DiffUtil.ItemCallback<UploadItemState>() {
    override fun areItemsTheSame(oldItem: UploadItemState, newItem: UploadItemState): Boolean =
        oldItem.uploadId == newItem.uploadId

    override fun areContentsTheSame(oldItem: UploadItemState, newItem: UploadItemState): Boolean =
        oldItem == newItem
}
