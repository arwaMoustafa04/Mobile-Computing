package com.example.test.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.Song
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

object MusicPlayerManager {

    private const val TAG = "MusicPlayerManager"
    private const val QUEUE_PREFIX = "media_queue_"
    private var _player: Player? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    
    val player: Player
        get() = _player ?: throw IllegalStateException("Player not initialized.")

    var activePlaybackList = mutableListOf<Song>()
    var activePlaylistId: String? = null
    var currentSong: Song? = null
    var currentSongIndex: Int = -1

    private var lastMediaId: String? = null

    var onStateChanged: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val p = _player ?: return

            // Check the song we are LEAVING
            val idToRemove = lastMediaId

            // Set the new "last" to the song that just started
            lastMediaId = mediaItem?.mediaId

            if (idToRemove != null && idToRemove.startsWith(QUEUE_PREFIX)) {
                // We only remove it if it's NOT the one currently playing
                if (idToRemove != mediaItem?.mediaId) {
                    removeMediaItemById(idToRemove)
                }
            }

            updateCurrentSongState()
            onStateChanged?.invoke()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                updateCurrentSongState()
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // Keep our local list in sync whenever the player's timeline changes
            syncWithPlayerTimeline()
            onStateChanged?.invoke()
        }
    }

    fun initialize(context: Context, onReady: (() -> Unit)? = null) {
        if (_player != null) {
            onReady?.invoke()
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                _player = controller
                controller?.addListener(playerListener)
                lastMediaId = controller?.currentMediaItem?.mediaId
                syncWithPlayerTimeline()
                mainHandler.post { onReady?.invoke() }
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                mainHandler.post { onReady?.invoke() }
            }
        }, MoreExecutors.directExecutor())
    }

    fun isInitialized(): Boolean = _player != null

    fun reset() {
        val p = _player ?: return
        try {
            p.stop()
            p.clearMediaItems()
        } catch (e: Exception) {
            Log.e(TAG, "Error during reset", e)
        }
        // Clear all in-memory state
        activePlaybackList.clear()
        activePlaylistId  = null
        currentSong       = null
        currentSongIndex  = -1
        lastMediaId       = null
        onStateChanged    = null
    }

    private fun updateCurrentSongState() {
        val p = _player ?: return
        val currentIndex = p.currentMediaItemIndex

        if (p.mediaItemCount != activePlaybackList.size) {
            syncWithPlayerTimeline()
        }

        if (currentIndex in 0 until activePlaybackList.size) {
            currentSongIndex = currentIndex
            currentSong = activePlaybackList[currentIndex]
        }
    }

    private fun removeMediaItemById(mediaId: String) {
        val p = _player ?: return
        for (i in 0 until p.mediaItemCount) {
            if (p.getMediaItemAt(i).mediaId == mediaId) {
                if (i != p.currentMediaItemIndex) {
                    p.removeMediaItem(i)
                    syncWithPlayerTimeline()
                }
                break
            }
        }
    }

    private fun createMediaItem(song: Song, isQueued: Boolean): MediaItem {
        val mediaId = if (isQueued) {
            "${QUEUE_PREFIX}${System.nanoTime()}_${song.audioUrl}"
        } else {
            song.audioUrl
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(Uri.parse(song.imageUrl))
            .setGenre(song.genre)
            .build()

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(Uri.parse(song.audioUrl))
            .setMediaMetadata(metadata)
            .build()
    }

    fun playPlaylist(songs: List<Song>, startIndex: Int, playlistId: String? = null) {
        if (!isInitialized()) return
        val p = player
        activePlaylistId = playlistId

        // 1. SAVE the existing queue items from the current timeline
        val existingQueueItems = mutableListOf<MediaItem>()
        for (i in 0 until p.mediaItemCount) {
            val item = p.getMediaItemAt(i)
            if (item.mediaId.startsWith(QUEUE_PREFIX)) {
                existingQueueItems.add(item)
            }
        }

        // 2. Prepare the new playlist items
        val playlistItems = songs.map { createMediaItem(it, isQueued = false) }

        // 3. Replace the timeline with the new playlist
        p.setMediaItems(playlistItems, startIndex, 0L)

        // 4. RE-INSERT the queue items at the end of the new timeline
        if (existingQueueItems.isNotEmpty()) {
            // Build the timeline so queue songs play immediately after the selected song:
            // [selected song] [queue songs...] [remaining playlist songs...]
            val selectedItem  = playlistItems[startIndex]
            val beforeSelected = playlistItems.subList(0, startIndex)          // songs before tapped song
            val afterSelected  = playlistItems.subList(startIndex + 1, playlistItems.size) // songs after
            // Full order: songs before | selected | queue | songs after
            val reordered = mutableListOf<MediaItem>()
            reordered.addAll(beforeSelected)
            reordered.add(selectedItem)
            reordered.addAll(existingQueueItems)   // queue plays right after selected song
            reordered.addAll(afterSelected)        // rest of playlist follows queue
            // Start playback at the selected song's position in the reordered list
            p.setMediaItems(reordered, startIndex, 0L)
        } else {
            // No queue — behave exactly as before
            p.setMediaItems(playlistItems, startIndex, 0L)
        }

        p.prepare()
        p.play()

        syncWithPlayerTimeline()
    }

    /**
     * Appends a song to the end of the current player timeline.
     * Useful when a song is added to the playlist that is currently playing.
     */
    fun addSongToEnd(song: Song) {
        if (!isInitialized()) return
        val p = player
        
        // Ensure we don't add the same song twice if it's already the last one
        if (p.mediaItemCount > 0) {
            val lastItem = p.getMediaItemAt(p.mediaItemCount - 1)
            if (lastItem.mediaId == song.audioUrl) return
        }

        val mediaItem = createMediaItem(song, isQueued = false)
        p.addMediaItem(mediaItem)
        
        // If the player was idle or ended, prepare it again to pick up the new item
        if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) {
            p.prepare()
        }
        
        syncWithPlayerTimeline()
    }

    fun addSongToQueue(song: Song) {
        if (!isInitialized()) return
        val p = player
        val currentIndex = p.currentMediaItemIndex

        var insertionIndex = if (p.mediaItemCount > 0) currentIndex + 1 else 0
        for (i in currentIndex + 1 until p.mediaItemCount) {
            if (p.getMediaItemAt(i).mediaId.startsWith(QUEUE_PREFIX)) {
                insertionIndex = i + 1
            } else {
                break
            }
        }

        val mediaItem = createMediaItem(song, isQueued = true)
        p.addMediaItem(insertionIndex, mediaItem)

        if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) {
            p.prepare()
            p.play()
        }

        syncWithPlayerTimeline()
    }

    fun syncWithPlayerTimeline() {
        val p = _player ?: return
        val newList = mutableListOf<Song>()
        for (i in 0 until p.mediaItemCount) {
            val item = p.getMediaItemAt(i)
            val metadata = item.mediaMetadata

            // CLEAN THE URL HERE
            val rawId = item.mediaId
            val cleanUrl = if (rawId.startsWith(QUEUE_PREFIX)) {
                // Find the 3rd underscore (media_queue_timestamp_URL)
                val first = rawId.indexOf("_")
                val second = rawId.indexOf("_", first + 1)
                val third = rawId.indexOf("_", second + 1)
                rawId.substring(third + 1)
            } else {
                rawId
            }

            newList.add(Song(
                title = metadata.title?.toString() ?: "Unknown",
                artist = metadata.artist?.toString() ?: "Unknown",
                imageUrl = metadata.artworkUri?.toString() ?: "",
                audioUrl = cleanUrl,
                genre = metadata.genre?.toString() ?: ""
            ))
        }
        activePlaybackList = newList

        val currentIndex = p.currentMediaItemIndex
        if (currentIndex in 0 until activePlaybackList.size) {
            currentSongIndex = currentIndex
            currentSong = activePlaybackList[currentIndex]
        }
    }
}
