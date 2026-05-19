package com.example.test.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.test.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build()

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>
                ): ListenableFuture<List<MediaItem>> {
                    // IMPORTANT: Restore the URI from MediaId so the background player can stream the song.
                    // URIs are stripped when MediaItems travel from the UI to the Service.
                    val updatedItems = mediaItems.map { item ->
                        val actualUrl = if (item.mediaId.startsWith("media_queue_")) {
                            // Extract everything after the THIRD underscore
                            // media_queue_timestamp_URL
                            val firstUnderscore = item.mediaId.indexOf("_")
                            val secondUnderscore = item.mediaId.indexOf("_", firstUnderscore + 1)
                            val thirdUnderscore = item.mediaId.indexOf("_", secondUnderscore + 1)

                            if (thirdUnderscore != -1) {
                                item.mediaId.substring(thirdUnderscore + 1)
                            } else {
                                item.mediaId
                            }
                        } else {
                            item.mediaId
                        }

                        item.buildUpon()
                            .setUri(Uri.parse(actualUrl))
                            .setMediaMetadata(item.mediaMetadata)
                            .build()
                    }
                    return Futures.immediateFuture(updatedItems)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
