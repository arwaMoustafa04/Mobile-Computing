package com.example.test.data.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.data.local.entity.SongEntity
import com.example.test.data.repository.MusicRepository
import kotlinx.coroutines.launch

class MusicViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _playlist = MutableLiveData<PlaylistEntity?>()
    val playlist: LiveData<PlaylistEntity?> = _playlist

    private val _songs = MutableLiveData<List<SongEntity>>()
    val songs: LiveData<List<SongEntity>> = _songs

    val allPlaylists: LiveData<List<PlaylistEntity>> = repository.getAllPlaylistsLive()

    fun loadPlaylist(id: String) {
        viewModelScope.launch {
            _playlist.value = repository.getPlaylist(id)
        }
    }

    fun loadSongs(playlistId: String) {
        viewModelScope.launch {
            val songEntities = repository.getSongsByPlaylist(playlistId)
            _songs.postValue(songEntities)
        }
    }

    fun addSongToPlaylist(song: SongEntity) {
        viewModelScope.launch {
            repository.addSong(song)
            loadSongs(song.playlistId)
        }
    }

    fun removeSongFromPlaylist(song: SongEntity) {
        viewModelScope.launch {
            repository.removeSong(song)
            loadSongs(song.playlistId)
        }
    }

    fun createPlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.savePlaylist(playlist)
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun updatePlaylist(id: String, name: String, image: String) {
        viewModelScope.launch {
            repository.updatePlaylistDetails(id, name, image)
            loadPlaylist(id)
        }
    }
}