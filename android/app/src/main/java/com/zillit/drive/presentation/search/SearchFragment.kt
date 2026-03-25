package com.zillit.drive.presentation.search

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zillit.drive.R
import com.zillit.drive.databinding.FragmentSearchBinding
import com.zillit.drive.domain.model.DriveItem
import com.zillit.drive.presentation.adapter.DriveItemAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var adapter: DriveItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearchInput()
        setupFilterChips()
        observeState()
    }

    private fun setupRecyclerView() {
        adapter = DriveItemAdapter(
            onItemClick = { item ->
                when (item) {
                    is DriveItem.File -> {
                        findNavController().navigate(
                            R.id.fileDetailFragment,
                            bundleOf("fileId" to item.file.id)
                        )
                    }
                    is DriveItem.Folder -> {
                        findNavController().navigate(
                            R.id.homeFragment,
                            bundleOf("folderId" to item.folder.id)
                        )
                    }
                }
            },
            onItemLongClick = { /* No-op for now */ },
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
    }

    private fun setupSearchInput() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                viewModel.search(query)
                binding.btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            }
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.search(binding.etSearch.text.toString())
                true
            } else false
        }

        binding.btnClear.setOnClickListener {
            binding.etSearch.text.clear()
        }
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener {
            viewModel.setFileTypeFilter(SearchFileTypeFilter.ALL)
        }
        binding.chipImages.setOnClickListener {
            viewModel.setFileTypeFilter(SearchFileTypeFilter.IMAGES)
        }
        binding.chipVideos.setOnClickListener {
            viewModel.setFileTypeFilter(SearchFileTypeFilter.VIDEOS)
        }
        binding.chipDocuments.setOnClickListener {
            viewModel.setFileTypeFilter(SearchFileTypeFilter.DOCUMENTS)
        }
        binding.chipPdf.setOnClickListener {
            viewModel.setFileTypeFilter(SearchFileTypeFilter.PDF)
        }

        // Date range chip
        binding.chipDateRange.setOnClickListener {
            showDateRangePicker()
        }
    }

    private fun showDateRangePicker() {
        val items = arrayOf("Today", "Last 7 days", "Last 30 days", "Custom range...", "Clear date filter")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter by date")
            .setItems(items) { _, which ->
                val calendar = Calendar.getInstance()
                when (which) {
                    0 -> { // Today
                        val startOfDay = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        viewModel.setDateRange(startOfDay.timeInMillis, System.currentTimeMillis())
                        binding.chipDateRange.text = "Today"
                    }
                    1 -> { // Last 7 days
                        val sevenDaysAgo = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, -7)
                        }
                        viewModel.setDateRange(sevenDaysAgo.timeInMillis, System.currentTimeMillis())
                        binding.chipDateRange.text = "Last 7 days"
                    }
                    2 -> { // Last 30 days
                        val thirtyDaysAgo = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, -30)
                        }
                        viewModel.setDateRange(thirtyDaysAgo.timeInMillis, System.currentTimeMillis())
                        binding.chipDateRange.text = "Last 30 days"
                    }
                    3 -> { // Custom range
                        showCustomDatePicker()
                    }
                    4 -> { // Clear
                        viewModel.clearDateRange()
                        binding.chipDateRange.text = "Date range"
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomDatePicker() {
        val calendar = Calendar.getInstance()

        // Pick start date
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val startCal = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startMillis = startCal.timeInMillis

                // Then pick end date
                DatePickerDialog(
                    requireContext(),
                    { _, endYear, endMonth, endDay ->
                        val endCal = Calendar.getInstance().apply {
                            set(endYear, endMonth, endDay, 23, 59, 59)
                            set(Calendar.MILLISECOND, 999)
                        }
                        viewModel.setDateRange(startMillis, endCal.timeInMillis)
                        binding.chipDateRange.text = "${month + 1}/$day/$year - ${endMonth + 1}/$endDay/$endYear"
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    setTitle("Select end date")
                }.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setTitle("Select start date")
        }.show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE

                    // Use filteredResults instead of results
                    val displayResults = state.filteredResults

                    binding.recyclerView.visibility =
                        if (displayResults.isNotEmpty()) View.VISIBLE else View.GONE

                    binding.tvError.visibility =
                        if (state.error != null) View.VISIBLE else View.GONE
                    binding.tvError.text = state.error

                    val showEmpty = !state.isLoading
                        && displayResults.isEmpty()
                        && state.error == null
                    binding.emptyState.visibility =
                        if (showEmpty) View.VISIBLE else View.GONE

                    binding.tvEmptyMessage.text = if (state.query.isNotBlank()) {
                        if (state.activeFilter != SearchFileTypeFilter.ALL) {
                            "No ${state.activeFilter.label.lowercase()} found for \"${state.query}\""
                        } else {
                            "No results for \"${state.query}\""
                        }
                    } else {
                        "Search for files and folders"
                    }

                    adapter.submitList(displayResults)

                    // Update chip selection state
                    binding.chipAll.isChecked = state.activeFilter == SearchFileTypeFilter.ALL
                    binding.chipImages.isChecked = state.activeFilter == SearchFileTypeFilter.IMAGES
                    binding.chipVideos.isChecked = state.activeFilter == SearchFileTypeFilter.VIDEOS
                    binding.chipDocuments.isChecked = state.activeFilter == SearchFileTypeFilter.DOCUMENTS
                    binding.chipPdf.isChecked = state.activeFilter == SearchFileTypeFilter.PDF
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
