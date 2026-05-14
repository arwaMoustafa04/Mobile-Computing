package com.example.test.ui.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.data.local.entity.SongEntity
import com.example.test.data.model.Playlist
import com.example.test.databinding.FragmentPlaylistDetailBinding
import com.example.test.player.MusicPlayerManager
import kotlinx.coroutines.launch
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.ViewModelProvider
import com.example.test.data.repository.MusicRepository
import com.example.test.data.ui.MusicViewModel
import com.example.test.data.ui.MusicViewModelFactory

class PlaylistDetailFragment : Fragment() {

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MusicViewModel
    private val timerHandler = Handler(Looper.getMainLooper())
    private lateinit var adapter: SongAdapter
    
    // Local state for the playlist metadata being edited or displayed
    private var currentPlaylistMetadata = Playlist(
        id = "test_playlist",
        name = "My Summer Hits",
        imageUrl = "https://raw.githubusercontent.com/TDMMELO/my-music-files/main/momken.jpg"
    )

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                val flag = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(uri, flag)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Update local state and UI immediately, will be saved to DB on "Save" click
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

        // Initialize ViewModel
        val database = AppDatabase.getDatabase(requireContext())
        val repository = MusicRepository(database.songDao(), database.playlistDao())
        val factory = MusicViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(MusicViewModel::class.java)

        setupRecyclerView()
        setupObservers()

        // Load initial data
        viewModel.loadPlaylist("test_playlist")
        viewModel.loadSongs("test_playlist")

        // Listeners
        binding.btnAddSong.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LibraryFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.edit.setOnClickListener {
            showEditDialog()
        }

        MusicPlayerManager.player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                adapter.notifyDataSetChanged()
            }
        })
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            songs = emptyList(),
            onSongClick = { song ->
                val index = MusicPlayerManager.playingList.indexOf(song)
                playPlaylistSong(song, index)
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
                    MusicPlayerManager.queue.add(song)
                    Toast.makeText(requireContext(), "${song.title} added to Queue", Toast.LENGTH_SHORT).show()
                    true
                }
                "Remove from Playlist" -> {
                    val entity = SongEntity(
                        audioUrl = song.audioUrl,
                        title = song.title,
                        artist = song.artist,
                        imageUrl = song.imageUrl,
                        playlistId = "test_playlist"
                    )
                    viewModel.removeSongFromPlaylist(entity)
                    Toast.makeText(requireContext(), "${song.title} removed", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun setupObservers() {
        // Observe Playlist metadata
        viewModel.playlist.observe(viewLifecycleOwner) { entity ->
            entity?.let {
                currentPlaylistMetadata = Playlist(it.id, it.name, it.imageUrl)
                updateUI(it.name, it.imageUrl)
            }
        }

        // Observe Songs
        viewModel.songs.observe(viewLifecycleOwner) { dbSongs ->
            val songList = dbSongs.map { Song(it.title, it.artist, it.imageUrl, it.audioUrl) }
            adapter.updateSongs(songList)
            MusicPlayerManager.playingList = songList
        }
    }

    private fun updateUI(name: String, imageUrl: String) {
        binding.playlistTitle.text = name
        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .into(binding.playlistArt)
    }

    private fun playPlaylistSong(song: Song, index: Int) {
        if (!MusicPlayerManager.isInitialized()) return

        // 1. LOCK the playback list to the items currently in the adapter.
        // This ensures that even if you navigate to the Library screen,
        // the "Next" button in MainActivity still sees the Playlist songs.
        MusicPlayerManager.activePlaybackList = adapter.getSongs().toMutableList()

        // 2. Set the state
        MusicPlayerManager.currentSong = song
        MusicPlayerManager.currentSongIndex = index

        // 3. Start playback
        val player = MusicPlayerManager.player
        val mediaItem = MediaItem.fromUri(song.audioUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        // 4. Refresh UI
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
                viewModel.updatePlaylist("test_playlist", newName, currentPlaylistMetadata.imageUrl)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        btnUpload.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
