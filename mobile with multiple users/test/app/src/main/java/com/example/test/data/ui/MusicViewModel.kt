package com.example.test.data.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.data.local.entity.SongEntity
import com.example.test.data.local.entity.UserEntity
import com.example.test.data.repository.MusicRepository
import kotlinx.coroutines.launch

class MusicViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _playlist = MutableLiveData<PlaylistEntity?>()
    val playlist: LiveData<PlaylistEntity?> = _playlist

    private val _songs = MutableLiveData<List<SongEntity>>()
    val songs: LiveData<List<SongEntity>> = _songs

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Fires true once syncPlaylistsFromCloud finishes.
     * LoginFragment observes this to navigate ONLY after Room is populated.
     */
    private val _syncComplete = MutableLiveData<Boolean>()
    val syncComplete: LiveData<Boolean> = _syncComplete

    val allPlaylists: LiveData<List<PlaylistEntity>> = repository.getAllPlaylistsLive()

    fun getPlaylistsByUser(userId: String): LiveData<List<PlaylistEntity>> =
        repository.getPlaylistsByUserLive(userId)

    fun getUser(userId: String): LiveData<UserEntity?> =
        repository.getUser(userId).asLiveData()

    fun saveUser(user: UserEntity) {
        viewModelScope.launch { repository.saveUser(user) }
    }

    fun loadPlaylist(id: String) {
        viewModelScope.launch { _playlist.value = repository.getPlaylist(id) }
    }

    fun loadSongs(playlistId: String) {
        viewModelScope.launch { _songs.postValue(repository.getSongsByPlaylist(playlistId)) }
    }

    fun addSongToPlaylist(song: SongEntity, userId: String) {
        viewModelScope.launch {
            try {
                repository.addSong(song, userId)
                loadSongs(song.playlistId)
            } catch (e: Exception) {
                _error.postValue("Failed to add song: ${e.message}")
            }
        }
    }

    fun removeSongFromPlaylist(song: SongEntity, userId: String) {
        viewModelScope.launch {
            try {
                repository.removeSong(song, userId)
                loadSongs(song.playlistId)
            } catch (e: Exception) {
                _error.postValue("Failed to remove song: ${e.message}")
            }
        }
    }

    fun createPlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            try {
                repository.savePlaylist(playlist)
            } catch (e: Exception) {
                _error.postValue("Failed to create playlist: ${e.message}")
            }
        }
    }

    fun deletePlaylist(id: String, userId: String) {
        viewModelScope.launch {
            try {
                repository.deletePlaylist(id, userId)
            } catch (e: Exception) {
                _error.postValue("Failed to delete playlist: ${e.message}")
            }
        }
    }

    fun updatePlaylist(id: String, userId: String, name: String, image: String) {
        viewModelScope.launch {
            try {
                repository.updatePlaylistDetails(id, userId, name, image)
                loadPlaylist(id)
            } catch (e: Exception) {
                _error.postValue("Failed to update playlist: ${e.message}")
            }
        }
    }

    /**
     * Fetches all playlists + songs from Firestore into local Room DB.
     * Posts to [syncComplete] when done so LoginFragment knows it's safe to navigate.
     */
    fun syncPlaylistsFromCloud(userId: String) {
        viewModelScope.launch {
            try {
                repository.syncPlaylistsFromFirestore(userId)
            } catch (e: Exception) {
                _error.postValue("Sync failed: ${e.message}")
            } finally {
                // Always navigate, even if sync threw — Room may already have cached data
                _syncComplete.postValue(true)
            }
        }
    }
}