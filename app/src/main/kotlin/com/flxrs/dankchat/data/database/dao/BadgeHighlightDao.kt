package com.flxrs.dankchat.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.flxrs.dankchat.data.database.entity.BadgeHighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BadgeHighlightDao {

    @Query("SELECT * FROM badge_highlight WHERE id = :id")
    suspend fun getBadgeHighlight(id: Long): BadgeHighlightEntity

    @Query("SELECT * FROM badge_highlight")
    suspend fun getBadgeHighlights(): List<BadgeHighlightEntity>

    @Query("SELECT * FROM badge_highlight")
    fun getBadgeHighlightsFlow(): Flow<List<BadgeHighlightEntity>>

    @Upsert
    suspend fun addHighlight(highlight: BadgeHighlightEntity): Long

    @Upsert
    suspend fun addHighlights(highlights: List<BadgeHighlightEntity>)

    @Delete
    suspend fun deleteHighlight(highlight: BadgeHighlightEntity)

    @Query("DELETE FROM badge_highlight")
    suspend fun deleteAllHighlights()
}
