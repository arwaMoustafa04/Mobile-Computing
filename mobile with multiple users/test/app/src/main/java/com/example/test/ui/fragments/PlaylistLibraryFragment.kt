package com.example.test.ui.fragments

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.test.PlaylistAdapter
import com.example.test.R
import com.example.test.data.local.database.AppDatabase
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.data.repository.MusicRepository
import com.example.test.data.ui.MusicViewModel
import com.example.test.data.ui.MusicViewModelFactory
import com.example.test.databinding.FragmentPlaylistLibraryBinding
import com.example.test.util.CloudinaryUploader
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.UUID

class PlaylistLibraryFragment : Fragment() {

    private var _binding: FragmentPlaylistLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MusicViewModel
    private lateinit var adapter: PlaylistAdapter
    private lateinit var auth: FirebaseAuth

    // Dialog state
    private var dialogCoverImageView: ImageView? = null
    private var dialogCreateButton: View? = null
    private var pendingCoverUrl: String? = null

    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) uploadCoverImage(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistLibraryBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database   = AppDatabase.getDatabase(requireContext())
        val repository = MusicRepository(database.songDao(), database.playlistDao(), database.userDao())
        val factory    = MusicViewModelFactory(repository)
        viewModel      = ViewModelProvider(this, factory)[MusicViewModel::class.java]

        setupRecyclerView()
        observePlaylists()
        binding.btnNewPlaylist.setOnClickListener { showCreatePlaylistDialog() }
    }

    private fun setupRecyclerView() {
        adapter = PlaylistAdapter(
            onPlaylistClick = { playlist -> openPlaylist(playlist) },
            onMoreClick     = { playlist, anchor -> showPlaylistOptionsMenu(playlist, anchor) }
        )
        binding.rvPlaylists.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPlaylists.adapter = adapter
    }

    private fun observePlaylists() {
        val userId = auth.currentUser?.uid ?: return
        viewModel.getPlaylistsByUser(userId).observe(viewLifecycleOwner) { playlists ->
            adapter.updatePlaylists(playlists)
            binding.tvEmpty.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openPlaylist(playlist: PlaylistEntity) {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.slide_in_left, android.R.anim.slide_out_right,
                android.R.anim.slide_in_left, android.R.anim.slide_out_right
            )
            .replace(R.id.fragment_container, PlaylistDetailFragment.newInstance(playlist.id))
            .addToBackStack(null).commit()
    }

    private fun showPlaylistOptionsMenu(playlist: PlaylistEntity, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add("Delete Playlist")
        popup.setOnMenuItemClickListener { item ->
            if (item.title == "Delete Playlist") { confirmDelete(playlist); true } else false
        }
        popup.show()
    }

    private fun confirmDelete(playlist: PlaylistEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Playlist")
            .setMessage("Are you sure you want to delete \"${playlist.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                val userId = auth.currentUser?.uid ?: return@setPositiveButton
                viewModel.deletePlaylist(playlist.id, userId)
                Toast.makeText(requireContext(), "Playlist deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showCreatePlaylistDialog() {
        pendingCoverUrl = null

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_playlist, null)
        val etName     = dialogView.findViewById<EditText>(R.id.etNewPlaylistName)
        val imgPreview = dialogView.findViewById<ImageView>(R.id.imgNewPlaylistCover)
        val btnPickImg = dialogView.findViewById<View>(R.id.btnPickCoverImage)
        val btnCreate  = dialogView.findViewById<View>(R.id.btnCreatePlaylist)
        val btnCancel  = dialogView.findViewById<View>(R.id.btnCancelCreate)

        dialogCoverImageView = imgPreview
        dialogCreateButton   = btnCreate

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnPickImg.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnCreate.setOnClickListener {
            val name   = etName.text.toString().trim()
            val userId = auth.currentUser?.uid
            if (name.isEmpty()) { etName.error = "Please enter a playlist name"; return@setOnClickListener }
            if (userId == null) { Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            viewModel.createPlaylist(
                PlaylistEntity(
                    id       = UUID.randomUUID().toString(),
                    userId   = userId,
                    name     = name,
                    imageUrl = pendingCoverUrl ?: ""
                )
            )
            Toast.makeText(requireContext(), "\"$name\" created!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { dialogCoverImageView = null; dialogCreateButton = null }
        dialog.show()
    }

    private fun uploadCoverImage(uri: Uri) {
        dialogCreateButton?.isEnabled = false
        Toast.makeText(requireContext(), "Uploading cover…", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val url = CloudinaryUploader.upload(requireContext(), uri)
                pendingCoverUrl = url
                dialogCoverImageView?.let { iv ->
                    iv.visibility = View.VISIBLE
                    Glide.with(this@PlaylistLibraryFragment).load(url)
                        .placeholder(R.drawable.placeholder_image).into(iv)
                }
                Toast.makeText(requireContext(), "Cover ready!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Cover upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                dialogCreateButton?.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dialogCoverImageView = null
        dialogCreateButton   = null
        _binding = null
    }
}
