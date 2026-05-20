package com.example.test.util



 // class representing every possible state a data-loading operation can be in.
 // Fragments observe LiveData from the ViewModel and render accordingly.

sealed class UiState<out T> {
    // Operation is in progress, show a loading spinner
    object Loading : UiState<Nothing>()

    // Operation succeeded, data is ready to display
    data class Success<T>(val data: T) : UiState<T>()

    // Operation failed, show an error message
    data class Error(val message: String) : UiState<Nothing>()

    // Device is offline, data shown is from local Room cache
    data class Offline<T>(val cachedData: T) : UiState<T>()
}
