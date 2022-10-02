package com.flxrs.dankchat.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.flxrs.dankchat.data.database.entity.UserHighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserHighlightDao {

    @Query("SELECT * FROM user_highlight")
    suspend fun getUserHighlights(): List<UserHighlightEntity>


    @Query("SELECT * FROM user_highlight")
    fun getUserHighlightsFlow(): Flow<List<UserHighlightEntity>>

    @Upsert
    suspend fun addHighlight(highlight: UserHighlightEntity)

    @Upsert
    suspend fun addHighlights(highlights: List<UserHighlightEntity>)

    @Delete
    suspend fun deleteHighlight(highlight: UserHighlightEntity)

    @Query("DELETE FROM user_highlight")
    suspend fun deleteAllHighlights()
}