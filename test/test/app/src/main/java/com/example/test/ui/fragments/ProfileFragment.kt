package com.example.test.ui.fragments

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.test.MainActivity
import com.example.test.R
import com.example.test.data.local.database.AppDatabase
import com.example.test.data.repository.MusicRepository
import com.example.test.data.ui.MusicViewModel
import com.example.test.data.ui.MusicViewModelFactory
import com.example.test.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var viewModel: MusicViewModel
    
    private var currentUsername = ""
    private var currentProfileImageUrl = ""
    private var currentEmail = ""

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uploadImage(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database = AppDatabase.getDatabase(requireContext())
        val repository = MusicRepository(database.songDao(), database.playlistDao(), database.userDao())
        val factory = MusicViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(MusicViewModel::class.java)

        val userId = auth.currentUser?.uid
        if (userId != null) {
            viewModel.getUser(userId).observe(viewLifecycleOwner) { user ->
                user?.let {
                    currentUsername = it.username
                    currentProfileImageUrl = it.profileImageUrl
                    currentEmail = it.email
                    updateUI()
                }
            }
        }

        binding.btnEditProfile.setOnClickListener {
            showEditDialog()
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            (activity as? MainActivity)?.navigateToLogin()
        }
    }

    private fun updateUI() {
        binding.tvUsername.text = currentUsername
        Glide.with(this)
            .load(currentProfileImageUrl)
            .placeholder(R.drawable.profile)
            .into(binding.profilePicture)
    }

    private fun showEditDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_profile, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.etUsername)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        val btnUpload = dialogView.findViewById<Button>(R.id.btnUploadImage)

        nameInput.setText(currentUsername)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnSave.setOnClickListener {
            val newName = nameInput.text.toString()
            if (newName.isNotEmpty()) {
                updateProfile(newName, currentProfileImageUrl)
                dialog.dismiss()
            }
        }

        btnUpload.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        dialog.show()
    }

    private fun uploadImage(uri: android.net.Uri) {
        val userId = auth.currentUser?.uid ?: return
        val ref = storage.reference.child("profile_pictures/$userId")
        ref.putFile(uri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    updateProfile(currentUsername, downloadUri.toString())
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Image upload failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateProfile(username: String, imageUrl: String) {
        val userId = auth.currentUser?.uid ?: return
        val updates = hashMapOf<String, Any>(
            "username" to username,
            "profileImageUrl" to imageUrl
        )

        db.collection("users").document(userId).update(updates)
            .addOnSuccessListener {
                viewModel.saveUser(com.example.test.data.local.entity.UserEntity(userId, username, currentEmail, imageUrl))
                Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to update profile", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
