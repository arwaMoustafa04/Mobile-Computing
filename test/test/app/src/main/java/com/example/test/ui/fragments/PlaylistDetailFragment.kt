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
import androidx.lifecycle.lifecycleScope // Required for Room
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.musicplayer.Song
import com.example.test.MainActivity
import com.example.test.R
import com.example.test.SongAdapter
import com.example.test.data.local.database.AppDatabase // Import your Room Database
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.data.model.Playlist
import com.example.test.databinding.FragmentPlaylistDetailBinding
import com.example.test.player.MusicPlayerManager
import kotlinx.coroutines.launch // Required for Room
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.Button
import android.widget.Toast

class PlaylistDetailFragment : Fragment() {

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!

    private val timerHandler = Handler(Looper.getMainLooper())
    private lateinit var adapter: SongAdapter
    private var currentSongIndex = -1

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            // Take persistable permission so it works after app restart
            try {
                val flag = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(uri, flag)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            testPlaylist = testPlaylist.copy(imageUrl = uri.toString())
            updateUIWithNewData()
        }
    }

    // Playlist header info (Image and Title)
    private var testPlaylist = Playlist(
        id = "test_01",
        name = "My Summer Hits",
        imageUrl = "https://raw.githubusercontent.com/TDMMELO/my-music-files/main/momken.jpg"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()

        // 1. Initialize Adapter with Room-compatible click listeners
        adapter = SongAdapter(
            songs = emptyList(),
            onSongClick = { song ->
                val index = MusicPlayerManager.fullSongList.indexOf(song)
                playPlaylistSong(song, index)
            },
            onAddClick = null,
            showAddButton = false // This hides the button for this fragment
        )

        binding.playlistRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.playlistRecyclerView.adapter = adapter

        // 2. Load the actual songs from Room
        loadSongsFromRoom()

        // 3. Yellow Highlight Listener: Refresh list when song changes
        MusicPlayerManager.player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                adapter.notifyDataSetChanged() // Turns the new song yellow
            }
        })

        // 4. Navigate to Library to add more songs
        binding.btnAddSong.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LibraryFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.edit.setOnClickListener {
            showEditDialog()
        }

        loadPlaylistMetadata()

    }

    private fun loadSongsFromRoom() {
        lifecycleScope.launch {
            val dbSongs = AppDatabase.getDatabase(requireContext())
                .songDao().getSongsByPlaylist("test_playlist")

            // LOG THIS to your terminal
            dbSongs.forEach { println("DATABASE CHECK: Song: ${it.title}, URL: ${it.audioUrl}") }

            // FIX: Corrected mapping order. Song constructor is (title, artist, imageUrl, audioUrl)
            val songList = dbSongs.map { Song(it.title, it.artist, it.imageUrl, it.audioUrl) }
            adapter.updateSongs(songList)
            MusicPlayerManager.fullSongList = songList
        }
    }
    private fun setupUI() {
        binding.playlistTitle.text = testPlaylist.name
        Glide.with(this).load(testPlaylist.imageUrl).into(binding.playlistArt)
    }

    private fun playPlaylistSong(song: Song, index: Int) {
        if (!MusicPlayerManager.isInitialized()) return
        val player = MusicPlayerManager.player

        MusicPlayerManager.currentSong = song
        MusicPlayerManager.currentSongIndex = index

        val mediaItem = MediaItem.fromUri(song.audioUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        // Refresh adapter so current song turns yellow immediately
        adapter.notifyDataSetChanged()
    }

    private fun openFullPlayer() {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, PlayerFragment())
            .addToBackStack("PlayerToLibrary")
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerHandler.removeCallbacksAndMessages(null)
        _binding = null
    }

    private fun showEditDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_playlist, null)
        val nameInput = dialogView.findViewById<android.widget.EditText>(R.id.etPlaylistName)
        val btnSave = dialogView.findViewById<android.widget.Button>(R.id.btnSave)
        val btnUpload = dialogView.findViewById<Button>(R.id.btnUploadImage)

        nameInput.setText(testPlaylist.name)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnSave.setOnClickListener {
            val newName = nameInput.text.toString()
            if (newName.isNotEmpty()) {
                val newImageUrl = testPlaylist.imageUrl

                lifecycleScope.launch {
                    // Use the local PlaylistEntity defined in the project for Room
                    val playlistEntity = PlaylistEntity("test_playlist", newName, newImageUrl)
                    AppDatabase.getDatabase(requireContext()).playlistDao().insertPlaylist(playlistEntity)

                    testPlaylist = testPlaylist.copy(name = newName, imageUrl = newImageUrl)
                    updateUIWithNewData()
                    dialog.dismiss()
                }
            }
        }


        btnUpload.setOnClickListener {
            // Use the variable we defined at the top
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        dialog.show()
    }

    private fun updateUIWithNewData() {
        binding.playlistTitle.text = testPlaylist.name

        Glide.with(this)
            .load(testPlaylist.imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .into(binding.playlistArt)
    }

    private fun loadPlaylistMetadata() {
        lifecycleScope.launch {
            val playlist = AppDatabase.getDatabase(requireContext())
                .playlistDao().getPlaylistById("test_playlist")

            playlist?.let {
                testPlaylist = testPlaylist.copy(name = it.name, imageUrl = it.imageUrl)
                updateUIWithNewData()
            }
        }
    }

}
