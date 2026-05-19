package com.example.test.ui.fragments

// AI-assisted: Firebase Firestore sync, Cloudinary image upload, real-time listeners

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.test.MainActivity
import com.example.test.R
import com.example.test.data.local.database.AppDatabase
import com.example.test.data.local.entity.UserEntity
import com.example.test.data.repository.MusicRepository
import com.example.test.data.ui.MusicViewModel
import com.example.test.data.ui.MusicViewModelFactory
import com.example.test.databinding.FragmentProfileBinding
import com.example.test.util.CloudinaryUploader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var viewModel: MusicViewModel

    private var currentUsername        = ""
    private var currentProfileImageUrl = ""
    private var currentEmail           = ""

    // Real-time Firestore listener — keeps profile in sync across devices
    private var profileListener: ListenerRegistration? = null

    // Dialog state
    private var openDialog: android.app.AlertDialog? = null
    private var dialogImageView: ImageView? = null
    private var dialogSaveButton: Button? = null
    private var pendingImageUrl: String?  = null

    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) uploadProfileImage(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
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

        auth.currentUser?.uid?.let { userId -> listenToProfile(userId) }

        binding.btnEditProfile.setOnClickListener { showEditDialog() }
        binding.btnLogout.setOnClickListener {
            // Remove Firestore listener before logout so it doesn't fire during teardown
            profileListener?.remove()
            profileListener = null
            (activity as? MainActivity)?.logout()
        }
    }

    /**
     * Attaches a real-time Firestore listener on the user document.
     * Any change (from this device OR another) is pushed here instantly
     * and reflected in the UI without requiring a logout/login.
     */
    private fun listenToProfile(userId: String) {
        profileListener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val newUsername = snapshot.getString("username")        ?: ""
                val newImageUrl = snapshot.getString("profileImageUrl") ?: ""
                val newEmail    = snapshot.getString("email")           ?: ""

                // Only re-draw if something actually changed
                val imageChanged = newImageUrl != currentProfileImageUrl

                currentUsername        = newUsername
                currentProfileImageUrl = newImageUrl
                currentEmail           = newEmail

                // Also keep Room in sync
                viewModel.saveUser(UserEntity(userId, newUsername, newEmail, newImageUrl))

                updateUI(imageChanged)
            }
    }

    private fun updateUI(skipImageCache: Boolean = false) {
        binding.tvUsername.text = currentUsername

        val glideRequest = Glide.with(this)
            .load(currentProfileImageUrl)
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .circleCrop()

        if (skipImageCache) {
            // Bypass Glide's disk cache so the new image is fetched immediately
            glideRequest.diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
        }

        glideRequest.into(binding.profilePicture)
    }

    private fun showEditDialog() {
        pendingImageUrl = null

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_profile, null)
        val nameInput  = dialogView.findViewById<EditText>(R.id.etUsername)
        val ivPreview  = dialogView.findViewById<ImageView>(R.id.ivProfilePreview)
        val btnChange  = dialogView.findViewById<Button>(R.id.btnUploadImage)
        val btnSave    = dialogView.findViewById<Button>(R.id.btnSave)

        nameInput.setText(currentUsername)
        Glide.with(this).load(currentProfileImageUrl)
            .placeholder(R.drawable.profile).circleCrop().into(ivPreview)

        dialogImageView  = ivPreview
        dialogSaveButton = btnSave

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        openDialog = dialog

        btnChange.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnSave.setOnClickListener {
            val newName = nameInput.text.toString().trim()
            if (newName.isNotEmpty()) {
                updateProfile(newName, pendingImageUrl ?: currentProfileImageUrl)
                dialog.dismiss()
            }
        }

        dialog.setOnDismissListener {
            openDialog       = null
            dialogImageView  = null
            dialogSaveButton = null
        }
        dialog.show()
    }

    private fun uploadProfileImage(uri: Uri) {
        dialogSaveButton?.isEnabled = false
        Toast.makeText(requireContext(), "Uploading picture…", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val url = CloudinaryUploader.upload(requireContext(), uri)
                pendingImageUrl = url
                dialogImageView?.let { iv ->
                    Glide.with(this@ProfileFragment).load(url)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .placeholder(R.drawable.profile).circleCrop().into(iv)
                }
                Toast.makeText(requireContext(), "Picture ready — tap Save", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                dialogSaveButton?.isEnabled = true
            }
        }
    }

    private fun updateProfile(username: String, imageUrl: String) {
        val userId = auth.currentUser?.uid ?: return
        // Writing to Firestore triggers the real-time listener above on ALL devices
        db.collection("users").document(userId)
            .update(mapOf("username" to username, "profileImageUrl" to imageUrl))
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove the listener to avoid memory leaks
        profileListener?.remove()
        profileListener = null
        _binding = null
    }
}