package com.example.test.data.local.dao

// AI-assisted: Firebase Firestore sync, Cloudinary image upload, real-time listeners

import androidx.room.*
import androidx.lifecycle.LiveData
import com.example.test.data.local.entity.PlaylistEntity

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE userId = :userId ORDER BY name ASC")
    fun getPlaylistsByUserLive(userId: String): LiveData<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylistsLive(): LiveData<List<PlaylistEntity>>

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: String)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun deleteSongsByPlaylistId(id: String)

    /** Wipes all playlists — called on logout to clear the previous user's data */
    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()
}