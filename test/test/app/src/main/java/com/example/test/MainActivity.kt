package com.example.test

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
import com.example.test.databinding.ActivityMainBinding
import com.example.test.ui.fragments.LibraryFragment
import com.example.test.player.MusicPlayerManager
import com.example.test.ui.fragments.PlayerFragment
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import com.example.test.ui.fragments.PlaylistDetailFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        MusicPlayerManager.initialize(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Register lifecycle callbacks to handle bottom navigation and mini player visibility
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                super.onFragmentResumed(fm, f)
                
                // Update bottom navigation based on the current fragment
                when (f) {
                    is LibraryFragment -> updateBottomNav("LIBRARY")
                    is PlaylistDetailFragment -> updateBottomNav("PLAYLIST")
                }

                // Manage mini player visibility
                if (f is PlayerFragment) {
                    binding.miniPlayer.visibility = View.GONE
                } else {
                    updateMiniPlayerUI()
                }
            }
        }, false)

        // Load the LibraryFragment by default when the app opens
        if (savedInstanceState == null) {
            navigateToLibrary()
        }

        setupGlobalPlayer()

        // Set up navigation clicks for the bottom bar
        binding.libraryBtn.setOnClickListener {
            navigateToLibrary()
        }
        
        binding.searchBtn.setOnClickListener {
            navigateToLibrary() // Assuming LibraryFragment contains the search functionality
        }
        
        binding.profileBtn.setOnClickListener {
            // Profile logic can be added here
            updateBottomNav("PROFILE")
        }
    }

    private fun navigateToLibrary() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LibraryFragment())
            .commit()
    }

    fun updateBottomNav(selectedTab: String) {
        // 1. Reset all icons to their default state (gray)
        binding.searchBtn.setImageResource(R.drawable.search)
        binding.libraryBtn.setImageResource(R.drawable.library)
        binding.profileBtn.setImageResource(R.drawable.profile)

        // 2. Highlight only the active tab if necessary
        when (selectedTab) {
            "LIBRARY" -> {
                // Search icon becomes yellow (selected) in Library Fragment
                binding.searchBtn.setImageResource(R.drawable.search_clicked)
            }
            "PROFILE" -> {
                // Profile icon becomes yellow (selected)
                binding.profileBtn.setImageResource(R.drawable.profile_clicked)
            }
            "PLAYLIST" -> {
                // Per request: In PlaylistDetailFragment, everything stays gray (default)
                binding.searchBtn.setImageResource(R.drawable.search)
                binding.libraryBtn.setImageResource(R.drawable.library)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (MusicPlayerManager.isInitialized()) {
            MusicPlayerManager.player.release()
        }
    }

    private fun setupGlobalPlayer() {
        val player = MusicPlayerManager.player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    playNextSong()
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateMiniPlayerUI()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val icon = if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
                binding.btnPlayPause.setImageResource(icon)
            }
        })

        binding.btnPlayPause.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }

        binding.btnNext.setOnClickListener { playNextSong() }
        binding.btnPrev.setOnClickListener { playPreviousSong() }

        binding.miniPlayer.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, PlayerFragment())
                .addToBackStack(null)
                .commit()
        }

        updateMiniPlayerUI()
    }

    fun updateMiniPlayerUI() {
        val currentSong = MusicPlayerManager.currentSong
        val isPlayerOpen = supportFragmentManager.findFragmentById(R.id.fragment_container) is PlayerFragment

        if (currentSong != null && !isPlayerOpen) {
            binding.miniPlayer.visibility = View.VISIBLE
            binding.miniSongTitle.text = currentSong.title
            binding.miniArtistName.text = currentSong.artist
            binding.miniSongTitle.setTextColor(android.graphics.Color.WHITE)

            Glide.with(this)
                .load(currentSong.imageUrl)
                .placeholder(R.drawable.placeholder_image)
                .into(binding.miniAlbumArt)

            val icon = if (MusicPlayerManager.player.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
            binding.btnPlayPause.setImageResource(icon)
        } else {
            binding.miniPlayer.visibility = View.GONE
        }
    }

    private fun playNextSong() {
        val list = MusicPlayerManager.fullSongList
        if (list.isNotEmpty()) {
            val nextIndex = (MusicPlayerManager.currentSongIndex + 1) % list.size
            playSongAt(nextIndex)
        }
    }

    private fun playPreviousSong() {
        val list = MusicPlayerManager.fullSongList
        if (list.isNotEmpty()) {
            var prevIndex = MusicPlayerManager.currentSongIndex - 1
            if (prevIndex < 0) prevIndex = list.size - 1
            playSongAt(prevIndex)
        }
    }

    private fun playSongAt(index: Int) {
        val song = MusicPlayerManager.fullSongList[index]
        MusicPlayerManager.currentSong = song
        MusicPlayerManager.currentSongIndex = index

        val mediaItem = MediaItem.fromUri(song.audioUrl)
        MusicPlayerManager.player.setMediaItem(mediaItem)
        MusicPlayerManager.player.prepare()
        MusicPlayerManager.player.play()
    }
}