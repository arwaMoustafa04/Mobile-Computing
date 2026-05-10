package com.example.test

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.Song
import com.example.test.databinding.ActivityMainBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.ktx.firestore

class MainActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private var songList = mutableListOf<Song>()

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SongAdapter
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        player = ExoPlayer.Builder(this).build()

        adapter = SongAdapter(songList) { selectedSong ->
            playSong(selectedSong)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        setupSearch()
        fetchSongsFromFirebase()
    }

    private fun fetchSongsFromFirebase() {
        db.collection("songs").get()
            .addOnSuccessListener { result ->
                songList.clear()
                for (document in result) {
                    val song = document.toObject(Song::class.java)
                    songList.add(song)
                }
                adapter.updateSongs(songList)
            }
            .addOnFailureListener { exception ->
                // Handle errors here (e.g., Log.e)
            }
    }

    private fun playSong(song: Song) {
        // Simple! Just use the URL string from Firebase
        val mediaItem = MediaItem.fromUri(song.audioUrl)

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    private fun setupSearch() {

        binding.searchBar.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {

                val query = s.toString().lowercase()

                val filteredSongs = songList.filter {
                    it.title.lowercase().contains(query) ||
                            it.artist.lowercase().contains(query)
                }

                adapter.updateSongs(filteredSongs)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}