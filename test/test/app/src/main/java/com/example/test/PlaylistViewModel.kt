package com.example.test

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class Success(val songs: List<Song>) : UiState
    data class Error(val message: String) : UiState
}

class PlaylistViewModel : ViewModel() {
    var uiState = mutableStateOf<UiState>(UiState.Idle)
        private set

    fun generatePlaylist(prompt: String) {
        viewModelScope.launch {
            uiState.value = UiState.Loading
            try {
                val response = RetrofitClient.apiService.generatePlaylist(PlaylistRequest(prompt))
                uiState.value = UiState.Success(response.playlist)
            } catch (e: Exception) {
                uiState.value = UiState.Error(e.localizedMessage ?: "Failed to connect to backend server")
            }
        }
    }

    fun resetState() {
        uiState.value = UiState.Idle
    }
}