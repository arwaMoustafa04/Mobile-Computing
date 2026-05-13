package com.example.test.data.model

import com.example.musicplayer.Song

data class Playlist(
    val id: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val songs: MutableList<Song> = mutableListOf()
)