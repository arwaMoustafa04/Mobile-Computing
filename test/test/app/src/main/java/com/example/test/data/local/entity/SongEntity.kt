package com.example.test.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "playlist_songs", primaryKeys = ["audioUrl", "playlistId"])
data class SongEntity(
    val audioUrl: String, // Use URL as unique ID
    val title: String,
    val artist: String,
    val imageUrl: String,
    val genre: String = "",
    val playlistId: String,
    val addedAt: Long = System.currentTimeMillis()
)
