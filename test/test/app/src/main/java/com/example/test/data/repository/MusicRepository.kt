package com.example.test.data.repository

import androidx.lifecycle.LiveData
import com.example.test.data.local.dao.PlaylistDao
import com.example.test.data.local.dao.SongDao
import com.example.test.data.local.dao.UserDao
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.data.local.entity.SongEntity
import com.example.test.data.local.entity.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MusicRepository(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val userDao: UserDao
) {

    // --- User Operations ---

    fun getUser(userId: String): Flow<UserEntity?> = userDao.getUserById(userId)

    suspend fun saveUser(user: UserEntity) = withContext(Dispatchers.IO) {
        userDao.insertUser(user)
    }


    suspend fun updateUserProfile(userId: String, username: String, profileImageUrl: String, email: String) = withContext(Dispatchers.IO) {
        val user = UserEntity(id = userId, username = username, email = email, profileImageUrl = profileImageUrl)
        userDao.insertUser(user)
    }

    // --- Song Operations ---

    suspend fun addSong(song: SongEntity) = withContext(Dispatchers.IO) {
        songDao.addSong(song)
    }

    suspend fun getSongsByPlaylist(playlistId: String): List<SongEntity> = withContext(Dispatchers.IO) {
        songDao.getSongsByPlaylist(playlistId)
    }

    suspend fun removeSong(song: SongEntity) = withContext(Dispatchers.IO) {
        songDao.removeSong(song)
    }

    // --- Playlist Operations ---

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