package com.example.test.data.repository

import androidx.lifecycle.LiveData
import com.example.test.data.local.dao.PlaylistDao
import com.example.test.data.local.dao.SongDao
import com.example.test.data.local.dao.UserDao
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.data.local.entity.SongEntity
import com.example.test.data.local.entity.UserEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MusicRepository(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val userDao: UserDao
) {

    private val db = FirebaseFirestore.getInstance()

    // ---------------------------------------------------------------------------
    // User Operations
    // ---------------------------------------------------------------------------

    fun getUser(userId: String): Flow<UserEntity?> = userDao.getUserById(userId)

    suspend fun saveUser(user: UserEntity) = withContext(Dispatchers.IO) {
        userDao.insertUser(user)
    }

    suspend fun updateUserProfile(
        userId: String,
        username: String,
        profileImageUrl: String,
        email: String
    ) = withContext(Dispatchers.IO) {
        val user = UserEntity(
            id = userId,
            username = username,
            email = email,
            profileImageUrl = profileImageUrl
        )
        userDao.insertUser(user)
    }

    // ---------------------------------------------------------------------------
    // Song Operations
    // ---------------------------------------------------------------------------

    /**
     * Adds a song both to the local Room cache and to Firestore under
     * users/{userId}/playlists/{playlistId}/songs/{audioUrl}
     */
    suspend fun addSong(song: SongEntity, userId: String) = withContext(Dispatchers.IO) {
        // 1. Save locally
        songDao.addSong(song)

        // 2. Sync to Firestore
        val songMap = mapOf(
            "audioUrl"  to song.audioUrl,
            "title"     to song.title,
            "artist"    to song.artist,
            "imageUrl"  to song.imageUrl,
            "playlistId" to song.playlistId,
            "addedAt"   to song.addedAt
        )
        db.collection("users")
            .document(userId)
            .collection("playlists")
            .document(song.playlistId)
            .collection("songs")
            // Use a URL-safe doc ID derived from the audioUrl
            .document(song.audioUrl.hashCode().toString())
            .set(songMap)
            .await()
    }

    suspend fun getSongsByPlaylist(playlistId: String): List<SongEntity> =
        withContext(Dispatchers.IO) {
            songDao.getSongsByPlaylist(playlistId)
        }

    /**
     * Removes a song from Room and from Firestore.
     */
    suspend fun removeSong(song: SongEntity, userId: String) = withContext(Dispatchers.IO) {
        // 1. Remove locally
        songDao.removeSong(song)

        // 2. Remove from Firestore
        db.collection("users")
            .document(userId)
            .collection("playlists")
            .document(song.playlistId)
            .collection("songs")
            .document(song.audioUrl.hashCode().toString())
            .delete()
            .await()
    }

    // ---------------------------------------------------------------------------
    // Playlist Operations
    // ---------------------------------------------------------------------------

    /**
     * Saves a playlist locally and syncs it to Firestore under
     * users/{userId}/playlists/{playlistId}
     */
    suspend fun savePlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        // 1. Save locally
        playlistDao.insertPlaylist(playlist)

        // 2. Sync to Firestore
        val playlistMap = mapOf(
            "id"       to playlist.id,
            "userId"   to playlist.userId,
            "name"     to playlist.name,
            "imageUrl" to playlist.imageUrl
        )
        db.collection("users")
            .document(playlist.userId)
            .collection("playlists")
            .document(playlist.id)
            .set(playlistMap)
            .await()
    }

    suspend fun getPlaylist(id: String): PlaylistEntity? = withContext(Dispatchers.IO) {
        playlistDao.getPlaylistById(id)
    }

    fun getPlaylistsByUserLive(userId: String): LiveData<List<PlaylistEntity>> =
        playlistDao.getPlaylistsByUserLive(userId)

    fun getAllPlaylistsLive(): LiveData<List<PlaylistEntity>> =
        playlistDao.getAllPlaylistsLive()

    /**
     * Deletes a playlist from Room and from Firestore (including its songs sub-collection).
     */
    suspend fun deletePlaylist(id: String, userId: String) = withContext(Dispatchers.IO) {
        // 1. Delete locally
        playlistDao.deleteSongsByPlaylistId(id)
        playlistDao.deletePlaylistById(id)

        // 2. Delete the playlist document from Firestore.
        //    Note: Firestore does NOT auto-delete sub-collections, so we first
        //    delete all song documents, then the playlist document itself.
        val playlistRef = db.collection("users")
            .document(userId)
            .collection("playlists")
            .document(id)

        val songsSnapshot = playlistRef.collection("songs").get().await()
        for (songDoc in songsSnapshot.documents) {
            songDoc.reference.delete().await()
        }
        playlistRef.delete().await()
    }

    suspend fun updatePlaylistDetails(
        playlistId: String,
        userId: String,
        newName: String,
        newImage: String
    ) = withContext(Dispatchers.IO) {
        val playlist = PlaylistEntity(
            id = playlistId,
            userId = userId,
            name = newName,
            imageUrl = newImage
        )
        // 1. Update locally (insertPlaylist uses REPLACE strategy)
        playlistDao.insertPlaylist(playlist)

        // 2. Update in Firestore
        db.collection("users")
            .document(userId)
            .collection("playlists")
            .document(playlistId)
            .update(
                mapOf(
                    "name"     to newName,
                    "imageUrl" to newImage
                )
            )
            .await()
    }

    // ---------------------------------------------------------------------------
    // Cloud Sync — call this once after login to pull the user's playlists
    // and songs from Firestore into the local Room database.
    // ---------------------------------------------------------------------------

    /**
     * Fetches all playlists (and their songs) for [userId] from Firestore and
     * caches them in the local Room database.  Safe to call every login.
     */
    suspend fun syncPlaylistsFromFirestore(userId: String) = withContext(Dispatchers.IO) {
        val playlistsSnapshot = db.collection("users")
            .document(userId)
            .collection("playlists")
            .get()
            .await()

        for (playlistDoc in playlistsSnapshot.documents) {
            val entity = PlaylistEntity(
                id       = playlistDoc.getString("id")       ?: playlistDoc.id,
                userId   = playlistDoc.getString("userId")   ?: userId,
                name     = playlistDoc.getString("name")     ?: "",
                imageUrl = playlistDoc.getString("imageUrl") ?: ""
            )
            playlistDao.insertPlaylist(entity)

            // Fetch songs for this playlist
            val songsSnapshot = playlistDoc.reference
                .collection("songs")
                .get()
                .await()

            for (songDoc in songsSnapshot.documents) {
                val songEntity = SongEntity(
                    audioUrl   = songDoc.getString("audioUrl")   ?: continue,
                    title      = songDoc.getString("title")      ?: "",
                    artist     = songDoc.getString("artist")     ?: "",
                    imageUrl   = songDoc.getString("imageUrl")   ?: "",
                    playlistId = songDoc.getString("playlistId") ?: entity.id,
                    addedAt    = songDoc.getLong("addedAt")      ?: System.currentTimeMillis()
                )
                songDao.addSong(songEntity)
            }
        }
    }
}
