package com.example.test

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.test.data.local.entity.PlaylistEntity

class PlaylistAdapter(
    private var playlists: List<PlaylistEntity> = emptyList(),
    private val onPlaylistClick: (PlaylistEntity) -> Unit,
    private val onMoreClick: (PlaylistEntity, View) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cover: ImageView = itemView.findViewById(R.id.imgPlaylistCover)
        val name: TextView = itemView.findViewById(R.id.tvPlaylistName)
        val songCount: TextView = itemView.findViewById(R.id.tvSongCount)
        val btnMore: ImageButton = itemView.findViewById(R.id.btnPlaylistMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.name.text = playlist.name
        holder.songCount.text = "By you"

        Glide.with(holder.itemView.context)
            .load(playlist.imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .centerCrop()
            .into(holder.cover)

        holder.itemView.setOnClickListener { onPlaylistClick(playlist) }
        holder.btnMore.setOnClickListener { onMoreClick(playlist, it) }
    }

    override fun getItemCount() = playlists.size

    fun updatePlaylists(newPlaylists: List<PlaylistEntity>) {
        playlists = newPlaylists
        notifyDataSetChanged()
    }
}