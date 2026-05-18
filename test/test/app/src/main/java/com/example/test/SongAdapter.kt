package com.example.test

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.musicplayer.Song
import com.example.test.databinding.ItemSongBinding
import com.example.test.player.MusicPlayerManager

class SongAdapter(
    private val showAddButton: Boolean = true,
    private var songs: List<Song>,
    private val onSongClick: (Song) -> Unit,
    private val onAddClick: ((Song, View) -> Unit)? = null,
    private val onOptionsClick: ((Song, View) -> Unit)? = null
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var playingSongUrl: String? = null

    fun setPlayingSong(url: String?) {
        playingSongUrl = url
        notifyDataSetChanged()
    }

    inner class SongViewHolder(val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        val context = holder.itemView.context

        // Inside onBindViewHolder
        val currentPlayingUrl = MusicPlayerManager.currentSong?.audioUrl
        val isPlaying = !currentPlayingUrl.isNullOrEmpty() && song.audioUrl == currentPlayingUrl

        if (isPlaying) {
            holder.binding.songTitle.setTextColor(Color.YELLOW)
            holder.binding.songArtist.setTextColor(Color.YELLOW)
        } else {
            holder.binding.songTitle.setTextColor(Color.WHITE)
            holder.binding.songArtist.setTextColor(Color.GRAY)
        }

        // 3. Set the Text and Image
        holder.binding.songTitle.text = song.title
        holder.binding.songArtist.text = song.artist

        Glide.with(context)
            .load(song.imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .into(holder.binding.songImage)

        // 4. Item Click Logic (Play the song)
        holder.itemView.setOnClickListener {
            onSongClick(song)
            // Refresh the list so the yellow highlight moves to this song
            notifyDataSetChanged()
        }

        // 5. Action Button Logic
        if (showAddButton) {
            holder.binding.btnAddSong.visibility = View.VISIBLE
            holder.binding.btnAddSong.setImageResource(R.drawable.add_better)
            holder.binding.btnAddSong.setOnClickListener {
                onAddClick?.invoke(song, it)
            }
        } else if (onOptionsClick != null) {
            holder.binding.btnAddSong.visibility = View.VISIBLE
            // Use the 'more' icon for options
            holder.binding.btnAddSong.setImageResource(R.drawable.more)
            holder.binding.btnAddSong.setOnClickListener {
                onOptionsClick.invoke(song, it)
            }
        } else {
            holder.binding.btnAddSong.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = songs.size

    fun getSongs(): List<Song> {
        return songs
    }

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }
}
