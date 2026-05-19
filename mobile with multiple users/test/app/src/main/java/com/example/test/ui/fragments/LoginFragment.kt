package com.example.test.ui.fragments

// AI-assisted: Firebase Firestore sync, Cloudinary image upload, real-time listeners

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.test.MainActivity
import com.example.test.R
import com.example.test.data.local.database.AppDatabase
import com.example.test.data.local.entity.UserEntity
import com.example.test.data.repository.MusicRepository
import com.example.test.data.ui.MusicViewModel
import com.example.test.data.ui.MusicViewModelFactory
import com.example.test.databinding.FragmentLoginBinding
import com.example.test.util.NetworkUtils
import com.example.test.util.UiState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var viewModel: MusicViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database   = AppDatabase.getDatabase(requireContext())
        val repository = MusicRepository(database.songDao(), database.playlistDao(), database.userDao())
        val factory    = MusicViewModelFactory(repository)
        viewModel      = ViewModelProvider(this, factory).get(MusicViewModel::class.java)

        // Navigate only after sync completes (or fails — Room cache will be used)
        viewModel.syncComplete.observe(viewLifecycleOwner) { done ->
            if (done == true) {
                binding.progressBar.visibility = View.GONE
                binding.btnLogin.isEnabled = true
                (activity as? MainActivity)?.onLoginSuccess()
            }
        }

        // Show sync progress/error state
        viewModel.syncState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvSyncStatus.text = getString(R.string.msg_loading)
                    binding.tvSyncStatus.visibility = View.VISIBLE
                }
                is UiState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvSyncStatus.visibility = View.GONE
                }
                is UiState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvSyncStatus.text = getString(R.string.msg_sync_failed)
                    binding.tvSyncStatus.visibility = View.VISIBLE
                }
                is UiState.Offline -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvSyncStatus.text = getString(R.string.msg_offline)
                    binding.tvSyncStatus.visibility = View.VISIBLE
                }
            }
        }

        binding.btnLogin.setOnClickListener {
            val identifier = binding.etEmail.text.toString().trim()
            val password   = binding.etPassword.text.toString().trim()

            if (identifier.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!NetworkUtils.isOnline(requireContext())) {
                Toast.makeText(requireContext(), getString(R.string.msg_no_internet), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnLogin.isEnabled = false

            if (android.util.Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
                loginWithEmail(identifier, password)
            } else {
                db.collection("users").whereEqualTo("username", identifier).get()
                    .addOnSuccessListener { documents ->
                        val email = documents.documents.firstOrNull()?.getString("email")
                        if (email != null) loginWithEmail(email, password)
                        else {
                            binding.btnLogin.isEnabled = true
                            Toast.makeText(requireContext(), "Username not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        binding.btnLogin.isEnabled = true
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        binding.tvRegister.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RegisterFragment())
                .addToBackStack(null).commit()
        }

        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Please enter a valid email address first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), getString(R.string.msg_password_reset_sent), Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun loginWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    fetchUserThenSync(auth.currentUser?.uid ?: "")
                } else {
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        "Login Failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun fetchUserThenSync(userId: String) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    viewModel.saveUser(
                        UserEntity(
                            id              = userId,
                            username        = document.getString("username")        ?: "",
                            email           = document.getString("email")           ?: "",
                            profileImageUrl = document.getString("profileImageUrl") ?: ""
                        )
                    )
                }
                viewModel.syncPlaylistsFromCloud(userId)
            }
            .addOnFailureListener {
                // Profile fetch failed — still sync playlists from Firestore
                viewModel.syncPlaylistsFromCloud(userId)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
