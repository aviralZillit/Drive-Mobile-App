package com.zillit.drive.presentation.folder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.zillit.drive.databinding.FragmentFolderBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Reuses the same browsing logic as HomeFragment but for a specific subfolder.
 * The HomeFragment handles all folder navigation via its ViewModel internally.
 * This fragment is available for deep-linking or future expansion.
 */
@AndroidEntryPoint
class FolderFragment : Fragment() {

    private var _binding: FragmentFolderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFolderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
