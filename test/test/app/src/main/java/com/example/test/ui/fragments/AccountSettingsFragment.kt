package com.example.test.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.test.R
import com.example.test.data.local.database.AppDatabase
import com.example.test.data.repository.MusicRepository
import com.example.test.data.ui.MusicViewModel
import com.example.test.data.ui.MusicViewModelFactory
import com.example.test.databinding.FragmentAccountSettingsBinding
import com.example.test.util.CloudinaryUploader
import com.example.test.data.local.entity.UserEntity
import com.example.test.util.UiState
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class AccountSettingsFragment : Fragment() {

    private var _binding: FragmentAccountSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var viewModel: MusicViewModel
    private var pendingImageUrl: String? = null
    private var currentImageUrl: String? = null

    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) uploadProfileImage(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountSettingsBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val database = AppDatabase.getDatabase(requireContext())
        val repository = MusicRepository(database.songDao(), database.playlistDao(), database.userDao())
        val factory = MusicViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(MusicViewModel::class.java)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserData()
        setupObservers()

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnChangePicture.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnResetPassword.setOnClickListener {
            resetPassword()
        }

        binding.btnSave.setOnClickListener {
            saveChanges()
        }
    }

    private fun setupObservers() {
        viewModel.syncState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.btnSave.isEnabled = false
                    Toast.makeText(requireContext(), getString(R.string.msg_loading), Toast.LENGTH_SHORT).show()
                }
                is UiState.Success -> {
                    binding.btnSave.isEnabled = true
                    Toast.makeText(requireContext(), getString(R.string.msg_profile_updated), Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
                is UiState.Error -> {
                    binding.btnSave.isEnabled = true
                    Toast.makeText(requireContext(), getString(R.string.error_auth_update_failed, state.message), Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            if (errorMsg != null) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return

        // Observe user data from Room via ViewModel for better offline support
        viewModel.getUser(userId).observe(viewLifecycleOwner) { userEntity ->
            userEntity?.let {
                // Only set text if the field is currently empty to avoid overwriting user input during edits
                if (binding.etUsername.text.isNullOrEmpty()) {
                    binding.etUsername.setText(it.username)
                }
                if (binding.etEmail.text.isNullOrEmpty()) {
                    binding.etEmail.setText(it.email)
                }

                currentImageUrl = it.profileImageUrl

                if (pendingImageUrl == null) {
                    Glide.with(this)
                        .load(it.profileImageUrl)
                        .placeholder(R.drawable.profile)
                        .circleCrop()
                        .into(binding.ivProfilePicture)
                }
            }
        }
    }

    private fun uploadProfileImage(uri: Uri) {
        binding.btnSave.isEnabled = false
        Toast.makeText(requireContext(), "Uploading picture…", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val url = CloudinaryUploader.upload(requireContext(), uri)
                pendingImageUrl = url
                Glide.with(this@AccountSettingsFragment)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .placeholder(R.drawable.profile)
                    .circleCrop()
                    .into(binding.ivProfilePicture)
                Toast.makeText(requireContext(), "Picture uploaded", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSave.isEnabled = true
            }
        }
    }

    private fun resetPassword() {
        val email = auth.currentUser?.email ?: return
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), getString(R.string.msg_password_reset_sent), Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun saveChanges() {
        val user = auth.currentUser ?: return
        val userId = user.uid
        val newUsername = binding.etUsername.text.toString().trim()
        val newEmail = binding.etEmail.text.toString().trim()

        if (newUsername.isEmpty() || newEmail.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.error_empty_fields), Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            Toast.makeText(requireContext(), getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show()
            return
        }

        // 1. If email changed, we use updateEmail with re-authentication for synchronization
        if (newEmail != user.email) {
            // 2. Add Re-authentication: Step before the update to prevent "requires-recent-login" errors
            showReauthDialog {
                binding.btnSave.isEnabled = false
                user.updateEmail(newEmail)
                    .addOnSuccessListener {
                        // 3. Sync to Firestore: Immediately after the Auth email update succeeds
                        viewModel.updateUserProfile(userId, newUsername, newEmail, pendingImageUrl)
                        Toast.makeText(requireContext(), "Email updated successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        binding.btnSave.isEnabled = true
                        // 4. Atomicity & Error Handling: Provide clear error handling
                        Toast.makeText(requireContext(), getString(R.string.error_auth_update_failed, e.message), Toast.LENGTH_LONG).show()
                    }
            }
        } else {
            // Email didn't change, just update via ViewModel
            viewModel.updateUserProfile(userId, newUsername, newEmail, pendingImageUrl)
        }
    }

    private fun showReauthDialog(onSuccess: () -> Unit) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.label_reauthenticate_title))
        builder.setMessage(getString(R.string.error_reauthenticate))

        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)

        builder.setPositiveButton(getString(R.string.action_confirm)) { _, _ ->
            val password = input.text.toString()
            if (password.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.error_password_empty), Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val user = auth.currentUser
            val email = user?.email ?: return@setPositiveButton
            val credential = EmailAuthProvider.getCredential(email, password)

            user.reauthenticate(credential)
                .addOnSuccessListener {
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), getString(R.string.error_reauth_failed, e.message), Toast.LENGTH_LONG).show()
                }
        }
        builder.setNegativeButton(getString(R.string.label_cancel)) { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}