package com.zillit.drive.presentation.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zillit.drive.databinding.FragmentActivityBinding
import com.zillit.drive.domain.model.DriveActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ActivityFragment : Fragment() {

    private var _binding: FragmentActivityBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ActivityViewModel by viewModels()
    private lateinit var adapter: ActivityItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeState()

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadActivity() }
    }

    private fun setupRecyclerView() {
        adapter = ActivityItemAdapter()
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.swipeRefresh.isRefreshing = state.isLoading
                    binding.progressBar.visibility =
                        if (state.isLoading && state.activities.isEmpty()) View.VISIBLE else View.GONE
                    binding.emptyState.visibility =
                        if (!state.isLoading && state.activities.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerView.visibility =
                        if (state.activities.isNotEmpty()) View.VISIBLE else View.GONE
                    adapter.submitList(state.activities)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Inline adapter for activity items
class ActivityItemAdapter : ListAdapter<DriveActivity, ActivityItemAdapter.ViewHolder>(
    object : DiffUtil.ItemCallback<DriveActivity>() {
        override fun areItemsTheSame(old: DriveActivity, new: DriveActivity) = old.id == new.id
        override fun areContentsTheSame(old: DriveActivity, new: DriveActivity) = old == new
    }
) {
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAction: TextView = itemView.findViewById(android.R.id.text1)
        private val tvDetails: TextView = itemView.findViewById(android.R.id.text2)

        fun bind(activity: DriveActivity) {
            tvAction.text = activity.action.replace("_", " ")
                .replaceFirstChar { it.uppercase() }
            val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                .format(Date(activity.createdOn))
            tvDetails.text = buildString {
                activity.details?.let { append(it); append(" · ") }
                append(dateStr)
            }
            tvDetails.setTextColor(0xFF5F6D88.toInt())
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            android.R.layout.simple_list_item_2, parent, false
        )
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
