package com.zillit.drive.presentation.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.zillit.drive.R
import com.zillit.drive.databinding.FragmentHomeBinding
import com.zillit.drive.domain.model.DriveItem
import com.zillit.drive.domain.model.DriveSection
import com.zillit.drive.domain.model.DriveTag
import com.zillit.drive.presentation.adapter.DriveItemAdapter
import com.zillit.drive.util.FileUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var adapter: DriveItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSectionTabs()
        setupRecyclerView()
        setupListeners()
        observeState()
        setupBackPress()
    }

    private fun setupSectionTabs() {
        binding.tabSection.addTab(binding.tabSection.newTab().setText("My Drive"))
        binding.tabSection.addTab(binding.tabSection.newTab().setText("Shared with me"))

        binding.tabSection.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val section = if (tab.position == 0) DriveSection.MY_DRIVE else DriveSection.SHARED_WITH_ME
                viewModel.switchSection(section)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private var currentIsGridView: Boolean? = null

    private fun setupRecyclerView() {
        adapter = DriveItemAdapter(
            currentUserId = viewModel.currentUserId,
            onItemClick = { item ->
                when (item) {
                    is DriveItem.Folder -> viewModel.navigateToFolder(item.folder.id, item.folder.folderName)
                    is DriveItem.File -> {
                        findNavController().navigate(
                            R.id.action_home_to_file_detail,
                            bundleOf("fileId" to item.file.id)
                        )
                    }
                }
            },
            onItemLongClick = { item ->
                if (!viewModel.uiState.value.isSelecting) {
                    // Enter select mode on long press
                    viewModel.enterSelectMode(item)
                } else {
                    showContextMenu(item)
                }
            },
            onFavoriteClick = { item ->
                val type = when (item) {
                    is DriveItem.File -> "file"
                    is DriveItem.Folder -> "folder"
                }
                viewModel.toggleFavorite(item.id, type)
            },
            onSelectionToggle = { item ->
                viewModel.toggleSelection(item)
            }
        )
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.setItemViewCacheSize(20)
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        binding.fabAdd.setOnClickListener {
            showAddMenu()
        }

        binding.btnViewToggle.setOnClickListener {
            viewModel.toggleViewMode()
        }

        binding.chipSort.setOnClickListener {
            showSortMenu()
        }

        // Filter chip -> show tag selection
        binding.chipFilter.setOnClickListener {
            showTagFilterMenu()
        }

        // Select chip -> enter multi-select mode
        binding.chipSelect.setOnClickListener {
            if (viewModel.uiState.value.isSelecting) {
                viewModel.exitSelectMode()
            } else {
                viewModel.enterSelectMode()
            }
        }

        // Bulk action buttons
        binding.btnBulkCancel.setOnClickListener {
            viewModel.exitSelectMode()
        }

        binding.btnBulkDelete.setOnClickListener {
            val count = viewModel.uiState.value.selectedIds.size
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete $count item${if (count != 1) "s" else ""}?")
                .setMessage("These items will be moved to trash.")
                .setPositiveButton("Delete") { _, _ -> viewModel.bulkDelete() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnBulkMove.setOnClickListener {
            showFolderPickerForBulkMove()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.swipeRefresh.isRefreshing = state.isLoading
                    binding.progressBar.visibility = if (state.isLoading && state.items.isEmpty()) View.VISIBLE else View.GONE
                    binding.emptyState.visibility = if (!state.isLoading && state.items.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerView.visibility = if (state.items.isNotEmpty()) View.VISIBLE else View.GONE

                    // Update badge data on adapter
                    adapter.folderBadges = state.folderBadges
                    adapter.fileBadges = state.fileBadges

                    // Update multi-select state on adapter
                    adapter.isSelecting = state.isSelecting
                    adapter.selectedIds = state.selectedIds

                    adapter.submitList(state.items.toList())

                    // Multi-select UI
                    binding.bulkActionBar.visibility = if (state.isSelecting) View.VISIBLE else View.GONE
                    binding.fabAdd.visibility = if (state.isSelecting) View.GONE
                        else if (state.driveSection == DriveSection.MY_DRIVE) View.VISIBLE else View.GONE
                    binding.tvSelectedCount.text = "${state.selectedIds.size} selected"
                    binding.chipSelect.text = if (state.isSelecting) "Cancel" else "Select"

                    // Empty state text per section
                    if (state.driveSection == DriveSection.SHARED_WITH_ME) {
                        binding.tvEmptyTitle.text = "No files shared with you yet"
                        binding.tvEmptySubtitle.text = "Files and folders shared with you will appear here"
                    } else {
                        binding.tvEmptyTitle.text = "No files yet"
                        binding.tvEmptySubtitle.text = "Upload files or create a folder to get started"
                    }

                    // Only recreate layout manager when view mode actually changes
                    if (currentIsGridView != state.isGridView) {
                        currentIsGridView = state.isGridView
                        binding.recyclerView.layoutManager = if (state.isGridView) {
                            GridLayoutManager(requireContext(), 2)
                        } else {
                            LinearLayoutManager(requireContext())
                        }
                    }

                    // Update breadcrumbs
                    updateBreadcrumbs(state.folderPath)

                    // Storage quota
                    val usage = state.storageUsage
                    if (usage != null && usage.totalBytes > 0) {
                        binding.storageQuotaLayout.visibility = View.VISIBLE
                        val percent = (usage.usedBytes.toDouble() / usage.totalBytes.toDouble() * 1000).toInt()
                        binding.storageProgressBar.progress = percent
                        binding.tvStorageUsage.text = "${FileUtils.formatFileSize(usage.usedBytes)} of ${FileUtils.formatFileSize(usage.totalBytes)} used"
                    } else {
                        binding.storageQuotaLayout.visibility = View.GONE
                    }

                    // Tag filter chip text
                    binding.chipFilter.text = state.selectedTag?.name ?: "Filter"

                    // Show error
                    state.error?.let { errorMsg ->
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    private fun updateBreadcrumbs(path: List<Pair<String?, String>>) {
        binding.breadcrumbScroll.visibility = if (path.size > 1) View.VISIBLE else View.GONE
        binding.breadcrumbContainer.removeAllViews()

        path.forEachIndexed { index, (_, name) ->
            if (index > 0) {
                val separator = TextView(requireContext()).apply {
                    text = " > "
                    setTextColor(0xFF8F9BB3.toInt())
                }
                binding.breadcrumbContainer.addView(separator)
            }

            val crumb = TextView(requireContext()).apply {
                text = name
                textSize = 14f
                setTextColor(if (index == path.lastIndex) 0xFF12213F.toInt() else 0xFF1353D1.toInt())
                if (index < path.lastIndex) {
                    setOnClickListener { viewModel.navigateToPathIndex(index) }
                }
                setPadding(4, 8, 4, 8)
            }
            binding.breadcrumbContainer.addView(crumb)
        }

        // Update title
        binding.tvTitle.text = path.last().second
    }

    private fun setupBackPress() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Exit select mode first
                    if (viewModel.uiState.value.isSelecting) {
                        viewModel.exitSelectMode()
                        return
                    }
                    if (!viewModel.navigateBack()) {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    // ─── Context Menu ───

    private fun showContextMenu(item: DriveItem) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_context_menu, null)

        val itemType = when (item) {
            is DriveItem.File -> "file"
            is DriveItem.Folder -> "folder"
        }

        // Favorite text
        val menuFavorite = view.findViewById<TextView>(R.id.menuFavorite)
        menuFavorite.text = if (item.isFavorite) "Remove from favorites" else "Add to favorites"

        // Open
        view.findViewById<TextView>(R.id.menuOpen).setOnClickListener {
            dialog.dismiss()
            when (item) {
                is DriveItem.Folder -> viewModel.navigateToFolder(item.folder.id, item.folder.folderName)
                is DriveItem.File -> {
                    findNavController().navigate(
                        R.id.action_home_to_file_detail,
                        bundleOf("fileId" to item.file.id)
                    )
                }
            }
        }

        // Favorite
        menuFavorite.setOnClickListener {
            dialog.dismiss()
            viewModel.toggleFavorite(item.id, itemType)
        }

        // Share
        view.findViewById<TextView>(R.id.menuShare).setOnClickListener {
            dialog.dismiss()
            findNavController().navigate(
                R.id.action_home_to_share,
                bundleOf("itemId" to item.id, "itemType" to itemType)
            )
        }

        // Move
        view.findViewById<TextView>(R.id.menuMove).setOnClickListener {
            dialog.dismiss()
            showFolderPicker(item.id, itemType)
        }

        // Rename
        view.findViewById<TextView>(R.id.menuRename).setOnClickListener {
            dialog.dismiss()
            showRenameDialog(item.id, itemType, item.name)
        }

        // Download (hide for folders)
        val menuDownload = view.findViewById<TextView>(R.id.menuDownload)
        if (item is DriveItem.Folder) {
            menuDownload.visibility = View.GONE
        } else {
            menuDownload.setOnClickListener {
                dialog.dismiss()
                // Copy share link as a lightweight "download" action
                if (item is DriveItem.File) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        // Generate share link for copy
                        Toast.makeText(requireContext(), "Generating link...", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Delete
        view.findViewById<TextView>(R.id.menuDelete).setOnClickListener {
            dialog.dismiss()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete \"${item.name}\"?")
                .setMessage("This item will be moved to trash.")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteItem(item.id, itemType)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    // ─── Rename Dialog ───

    private fun showRenameDialog(itemId: String, itemType: String, currentName: String) {
        val editText = EditText(requireContext()).apply {
            setText(currentName)
            selectAll()
            setPadding(48, 32, 48, 16)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentName) {
                    viewModel.renameItem(itemId, itemType, newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Folder Picker Dialog ───

    private fun showFolderPicker(itemId: String, itemType: String) {
        viewModel.loadRootFolders()

        // Use a simple dialog with root folder list + "My Drive root" option
        viewLifecycleOwner.lifecycleScope.launch {
            // Wait briefly for folders to load
            kotlinx.coroutines.delay(500)
            val state = viewModel.uiState.value
            val folders = state.rootFolders.filter { it.first != itemId } // Don't move to itself

            val names = mutableListOf("My Drive (root)")
            names.addAll(folders.map { it.second })

            val ids = mutableListOf<String?>(null) // null = root
            ids.addAll(folders.map { it.first })

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Move to...")
                .setItems(names.toTypedArray()) { _, which ->
                    val targetFolderId = ids[which]
                    viewModel.moveItem(itemId, itemType, targetFolderId)
                    Toast.makeText(requireContext(), "Item moved", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showFolderPickerForBulkMove() {
        viewModel.loadRootFolders()

        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            val state = viewModel.uiState.value
            val selectedIds = state.selectedIds
            val folders = state.rootFolders.filter { it.first !in selectedIds }

            val names = mutableListOf("My Drive (root)")
            names.addAll(folders.map { it.second })

            val ids = mutableListOf<String?>(null)
            ids.addAll(folders.map { it.first })

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Move ${selectedIds.size} items to...")
                .setItems(names.toTypedArray()) { _, which ->
                    viewModel.bulkMove(ids[which])
                    Toast.makeText(requireContext(), "Items moved", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ─── Tag Filter Menu ───

    private fun showTagFilterMenu() {
        val state = viewModel.uiState.value
        val tags = state.allTags

        val names = mutableListOf("All (no filter)")
        names.addAll(tags.map { it.name })

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter by Tag")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    viewModel.setTagFilter(null)
                } else {
                    viewModel.setTagFilter(tags[which - 1])
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Existing Menus ───

    private fun showAddMenu() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_add_menu, null)
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showSortMenu() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_sort_menu, null)
        dialog.setContentView(view)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
