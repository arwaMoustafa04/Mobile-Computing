package com.example.test.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.test.data.local.dao.PlaylistDao
import com.example.test.data.local.dao.SongDao
import com.example.test.data.local.dao.UserDao
import com.example.test.data.local.entity.PlaylistEntity
import com.example.test.data.local.entity.SongEntity
import com.example.test.data.local.entity.UserEntity

@Database(entities = [SongEntity::class, PlaylistEntity::class, UserEntity::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "music_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}