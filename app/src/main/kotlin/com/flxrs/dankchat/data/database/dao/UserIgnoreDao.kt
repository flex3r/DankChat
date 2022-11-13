package com.flxrs.dankchat.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.flxrs.dankchat.data.database.entity.UserIgnoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserIgnoreDao {

    @Query("SELECT * FROM blacklisted_user WHERE id = :id")
    suspend fun getUserIgnore(id: Long): UserIgnoreEntity

    @Query("SELECT * FROM blacklisted_user")
    fun getUserIgnoresFlow(): Flow<List<UserIgnoreEntity>>

    @Upsert
    suspend fun addIgnore(ignore: UserIgnoreEntity): Long

    @Upsert
    suspend fun addIgnores(ignores: List<UserIgnoreEntity>)

    @Delete
    suspend fun deleteIgnore(ignore: UserIgnoreEntity)

    @Query("DELETE FROM blacklisted_user")
    suspend fun deleteAllIgnores()
}