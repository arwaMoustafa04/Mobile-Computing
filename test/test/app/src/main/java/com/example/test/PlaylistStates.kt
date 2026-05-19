package com.example.test

import com.example.musicplayer.Song

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
