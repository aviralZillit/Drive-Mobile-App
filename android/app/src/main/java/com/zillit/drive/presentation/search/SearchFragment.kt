package com.zillit.drive.presentation.search

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
import com.zillit.drive.R
import com.zillit.drive.databinding.FragmentSearchBinding
import com.zillit.drive.domain.model.DriveItem
import com.zillit.drive.presentation.adapter.DriveItemAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE

                    binding.recyclerView.visibility =
                        if (state.results.isNotEmpty()) View.VISIBLE else View.GONE

                    binding.tvError.visibility =
                        if (state.error != null) View.VISIBLE else View.GONE
                    binding.tvError.text = state.error

                    val showEmpty = !state.isLoading
                        && state.results.isEmpty()
                        && state.error == null
                    binding.emptyState.visibility =
                        if (showEmpty) View.VISIBLE else View.GONE

                    binding.tvEmptyMessage.text = if (state.query.isNotBlank()) {
                        "No results for \"${state.query}\""
                    } else {
                        "Search for files and folders"
                    }

                    adapter.submitList(state.results)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
