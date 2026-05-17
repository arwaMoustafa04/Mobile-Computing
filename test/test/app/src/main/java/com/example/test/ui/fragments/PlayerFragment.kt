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
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.example.test.R
import com.example.test.databinding.FragmentPlayerBinding
import com.example.test.player.MusicPlayerManager

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = MusicPlayerManager.player.currentMediaItemIndex
            if (index != -1 && index < MusicPlayerManager.activePlaybackList.size) {
                MusicPlayerManager.currentSongIndex = index
                MusicPlayerManager.currentSong = MusicPlayerManager.activePlaybackList[index]
            }
            setupUI()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val icon = if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
            binding.btnPlayPause.setImageResource(icon)
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

        if (MusicPlayerManager.isInitialized()) {
            setupPlayerUI()
        } else {
            MusicPlayerManager.initialize(requireContext()) {
                if (isAdded) setupPlayerUI()
            }
        }
    }

    private fun setupPlayerUI() {
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

        val icon = if (player.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
        binding.btnPlayPause.setImageResource(icon)

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
            if (player.isPlaying) player.pause() else player.play()
        }

        binding.btnNext.setOnClickListener {
            if (player.hasNextMediaItem()) {
                player.seekToNext()
            } else {
                // Manual wrap around to the first song if at the end
                player.seekTo(0, 0)
            }
        }

        binding.btnPrev.setOnClickListener {
            if (player.currentPosition > 3000) {
                player.seekTo(0)
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPrevious()
            } else {
                // Manual wrap around to the last song if at the beginning
                val lastIndex = player.mediaItemCount - 1
                if (lastIndex >= 0) {
                    player.seekTo(lastIndex, 0)
                }
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = if (ms < 0) 0 else ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun setupSeekBar() {
        val player = MusicPlayerManager.player
        binding.seekBar.max = 1000

        handler.post(object : Runnable {
            override fun run() {
                if (!isAdded) return
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
                    if (duration > 0) {
                        val newPosition = (duration * progress) / 1000
                        player.seekTo(newPosition)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (MusicPlayerManager.isInitialized()) {
            MusicPlayerManager.player.removeListener(playerListener)
        }
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}
