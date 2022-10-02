package com.flxrs.dankchat.data.repo

import android.util.Log
import com.flxrs.dankchat.data.database.dao.MessageHighlightDao
import com.flxrs.dankchat.data.database.dao.UserHighlightDao
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntity
import com.flxrs.dankchat.data.database.entity.MessageHighlightType
import com.flxrs.dankchat.data.database.entity.UserHighlightEntity
import com.flxrs.dankchat.di.ApplicationScope
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.multientry.MultiEntryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HighlightsRepository @Inject constructor(
    private val messageHighlightDao: MessageHighlightDao,
    private val userHighlightDao: UserHighlightDao,
    private val preferences: DankChatPreferenceStore,
    @ApplicationScope private val coroutineScope: CoroutineScope
) {

    fun runMigrationsIfNeeded() = coroutineScope.launch {
        runCatching {
            if (messageHighlightDao.getMessageHighlights().isNotEmpty()) {
                // Assume nothing needs to be done, if there is at least one highlight in the db
                return@launch
            }

            Log.d(TAG, "Running highlights migration...")
            messageHighlightDao.addHighlights(DEFAULT_HIGHLIGHTS)

            val existingMentions = preferences.customMentions
            val messageHighlights = existingMentions.mapToMessageHighlightEntities()
            messageHighlightDao.addHighlights(messageHighlights)

            val userHighlights = existingMentions.mapToUserHighlightEntities()
            userHighlightDao.addHighlights(userHighlights)

            val totalHighlights = DEFAULT_HIGHLIGHTS.size + messageHighlights.size + userHighlights.size
            Log.d(TAG, "Highlights migration completed, added $totalHighlights entries.")
        }.getOrElse {
            Log.e(TAG, "Failed to run highlights migration", it)
            runCatching {
                messageHighlightDao.deleteAllHighlights()
                userHighlightDao.deleteAllHighlights()
                return@launch
            }
        }

        // TODO
        //preferences.customMentions = emptyList()
    }


    private fun List<MultiEntryDto>.mapToMessageHighlightEntities(): List<MessageHighlightEntity> {
        return map {
            MessageHighlightEntity(
                id = 0,
                enabled = true,
                type = MessageHighlightType.Custom,
                pattern = it.entry,
                isRegex = it.isRegex
            )
        }
    }

    private fun List<MultiEntryDto>.mapToUserHighlightEntities(): List<UserHighlightEntity> {
        return filter { it.matchUser }
            .map {
                UserHighlightEntity(
                    id = 0,
                    enabled = true,
                    username = it.entry
                )
            }
    }

    companion object {
        private val TAG = HighlightsRepository::class.java.simpleName
        private val DEFAULT_HIGHLIGHTS = listOf(
            MessageHighlightEntity(id = 1, enabled = true, type = MessageHighlightType.Username, pattern = ""),
            MessageHighlightEntity(id = 2, enabled = true, type = MessageHighlightType.Subscription, pattern = ""),
            MessageHighlightEntity(id = 3, enabled = true, type = MessageHighlightType.ChannelPointRedemption, pattern = ""),
            MessageHighlightEntity(id = 4, enabled = true, type = MessageHighlightType.FirstMessage, pattern = "")
        )
    }
}