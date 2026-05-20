package com.example.test.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val userId: String, // links playlist to a user
    val name: String,
    val imageUrl: String
)