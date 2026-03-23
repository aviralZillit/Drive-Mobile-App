package com.zillit.drive.presentation.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zillit.drive.databinding.ItemTrashBinding
import com.zillit.drive.domain.model.DriveFile
import com.zillit.drive.domain.model.DriveFolder
import com.zillit.drive.domain.model.DriveItem
import com.zillit.drive.util.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrashItemAdapter(
    private val onRestoreClick: (DriveItem) -> Unit,
    private val onDeleteClick: (DriveItem) -> Unit
) : ListAdapter<DriveItem, TrashItemAdapter.ViewHolder>(TrashItemDiffCallback()) {

    inner class ViewHolder(
        private val binding: ItemTrashBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DriveItem) {
            when (item) {
                is DriveItem.File -> bindFile(item.file)
                is DriveItem.Folder -> bindFolder(item.folder)
            }

            binding.btnRestore.setOnClickListener { onRestoreClick(item) }
            binding.btnDelete.setOnClickListener { onDeleteClick(item) }
        }

        private fun bindFile(file: DriveFile) {
            binding.tvName.text = file.fileName
            binding.tvIconBadge.text = file.fileExtension.uppercase()
            binding.tvIconBadge.setBackgroundColor(FileUtils.getExtensionColor(file.fileExtension))
            binding.tvDeletedDate.text = formatDeletedDate(file.deletedOn)
        }

        private fun bindFolder(folder: DriveFolder) {
            binding.tvName.text = folder.folderName
            binding.tvIconBadge.text = "\uD83D\uDCC1"
            binding.tvIconBadge.setBackgroundColor(Color.parseColor("#5F6D88"))
            binding.tvDeletedDate.text = formatDeletedDate(folder.deletedOn)
        }

        private fun formatDeletedDate(timestamp: Long): String {
            if (timestamp <= 0) return "Deleted"
            val date = Date(timestamp)
            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            return "Deleted ${formatter.format(date)}"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTrashBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class TrashItemDiffCallback : DiffUtil.ItemCallback<DriveItem>() {
    override fun areItemsTheSame(oldItem: DriveItem, newItem: DriveItem): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: DriveItem, newItem: DriveItem): Boolean =
        oldItem == newItem
}
