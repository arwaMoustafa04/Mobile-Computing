package com.example.test

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.databinding.ItemPlaylistBinding

class PlaylistAdapter(
    private val onPlaylistClick: (PlaylistEntity) -> Unit,
    private val onMoreClick: (PlaylistEntity, View) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    private var playlists: List<PlaylistEntity> = emptyList()

    inner class PlaylistViewHolder(val binding: ItemPlaylistBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.binding.tvPlaylistName.text = playlist.name
        
        Glide.with(holder.itemView.context)
            .load(playlist.imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .into(holder.binding.imgPlaylistCover)

        holder.itemView.setOnClickListener { onPlaylistClick(playlist) }
        holder.binding.btnPlaylistMore.setOnClickListener { onMoreClick(playlist, it) }
    }

    override fun getItemCount() = playlists.size

    fun updatePlaylists(newPlaylists: List<PlaylistEntity>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = playlists.size
            override fun getNewListSize() = newPlaylists.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                playlists[oldPos].id == newPlaylists[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                playlists[oldPos] == newPlaylists[newPos]
        })
        playlists = newPlaylists
        diff.dispatchUpdatesTo(this)
    }
}
