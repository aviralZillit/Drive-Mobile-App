package com.zillit.drive.presentation.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.zillit.drive.R
import com.zillit.drive.databinding.FragmentFavoritesBinding
import com.zillit.drive.domain.model.DriveItem
import com.zillit.drive.presentation.adapter.DriveItemAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FavoritesViewModel by viewModels()
    private lateinit var adapter: DriveItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeState()
    }

    private fun setupRecyclerView() {
        adapter = DriveItemAdapter(
            onItemClick = { item ->
                when (item) {
                    is DriveItem.File -> {
                        findNavController().navigate(
                            R.id.action_favorites_to_file_detail,
                            bundleOf("fileId" to item.file.id)
                        )
                    }
                    is DriveItem.Folder -> {
                        findNavController().navigate(
                            R.id.action_favorites_to_folder,
                            bundleOf(
                                "folderId" to item.folder.id,
                                "folderName" to item.folder.folderName
                            )
                        )
                    }
                }
            },
            onItemLongClick = { item ->
                // Long press shows a toast for now; context menu can be added later
                Toast.makeText(requireContext(), item.name, Toast.LENGTH_SHORT).show()
            },
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

    private fun setupListeners() {
        binding.swipeRefresh.setColorSchemeColors(0xFFF99300.toInt())
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.swipeRefresh.isRefreshing = state.isLoading && state.items.isNotEmpty()
                    binding.progressBar.visibility =
                        if (state.isLoading && state.items.isEmpty()) View.VISIBLE else View.GONE
                    binding.emptyState.visibility =
                        if (!state.isLoading && state.items.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerView.visibility =
                        if (state.items.isNotEmpty()) View.VISIBLE else View.GONE

                    adapter.submitList(state.items)

                    state.error?.let { errorMsg ->
                        Snackbar.make(binding.root, errorMsg, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
