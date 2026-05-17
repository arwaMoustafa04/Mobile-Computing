package com.example.test

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST


data class PlaylistRequest(val prompt: String)
data class PlaylistResponse(val playlist: List<Song>)
data class Song(
    val title: String,
    val artist: String,
    val duration: String,
    val url: String
)

interface ApiService {
    @POST("generate-playlist")
    suspend fun generatePlaylist(@Body request: PlaylistRequest): PlaylistResponse
}

// Retrofit Client
object RetrofitClient {
    private const val BASE_URL = "http://192.168.1.X:8000/"

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}