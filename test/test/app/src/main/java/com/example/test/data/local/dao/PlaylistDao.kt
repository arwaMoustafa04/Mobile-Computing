package com.example.test.data.local.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import com.example.test.data.local.entity.PlaylistEntity

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylistsLive(): LiveData<List<PlaylistEntity>>

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: String)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun deleteSongsByPlaylistId(id: String)
}