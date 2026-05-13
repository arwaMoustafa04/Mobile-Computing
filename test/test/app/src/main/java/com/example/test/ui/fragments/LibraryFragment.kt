package com.example.test.ui.fragments

import android.os.Bundle
import android.os.Handler // Corrected Import
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.example.test.MainActivity

class LibraryFragment : Fragment() {

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                playNextSongAutomatically()
            }
        }
    }

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
        val list = MusicPlayerManager.fullSongList
        if (list.isNotEmpty()) {
            val nextIndex = (MusicPlayerManager.currentSongIndex + 1) % list.size
            playSong(list[nextIndex])
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Listener to refresh yellow highlights when song changes globally
        MusicPlayerManager.player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                adapter.notifyDataSetChanged()
            }
        })

        adapter = SongAdapter(
            songs = emptyList(),
            onSongClick = { song -> playSong(song) },
            onAddClick = { song ->
                lifecycleScope.launch {
                    val entity = SongEntity(song.audioUrl, song.title, song.artist, song.imageUrl, "test_playlist")
                    AppDatabase.getDatabase(requireContext()).songDao().addSong(entity)
                    Toast.makeText(requireContext(), "Added to Playlist!", Toast.LENGTH_SHORT).show()
                }
            }
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
            fullSongList.clear()
            for (document in result) {
                val song = document.toObject(Song::class.java)
                fullSongList.add(song)
            }
            MusicPlayerManager.fullSongList = fullSongList
            adapter.updateSongs(fullSongList)
        }
    }

    private fun playSong(song: Song) {
        if (!MusicPlayerManager.isInitialized()) return
        val player = MusicPlayerManager.player

        // Update the Manager's state
        MusicPlayerManager.currentSong = song
        MusicPlayerManager.currentSongIndex = fullSongList.indexOf(song)

        val mediaItem = MediaItem.fromUri(song.audioUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        // Refresh the adapter so the text turns yellow
        adapter.notifyDataSetChanged()

        // Tell MainActivity to refresh the global mini-player views
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


    override fun onDestroyView() {
        super.onDestroyView()
        // No need to remove playerListener here if you want it to persist,
        // but keep it if you only want it active while the fragment is visible.
        _binding = null
    }
}
