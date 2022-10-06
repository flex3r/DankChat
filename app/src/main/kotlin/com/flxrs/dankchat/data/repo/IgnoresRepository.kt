package com.flxrs.dankchat.data.repo

import android.util.Log
import com.flxrs.dankchat.data.database.dao.MessageIgnoreDao
import com.flxrs.dankchat.data.database.dao.UserIgnoreDao
import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntity
import com.flxrs.dankchat.data.database.entity.UserIgnoreEntity
import com.flxrs.dankchat.data.twitch.message.*
import com.flxrs.dankchat.di.ApplicationScope
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.multientry.MultiEntryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IgnoresRepository @Inject constructor(
    private val messageIgnoreDao: MessageIgnoreDao,
    private val userIgnoreDao: UserIgnoreDao,
    private val preferences: DankChatPreferenceStore,
    @ApplicationScope private val coroutineScope: CoroutineScope
) {

    private val messageIgnores = messageIgnoreDao.getMessageIgnoresFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    private val userIgnores = userIgnoreDao.getUserIgnoresFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    fun applyIgnores(message: Message): Message? {
        return when (message) {
            is NoticeMessage          -> message.applyIgnores()
            is PointRedemptionMessage -> message.applyIgnores()
            is PrivMessage            -> message.applyIgnores()
            is UserNoticeMessage      -> message.applyIgnores()
            is WhisperMessage         -> message.applyIgnores()
            else                      -> message
        }
    }

    private fun NoticeMessage.applyIgnores(): NoticeMessage? {
        // TODO
        return this
    }

    private fun UserNoticeMessage.applyIgnores(): UserNoticeMessage? {
        // TODO
        return this
    }

    private fun PointRedemptionMessage.applyIgnores(): PointRedemptionMessage? {
        // TODO
        return this
    }

    private fun PrivMessage.applyIgnores(): PrivMessage? {
        userIgnores.value.forEach {
            val hasMatch = when {
                it.isRegex -> it.regex?.let { regex -> name.matches(regex) || displayName.matches(regex) } ?: false
                else       -> name == it.username || displayName == it.username // TODO check
            }

            if (hasMatch) {
                return null
            }
        }

        (messageIgnores.value + MessageIgnoreEntity(999, true, "FeelsDankMan", replacement = "asdf")).forEach {
            val regex = it.regex ?: return@forEach

            if (message.contains(regex)) {
                if (it.replacement != null) {
                    val filteredMessage = message.replace(regex, it.replacement)
                    return copy(message = filteredMessage)
                }

                return null
            }
        }

        return this
    }

    private fun WhisperMessage.applyIgnores(): WhisperMessage? {
        // TODO
        return this
    }

    fun runMigrationsIfNeeded() = coroutineScope.launch {
        runCatching {
            if (messageIgnoreDao.getMessageIgnores().isNotEmpty()) {
                // Assume nothing needs to be done, if there is at least one ignore in the db
                return@launch
            }

            Log.d(TAG, "Running ignores migration...")

            val existingBlacklistEntries = preferences.customBlacklist
            val messageIgnores = existingBlacklistEntries.mapToMessageIgnoreEntities()
            messageIgnoreDao.addIgnores(messageIgnores)

            val userIgnores = existingBlacklistEntries.mapToUserIgnoreEntities()
            userIgnoreDao.addIgnores(userIgnores)

            val totalIgnores = messageIgnores.size + userIgnores.size
            Log.d(TAG, "Ignores migration completed, added $totalIgnores entries.")
        }.getOrElse {
            Log.e(TAG, "Failed to run ignores migration", it)
            runCatching {
                messageIgnoreDao.deleteAllIgnores()
                userIgnoreDao.deleteAllIgnores()
                return@launch
            }
        }

        // TODO
        //preferences.customBlacklist = emptyList()
    }

    private fun List<MultiEntryDto>.mapToMessageIgnoreEntities(): List<MessageIgnoreEntity> {
        return map {
            MessageIgnoreEntity(
                id = 0,
                enabled = true,
                pattern = it.entry,
                isRegex = it.isRegex

            )
        }
    }

    private fun List<MultiEntryDto>.mapToUserIgnoreEntities(): List<UserIgnoreEntity> {
        return filter { it.matchUser }
            .map {
                UserIgnoreEntity(
                    id = 0,
                    enabled = true,
                    username = it.entry,
                    isRegex = it.isRegex
                )
            }
    }

    companion object {
        private val TAG = IgnoresRepository::class.java.simpleName
    }
}