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
import com.example.musicplayer.Song
import com.example.test.ui.fragments.PlaylistDetailFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.miniPlayer.visibility = View.GONE

        MusicPlayerManager.initialize(this) {
            setupGlobalPlayer()
            updateMiniPlayerUI()
            
            if (savedInstanceState == null) {
                navigateToLibrary()
            }
        }

        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                super.onFragmentResumed(fm, f)
                
                when (f) {
                    is LibraryFragment -> updateBottomNav("LIBRARY")
                    is PlaylistDetailFragment -> updateBottomNav("PLAYLIST")
                }

                if (f is PlayerFragment) {
                    binding.miniPlayer.visibility = View.GONE
                } else {
                    updateMiniPlayerUI()
                }
            }
        }, false)

        binding.searchBtn.setOnClickListener { navigateToLibrary() }
        binding.profileBtn.setOnClickListener { updateBottomNav("PROFILE") }
    }

    private fun navigateToLibrary() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LibraryFragment())
            .commit()
    }

    fun updateBottomNav(selectedTab: String) {
        binding.searchBtn.setImageResource(R.drawable.search)
        binding.libraryBtn.setImageResource(R.drawable.library)
        binding.profileBtn.setImageResource(R.drawable.profile)
        when (selectedTab) {
            "LIBRARY" -> binding.searchBtn.setImageResource(R.drawable.search_clicked)
            "PROFILE" -> binding.profileBtn.setImageResource(R.drawable.profile_clicked)
        }
    }

    private fun setupGlobalPlayer() {
        if (!MusicPlayerManager.isInitialized()) return
        val player = MusicPlayerManager.player

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                if (index != -1 && index < MusicPlayerManager.activePlaybackList.size) {
                    MusicPlayerManager.currentSongIndex = index
                    MusicPlayerManager.currentSong = MusicPlayerManager.activePlaybackList[index]
                }
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

        binding.btnNext.setOnClickListener {
            if (player.hasNextMediaItem()) {
                player.seekToNext()
            } else {
                // Wrap around to the first song
                player.seekTo(0, 0)
            }
        }
        
        binding.btnPrev.setOnClickListener {
            if (player.currentPosition > 3000) {
                player.seekTo(0)
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPrevious()
            } else {
                // Wrap around to the last song
                val lastIndex = player.mediaItemCount - 1
                if (lastIndex >= 0) {
                    player.seekTo(lastIndex, 0)
                }
            }
        }

        binding.miniPlayer.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, PlayerFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    fun updateMiniPlayerUI() {
        if (!MusicPlayerManager.isInitialized()) return
        val currentSong = MusicPlayerManager.currentSong
        val isPlayerOpen = supportFragmentManager.findFragmentById(R.id.fragment_container) is PlayerFragment

        if (currentSong != null && !isPlayerOpen) {
            binding.miniPlayer.visibility = View.VISIBLE
            binding.miniSongTitle.text = currentSong.title
            binding.miniArtistName.text = currentSong.artist
            
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
}
