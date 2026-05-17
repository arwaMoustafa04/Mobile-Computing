package com.example.test.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

object MusicPlayerManager {
    private var _player: Player? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    
    val player: Player
        get() = _player ?: throw IllegalStateException("Player not initialized. Call initialize(context) first.")

    var activePlaybackList = mutableListOf<Song>()
    var currentSong: Song? = null
    var currentSongIndex: Int = -1
    
    var playingList: List<Song> = emptyList()
    val queue = mutableListOf<Song>()

    fun initialize(context: Context, onReady: (() -> Unit)? = null) {
        if (_player != null) {
            onReady?.invoke()
            return
        }

        if (controllerFuture == null) {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture?.addListener({
                try {
                    _player = controllerFuture?.get()
                    onReady?.invoke()
                } catch (e: Exception) {
                    controllerFuture = null 
                }
            }, MoreExecutors.directExecutor())
        } else {
            controllerFuture?.addListener({
                onReady?.invoke()
            }, MoreExecutors.directExecutor())
        }
    }

    fun isInitialized(): Boolean = _player != null

    fun createMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(Uri.parse(song.imageUrl))
            .setDisplayTitle(song.title)
            .setSubtitle(song.artist)
            .setAlbumTitle("Library")
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()

        return MediaItem.Builder()
            .setMediaId(song.audioUrl)
            .setUri(Uri.parse(song.audioUrl))
            .setMediaMetadata(metadata)
            .build()
    }

    fun playPlaylist(songs: List<Song>, startIndex: Int) {
        if (!isInitialized()) return
        
        activePlaybackList = songs.toMutableList()
        currentSongIndex = startIndex
        currentSong = songs.getOrNull(startIndex)

        val mediaItems = songs.map { createMediaItem(it) }
        
        val p = player
        // Set repeat mode to ALL so skip next on last song goes to first song,
        // and skip previous on first song goes to last song.
        p.repeatMode = Player.REPEAT_MODE_ALL

        p.setMediaItems(mediaItems, startIndex, 0L)
        p.prepare()
        p.play()
    }
}
