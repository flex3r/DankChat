package com.flxrs.dankchat.service

import com.flxrs.dankchat.service.database.EmoteUsageDao
import com.flxrs.dankchat.service.database.EmoteUsageEntity
import java.time.Instant
import javax.inject.Inject

class EmoteUsageRepository @Inject constructor(
    private val emoteUsageDao: EmoteUsageDao
) {
    suspend fun addEmoteUsage(emoteId: String) {
        val entity = EmoteUsageEntity(
            emoteId = emoteId,
            lastUsed = Instant.now()
        )
        emoteUsageDao.addUsage(entity)
    }

    suspend fun clearUsages() = emoteUsageDao.clearUsages()
    fun getRecentUsages() = emoteUsageDao.getRecentUsages()
}