package com.flxrs.dankchat.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.flxrs.dankchat.data.database.entity.UserDisplayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDisplayDao {

    @Query("SELECT * from user_display")
    fun getUserDisplaysFlow(): Flow<List<UserDisplayEntity>>

    @Upsert
    suspend fun upsertAll(users: List<UserDisplayEntity>)

    @Upsert
    suspend fun upsert(user: UserDisplayEntity): Long

    @Delete
    suspend fun delete(user: UserDisplayEntity): Int
}
