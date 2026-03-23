package com.zillit.drive.presentation.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zillit.drive.R
import com.zillit.drive.databinding.ItemDriveFileBinding
import com.zillit.drive.domain.model.DriveFile
import com.zillit.drive.domain.model.DriveFolder
import com.zillit.drive.domain.model.DriveItem
import com.zillit.drive.util.FileUtils

class DriveItemAdapter(
    private val onItemClick: (DriveItem) -> Unit,
    private val onItemLongClick: (DriveItem) -> Unit,
    private val onFavoriteClick: (DriveItem) -> Unit,
    var currentUserId: String? = null,
    var folderBadges: Map<String, Int> = emptyMap(),
    var fileBadges: Set<String> = emptySet()
) : ListAdapter<DriveItem, DriveItemAdapter.ViewHolder>(DriveItemDiffCallback()) {

    inner class ViewHolder(
        private val binding: ItemDriveFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DriveItem) {
            when (item) {
                is DriveItem.File -> bindFile(item.file, item)
                is DriveItem.Folder -> bindFolder(item.folder, item)
            }

            // Shared indicator
            val createdBy = item.createdBy
            binding.ivSharedIndicator.visibility =
                if (currentUserId != null && createdBy.isNotEmpty() && createdBy != currentUserId)
                    View.VISIBLE else View.GONE

            // Badge count (folders) or unread dot (files)
            val badgeCount = folderBadges[item.id] ?: 0
            val hasFileBadge = fileBadges.contains(item.id)
            if (badgeCount > 0 && item is DriveItem.Folder) {
                binding.tvBadgeCount.text = badgeCount.toString()
                binding.tvBadgeCount.visibility = View.VISIBLE
            } else if (hasFileBadge && item is DriveItem.File) {
                binding.tvBadgeCount.text = ""
                binding.tvBadgeCount.visibility = View.VISIBLE
                // Show as small dot for files
                binding.tvBadgeCount.setPadding(8, 8, 8, 8)
            } else {
                binding.tvBadgeCount.visibility = View.GONE
            }

            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
            binding.btnFavorite.setOnClickListener { onFavoriteClick(item) }
        }

        private fun bindFile(file: DriveFile, item: DriveItem) {
            binding.tvName.text = file.fileName
            binding.tvSubtitle.text = buildString {
                append(FileUtils.formatFileSize(file.fileSizeBytes))
                if (file.fileExtension.isNotEmpty()) {
                    append(" · ")
                    append(file.fileExtension.uppercase())
                }
            }

            // Show extension badge, hide folder icon
            binding.tvExtBadge.visibility = View.VISIBLE
            binding.ivFolderIcon.visibility = View.GONE
            binding.tvExtBadge.text = file.fileExtension.uppercase()

            // Set rounded background color based on extension
            val color = FileUtils.getExtensionColor(file.fileExtension)
            val bg = binding.iconBackground.background
            if (bg is GradientDrawable) {
                bg.setColor(adjustAlpha(color, 0.15f))
            } else {
                val shape = GradientDrawable().apply {
                    cornerRadius = dpToPx(10f)
                    setColor(adjustAlpha(color, 0.15f))
                }
                binding.iconBackground.background = shape
            }
            binding.tvExtBadge.setTextColor(color)

            // Favorite star
            binding.btnFavorite.setImageResource(
                if (item.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
        }

        private fun bindFolder(folder: DriveFolder, item: DriveItem) {
            binding.tvName.text = folder.folderName
            binding.tvSubtitle.text = buildString {
                if (folder.folderCount > 0) append("${folder.folderCount} folders")
                if (folder.folderCount > 0 && folder.fileCount > 0) append(", ")
                if (folder.fileCount > 0) append("${folder.fileCount} files")
                if (isEmpty()) append("Empty")
            }

            // Show folder icon, hide extension badge
            binding.tvExtBadge.visibility = View.GONE
            binding.ivFolderIcon.visibility = View.VISIBLE
            binding.ivFolderIcon.setImageResource(R.drawable.ic_folder)

            // Orange tint background for folders
            val folderColor = Color.parseColor("#F99300")
            val bg = binding.iconBackground.background
            if (bg is GradientDrawable) {
                bg.setColor(adjustAlpha(folderColor, 0.12f))
            } else {
                val shape = GradientDrawable().apply {
                    cornerRadius = dpToPx(10f)
                    setColor(adjustAlpha(folderColor, 0.12f))
                }
                binding.iconBackground.background = shape
            }

            // Favorite star
            binding.btnFavorite.setImageResource(
                if (item.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
        }

        private fun adjustAlpha(color: Int, factor: Float): Int {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            // Blend with white
            val blendR = (r + (255 - r) * (1 - factor)).toInt()
            val blendG = (g + (255 - g) * (1 - factor)).toInt()
            val blendB = (b + (255 - b) * (1 - factor)).toInt()
            return Color.rgb(blendR, blendG, blendB)
        }

        private fun dpToPx(dp: Float): Float {
            return dp * binding.root.context.resources.displayMetrics.density
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDriveFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class DriveItemDiffCallback : DiffUtil.ItemCallback<DriveItem>() {
    override fun areItemsTheSame(oldItem: DriveItem, newItem: DriveItem): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: DriveItem, newItem: DriveItem): Boolean =
        oldItem == newItem
}
