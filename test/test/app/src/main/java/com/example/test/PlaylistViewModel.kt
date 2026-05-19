package com.example.test

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.Song
import com.example.test.data.local.database.AppDatabase
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.data.local.entity.SongEntity
import com.example.test.data.repository.MusicRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ─── UI State ─────────────────────────────────────────────────────────────────

sealed interface UiState {
    object Idle    : UiState
    object Loading : UiState
    data class Success(val songs: List<Song>, val playlistName: String) : UiState
    data class Error(val message: String) : UiState
}

sealed interface SaveState {
    object Idle    : SaveState
    object Saving  : SaveState
    object Saved   : SaveState
    data class Error(val message: String) : SaveState
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class PlaylistViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _currentScreen = MutableStateFlow("prompt")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    /**
     * Main entry point:
     * 1. Fetch the song catalogue from Firestore.
     * 2. Send the user's prompt + rich metadata catalogue to OpenRouter AI.
     * 3. AI returns the best-matching songs → shown to the user.
     *
     * If [existingSongs] is provided we skip the Firestore fetch.
     */
    fun generatePlaylist(prompt: String, existingSongs: List<Song> = emptyList()) {
        _saveState.value = SaveState.Idle  // Reset save state on each new generation
        _currentScreen.value = "result"
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val catalogue = if (existingSongs.isNotEmpty()) {
                    existingSongs
                } else {
                    fetchSongsFromFirestore()
                }

                if (catalogue.isEmpty()) {
                    _uiState.value = UiState.Error(
                        "Could not load songs from the repository. " +
                                "Please check your internet connection and try again."
                    )
                    return@launch
                }

                val selectedSongs = OpenRouterClient.generatePlaylist(prompt, catalogue)

                if (selectedSongs.isEmpty()) {
                    _uiState.value = UiState.Error(
                        "No matching songs found for \"$prompt\". Try a different description!"
                    )
                } else {
                    // Use the AI-generated playlist name when available, fall back to user prompt
                    val playlistName = OpenRouterClient.lastPlaylistName.ifBlank { prompt }
                    _uiState.value = UiState.Success(songs = selectedSongs, playlistName = playlistName)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    e.localizedMessage ?: "Something went wrong. Please try again."
                )
            }
        }
    }

    /**
     * Saves the currently generated playlist (with all its songs) into Room + Firestore
     * so it appears in the user's regular playlist library.
     */
    fun saveGeneratedPlaylist(
        context: android.content.Context,
        userId: String,
        playlistName: String,
        songs: List<Song>
    ) {
        if (_saveState.value is SaveState.Saving || _saveState.value is SaveState.Saved) return
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            try {
                val database   = AppDatabase.getDatabase(context)
                val repository = MusicRepository(
                    database.songDao(), database.playlistDao(), database.userDao()
                )

                val playlistId = UUID.randomUUID().toString()
                val playlist   = PlaylistEntity(
                    id       = playlistId,
                    userId   = userId,
                    name     = playlistName,
                    imageUrl = ""           // No cover — user can edit later
                )
                repository.savePlaylist(playlist)

                songs.forEach { song ->
                    val songEntity = SongEntity(
                        audioUrl   = song.audioUrl,
                        title      = song.title,
                        artist     = song.artist,
                        imageUrl   = song.imageUrl,
                        genre      = song.genre,
                        playlistId = playlistId,
                        addedAt    = System.currentTimeMillis()
                    )
                    repository.addSong(songEntity, userId)
                }

                _saveState.value = SaveState.Saved
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(
                    e.localizedMessage ?: "Failed to save playlist."
                )
            }
        }
    }

    fun resetState() {
        _uiState.value  = UiState.Idle
        _saveState.value = SaveState.Idle
    }

    private suspend fun fetchSongsFromFirestore(): List<Song> = withContext(Dispatchers.IO) {
        try {
            val snapshot = FirebaseFirestore.getInstance().collection("songs").get().await()
            snapshot.documents.mapNotNull { it.toObject(Song::class.java) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}