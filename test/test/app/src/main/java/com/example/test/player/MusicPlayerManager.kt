package com.example.test.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.example.musicplayer.Song

object MusicPlayerManager {
    private var _player: ExoPlayer? = null
    val player: ExoPlayer
        get() = _player ?: throw IllegalStateException("Player not initialized. Call initialize(context) first.")

    var activePlaybackList = mutableListOf<Song>()
    var currentSong: Song? = null
    var currentSongIndex: Int = -1
    
    // The list that is currently being played from
    var playingList: List<Song> = emptyList()

    val queue = mutableListOf<Song>()

    fun initialize(context: Context) {
        if (_player == null) {
            _player = ExoPlayer.Builder(context.applicationContext).build()
        }
    }

    fun isInitialized(): Boolean = _player != null
}
