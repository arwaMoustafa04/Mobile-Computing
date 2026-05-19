package com.example.test.data.local.dao

// AI-assisted: Firebase Firestore sync, Cloudinary image upload, real-time listeners

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

    /** Wipes all songs — called on logout to clear the previous user's data */
    @Query("DELETE FROM playlist_songs")
    suspend fun deleteAllSongs()
}