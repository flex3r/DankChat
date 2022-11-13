package com.flxrs.dankchat.data.repo

import com.flxrs.dankchat.data.database.dao.EmoteUsageDao
import com.flxrs.dankchat.data.database.entity.EmoteUsageEntity
import com.flxrs.dankchat.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmoteUsageRepository @Inject constructor(
    private val emoteUsageDao: EmoteUsageDao,
    @ApplicationScope coroutineScope: CoroutineScope
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