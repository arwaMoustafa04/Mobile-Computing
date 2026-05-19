package com.example.test

import android.os.Bundle
import android.view.View
import android.widget.Toast
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
import com.example.test.ui.fragments.ProfileFragment
import com.example.test.ui.fragments.PlaylistDetailFragment
import com.example.test.ui.fragments.LoginFragment
import com.google.firebase.auth.FirebaseAuth
import com.example.test.ui.fragments.PlaylistLibraryFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private var globalPlayerListener: Player.Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        binding.miniPlayer.visibility = View.GONE

        MusicPlayerManager.initialize(this) {
            if (isFinishing || isDestroyed) return@initialize
            setupGlobalPlayer()
            updateMiniPlayerUI()

            if (savedInstanceState == null) {
                if (auth.currentUser == null) navigateToLogin()
                else navigateToPlaylistLibrary()
            }
        }

        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    super.onFragmentResumed(fm, f)
                    when (f) {
                        is LibraryFragment        -> updateBottomNav("LIBRARY")
                        is PlaylistLibraryFragment -> updateBottomNav("PLAYLIST_LIBRARY")
                        is PlaylistDetailFragment  -> updateBottomNav("PLAYLIST_LIBRARY")
                        is ProfileFragment         -> updateBottomNav("PROFILE")
                        is AIPlaylistFragment      -> updateBottomNav("AI")
                    }
                    if (f is PlayerFragment || f is LoginFragment) {
                        binding.miniPlayer.visibility = View.GONE
                    } else {
                        updateMiniPlayerUI()
                    }
                }
            }, false
        )

        // Bottom nav tap handlers
        binding.libraryBtn.setOnClickListener { navigateToPlaylistLibrary() }
        binding.searchBtn.setOnClickListener  { navigateToLibrary() }
        binding.profileBtn.setOnClickListener { navigateToProfile() }

        // ── AI button — the sparkle tab in the bottom bar ──────────────────
        binding.aiBtn.setOnClickListener { navigateToAIPlaylist() }
    }

    // ── Navigation helpers ────────────────────────────────────────────────────

    fun navigateToLibrary() {
        binding.bottomNav.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LibraryFragment())
            .commitAllowingStateLoss()
    }

    fun navigateToLogin() {
        binding.bottomNav.visibility   = View.GONE
        binding.miniPlayer.visibility  = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commitAllowingStateLoss()
    }

    fun onLoginSuccess() { navigateToLibrary() }

    fun navigateToProfile() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ProfileFragment())
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    fun navigateToAIPlaylist() {
        binding.bottomNav.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, AIPlaylistFragment())
            .commitAllowingStateLoss()
    }

    private fun navigateToPlaylistLibrary() {
        binding.bottomNav.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, PlaylistLibraryFragment())
            .commitAllowingStateLoss()
    }

    fun updateBottomNav(selectedTab: String) {
        binding.searchBtn.setImageResource(R.drawable.search)
        binding.libraryBtn.setImageResource(R.drawable.library)
        binding.profileBtn.setImageResource(R.drawable.profile)
        // Reset AI button to default tint
        binding.aiBtn.setColorFilter(
            resources.getColor(android.R.color.darker_gray, theme)
        )

        when (selectedTab) {
            "LIBRARY"          -> binding.searchBtn.setImageResource(R.drawable.search_clicked)
            "PLAYLIST_LIBRARY" -> binding.libraryBtn.setImageResource(R.drawable.library_clicked)
            "PROFILE"          -> binding.profileBtn.setImageResource(R.drawable.profile_clicked)
            "AI"               -> binding.aiBtn.setColorFilter(
                resources.getColor(android.R.color.holo_purple, theme)
            )
        }
    }

    // ── Mini player ───────────────────────────────────────────────────────────

    private fun setupGlobalPlayer() {
        if (!MusicPlayerManager.isInitialized()) return
        val player = MusicPlayerManager.player

        globalPlayerListener?.let { player.removeListener(it) }

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateMiniPlayerUI()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val icon = if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
                binding.btnPlayPause.setImageResource(icon)
            }
        }
        globalPlayerListener = listener
        player.addListener(listener)

        binding.btnPlayPause.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }
        binding.btnNext.setOnClickListener {
            if (player.hasNextMediaItem()) player.seekToNext()
            else player.seekTo(0, 0)
        }
        binding.btnPrev.setOnClickListener {
            when {
                player.currentPosition > 3000     -> player.seekTo(0)
                player.hasPreviousMediaItem()      -> player.seekToPrevious()
                else                               -> player.seekTo(player.mediaItemCount - 1, 0)
            }
        }
        binding.miniPlayer.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, PlayerFragment())
                .addToBackStack(null)
                .commitAllowingStateLoss()
        }
    }

    fun updateMiniPlayerUI() {
        if (!MusicPlayerManager.isInitialized()) return
        val currentSong    = MusicPlayerManager.currentSong
        val isPlayerOpen   = supportFragmentManager.findFragmentById(R.id.fragment_container) is PlayerFragment
        val isLoginOpen    = supportFragmentManager.findFragmentById(R.id.fragment_container) is LoginFragment

        if (currentSong != null && !isPlayerOpen && !isLoginOpen) {
            binding.miniPlayer.visibility  = View.VISIBLE
            binding.miniSongTitle.text     = currentSong.title
            binding.miniArtistName.text    = currentSong.artist
            Glide.with(this).load(currentSong.imageUrl)
                .placeholder(R.drawable.placeholder_image).into(binding.miniAlbumArt)
            val icon = if (MusicPlayerManager.player.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
            binding.btnPlayPause.setImageResource(icon)
        } else {
            binding.miniPlayer.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        globalPlayerListener?.let {
            if (MusicPlayerManager.isInitialized()) MusicPlayerManager.player.removeListener(it)
        }
        globalPlayerListener = null
    }
}