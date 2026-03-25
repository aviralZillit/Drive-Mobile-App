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
    private val onRemoveClick: (UploadItemState) -> Unit,
    private val onPauseClick: (UploadItemState) -> Unit = {},
    private val onResumeClick: (UploadItemState) -> Unit = {},
    private val onRetryClick: (UploadItemState) -> Unit = {}
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

            // Status text (using computed property from UploadItemState)
            binding.tvStatus.text = item.statusText

            // Status text color
            binding.tvStatus.setTextColor(
                when (item.status) {
                    UploadStatus.COMPLETED -> 0xFF2E7D32.toInt() // green
                    UploadStatus.FAILED -> 0xFFE53935.toInt()    // red
                    UploadStatus.CANCELLED -> 0xFF8F9BB3.toInt() // grey
                    UploadStatus.PAUSED -> 0xFFF99300.toInt()    // orange
                    else -> 0xFF5F6D88.toInt()                   // default secondary
                }
            )

            // Progress bar
            when (item.status) {
                UploadStatus.UPLOADING, UploadStatus.PAUSED -> {
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

            // Speed + ETA text
            if (item.status == UploadStatus.UPLOADING && item.speed > 0) {
                binding.tvSpeedEta.visibility = View.VISIBLE
                val speedText = FileUtils.formatFileSize(item.speed.toLong()) + "/s"
                val etaText = if (item.eta > 0) formatEta(item.eta) else ""
                binding.tvSpeedEta.text = buildString {
                    append(speedText)
                    if (etaText.isNotEmpty()) append(" \u2022 $etaText remaining")
                }
            } else {
                binding.tvSpeedEta.visibility = View.GONE
            }

            // Pause/Resume button
            when (item.status) {
                UploadStatus.UPLOADING -> {
                    binding.btnPauseResume.visibility = View.VISIBLE
                    binding.btnPauseResume.setImageResource(android.R.drawable.ic_media_pause)
                    binding.btnPauseResume.contentDescription = "Pause upload"
                    binding.btnPauseResume.setOnClickListener { onPauseClick(item) }
                }
                UploadStatus.PAUSED -> {
                    binding.btnPauseResume.visibility = View.VISIBLE
                    binding.btnPauseResume.setImageResource(android.R.drawable.ic_media_play)
                    binding.btnPauseResume.contentDescription = "Resume upload"
                    binding.btnPauseResume.setOnClickListener { onResumeClick(item) }
                }
                else -> {
                    binding.btnPauseResume.visibility = View.GONE
                }
            }

            // Retry button (for failed uploads)
            if (item.status == UploadStatus.FAILED) {
                binding.btnRetry.visibility = View.VISIBLE
                binding.btnRetry.setOnClickListener { onRetryClick(item) }
            } else {
                binding.btnRetry.visibility = View.GONE
            }

            // Cancel / Remove button
            when (item.status) {
                UploadStatus.PENDING, UploadStatus.UPLOADING, UploadStatus.PAUSED -> {
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

        private fun formatEta(seconds: Long): String {
            return when {
                seconds < 60 -> "${seconds}s"
                seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
                else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
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
