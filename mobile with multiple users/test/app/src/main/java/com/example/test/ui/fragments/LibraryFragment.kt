package com.example.test.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.app.AlertDialog
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
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.SongAdapter
import com.example.test.data.local.database.AppDatabase
import com.example.test.data.local.entity.SongEntity
import com.example.test.data.repository.MusicRepository
import com.example.test.data.ui.MusicViewModel
import com.example.test.data.ui.MusicViewModelFactory
import com.example.test.databinding.FragmentLibraryBinding
import com.example.test.player.MusicPlayerManager
import com.google.firebase.auth.FirebaseAuth
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
    private var librarySongs = mutableListOf<Song>()
    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SongAdapter
    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore
    private var fullSongList = mutableListOf<Song>()

    private var playerListener: Player.Listener? = null

    private var allPlaylists: List<PlaylistEntity> = emptyList()
    private val targetPlaylistId: String?
        get() = arguments?.getString(ARG_TARGET_PLAYLIST_ID)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database = AppDatabase.getDatabase(requireContext())
        val repository = MusicRepository(database.songDao(), database.playlistDao(), database.userDao())
        val factory = MusicViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(MusicViewModel::class.java)

        setupAdapter()

        val userId = auth.currentUser?.uid
        if (userId != null) {
            viewModel.getPlaylistsByUser(userId).observe(viewLifecycleOwner) { playlists ->
                allPlaylists = playlists
            }
        }

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
                adapter.notifyDataSetChanged()
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
        MusicPlayerManager.playPlaylist(librarySongs, index, null)
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
            onAddClick = { song, anchorView ->
                showPopup(song, anchorView)
            },
            showAddButton = true
        )
    }

    private fun showPopup(song: Song, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_song_options, popup.menu)
        targetPlaylistId?.let {
            popup.menu.findItem(R.id.action_add_to_playlist).title = "Add to current Playlist"
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
        // ← Get userId so we can sync the song addition to Firestore
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val entity = SongEntity(
            audioUrl   = song.audioUrl,
            title      = song.title,
            artist     = song.artist,
            imageUrl   = song.imageUrl,
            playlistId = playlistId,
            addedAt    = System.currentTimeMillis()
        )
        // ← Pass userId so the repo writes to Firestore as well as Room
        viewModel.addSongToPlaylist(entity, userId)

        // If this playlist is currently playing, add the song to the player timeline too
        if (MusicPlayerManager.isInitialized() && MusicPlayerManager.activePlaylistId == playlistId) {
            MusicPlayerManager.addSongToEnd(song)
        }

        Toast.makeText(requireContext(), "Added to playlist!", Toast.LENGTH_SHORT).show()
    }

    private fun showPlaylistPickerDialog(song: Song) {
        if (allPlaylists.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "No playlists yet. Create one first!",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val playlistNames = allPlaylists.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Add to Playlist")
            .setItems(playlistNames) { _, index ->
                addSongToSpecificPlaylist(song, allPlaylists[index].id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (MusicPlayerManager.isInitialized() && playerListener != null) {
            MusicPlayerManager.player.removeListener(playerListener!!)
        }
        _binding = null
    }
}
