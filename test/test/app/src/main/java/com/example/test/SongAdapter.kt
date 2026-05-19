package com.example.test

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.musicplayer.Song
import com.example.test.databinding.ItemSongBinding
import com.example.test.player.MusicPlayerManager

// AI-assisted: DiffUtil implementation for efficient RecyclerView updates
class SongAdapter(
    private val showAddButton: Boolean = true,
    private var songs: List<Song>,
    private val onSongClick: (Song) -> Unit,
    private val onAddClick: ((Song, View) -> Unit)? = null,
    private val onOptionsClick: ((Song, View) -> Unit)? = null
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    inner class SongViewHolder(val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]

        val isPlaying = MusicPlayerManager.currentSong?.audioUrl
            ?.let { it == song.audioUrl } == true

        if (isPlaying) {
            holder.binding.songTitle.setTextColor(Color.YELLOW)
            holder.binding.songArtist.setTextColor(Color.YELLOW)
        } else {
            holder.binding.songTitle.setTextColor(Color.WHITE)
            holder.binding.songArtist.setTextColor(Color.GRAY)
        }

        holder.binding.songTitle.text = song.title
        holder.binding.songArtist.text = song.artist

        Glide.with(holder.itemView.context)
            .load(song.imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .into(holder.binding.songImage)

        holder.itemView.setOnClickListener {
            onSongClick(song)
            notifyDataSetChanged()
        }

        when {
            showAddButton -> {
                holder.binding.btnAddSong.visibility = View.VISIBLE
                holder.binding.btnAddSong.setImageResource(R.drawable.add_better)
                holder.binding.btnAddSong.setOnClickListener { onAddClick?.invoke(song, it) }
            }
            onOptionsClick != null -> {
                holder.binding.btnAddSong.visibility = View.VISIBLE
                holder.binding.btnAddSong.setImageResource(R.drawable.more)
                holder.binding.btnAddSong.setOnClickListener { onOptionsClick.invoke(song, it) }
            }
            else -> holder.binding.btnAddSong.visibility = View.GONE
        }
    }

    override fun getItemCount() = songs.size

    fun getSongs(): List<Song> = songs

    fun updateSongs(newSongs: List<Song>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = songs.size
            override fun getNewListSize() = newSongs.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                songs[oldPos].audioUrl == newSongs[newPos].audioUrl
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                songs[oldPos] == newSongs[newPos]
        })
        songs = newSongs
        diff.dispatchUpdatesTo(this)
    }
}
