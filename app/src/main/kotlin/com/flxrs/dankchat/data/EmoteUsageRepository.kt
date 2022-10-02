package com.flxrs.dankchat.data

import com.flxrs.dankchat.data.database.EmoteUsageDao
import com.flxrs.dankchat.data.database.EmoteUsageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

class EmoteUsageRepository @Inject constructor(
    private val emoteUsageDao: EmoteUsageDao,
    coroutineScope: CoroutineScope
) {

    init {
        coroutineScope.launch {
            runCatching {
                emoteUsageDao.deleteOldUsages()
            }
        }
    }

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