package com.example.test.data.local.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import com.example.test.data.local.entity.SongEntity

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSong(song: SongEntity)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :id ORDER BY addedAt ASC")
    suspend fun getSongsByPlaylist(id: String): List<SongEntity>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :id ORDER BY addedAt ASC")
    fun getSongsByPlaylistLive(id: String): LiveData<List<SongEntity>>

    @Delete
    suspend fun removeSong(song: SongEntity)
    @Query("DELETE FROM playlist_songs")
    suspend fun deleteAllSongs()
}