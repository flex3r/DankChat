package com.flxrs.dankchat.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageHighlightDao {

    @Query("SELECT * FROM message_highlight WHERE id = :id")
    suspend fun getMessageHighlight(id: Long): MessageHighlightEntity

    @Query("SELECT * FROM message_highlight")
    suspend fun getMessageHighlights(): List<MessageHighlightEntity>

    @Query("SELECT * FROM message_highlight")
    fun getMessageHighlightsFlow(): Flow<List<MessageHighlightEntity>>

    @Upsert
    suspend fun addHighlight(highlight: MessageHighlightEntity): Long

    @Upsert
    suspend fun addHighlights(highlights: List<MessageHighlightEntity>)

    @Delete
    suspend fun deleteHighlight(highlight: MessageHighlightEntity)

    @Query("DELETE FROM message_highlight")
    suspend fun deleteAllHighlights()
}