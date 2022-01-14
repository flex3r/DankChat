package com.flxrs.dankchat.service.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EmoteUsageDao {

    @Query("SELECT * FROM emote_usage ORDER BY last_used DESC LIMIT $RECENT_EMOTE_USAGE_LIMIT")
    fun getRecentUsages(): Flow<List<EmoteUsageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addUsage(usage: EmoteUsageEntity)

    @Query("DELETE FROM emote_usage")
    suspend fun clearUsages()

    companion object {
        private const val RECENT_EMOTE_USAGE_LIMIT = 30
    }
}