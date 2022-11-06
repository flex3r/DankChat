package com.flxrs.dankchat.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageIgnoreDao {

    @Query("SELECT * FROM message_ignore WHERE id = :id")
    suspend fun getMessageIgnore(id: Long): MessageIgnoreEntity

    @Query("SELECT * FROM message_ignore")
    suspend fun getMessageIgnores(): List<MessageIgnoreEntity>

    @Query("SELECT * FROM message_ignore")
    fun getMessageIgnoresFlow(): Flow<List<MessageIgnoreEntity>>

    @Upsert
    suspend fun addIgnore(ignore: MessageIgnoreEntity): Long

    @Upsert
    suspend fun addIgnores(ignores: List<MessageIgnoreEntity>)

    @Delete
    suspend fun deleteIgnore(ignore: MessageIgnoreEntity)

    @Query("DELETE FROM message_ignore")
    suspend fun deleteAllIgnores()
}