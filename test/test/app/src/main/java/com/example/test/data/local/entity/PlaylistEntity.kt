package com.example.test.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val userId: String, // Added to link playlist to a user
    val name: String,
    val imageUrl: String
)