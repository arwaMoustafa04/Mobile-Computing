package com.example.test

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.musicplayer.Song
import com.example.test.databinding.ItemSongBinding

class SongAdapter(
    private var songs: List<Song>,
    private val onSongClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {
    private var playingSongUrl: String? = null

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

        // Set the data
        holder.binding.songTitle.text = song.title
        holder.binding.songArtist.text = song.artist
        Glide.with(holder.itemView.context)
            .load(song.imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .into(holder.binding.songImage)

        if (song.audioUrl == playingSongUrl) {
            val highlightColor = ContextCompat.getColor(holder.itemView.context, R.color.brand_green)
            holder.binding.songTitle.setTextColor(highlightColor)
            holder.binding.songArtist.setTextColor(highlightColor)
        } else {
            holder.binding.songTitle.setTextColor(Color.WHITE)
            holder.binding.songArtist.setTextColor(Color.GRAY)
        }
        holder.itemView.setOnClickListener {
            playingSongUrl = song.audioUrl

            notifyDataSetChanged()

            onSongClick(song)
        }
    }

    override fun getItemCount(): Int = songs.size

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }
}