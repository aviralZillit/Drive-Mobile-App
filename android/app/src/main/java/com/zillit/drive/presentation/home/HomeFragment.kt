package com.zillit.drive.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
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
import com.google.android.material.tabs.TabLayout
import com.zillit.drive.R
import com.zillit.drive.databinding.FragmentHomeBinding
import com.zillit.drive.domain.model.DriveItem
import com.zillit.drive.domain.model.DriveSection
import com.zillit.drive.presentation.adapter.DriveItemAdapter
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
            onItemLongClick = { item -> showContextMenu(item) },
            onFavoriteClick = { item ->
                val type = when (item) {
                    is DriveItem.File -> "file"
                    is DriveItem.Folder -> "folder"
                }
                viewModel.toggleFavorite(item.id, type)
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

                    adapter.submitList(state.items.toList())

                    // FAB visibility — hide in "Shared with me"
                    binding.fabAdd.visibility = if (state.driveSection == DriveSection.MY_DRIVE) View.VISIBLE else View.GONE

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
                    if (!viewModel.navigateBack()) {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun showContextMenu(item: DriveItem) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_context_menu, null)
        // Context menu items are set up in the layout
        dialog.setContentView(view)
        dialog.show()
    }

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
