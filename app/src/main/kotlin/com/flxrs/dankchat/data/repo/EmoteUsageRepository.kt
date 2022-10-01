package com.flxrs.dankchat.data.repo

import android.util.Log
import com.flxrs.dankchat.data.database.EmoteUsageDao
import com.flxrs.dankchat.data.database.entity.EmoteUsageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

class EmoteUsageRepository @Inject constructor(
    private val emoteUsageDao: EmoteUsageDao,
    private val coroutineScope: CoroutineScope
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