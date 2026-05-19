package com.example.test.ui.fragments

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.musicplayer.Song
import com.example.test.MainActivity
import com.example.test.R
import com.example.test.SongAdapter
import com.example.test.data.local.database.AppDatabase
import com.example.test.data.local.entity.SongEntity
import com.example.test.data.model.Playlist
import com.example.test.data.repository.MusicRepository
import com.example.test.data.ui.MusicViewModel
import com.example.test.data.ui.MusicViewModelFactory
import com.example.test.databinding.FragmentPlaylistDetailBinding
import com.example.test.player.MusicPlayerManager
import com.example.test.util.CloudinaryUploader
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class PlaylistDetailFragment : Fragment() {

    companion object {
        private const val ARG_PLAYLIST_ID = "playlist_id"
        fun newInstance(playlistId: String) = PlaylistDetailFragment().apply {
            arguments = Bundle().apply { putString(ARG_PLAYLIST_ID, playlistId) }
        }
    }

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MusicViewModel
    private lateinit var adapter: SongAdapter
    private lateinit var auth: FirebaseAuth
    private var playerListener: Player.Listener? = null

    // Edit dialog state
    private var dialogCoverPreview: ImageView? = null
    private var dialogSaveButton: Button? = null
    private var pendingCoverUrl: String? = null

    private val playlistId: String
        get() = arguments?.getString(ARG_PLAYLIST_ID) ?: "test_playlist"

    private var currentPlaylistMetadata = Playlist("test_playlist", "My Playlist", "")

    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) uploadCoverImage(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistDetailBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database   = AppDatabase.getDatabase(requireContext())
        val repository = MusicRepository(database.songDao(), database.playlistDao(), database.userDao())
        val factory    = MusicViewModelFactory(repository)
        viewModel      = ViewModelProvider(this, factory).get(MusicViewModel::class.java)

        setupRecyclerView()
        setupObservers()
        viewModel.loadPlaylist(playlistId)
        viewModel.loadSongs(playlistId)

        binding.btnAddSong.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LibraryFragment.newInstance(playlistId))
                .addToBackStack(null).commit()
        }
        binding.edit.setOnClickListener { showEditDialog() }

        if (MusicPlayerManager.isInitialized()) attachPlayerListeners()
        else MusicPlayerManager.initialize(requireContext()) { if (isAdded) attachPlayerListeners() }
    }

    private fun attachPlayerListeners() {
        playerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                adapter.notifyDataSetChanged()
                (activity as? MainActivity)?.updateMiniPlayerUI()
            }
        }
        MusicPlayerManager.player.addListener(playerListener!!)
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            songs         = emptyList(),
            onSongClick   = { song ->
                val songs = adapter.getSongs()
                playPlaylistSongs(songs, songs.indexOf(song))
            },
            onAddClick    = null,
            showAddButton = false,
            onOptionsClick = { song, anchor -> showSongOptionsPopup(song, anchor) }
        )
        binding.playlistRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.playlistRecyclerView.adapter = adapter
    }

    private fun showSongOptionsPopup(song: Song, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menu.add("Add to Queue")
        popup.menu.add("Remove from Playlist")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Add to Queue" -> {
                    MusicPlayerManager.addSongToQueue(song)
                    Toast.makeText(requireContext(), "${song.title} added to Queue", Toast.LENGTH_SHORT).show()
                    true
                }
                "Remove from Playlist" -> {
                    auth.currentUser?.uid?.let { userId ->
                        viewModel.removeSongFromPlaylist(
                            SongEntity(song.audioUrl, song.title, song.artist, song.imageUrl, playlistId),
                            userId
                        )
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun setupObservers() {
        viewModel.playlist.observe(viewLifecycleOwner) { entity ->
            entity?.let {
                val imageChanged = it.imageUrl != currentPlaylistMetadata.imageUrl
                currentPlaylistMetadata = Playlist(it.id, it.name, it.imageUrl)
                updateUI(it.name, it.imageUrl, skipImageCache = imageChanged)
            }
        }
        viewModel.songs.observe(viewLifecycleOwner) { dbSongs ->
            adapter.updateSongs(dbSongs.map { Song(it.title, it.artist, it.imageUrl, it.audioUrl) })
        }
    }

    private fun updateUI(name: String, imageUrl: String, skipImageCache: Boolean = false) {
        binding.playlistTitle.text = name
        val glideRequest = Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.placeholder_image)
        if (skipImageCache) {
            glideRequest
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
        }
        glideRequest.into(binding.playlistArt)
    }

    private fun playPlaylistSongs(songs: List<Song>, index: Int) {
        if (!MusicPlayerManager.isInitialized()) return
        MusicPlayerManager.playPlaylist(songs, index, playlistId)
        adapter.notifyDataSetChanged()
        (activity as? MainActivity)?.updateMiniPlayerUI()
    }

    private fun showEditDialog() {
        pendingCoverUrl = null

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_playlist, null)
        val nameInput  = dialogView.findViewById<android.widget.EditText>(R.id.etPlaylistName)
        val ivPreview  = dialogView.findViewById<ImageView>(R.id.ivCoverPreview)
        val btnChange  = dialogView.findViewById<Button>(R.id.btnUploadImage)
        val btnSave    = dialogView.findViewById<Button>(R.id.btnSave)

        nameInput.setText(currentPlaylistMetadata.name)
        Glide.with(this).load(currentPlaylistMetadata.imageUrl)
            .placeholder(R.drawable.placeholder_image).into(ivPreview)

        dialogCoverPreview = ivPreview
        dialogSaveButton   = btnSave

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnChange.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnSave.setOnClickListener {
            val newName = nameInput.text.toString().trim()
            val userId  = auth.currentUser?.uid
            if (newName.isNotEmpty() && userId != null) {
                viewModel.updatePlaylist(playlistId, userId, newName, pendingCoverUrl ?: currentPlaylistMetadata.imageUrl)
                dialog.dismiss()
            }
        }

        dialog.setOnDismissListener { dialogCoverPreview = null; dialogSaveButton = null }
        dialog.show()
    }

    private fun uploadCoverImage(uri: Uri) {
        dialogSaveButton?.isEnabled = false
        Toast.makeText(requireContext(), "Uploading cover…", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val url = CloudinaryUploader.upload(requireContext(), uri)
                pendingCoverUrl = url
                dialogCoverPreview?.let { iv ->
                    Glide.with(this@PlaylistDetailFragment).load(url)
                        .placeholder(R.drawable.placeholder_image).into(iv)
                }
                Toast.makeText(requireContext(), "Cover ready — tap Save", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Cover upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                dialogSaveButton?.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (MusicPlayerManager.isInitialized() && playerListener != null) {
            MusicPlayerManager.player.removeListener(playerListener!!)
        }
        dialogCoverPreview = null
        dialogSaveButton   = null
        _binding = null
    }
}