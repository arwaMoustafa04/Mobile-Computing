package com.example.test.data.local.dao

// AI-assisted: Firebase Firestore sync, Cloudinary image upload, real-time listeners

import androidx.room.*
import com.example.test.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserById(userId: String): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    /** Wipes all users — called on logout to clear the previous user's data */
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}