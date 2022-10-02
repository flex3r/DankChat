package com.flxrs.dankchat.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EmoteUsageDao {

    @Query("SELECT * FROM emote_usage ORDER BY last_used DESC LIMIT $RECENT_EMOTE_USAGE_LIMIT")
    fun getRecentUsages(): Flow<List<EmoteUsageEntity>>

    @Upsert
    suspend fun addUsage(usage: EmoteUsageEntity)

    @Query("DELETE FROM emote_usage")
    suspend fun clearUsages()

    @Query("DELETE FROM emote_usage WHERE emote_id not in (SELECT emote_id FROM emote_usage ORDER BY last_used DESC LIMIT $RECENT_EMOTE_USAGE_LIMIT)")
    suspend fun deleteOldUsages()

    companion object {
        private const val RECENT_EMOTE_USAGE_LIMIT = 30
    }
}