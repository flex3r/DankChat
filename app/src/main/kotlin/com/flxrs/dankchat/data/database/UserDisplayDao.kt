package com.flxrs.dankchat.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDisplayDao {
    @Query("SELECT * from user_display")
    suspend fun getAll(): List<UserDisplayEntity>

    @Query("SELECT * from user_display")
    fun getUserDisplaysFlow(): Flow<List<UserDisplayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<UserDisplayEntity>)

    @Delete
    suspend fun deleteAll(user: List<UserDisplayEntity>): Int
}