package com.zillit.drive.presentation.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zillit.drive.R
import com.zillit.drive.domain.model.FileAccess
import com.zillit.drive.domain.model.FolderAccess
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShareFragment : Fragment() {

    private val viewModel: ShareViewModel by viewModels()

    private lateinit var progressBar: ProgressBar
    private lateinit var scrollView: ScrollView
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var cardShareLink: CardView
    private lateinit var shareLinkContent: LinearLayout
    private lateinit var tvShareLinkUrl: TextView
    private lateinit var tvShareExpiry: TextView
    private lateinit var btnCopyLink: Button
    private lateinit var btnGenerateLink: Button
    private lateinit var accessContainer: LinearLayout
    private lateinit var tvNoAccess: TextView
    private lateinit var btnSave: Button
    private lateinit var tvError: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_share, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupListeners()
        observeState()

        val itemId = arguments?.getString("itemId")
        val itemType = arguments?.getString("itemType")
        if (itemId != null && itemType != null) {
            viewModel.init(itemId, itemType)
        } else {
            Toast.makeText(requireContext(), "Missing item information", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    private fun bindViews(view: View) {
        progressBar = view.findViewById(R.id.progressBar)
        scrollView = view.findViewById(R.id.scrollView)
        tvTitle = view.findViewById(R.id.tvTitle)
        btnBack = view.findViewById(R.id.btnBack)
        cardShareLink = view.findViewById(R.id.cardShareLink)
        shareLinkContent = view.findViewById(R.id.shareLinkContent)
        tvShareLinkUrl = view.findViewById(R.id.tvShareLinkUrl)
        tvShareExpiry = view.findViewById(R.id.tvShareExpiry)
        btnCopyLink = view.findViewById(R.id.btnCopyLink)
        btnGenerateLink = view.findViewById(R.id.btnGenerateLink)
        accessContainer = view.findViewById(R.id.accessContainer)
        tvNoAccess = view.findViewById(R.id.tvNoAccess)
        btnSave = view.findViewById(R.id.btnSave)
        tvError = view.findViewById(R.id.tvError)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        btnGenerateLink.setOnClickListener {
            viewModel.generateShareLink()
        }

        btnCopyLink.setOnClickListener {
            val url = viewModel.uiState.value.shareLink?.url ?: return@setOnClickListener
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Share Link", url))
            Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnSave.setOnClickListener {
            viewModel.savePermissions()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Loading
                    progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    scrollView.visibility = if (state.isLoading) View.GONE else View.VISIBLE

                    // Title
                    tvTitle.text = if (state.itemType == "file") "Manage File Access" else "Manage Folder Access"

                    // Share link section (files only)
                    cardShareLink.visibility = if (state.itemType == "file") View.VISIBLE else View.GONE

                    // Generate link button state
                    btnGenerateLink.isEnabled = !state.isGeneratingLink
                    btnGenerateLink.text = if (state.isGeneratingLink) "Generating..." else "Generate Share Link"

                    // Share link display
                    if (state.shareLink != null) {
                        shareLinkContent.visibility = View.VISIBLE
                        btnGenerateLink.visibility = View.GONE
                        tvShareLinkUrl.text = state.shareLink.url
                        tvShareExpiry.text = if (state.shareLink.expiresAt != null) {
                            "Expires: ${state.shareLink.expiresAt}"
                        } else {
                            "No expiration"
                        }
                    } else {
                        shareLinkContent.visibility = View.GONE
                        btnGenerateLink.visibility = View.VISIBLE
                    }

                    // Build access entries
                    if (state.itemType == "file") {
                        buildFileAccessEntries(state.fileAccessEntries)
                    } else {
                        buildFolderAccessEntries(state.folderAccessEntries)
                    }

                    // Empty state
                    val hasEntries = if (state.itemType == "file") {
                        state.fileAccessEntries.isNotEmpty()
                    } else {
                        state.folderAccessEntries.isNotEmpty()
                    }
                    tvNoAccess.visibility = if (!hasEntries && !state.isLoading) View.VISIBLE else View.GONE

                    // Save button
                    btnSave.visibility = if (hasEntries) View.VISIBLE else View.GONE
                    btnSave.isEnabled = !state.isSaving
                    btnSave.text = if (state.isSaving) "Saving..." else "Save Permissions"

                    // Error
                    if (state.error != null) {
                        tvError.visibility = View.VISIBLE
                        tvError.text = state.error
                    } else {
                        tvError.visibility = View.GONE
                    }

                    // Save success
                    if (state.saveSuccess) {
                        Toast.makeText(requireContext(), "Permissions saved successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun buildFileAccessEntries(entries: List<FileAccess>) {
        accessContainer.removeAllViews()

        for (entry in entries) {
            val card = CardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 12
                }
                radius = 12f * resources.displayMetrics.density
                cardElevation = 2f * resources.displayMetrics.density
            }

            val cardContent = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }

            // Header row: User ID + Remove button
            val headerRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }
            }

            val tvUser = TextView(requireContext()).apply {
                text = entry.userId
                textSize = 15f
                setTextColor(Color.parseColor("#12213F"))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            headerRow.addView(tvUser)

            // Remove button
            val btnRemove = ImageButton(requireContext()).apply {
                setImageResource(android.R.drawable.ic_menu_delete)
                background = null
                contentDescription = "Remove access"
                setColorFilter(Color.parseColor("#E53935"))
                layoutParams = LinearLayout.LayoutParams(
                    dp(32), dp(32)
                )
                setOnClickListener {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Remove access")
                        .setMessage("Remove access for ${entry.userId}?")
                        .setPositiveButton("Remove") { _, _ ->
                            viewModel.removeFileAccess(entry.userId)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            headerRow.addView(btnRemove)

            cardContent.addView(headerRow)

            // Can View switch
            cardContent.addView(createPermissionSwitch("Can View", entry.canView) { isChecked ->
                val current = viewModel.uiState.value.fileAccessEntries.find { it.userId == entry.userId } ?: return@createPermissionSwitch
                viewModel.updateFilePermission(entry.userId, isChecked, current.canEdit, current.canDownload)
            })

            // Can Edit switch
            cardContent.addView(createPermissionSwitch("Can Edit", entry.canEdit) { isChecked ->
                val current = viewModel.uiState.value.fileAccessEntries.find { it.userId == entry.userId } ?: return@createPermissionSwitch
                viewModel.updateFilePermission(entry.userId, current.canView, isChecked, current.canDownload)
            })

            // Can Download switch
            cardContent.addView(createPermissionSwitch("Can Download", entry.canDownload) { isChecked ->
                val current = viewModel.uiState.value.fileAccessEntries.find { it.userId == entry.userId } ?: return@createPermissionSwitch
                viewModel.updateFilePermission(entry.userId, current.canView, current.canEdit, isChecked)
            })

            card.addView(cardContent)
            accessContainer.addView(card)
        }
    }

    private fun createPermissionSwitch(
        label: String,
        isChecked: Boolean,
        onChanged: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }

        val tvLabel = TextView(requireContext()).apply {
            text = label
            textSize = 14f
            setTextColor(Color.parseColor("#5F6D88"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        row.addView(tvLabel)

        val switch = SwitchCompat(requireContext()).apply {
            this.isChecked = isChecked
            setOnCheckedChangeListener { _, checked ->
                onChanged(checked)
            }
        }
        row.addView(switch)

        return row
    }

    private fun buildFolderAccessEntries(entries: List<FolderAccess>) {
        accessContainer.removeAllViews()

        val roles = arrayOf("viewer", "editor", "owner")

        for (entry in entries) {
            val card = CardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 12
                }
                radius = 12f * resources.displayMetrics.density
                cardElevation = 2f * resources.displayMetrics.density
            }

            val cardContent = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }

            // User ID
            val tvUser = TextView(requireContext()).apply {
                text = entry.userId
                textSize = 15f
                setTextColor(Color.parseColor("#12213F"))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            cardContent.addView(tvUser)

            // Inherited badge
            if (entry.inherited) {
                val badge = TextView(requireContext()).apply {
                    text = "inherited"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    setPadding(dp(8), dp(2), dp(8), dp(2))
                    val bg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 6f * resources.displayMetrics.density
                        setColor(Color.parseColor("#8F9BB3"))
                    }
                    background = bg
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dp(8)
                    }
                }
                cardContent.addView(badge)
            }

            // Role spinner
            val spinner = Spinner(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    roles
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                this.adapter = adapter
                setSelection(roles.indexOf(entry.role).coerceAtLeast(0))

                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val selectedRole = roles[position]
                        if (selectedRole != entry.role) {
                            viewModel.updateFolderRole(entry.userId, selectedRole)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
            cardContent.addView(spinner)

            // Remove button for folder access
            val btnRemove = ImageButton(requireContext()).apply {
                setImageResource(android.R.drawable.ic_menu_delete)
                background = null
                contentDescription = "Remove access"
                setColorFilter(Color.parseColor("#E53935"))
                layoutParams = LinearLayout.LayoutParams(
                    dp(32), dp(32)
                ).apply {
                    marginStart = dp(8)
                }
                setOnClickListener {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Remove access")
                        .setMessage("Remove access for ${entry.userId}?")
                        .setPositiveButton("Remove") { _, _ ->
                            viewModel.removeFolderAccess(entry.userId)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            cardContent.addView(btnRemove)

            card.addView(cardContent)
            accessContainer.addView(card)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
