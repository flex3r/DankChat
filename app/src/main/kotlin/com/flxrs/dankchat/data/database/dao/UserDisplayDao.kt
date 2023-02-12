package com.flxrs.dankchat.data.database.dao

import androidx.room.*
import com.flxrs.dankchat.data.database.entity.UserDisplayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDisplayDao {

    @Query("SELECT * from user_display")
    fun getUserDisplaysFlow(): Flow<List<UserDisplayEntity>>

    @Upsert
    suspend fun insertAll(users: List<UserDisplayEntity>)

    @Upsert
    suspend fun insert(user: UserDisplayEntity): Long

    @Delete
    suspend fun delete(user: UserDisplayEntity): Int
}
