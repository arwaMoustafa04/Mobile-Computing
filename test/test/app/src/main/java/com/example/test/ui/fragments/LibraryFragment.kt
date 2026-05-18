package com.example.test.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.Song
import com.example.test.MainActivity
import com.example.test.R
import com.example.test.SongAdapter
import com.example.test.data.local.database.AppDatabase
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.data.local.entity.SongEntity
import com.example.test.data.repository.MusicRepository
import com.example.test.data.ui.MusicViewModel
import com.example.test.data.ui.MusicViewModelFactory
import com.example.test.databinding.FragmentLibraryBinding
import com.example.test.player.MusicPlayerManager
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class LibraryFragment : Fragment() {

    companion object {
        private const val ARG_TARGET_PLAYLIST_ID = "target_playlist_id"

        fun newInstance(targetPlaylistId: String? = null): LibraryFragment {
            return LibraryFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TARGET_PLAYLIST_ID, targetPlaylistId)
                }
            }
        }
    }

    private lateinit var viewModel: MusicViewModel
    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SongAdapter
    private val db = Firebase.firestore

    private var librarySongs = mutableListOf<Song>()
    private var fullSongList = mutableListOf<Song>()
    private var allPlaylists: List<PlaylistEntity> = emptyList()
    private var playerListener: Player.Listener? = null

    private val targetPlaylistId: String?
        get() = arguments?.getString(ARG_TARGET_PLAYLIST_ID)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel with Repository (Including UserDao if required by your old version)
        val database = AppDatabase.getDatabase(requireContext())
        val repository = MusicRepository(database.songDao(), database.playlistDao(), database.userDao())
        val factory = MusicViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(MusicViewModel::class.java)

        setupAdapter()

        // Observe playlists so the picker dialog is always ready
        viewModel.allPlaylists.observe(viewLifecycleOwner) { playlists ->
            allPlaylists = playlists
        }

        // Initialize Player and Listeners
        if (MusicPlayerManager.isInitialized()) {
            attachPlayerListeners()
        } else {
            MusicPlayerManager.initialize(requireContext()) {
                if (isAdded) attachPlayerListeners()
            }
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        setupSearch()
        fetchSongsFromFirebase()
    }

    private fun attachPlayerListeners() {
        playerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Refresh yellow highlight on list
                adapter.notifyDataSetChanged()
                // Sync MiniPlayer in MainActivity
                (activity as? MainActivity)?.updateMiniPlayerUI()
            }
        }
        MusicPlayerManager.player.addListener(playerListener!!)
    }

    private fun fetchSongsFromFirebase() {
        db.collection("songs").get().addOnSuccessListener { result ->
            librarySongs.clear()
            fullSongList.clear()
            for (document in result) {
                val song = document.toObject(Song::class.java)
                librarySongs.add(song)
                fullSongList.add(song)
            }
            adapter.updateSongs(librarySongs)
        }
    }

    private fun playSong(song: Song) {
        if (!MusicPlayerManager.isInitialized()) return
        val index = librarySongs.indexOf(song)

        // Start playback and update global UI
        MusicPlayerManager.playPlaylist(librarySongs, index)
        adapter.notifyDataSetChanged()
        (activity as? MainActivity)?.updateMiniPlayerUI()
    }

    private fun setupSearch() {
        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.cancel.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                val query = s.toString().lowercase()
                val filtered = fullSongList.filter {
                    it.title.lowercase().contains(query) || it.artist.lowercase().contains(query)
                }
                adapter.updateSongs(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.cancel.setOnClickListener {
            binding.searchBar.text?.clear()
            binding.searchBar.clearFocus()
            adapter.updateSongs(fullSongList)
        }
    }

    private fun setupAdapter() {
        adapter = SongAdapter(
            songs = fullSongList,
            onSongClick = { song -> playSong(song) },
            onAddClick = { song, anchorView -> showPopup(song, anchorView) },
            showAddButton = true
        )
    }

    private fun showPopup(song: Song, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_song_options, popup.menu)

        // Context-aware menu title
        targetPlaylistId?.let {
            val item = popup.menu.findItem(R.id.action_add_to_playlist)
            item.title = "Add to current Playlist"
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_add_to_playlist -> {
                    if (targetPlaylistId != null) {
                        addSongToSpecificPlaylist(song, targetPlaylistId!!)
                    } else {
                        showPlaylistPickerDialog(song)
                    }
                    true
                }
                R.id.action_add_to_queue -> {
                    MusicPlayerManager.addSongToQueue(song)
                    Toast.makeText(requireContext(), "Added to queue!", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun addSongToSpecificPlaylist(song: Song, playlistId: String) {
        val entity = SongEntity(
            audioUrl = song.audioUrl,
            title = song.title,
            artist = song.artist,
            imageUrl = song.imageUrl,
            playlistId = playlistId,
            //addedAt = System.currentTimeMillis()
        )
        viewModel.addSongToPlaylist(entity)
        Toast.makeText(requireContext(), "Added to playlist!", Toast.LENGTH_SHORT).show()
    }

    private fun showPlaylistPickerDialog(song: Song) {
        if (allPlaylists.isEmpty()) {
            Toast.makeText(requireContext(), "No playlists found.", Toast.LENGTH_SHORT).show()
            return
        }

        val playlistNames = allPlaylists.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Add to Playlist")
            .setItems(playlistNames) { _, index ->
                val selectedPlaylist = allPlaylists[index]
                addSongToSpecificPlaylist(song, selectedPlaylist.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Prevent memory leaks by removing listeners
        if (MusicPlayerManager.isInitialized() && playerListener != null) {
            MusicPlayerManager.player.removeListener(playerListener!!)
        }
        _binding = null
    }
}