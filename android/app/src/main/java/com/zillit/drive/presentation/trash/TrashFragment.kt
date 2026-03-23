package com.zillit.drive.presentation.trash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zillit.drive.databinding.FragmentTrashBinding
import com.zillit.drive.domain.model.DriveItem
import com.zillit.drive.presentation.adapter.TrashItemAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrashFragment : Fragment() {

    private var _binding: FragmentTrashBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TrashViewModel by viewModels()
    private lateinit var adapter: TrashItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeState()
    }

    private fun setupRecyclerView() {
        adapter = TrashItemAdapter(
            onRestoreClick = { item -> onRestoreItem(item) },
            onDeleteClick = { item -> onPermanentDelete(item) }
        )
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadTrash()
        }

        binding.btnEmptyTrash.setOnClickListener {
            showEmptyTrashConfirmation()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.swipeRefresh.isRefreshing = state.isLoading
                    binding.progressBar.visibility =
                        if (state.isLoading && state.items.isEmpty()) View.VISIBLE else View.GONE
                    binding.emptyState.visibility =
                        if (!state.isLoading && state.items.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerView.visibility =
                        if (state.items.isNotEmpty()) View.VISIBLE else View.GONE
                    binding.btnEmptyTrash.visibility =
                        if (state.items.isNotEmpty()) View.VISIBLE else View.GONE

                    adapter.submitList(state.items)

                    state.error?.let { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun onRestoreItem(item: DriveItem) {
        viewModel.restoreItem(item)
        Toast.makeText(
            requireContext(),
            "${item.name} restored",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun onPermanentDelete(item: DriveItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete permanently?")
            .setMessage("\"${item.name}\" will be permanently deleted. This action cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                viewModel.permanentDelete(item)
            }
            .show()
    }

    private fun showEmptyTrashConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Empty trash?")
            .setMessage("All items in the trash will be permanently deleted. This action cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Empty Trash") { _, _ ->
                viewModel.emptyTrash()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
