package com.example.test.ui.fragments

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
import com.example.test.data.local.entity.SongEntity
import com.example.test.data.repository.MusicRepository
import com.example.test.data.ui.MusicViewModel
import com.example.test.data.ui.MusicViewModelFactory
import com.example.test.databinding.FragmentLibraryBinding
import com.example.test.player.MusicPlayerManager
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class LibraryFragment : Fragment() {
    private lateinit var viewModel: MusicViewModel
    private var librarySongs = mutableListOf<Song>()
    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SongAdapter
    private val db = Firebase.firestore
    private var fullSongList = mutableListOf<Song>()

    private var playerListener: Player.Listener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database = AppDatabase.getDatabase(requireContext())
        val repository = MusicRepository(database.songDao(), database.playlistDao())
        val factory = MusicViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(MusicViewModel::class.java)

        setupAdapter()
        
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

        binding.btnTestPlaylist.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .replace(R.id.fragment_container, PlaylistDetailFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun attachPlayerListeners() {
        playerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                adapter.notifyDataSetChanged()
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
        
        // Use the centralized playPlaylist logic which handles metadata and skipping
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
            onAddClick = { song, anchorView ->
                showPopup(song, anchorView)
            },
            showAddButton = true
        )
    }

    private fun showPopup(song: Song, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_song_options, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_add_to_playlist -> {
                    val entity = SongEntity(song.audioUrl, song.title, song.artist, song.imageUrl, "test_playlist")
                    viewModel.addSongToPlaylist(entity)
                    Toast.makeText(requireContext(), "Added!", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_add_to_queue -> {
                    MusicPlayerManager.queue.add(song)
                    Toast.makeText(requireContext(), "Queued!", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (MusicPlayerManager.isInitialized() && playerListener != null) {
            MusicPlayerManager.player.removeListener(playerListener!!)
        }
        _binding = null
    }
}
