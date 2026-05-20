package com.example.test.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.test.MainActivity
import com.example.test.R
import com.example.test.data.local.database.AppDatabase
import com.example.test.data.local.entity.UserEntity
import com.example.test.data.repository.MusicRepository
import com.example.test.data.ui.MusicViewModel
import com.example.test.data.ui.MusicViewModelFactory
import com.example.test.databinding.FragmentRegisterBinding
import com.example.test.util.CloudinaryUploader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var viewModel: MusicViewModel

    private var selectedImageUri: Uri? = null

    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                selectedImageUri = uri
                Glide.with(this).load(uri).circleCrop().into(binding.ivProfile)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
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

        binding.btnUploadImage.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnRegister.setOnClickListener { registerUser() }
        binding.tvLogin.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun registerUser() {
        val username = binding.etUsername.text.toString().trim()
        val email    = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.error_empty_fields), Toast.LENGTH_SHORT).show()
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(requireContext(), getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRegister.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: ""
                    if (selectedImageUri != null) {
                        uploadImageThenSave(userId, username, email)
                    } else {
                        saveUserToDatabase(userId, username, email, "")
                    }
                } else {
                    binding.btnRegister.isEnabled = true
                    Toast.makeText(requireContext(), "Registration Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun uploadImageThenSave(userId: String, username: String, email: String) {
        Toast.makeText(requireContext(), "Uploading picture…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val url = CloudinaryUploader.upload(requireContext(), selectedImageUri!!)
                saveUserToDatabase(userId, username, email, url)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Image upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                saveUserToDatabase(userId, username, email, "")
            }
        }
    }

    private fun saveUserToDatabase(userId: String, username: String, email: String, imageUrl: String) {
        val userMap = hashMapOf(
            "id"              to userId,
            "username"        to username,
            "email"           to email,
            "profileImageUrl" to imageUrl
        )
        db.collection("users").document(userId).set(userMap)
            .addOnSuccessListener {
                viewModel.saveUser(UserEntity(userId, username, email, imageUrl))
                (activity as? MainActivity)?.onLoginSuccess()
            }
            .addOnFailureListener {
                binding.btnRegister.isEnabled = true
                Toast.makeText(requireContext(), "Failed to save user info", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
