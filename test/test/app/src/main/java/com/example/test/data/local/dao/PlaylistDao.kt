// path: app/src/main/java/com/example/test/data/local/dao/PlaylistDao.kt
package com.example.test.data.local.dao

import androidx.room.*
import com.example.test.data.local.entity.PlaylistEntity

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Query("UPDATE playlist_songs SET playlistId = :newName, imageUrl = :newImage WHERE playlistId = :playlistId")
    suspend fun updatePlaylistMetadata(playlistId: String, newName: String, newImage: String)
}