package com.example.test.data.repository

// AI-assisted: Firebase Firestore sync, Cloudinary image upload, real-time listeners

import androidx.lifecycle.LiveData
import com.example.test.data.local.dao.PlaylistDao
import com.example.test.data.local.dao.SongDao
import com.example.test.data.local.dao.UserDao
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.data.local.entity.SongEntity
import com.example.test.data.local.entity.UserEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MusicRepository(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val userDao: UserDao
) {
    // Scoped coroutines for Firestore listener callbacks (not tied to any single Fragment)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = FirebaseFirestore.getInstance()

    // ---------------------------------------------------------------------------
    // User
    // ---------------------------------------------------------------------------

    fun getUser(userId: String): Flow<UserEntity?> = userDao.getUserById(userId)

    suspend fun saveUser(user: UserEntity) = withContext(Dispatchers.IO) {
        userDao.insertUser(user)
    }

    // ---------------------------------------------------------------------------
    // Songs
    // ---------------------------------------------------------------------------

    suspend fun addSong(song: SongEntity, userId: String) = withContext(Dispatchers.IO) {
        songDao.addSong(song)
        val songMap = mapOf(
            "audioUrl"   to song.audioUrl,
            "title"      to song.title,
            "artist"     to song.artist,
            "imageUrl"   to song.imageUrl,
            "genre"      to song.genre,
            "playlistId" to song.playlistId,
            "addedAt"    to song.addedAt
        )
        db.collection("users").document(userId)
            .collection("playlists").document(song.playlistId)
            .collection("songs").document(song.audioUrl.hashCode().toString())
            .set(songMap).await()
    }

    suspend fun getSongsByPlaylist(playlistId: String): List<SongEntity> =
        withContext(Dispatchers.IO) { songDao.getSongsByPlaylist(playlistId) }

    fun getSongsByPlaylistLive(playlistId: String): LiveData<List<SongEntity>> =
        songDao.getSongsByPlaylistLive(playlistId)

    suspend fun removeSong(song: SongEntity, userId: String) = withContext(Dispatchers.IO) {
        songDao.removeSong(song)
        db.collection("users").document(userId)
            .collection("playlists").document(song.playlistId)
            .collection("songs").document(song.audioUrl.hashCode().toString())
            .delete().await()
    }

    // ---------------------------------------------------------------------------
    // Playlists — local Room operations (Firestore writes done in fragments via
    // real-time listeners so the repo just handles the Room cache here)
    // ---------------------------------------------------------------------------

    suspend fun savePlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(playlist)
        val playlistMap = mapOf(
            "id"       to playlist.id,
            "userId"   to playlist.userId,
            "name"     to playlist.name,
            "imageUrl" to playlist.imageUrl
        )
        db.collection("users").document(playlist.userId)
            .collection("playlists").document(playlist.id)
            .set(playlistMap).await()
    }

    suspend fun getPlaylist(id: String): PlaylistEntity? =
        withContext(Dispatchers.IO) { playlistDao.getPlaylistById(id) }

    fun getPlaylistsByUserLive(userId: String): LiveData<List<PlaylistEntity>> =
        playlistDao.getPlaylistsByUserLive(userId)

    fun getAllPlaylistsLive(): LiveData<List<PlaylistEntity>> =
        playlistDao.getAllPlaylistsLive()

    suspend fun deletePlaylist(id: String, userId: String) = withContext(Dispatchers.IO) {
        playlistDao.deleteSongsByPlaylistId(id)
        playlistDao.deletePlaylistById(id)
        val playlistRef = db.collection("users").document(userId)
            .collection("playlists").document(id)
        val songsSnapshot = playlistRef.collection("songs").get().await()
        songsSnapshot.documents.forEach { it.reference.delete().await() }
        playlistRef.delete().await()
    }

    suspend fun updatePlaylistDetails(
        playlistId: String, userId: String, newName: String, newImage: String
    ) = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(
            PlaylistEntity(id = playlistId, userId = userId, name = newName, imageUrl = newImage)
        )
        db.collection("users").document(userId)
            .collection("playlists").document(playlistId)
            .update(mapOf("name" to newName, "imageUrl" to newImage)).await()
    }

    // ---------------------------------------------------------------------------
    // Cloud sync — called once after login to seed Room from Firestore
    // ---------------------------------------------------------------------------

    suspend fun syncPlaylistsFromFirestore(userId: String) = withContext(Dispatchers.IO) {
        val playlistsSnapshot = db.collection("users").document(userId)
            .collection("playlists").get().await()

        for (playlistDoc in playlistsSnapshot.documents) {
            val entity = PlaylistEntity(
                id       = playlistDoc.getString("id")       ?: playlistDoc.id,
                userId   = playlistDoc.getString("userId")   ?: userId,
                name     = playlistDoc.getString("name")     ?: "",
                imageUrl = playlistDoc.getString("imageUrl") ?: ""
            )
            playlistDao.insertPlaylist(entity)

            val songsSnapshot = playlistDoc.reference.collection("songs").get().await()
            for (songDoc in songsSnapshot.documents) {
                val songEntity = SongEntity(
                    audioUrl   = songDoc.getString("audioUrl")   ?: continue,
                    title      = songDoc.getString("title")      ?: "",
                    artist     = songDoc.getString("artist")     ?: "",
                    imageUrl   = songDoc.getString("imageUrl")   ?: "",
                    genre      = songDoc.getString("genre")      ?: "",
                    playlistId = songDoc.getString("playlistId") ?: entity.id,
                    addedAt    = songDoc.getLong("addedAt")      ?: System.currentTimeMillis()
                )
                songDao.addSong(songEntity)
            }
        }
    }

    suspend fun syncSongsForPlaylist(playlistId: String, userId: String) = withContext(Dispatchers.IO) {
        val songsSnapshot = db.collection("users").document(userId)
            .collection("playlists").document(playlistId)
            .collection("songs").get().await()
        for (songDoc in songsSnapshot.documents) {
            val songEntity = SongEntity(
                audioUrl   = songDoc.getString("audioUrl")   ?: continue,
                title      = songDoc.getString("title")      ?: "",
                artist     = songDoc.getString("artist")     ?: "",
                imageUrl   = songDoc.getString("imageUrl")   ?: "",
                genre      = songDoc.getString("genre")      ?: "",
                playlistId = songDoc.getString("playlistId") ?: playlistId,
                addedAt    = songDoc.getLong("addedAt")      ?: System.currentTimeMillis()
            )
            songDao.addSong(songEntity)
        }
    }

    // ---------------------------------------------------------------------------
    // Real-time playlist listener — call this after login and keep the
    // returned ListenerRegistration to remove it on logout/destroy
    // ---------------------------------------------------------------------------

    /**
     * Attaches a real-time Firestore listener that keeps Room in sync whenever
     * any playlist is created, updated, or deleted on any device.
     * Returns the [ListenerRegistration] — caller must call .remove() on it.
     */
    fun listenToPlaylists(userId: String): ListenerRegistration {
        return db.collection("users").document(userId)
            .collection("playlists")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                for (change in snapshot.documentChanges) {
                    val doc = change.document
                    val entity = PlaylistEntity(
                        id       = doc.getString("id")       ?: doc.id,
                        userId   = doc.getString("userId")   ?: userId,
                        name     = doc.getString("name")     ?: "",
                        imageUrl = doc.getString("imageUrl") ?: ""
                    )
                    when (change.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                            // Run on IO thread — Room operations can't be on main thread
                            repositoryScope.launch {
                                playlistDao.insertPlaylist(entity)
                            }
                        }
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                            repositoryScope.launch {
                                playlistDao.deletePlaylistById(entity.id)
                                playlistDao.deleteSongsByPlaylistId(entity.id)
                            }
                        }
                    }
                }
            }
    }
}
