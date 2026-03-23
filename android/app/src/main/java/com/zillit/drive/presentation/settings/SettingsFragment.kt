package com.zillit.drive.presentation.settings

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zillit.drive.R
import com.zillit.drive.databinding.FragmentSettingsBinding
import com.zillit.drive.domain.model.FolderAccess
import com.zillit.drive.util.FileUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        binding.rowTrash.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_trash)
        }

        binding.rowActivity.setOnClickListener {
            Toast.makeText(requireContext(), "Activity coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Storage info
                    state.storageUsage?.let { usage ->
                        val usedFormatted = FileUtils.formatFileSize(usage.usedBytes)
                        val totalFormatted = FileUtils.formatFileSize(usage.totalBytes)
                        binding.tvStorageUsage.text = "$usedFormatted / $totalFormatted used"
                        binding.tvFileCount.text = "${usage.fileCount} files"

                        val percentage = if (usage.totalBytes > 0) {
                            ((usage.usedBytes.toDouble() / usage.totalBytes) * 100).toInt()
                        } else {
                            0
                        }
                        binding.storageProgressBar.progress = percentage
                    }

                    // Session / Account info
                    state.session?.let { session ->
                        binding.tvUserName.text = session.userName.ifEmpty { "N/A" }
                        binding.tvUserEmail.text = session.userEmail.ifEmpty { "N/A" }
                        binding.tvUserId.text = session.userId
                        binding.tvProjectId.text = session.projectId
                        binding.tvDeviceId.text = session.deviceId
                        binding.tvEnvironment.text = session.environment

                        // Profile card
                        binding.tvAvatar.text = session.userName.firstOrNull()?.uppercase() ?: "?"
                        binding.tvProfileName.text = session.userName.ifEmpty { "User" }
                        binding.tvProfileEmail.text = session.userEmail.ifEmpty { session.userId }
                        binding.tvProfileProject.text = "Project: ${session.projectId}"
                    }

                    // Team Members
                    if (state.teamMembers.isNotEmpty()) {
                        binding.cardTeamMembers.visibility = View.VISIBLE
                        populateTeamMembers(state.teamMembers)
                    }

                    // Error
                    state.error?.let { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    }

                    // Logout navigation
                    if (state.isLoggedOut) {
                        findNavController().navigate(R.id.loginFragment)
                    }
                }
            }
        }
    }

    private fun populateTeamMembers(members: List<FolderAccess>) {
        val container = binding.layoutTeamMembers
        container.removeAllViews()

        members.forEachIndexed { index, member ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }

            // Avatar circle
            val avatar = TextView(requireContext()).apply {
                val size = (36 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size)
                gravity = Gravity.CENTER
                setTextColor(resources.getColor(android.R.color.white, null))
                textSize = 14f
                setBackgroundResource(R.drawable.bg_avatar_circle)
                text = member.userId.firstOrNull()?.uppercase() ?: "?"
            }
            row.addView(avatar)

            // Info column
            val info = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (12 * resources.displayMetrics.density).toInt()
                }
            }

            val nameText = TextView(requireContext()).apply {
                text = member.userId
                textSize = 14f
                setTextColor(0xFF12213F.toInt())
            }
            info.addView(nameText)

            val roleText = TextView(requireContext()).apply {
                text = member.role.replaceFirstChar { it.uppercase() }
                textSize = 12f
                setTextColor(0xFF8F9BB3.toInt())
            }
            info.addView(roleText)

            row.addView(info)
            container.addView(row)

            // Add divider between items (not after last)
            if (index < members.size - 1) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * resources.displayMetrics.density).toInt()
                    ).apply {
                        topMargin = (4 * resources.displayMetrics.density).toInt()
                        bottomMargin = (4 * resources.displayMetrics.density).toInt()
                    }
                    setBackgroundColor(0xFFF0F0F0.toInt())
                }
                container.addView(divider)
            }
        }
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Logout") { _, _ ->
                viewModel.logout()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
