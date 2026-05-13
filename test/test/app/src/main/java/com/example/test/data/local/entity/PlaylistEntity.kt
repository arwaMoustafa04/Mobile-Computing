// path: app/src/main/java/com/example/test/data/local/entity/PlaylistEntity.kt
package com.example.test.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imageUrl: String
)