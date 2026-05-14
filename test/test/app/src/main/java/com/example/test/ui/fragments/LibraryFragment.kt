package com.example.test.ui.fragments

import android.os.Bundle
import android.os.Handler // Corrected Import
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.musicplayer.Song
import com.example.test.R
import com.example.test.SongAdapter
import com.example.test.databinding.FragmentLibraryBinding
import com.example.test.player.MusicPlayerManager
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.test.data.local.entity.SongEntity
import com.example.test.data.local.database.AppDatabase
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.example.test.MainActivity
import com.example.test.data.repository.MusicRepository
import com.example.test.data.ui.MusicViewModel
import com.example.test.data.ui.MusicViewModelFactory

class LibraryFragment : Fragment() {
    private lateinit var viewModel: MusicViewModel
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                playNextSongAutomatically()
            }
        }
    }

    private var librarySongs = mutableListOf<Song>()
    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SongAdapter
    private val db = Firebase.firestore
    private var fullSongList = mutableListOf<Song>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun playNextSongAutomatically() {
        val list = MusicPlayerManager.playingList
        if (list.isNotEmpty()) {
            val nextIndex = (MusicPlayerManager.currentSongIndex + 1) % list.size
            playSong(list[nextIndex])
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database = AppDatabase.getDatabase(requireContext())
        val repository = MusicRepository(database.songDao(), database.playlistDao())

        // 2. Initialize ViewModel using the Factory
        val factory = MusicViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(MusicViewModel::class.java)

        // 3. Update your Add Click logic to use the ViewModel
        setupAdapter()

        // Listener to refresh yellow highlights when song changes globally
        MusicPlayerManager.player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                adapter.notifyDataSetChanged()
            }
        })

        adapter = SongAdapter(
            songs = emptyList(),
            onSongClick = { song -> playSong(song) },
            onAddClick = { song, anchorView -> // Now receiving the view as an anchor
                // 1. Create the PopupMenu
                val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchorView)
                popup.menuInflater.inflate(R.menu.menu_song_options, popup.menu)

                // 2. Handle the clicks
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_add_to_playlist -> {
                            // Your existing Room logic
                            lifecycleScope.launch {
                                val entity = SongEntity(song.audioUrl, song.title, song.artist, song.imageUrl, "test_playlist")
                                AppDatabase.getDatabase(requireContext()).songDao().addSong(entity)
                                Toast.makeText(requireContext(), "${song.title} added to Playlist!", Toast.LENGTH_SHORT).show()
                            }
                            true
                        }
                        R.id.action_add_to_queue -> {
                            // New Queue logic
                            MusicPlayerManager.queue.add(song)
                            Toast.makeText(requireContext(), "${song.title} added to Queue (Play Next)", Toast.LENGTH_SHORT).show()
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            },
            showAddButton = true
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        setupSearch()
        fetchSongsFromFirebase()

        binding.btnTestPlaylist.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .replace(R.id.fragment_container, PlaylistDetailFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun fetchSongsFromFirebase() {
        db.collection("songs").get().addOnSuccessListener { result ->
            librarySongs.clear()
            for (document in result) {
                val song = document.toObject(Song::class.java)
                librarySongs.add(song)
            }
            // ONLY update the adapter, NOT the MusicPlayerManager.activePlaybackList
            adapter.updateSongs(librarySongs)
        }
    }

    private fun playSong(song: Song) {
        if (!MusicPlayerManager.isInitialized()) return

        // NOW we commit the library as the active playback list because the user clicked it
        MusicPlayerManager.activePlaybackList = librarySongs.toMutableList()

        MusicPlayerManager.currentSong = song
        MusicPlayerManager.currentSongIndex = librarySongs.indexOf(song)

        val mediaItem = MediaItem.fromUri(song.audioUrl)
        MusicPlayerManager.player.setMediaItem(mediaItem)
        MusicPlayerManager.player.prepare()
        MusicPlayerManager.player.play()

        adapter.notifyDataSetChanged()
        (activity as? MainActivity)?.updateMiniPlayerUI()
    }

    private fun openFullPlayer() {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, PlayerFragment())
            .addToBackStack("PlayerToLibrary")
            .commit()
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
        // 1. Initialize the adapter with your song list and click logic
        adapter = SongAdapter(
            songs = fullSongList,
            onSongClick = { song -> playSong(song) },
            onAddClick = { song, anchorView ->
                showPopup(song, anchorView)
            }
        )

        // 2. Set the LayoutManager (Vertical list is standard for music apps)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // 3. Attach the adapter to the RecyclerView
        binding.recyclerView.adapter = adapter
    }

    private fun showPopup(song: Song, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_song_options, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_add_to_playlist -> {
                    // USE VIEWMODEL INSTEAD OF DIRECT DAO CALL
                    val entity = SongEntity(song.audioUrl, song.title, song.artist, song.imageUrl, "test_playlist")
                    viewModel.addSongToPlaylist(entity)
                    Toast.makeText(requireContext(), "Added!", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_add_to_queue -> {
                    MusicPlayerManager.queue.add(song)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // No need to remove playerListener here if you want it to persist,
        // but keep it if you only want it active while the fragment is visible.
        _binding = null
    }
}
