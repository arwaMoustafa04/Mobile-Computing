package com.example.test.data.local.dao

import androidx.room.*
import com.example.test.data.local.entity.SongEntity

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSong(song: SongEntity)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :id")
    suspend fun getSongsByPlaylist(id: String): List<SongEntity>

    @Delete
    suspend fun removeSong(song: SongEntity)
}