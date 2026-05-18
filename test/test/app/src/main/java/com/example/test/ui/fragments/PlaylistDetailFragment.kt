package com.example.test.ui.fragments

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
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

class PlaylistDetailFragment : Fragment() {

    companion object {
        private const val ARG_PLAYLIST_ID = "playlist_id"

        fun newInstance(playlistId: String): PlaylistDetailFragment {
            return PlaylistDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PLAYLIST_ID, playlistId)
                }
            }
        }
    }

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MusicViewModel
    private lateinit var adapter: SongAdapter
    private var playerListener: Player.Listener? = null

    private val playlistId: String
        get() = arguments?.getString(ARG_PLAYLIST_ID) ?: "test_playlist"

    private var currentPlaylistMetadata = Playlist(
        id = "test_playlist",
        name = "My Playlist",
        imageUrl = ""
    )

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                val flag = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(uri, flag)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            currentPlaylistMetadata = currentPlaylistMetadata.copy(imageUrl = uri.toString())
            updateUI(currentPlaylistMetadata.name, currentPlaylistMetadata.imageUrl)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database = AppDatabase.getDatabase(requireContext())
        // Merged repository initialization (ensuring all necessary DAOs are passed)
        val repository = MusicRepository(database.songDao(), database.playlistDao(), database.userDao())
        val factory = MusicViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(MusicViewModel::class.java)

        setupRecyclerView()
        setupObservers()

        viewModel.loadPlaylist(playlistId)
        viewModel.loadSongs(playlistId)

        binding.btnAddSong.setOnClickListener {
            // Merged navigation: Passes current ID to LibraryFragment so it knows the target
            val libraryFragment = LibraryFragment.newInstance(playlistId)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, libraryFragment)
                .addToBackStack(null)
                .commit()
        }

        binding.edit.setOnClickListener {
            showEditDialog()
        }

        if (MusicPlayerManager.isInitialized()) {
            attachPlayerListeners()
        } else {
            MusicPlayerManager.initialize(requireContext()) {
                if (isAdded) attachPlayerListeners()
            }
        }
    }

    private fun attachPlayerListeners() {
        playerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Keep list highlight in sync with playback
                adapter.notifyDataSetChanged()
                // Ensure MiniPlayer updates when songs skip automatically
                (activity as? MainActivity)?.updateMiniPlayerUI()
            }
        }
        MusicPlayerManager.player.addListener(playerListener!!)
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            songs = emptyList(),
            onSongClick = { song ->
                val songs = adapter.getSongs()
                val index = songs.indexOf(song)
                playPlaylistSongs(songs, index)
            },
            onAddClick = null,
            showAddButton = false,
            onOptionsClick = { song, anchorView ->
                showSongOptionsPopup(song, anchorView)
            }
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
                    val entity = SongEntity(song.audioUrl, song.title, song.artist, song.imageUrl, playlistId)
                    viewModel.removeSongFromPlaylist(entity)
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
                currentPlaylistMetadata = Playlist(it.id, it.name, it.imageUrl)
                updateUI(it.name, it.imageUrl)
            }
        }

        viewModel.songs.observe(viewLifecycleOwner) { dbSongs ->
            val songList = dbSongs.map { Song(it.title, it.artist, it.imageUrl, it.audioUrl) }
            adapter.updateSongs(songList)
        }
    }

    private fun updateUI(name: String, imageUrl: String) {
        binding.playlistTitle.text = name
        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .into(binding.playlistArt)
    }

    private fun playPlaylistSongs(songs: List<Song>, index: Int) {
        if (!MusicPlayerManager.isInitialized()) return
        MusicPlayerManager.playPlaylist(songs, index)
        adapter.notifyDataSetChanged()
        (activity as? MainActivity)?.updateMiniPlayerUI()
    }

    private fun showEditDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_playlist, null)
        val nameInput = dialogView.findViewById<android.widget.EditText>(R.id.etPlaylistName)
        val btnSave = dialogView.findViewById<android.widget.Button>(R.id.btnSave)
        val btnUpload = dialogView.findViewById<Button>(R.id.btnUploadImage)

        nameInput.setText(currentPlaylistMetadata.name)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnSave.setOnClickListener {
            val newName = nameInput.text.toString()
            if (newName.isNotEmpty()) {
                viewModel.updatePlaylist(playlistId, newName, currentPlaylistMetadata.imageUrl)
                dialog.dismiss()
            }
        }

        btnUpload.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (MusicPlayerManager.isInitialized() && playerListener != null) {
            MusicPlayerManager.player.removeListener(playerListener!!)
        }
        _binding = null
    }
}