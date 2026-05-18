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
import com.example.test.ui.fragments.ProfileFragment
import com.example.test.ui.fragments.PlaylistDetailFragment
import com.example.test.ui.fragments.PlaylistLibraryFragment
import com.example.test.ui.fragments.LoginFragment
import com.example.test.ui.fragments.RegisterFragment
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        binding.miniPlayer.visibility = View.GONE

        // Auth check runs immediately so the correct screen shows without waiting for the player service
        if (savedInstanceState == null) {
            if (auth.currentUser == null) {
                navigateToLogin()
            } else {
                navigateToPlaylistLibrary()
            }
        }

        // Initialize Player Manager
        MusicPlayerManager.initialize(this) {
            setupGlobalPlayer()
            updateMiniPlayerUI()
        }

        // Handle Fragment Lifecycle & UI State
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                super.onFragmentResumed(fm, f)

                // 1. Update Bottom Navigation Icons
                when (f) {
                    is LibraryFragment -> updateBottomNav("LIBRARY")
                    is PlaylistLibraryFragment -> updateBottomNav("PLAYLIST_LIBRARY")
                    is PlaylistDetailFragment -> updateBottomNav("PLAYLIST_LIBRARY")
                    is ProfileFragment -> updateBottomNav("PROFILE")
                }

                // 2. Manage Visibility for MiniPlayer and BottomNav
                val isAuthScreen = f is LoginFragment || f is RegisterFragment
                binding.bottomNav.visibility = if (isAuthScreen) View.GONE else View.VISIBLE
                if (isAuthScreen || f is PlayerFragment) {
                    binding.miniPlayer.visibility = View.GONE
                } else {
                    binding.miniPlayer.visibility = View.VISIBLE
                    updateMiniPlayerUI()
                }
            }
        }, false)

        // Bottom Navigation Click Listeners
        binding.libraryBtn.setOnClickListener { navigateToPlaylistLibrary() }
        binding.searchBtn.setOnClickListener { navigateToLibrary() }
        binding.profileBtn.setOnClickListener { navigateToProfile() }
    }

    // --- Navigation Logic ---

    fun navigateToLibrary() {
        binding.miniPlayer.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LibraryFragment())
            .commit()
    }

    private fun navigateToPlaylistLibrary() {
        binding.miniPlayer.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, PlaylistLibraryFragment())
            .commit()
    }

    private fun navigateToProfile() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ProfileFragment())
            .addToBackStack(null)
            .commit()
    }

    fun navigateToLogin() {
        binding.miniPlayer.visibility = View.GONE
        binding.bottomNav.visibility = View.GONE
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }

    fun onLoginSuccess() {
        binding.bottomNav.visibility = View.VISIBLE
        navigateToPlaylistLibrary()
    }

    // --- UI Update Logic ---

    fun updateBottomNav(selectedTab: String) {
        binding.searchBtn.setImageResource(R.drawable.search)
        binding.libraryBtn.setImageResource(R.drawable.library)
        binding.profileBtn.setImageResource(R.drawable.profile)

        when (selectedTab) {
            "LIBRARY" -> binding.searchBtn.setImageResource(R.drawable.search_clicked)
            "PLAYLIST_LIBRARY" -> binding.libraryBtn.setImageResource(R.drawable.library_clicked)
            "PROFILE" -> binding.profileBtn.setImageResource(R.drawable.profile_clicked)
        }
    }

    private fun setupGlobalPlayer() {
        if (!MusicPlayerManager.isInitialized()) return
        val player = MusicPlayerManager.player

        player.addListener(object : Player.Listener {
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

        binding.btnNext.setOnClickListener {
            if (player.hasNextMediaItem()) player.seekToNext() else player.seekTo(0, 0)
        }

        binding.btnPrev.setOnClickListener {
            if (player.currentPosition > 3000) {
                player.seekTo(0)
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPrevious()
            } else {
                val lastIndex = player.mediaItemCount - 1
                if (lastIndex >= 0) player.seekTo(lastIndex, 0)
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

        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        val isPlayerOpen = currentFragment is PlayerFragment
        val isLoginOpen = currentFragment is LoginFragment

        if (currentSong != null && !isPlayerOpen && !isLoginOpen) {
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