package com.example.test.data.repository

import androidx.lifecycle.LiveData
import com.example.test.data.local.dao.PlaylistDao
import com.example.test.data.local.dao.SongDao
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.data.local.entity.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao
) {

    suspend fun addSong(song: SongEntity) = withContext(Dispatchers.IO) {
        songDao.addSong(song)
    }

    suspend fun getSongsByPlaylist(playlistId: String): List<SongEntity> = withContext(Dispatchers.IO) {
        songDao.getSongsByPlaylist(playlistId)
    }

    suspend fun removeSong(song: SongEntity) = withContext(Dispatchers.IO) {
        songDao.removeSong(song)
    }

    suspend fun savePlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(playlist)
    }

    suspend fun getPlaylist(id: String): PlaylistEntity? = withContext(Dispatchers.IO) {
        playlistDao.getPlaylistById(id)
    }

    fun getAllPlaylistsLive(): LiveData<List<PlaylistEntity>> {
        return playlistDao.getAllPlaylistsLive()
    }

    suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) {
        playlistDao.deleteSongsByPlaylistId(id)
        playlistDao.deletePlaylistById(id)
    }

    suspend fun updatePlaylistDetails(playlistId: String, newName: String, newImage: String) =
        withContext(Dispatchers.IO) {
            val playlist = PlaylistEntity(id = playlistId, name = newName, imageUrl = newImage)
            playlistDao.insertPlaylist(playlist)
        }
}