package com.example.test.ui.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player // Add this import
import com.bumptech.glide.Glide
import com.example.musicplayer.Song
import com.example.test.R
import com.example.test.databinding.FragmentPlayerBinding
import com.example.test.player.MusicPlayerManager

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

    // 1. Create the Listener for auto-advancing
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                playNextSong()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 2. Attach the listener to the player
        MusicPlayerManager.player.addListener(playerListener)

        setupUI()
        setupControls()
        setupSeekBar()
    }

    private fun setupUI() {
        val song = MusicPlayerManager.currentSong ?: return
        val player = MusicPlayerManager.player

        binding.songTitle.text = song.title
        binding.artistName.text = song.artist

        // FIX: Update icon based on current state immediately
        if (player.playWhenReady) {
            binding.btnPlayPause.setImageResource(R.drawable.ic_media_pause)
        } else {
            binding.btnPlayPause.setImageResource(R.drawable.ic_media_play)
        }

        Glide.with(this)
            .load(song.imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .into(binding.albumArt)
    }

    private fun setupControls() {
        val player = MusicPlayerManager.player

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnPlayPause.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
                binding.btnPlayPause.setImageResource(R.drawable.ic_media_play)
            } else {
                player.play()
                binding.btnPlayPause.setImageResource(R.drawable.ic_media_pause)
            }
        }

        binding.btnNext.setOnClickListener {
            playNextSong()
        }

        binding.btnPrev.setOnClickListener {
            playPreviousSong()
        }
    }

    // 3. Extracted these to functions so the Listener can use them
    private fun playNextSong() {
        val list = MusicPlayerManager.playingList
        if (list.isNotEmpty()) {
            val nextIndex = (MusicPlayerManager.currentSongIndex + 1) % list.size
            playSpecificSong(list[nextIndex], nextIndex)
        }
    }

    private fun playPreviousSong() {
        val list = MusicPlayerManager.playingList
        if (list.isNotEmpty()) {
            var prevIndex = MusicPlayerManager.currentSongIndex - 1
            if (prevIndex < 0) prevIndex = list.size - 1
            playSpecificSong(list[prevIndex], prevIndex)
        }
    }

    private fun playSpecificSong(song: Song, index: Int) {
        val player = MusicPlayerManager.player
        MusicPlayerManager.currentSong = song
        MusicPlayerManager.currentSongIndex = index

        val mediaItem = MediaItem.fromUri(song.audioUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        setupUI()

        binding.btnPlayPause.setImageResource(R.drawable.ic_media_pause)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun setupSeekBar() {
        val player = MusicPlayerManager.player
        binding.seekBar.max = 1000

        handler.post(object : Runnable {
            override fun run() {
                val currentPos = player.currentPosition
                val duration = player.duration
                if (duration > 0) {
                    val progress = ((currentPos * 1000) / duration).toInt()
                    binding.seekBar.progress = progress
                    binding.tvCurrentTime.text = formatTime(currentPos)
                    val remainingTime = duration - currentPos
                    binding.tvRemainingTime.text = "-${formatTime(remainingTime)}"
                }
                handler.postDelayed(this, 500)
            }
        })

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = player.duration
                    val newPosition = (duration * progress) / 1000
                    player.seekTo(newPosition)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 4. Important: Remove listener to prevent memory leaks/double-skipping
        MusicPlayerManager.player.removeListener(playerListener)
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}