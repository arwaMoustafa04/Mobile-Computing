package com.example.test.data.ui

// AI-assisted: Firebase Firestore sync, Cloudinary image upload, real-time listeners

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.data.local.entity.SongEntity
import com.example.test.data.local.entity.UserEntity
import com.example.test.data.repository.MusicRepository
import com.example.test.util.UiState
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class MusicViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _playlist = MutableLiveData<PlaylistEntity?>()
    val playlist: LiveData<PlaylistEntity?> = _playlist

    private val _songs = MutableLiveData<List<SongEntity>>()
    val songs: LiveData<List<SongEntity>> = _songs

    // General-purpose error message (one-shot toast/snackbar events)
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Structured UI state for screens that show loading spinners / offline banners
    private val _syncState = MutableLiveData<UiState<Unit>>()
    val syncState: LiveData<UiState<Unit>> = _syncState

    // Fires true once the post-login cloud sync finishes so LoginFragment can navigate
    private val _syncComplete = MutableLiveData<Boolean>()
    val syncComplete: LiveData<Boolean> = _syncComplete

    private var playlistListenerRegistration: ListenerRegistration? = null

    val allPlaylists: LiveData<List<PlaylistEntity>> = repository.getAllPlaylistsLive()

    fun getPlaylistsByUser(userId: String): LiveData<List<PlaylistEntity>> =
        repository.getPlaylistsByUserLive(userId)

    fun getUser(userId: String): LiveData<UserEntity?> =
        repository.getUser(userId).asLiveData()

    fun saveUser(user: UserEntity) {
        viewModelScope.launch {
            try {
                repository.saveUser(user)
            } catch (e: Exception) {
                _error.postValue("Failed to save user: ${e.message}")
            }
        }
    }

    fun updateUserProfile(userId: String, username: String, email: String, imageUrl: String?) {
        viewModelScope.launch {
            try {
                _syncState.postValue(UiState.Loading)
                repository.updateUserProfile(userId, username, email, imageUrl)
                _syncState.postValue(UiState.Success(Unit))
            } catch (e: Exception) {
                // If it's a network error, the local update likely still happened or will happen.
                // We show an error but don't necessarily treat it as a total failure if Room is updated.
                val errorMessage = e.message ?: "Unknown error"
                if (errorMessage.contains("UnknownHostException", ignoreCase = true)) {
                    _error.postValue("Profile saved locally. Cloud sync will resume when online.")
                    _syncState.postValue(UiState.Success(Unit)) // Allow navigation as Room is updated
                } else {
                    _error.postValue("Update failed: $errorMessage")
                    _syncState.postValue(UiState.Error(errorMessage))
                }
            }
        }
    }

    fun getSongsByPlaylist(playlistId: String): LiveData<List<SongEntity>> =
        repository.getSongsByPlaylistLive(playlistId)

    fun loadPlaylist(id: String) {
        viewModelScope.launch {
            try {
                _playlist.value = repository.getPlaylist(id)
            } catch (e: Exception) {
                _error.postValue("Failed to load playlist: ${e.message}")
            }
        }
    }

    fun loadSongs(playlistId: String) {
        viewModelScope.launch {
            try {
                _songs.postValue(repository.getSongsByPlaylist(playlistId))
            } catch (e: Exception) {
                _error.postValue("Failed to load songs: ${e.message}")
            }
        }
    }

    fun refreshPlaylistSongs(playlistId: String, userId: String) {
        viewModelScope.launch {
            try {
                repository.syncSongsForPlaylist(playlistId, userId)
            } catch (e: Exception) {
                _error.postValue("Failed to refresh songs: ${e.message}")
            }
        }
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
     * Post-login sync: pulls all playlists + songs from Firestore into Room.
     * Exposes [UiState] so the UI can show a loading spinner during sync.
     * Falls back gracefully — if offline or sync fails, Room cache is used.
     */
    fun syncPlaylistsFromCloud(userId: String) {
        _syncState.postValue(UiState.Loading)
        viewModelScope.launch {
            try {
                repository.syncPlaylistsFromFirestore(userId)
                _syncState.postValue(UiState.Success(Unit))
            } catch (e: Exception) {
                // Sync failed but Room may already have cached data — still navigate
                _syncState.postValue(UiState.Error("Sync failed: ${e.message}"))
            } finally {
                _syncComplete.postValue(true)
            }
        }
    }

    fun startPlaylistListener(userId: String) {
        if (playlistListenerRegistration != null) return
        playlistListenerRegistration = repository.listenToPlaylists(userId)
    }

    fun stopPlaylistListener() {
        playlistListenerRegistration?.remove()
        playlistListenerRegistration = null
    }

    /** Clears a shown error so it doesn't re-show after rotation / resubscription */
    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPlaylistListener()
    }
}
