package com.example.test.ui.fragments

// AI-assisted: Firebase Firestore sync, Cloudinary image upload, real-time listeners

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
import com.example.test.util.NetworkUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class LibraryFragment : Fragment() {

    companion object {
        private const val ARG_TARGET_PLAYLIST_ID = "target_playlist_id"
        fun newInstance(targetPlaylistId: String? = null) = LibraryFragment().apply {
            arguments = Bundle().apply { putString(ARG_TARGET_PLAYLIST_ID, targetPlaylistId) }
        }
    }

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MusicViewModel
    private lateinit var adapter: SongAdapter
    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore

    private var librarySongs = mutableListOf<Song>()
    private var fullSongList = mutableListOf<Song>()
    private var allPlaylists: List<PlaylistEntity> = emptyList()
    private var playerListener: Player.Listener? = null

    private val targetPlaylistId: String?
        get() = arguments?.getString(ARG_TARGET_PLAYLIST_ID)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
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
        setupSearch()
        observeErrors()

        auth.currentUser?.uid?.let { userId ->
            viewModel.getPlaylistsByUser(userId).observe(viewLifecycleOwner) { allPlaylists = it }
        }

        if (MusicPlayerManager.isInitialized()) attachPlayerListeners()
        else MusicPlayerManager.initialize(requireContext()) { if (isAdded) attachPlayerListeners() }

        fetchSongs()
    }

    // ---------------------------------------------------------------------------
    // Data loading — offline-first strategy
    // ---------------------------------------------------------------------------

    private fun fetchSongs() {
        if (NetworkUtils.isOnline(requireContext())) {
            fetchSongsFromFirestore()
        } else {
            // Offline — nothing to show from local DB for the global song library
            // (songs live in Firestore, not Room), so show a friendly message
            showOfflineBanner()
        }
    }

    private fun fetchSongsFromFirestore() {
        showLoading(true)
        db.collection("songs").get()
            .addOnSuccessListener { result ->
                if (!isAdded) return@addOnSuccessListener
                showLoading(false)
                hideOfflineBanner()

                val fetched = result.documents.mapNotNull { it.toObject(Song::class.java) }
                librarySongs = fetched.toMutableList()
                fullSongList = fetched.toMutableList()
                adapter.updateSongs(fetched)

                if (fetched.isEmpty()) {
                    showEmptyState("No songs available yet.")
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                showLoading(false)
                // Network call failed — show cached data if any, else error
                if (fullSongList.isNotEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Couldn't refresh songs. Showing cached data.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    showEmptyState("Couldn't load songs: ${e.message}")
                }
            }
    }

    // ---------------------------------------------------------------------------
    // UI state helpers
    // ---------------------------------------------------------------------------

    private fun showLoading(show: Boolean) {
        // Uses the progressBar view added to fragment_library.xml
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showOfflineBanner() {
        binding.tvOffline.visibility = View.VISIBLE
        binding.tvOffline.text = getString(R.string.msg_offline)
        // Still show whatever is cached
        adapter.updateSongs(fullSongList)
    }

    private fun hideOfflineBanner() {
        binding.tvOffline.visibility = View.GONE
    }

    private fun showEmptyState(message: String) {
        binding.tvOffline.visibility = View.VISIBLE
        binding.tvOffline.text = message
    }

    private fun observeErrors() {
        viewModel.error.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    // ---------------------------------------------------------------------------
    // RecyclerView & search
    // ---------------------------------------------------------------------------

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            songs         = emptyList(),
            onSongClick   = { song -> playSong(song) },
            onAddClick    = { song, anchor -> showPopup(song, anchor) },
            showAddButton = true
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
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

    private fun playSong(song: Song) {
        if (!MusicPlayerManager.isInitialized()) return
        MusicPlayerManager.playPlaylist(librarySongs, librarySongs.indexOf(song), null)
        adapter.notifyDataSetChanged()
        (activity as? MainActivity)?.updateMiniPlayerUI()
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

    // ---------------------------------------------------------------------------
    // Popup menu
    // ---------------------------------------------------------------------------

    private fun showPopup(song: Song, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_song_options, popup.menu)
        targetPlaylistId?.let {
            popup.menu.findItem(R.id.action_add_to_playlist).title = "Add to current Playlist"
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_add_to_playlist -> {
                    if (targetPlaylistId != null) addSongToSpecificPlaylist(song, targetPlaylistId!!)
                    else showPlaylistPickerDialog(song)
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
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        if (!NetworkUtils.isOnline(requireContext())) {
            Toast.makeText(requireContext(), "You're offline. Can't add songs right now.", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.addSongToPlaylist(
            SongEntity(
                audioUrl = song.audioUrl,
                title = song.title,
                artist = song.artist,
                imageUrl = song.imageUrl,
                genre = song.genre,
                playlistId = playlistId,
                addedAt = System.currentTimeMillis()
            ),
            userId
        )
        if (MusicPlayerManager.isInitialized() && MusicPlayerManager.activePlaylistId == playlistId) {
            MusicPlayerManager.addSongToEnd(song)
        }
        Toast.makeText(requireContext(), "Added to playlist!", Toast.LENGTH_SHORT).show()
    }

    private fun showPlaylistPickerDialog(song: Song) {
        if (allPlaylists.isEmpty()) {
            Toast.makeText(requireContext(), "No playlists yet. Create one first!", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Add to Playlist")
            .setItems(allPlaylists.map { it.name }.toTypedArray()) { _, index ->
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
