package com.flxrs.dankchat.data.repo.emote

import com.flxrs.dankchat.data.database.dao.EmoteUsageDao
import com.flxrs.dankchat.data.database.entity.EmoteUsageEntity
import com.flxrs.dankchat.di.DispatchersProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import java.time.Instant

@Single
class EmoteUsageRepository(
    private val emoteUsageDao: EmoteUsageDao,
    dispatchersProvider: DispatchersProvider,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)

    init {
        scope.launch {
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
