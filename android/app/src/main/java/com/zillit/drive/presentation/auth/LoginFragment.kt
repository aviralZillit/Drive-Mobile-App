package com.zillit.drive.presentation.auth

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
import androidx.navigation.fragment.findNavController
import com.zillit.drive.R
import com.zillit.drive.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check if already logged in
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loginState.collect { state ->
                    when (state) {
                        is LoginState.Idle -> {}
                        is LoginState.Loading -> {
                            binding.btnLogin.isEnabled = false
                            binding.progressBar.visibility = View.VISIBLE
                        }
                        is LoginState.Success -> {
                            findNavController().navigate(R.id.action_login_to_home)
                        }
                        is LoginState.Error -> {
                            binding.btnLogin.isEnabled = true
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                        is LoginState.AlreadyLoggedIn -> {
                            findNavController().navigate(R.id.action_login_to_home)
                        }
                    }
                }
            }
        }

        binding.btnLogin.setOnClickListener {
            val userId = binding.etUserId.text.toString().trim()
            val projectId = binding.etProjectId.text.toString().trim()
            val deviceId = binding.etDeviceId.text.toString().trim()

            if (userId.isBlank() || projectId.isBlank() || deviceId.isBlank()) {
                Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.login(userId, projectId, deviceId)
        }

        viewModel.checkExistingSession()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
