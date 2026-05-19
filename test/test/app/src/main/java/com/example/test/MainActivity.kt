package com.example.test

// AI-assisted: Firebase Firestore sync, Cloudinary image upload, real-time listeners

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
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
import com.example.test.data.local.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                if (auth.currentUser == null) {
                    navigateToLogin()
                } else {
                    navigateToPlaylistLibrary()
                }
            }
        }

        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                super.onFragmentResumed(fm, f)
                when (f) {
                    is LibraryFragment        -> updateBottomNav("LIBRARY")
                    is PlaylistLibraryFragment -> updateBottomNav("PLAYLIST_LIBRARY")
                    is PlaylistDetailFragment  -> updateBottomNav("PLAYLIST_LIBRARY")
                    is ProfileFragment         -> updateBottomNav("PROFILE")
                }
                if (f is PlayerFragment || f is LoginFragment) {
                    binding.miniPlayer.visibility = View.GONE
                } else {
                    updateMiniPlayerUI()
                }
            }
        }, false)

        binding.libraryBtn.setOnClickListener { navigateToPlaylistLibrary() }
        binding.searchBtn.setOnClickListener  { navigateToLibrary() }
        binding.profileBtn.setOnClickListener { navigateToProfile() }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    fun navigateToLibrary() {
        binding.bottomNav.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LibraryFragment())
            .commitAllowingStateLoss()
    }

    fun navigateToLogin() {
        binding.bottomNav.visibility  = View.GONE
        binding.miniPlayer.visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LoginFragment())
            .commitAllowingStateLoss()
    }

    fun onLoginSuccess() {
        navigateToLibrary()
    }

    fun navigateToProfile() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ProfileFragment())
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    private fun navigateToPlaylistLibrary() {
        binding.bottomNav.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, PlaylistLibraryFragment())
            .commitAllowingStateLoss()
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    /**
     * Full logout sequence:
     * 1. Stop playback and clear all player state
     * 2. Hide mini player immediately
     * 3. Clear the entire Fragment back stack
     * 4. Wipe Room database (playlists, songs, user) on a background thread
     * 5. Sign out of Firebase Auth
     * 6. Navigate to Login
     */
    fun logout() {
        // 1. Stop music and clear player state
        if (MusicPlayerManager.isInitialized()) {
            MusicPlayerManager.reset()
        }

        // 2. Hide mini player immediately
        binding.miniPlayer.visibility = View.GONE
        binding.bottomNav.visibility  = View.GONE

        // 3. Clear the entire Fragment back stack so Back doesn't return to app screens
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        // 4. Wipe Room on IO thread — user data must not bleed into the next login
        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                db.playlistDao().deleteAllPlaylists()
                db.songDao().deleteAllSongs()
                db.userDao().deleteAllUsers()
            } catch (e: Exception) {
                // Non-fatal — the next login sync will overwrite stale data anyway
            }
        }

        // 5. Firebase sign-out
        auth.signOut()

        // 6. Navigate to login
        navigateToLogin()
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

    fun updateBottomNav(selectedTab: String) {
        binding.searchBtn.setImageResource(R.drawable.search)
        binding.libraryBtn.setImageResource(R.drawable.library)
        binding.profileBtn.setImageResource(R.drawable.profile)

        when (selectedTab) {
            "LIBRARY"          -> binding.searchBtn.setImageResource(R.drawable.search_clicked)
            "PLAYLIST_LIBRARY" -> binding.libraryBtn.setImageResource(R.drawable.library_clicked)
            "PROFILE"          -> binding.profileBtn.setImageResource(R.drawable.profile_clicked)
        }
    }

    // ── Player ────────────────────────────────────────────────────────────────

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
            if (player.currentPosition > 3000) player.seekTo(0)
            else if (player.hasPreviousMediaItem()) player.seekToPrevious()
            else {
                val lastIndex = player.mediaItemCount - 1
                if (lastIndex >= 0) player.seekTo(lastIndex, 0)
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
        val currentSong  = MusicPlayerManager.currentSong
        val isPlayerOpen = supportFragmentManager.findFragmentById(R.id.fragment_container) is PlayerFragment
        val isLoginOpen  = supportFragmentManager.findFragmentById(R.id.fragment_container) is LoginFragment

        if (currentSong != null && !isPlayerOpen && !isLoginOpen) {
            binding.miniPlayer.visibility = View.VISIBLE
            binding.miniSongTitle.text    = currentSong.title
            binding.miniArtistName.text   = currentSong.artist

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

    override fun onDestroy() {
        super.onDestroy()
        globalPlayerListener?.let {
            if (MusicPlayerManager.isInitialized()) {
                MusicPlayerManager.player.removeListener(it)
            }
        }
        globalPlayerListener = null
    }
}