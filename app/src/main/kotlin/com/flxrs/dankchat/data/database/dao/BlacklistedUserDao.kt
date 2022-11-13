package com.flxrs.dankchat.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.flxrs.dankchat.data.database.entity.BlacklistedUserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistedUserDao {
    @Query("SELECT * FROM blacklisted_user_highlight WHERE id = :id")
    suspend fun getBlacklistedUser(id: Long): BlacklistedUserEntity

    @Query("SELECT * FROM blacklisted_user_highlight")
    fun getBlacklistedUserFlow(): Flow<List<BlacklistedUserEntity>>

    @Upsert
    suspend fun addBlacklistedUser(user: BlacklistedUserEntity): Long

    @Upsert
    suspend fun addBlacklistedUsers(user: List<BlacklistedUserEntity>)

    @Delete
    suspend fun deleteBlacklistedUser(user: BlacklistedUserEntity)

    @Query("DELETE FROM blacklisted_user_highlight")
    suspend fun deleteAllBlacklistedUsers()
}