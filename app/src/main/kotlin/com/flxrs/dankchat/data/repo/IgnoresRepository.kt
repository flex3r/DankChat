package com.flxrs.dankchat.data.repo

import android.util.Log
import com.flxrs.dankchat.data.api.ApiManager
import com.flxrs.dankchat.data.database.dao.MessageIgnoreDao
import com.flxrs.dankchat.data.database.dao.UserIgnoreDao
import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntity
import com.flxrs.dankchat.data.database.entity.UserIgnoreEntity
import com.flxrs.dankchat.data.twitch.message.*
import com.flxrs.dankchat.di.ApplicationScope
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.multientry.MultiEntryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IgnoresRepository @Inject constructor(
    private val apiManager: ApiManager,
    private val messageIgnoreDao: MessageIgnoreDao,
    private val userIgnoreDao: UserIgnoreDao,
    private val preferences: DankChatPreferenceStore,
    @ApplicationScope private val coroutineScope: CoroutineScope
) {

    private val messageIgnores = messageIgnoreDao.getMessageIgnoresFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    private val userIgnores = userIgnoreDao.getUserIgnoresFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    private val _userBlocks = MutableStateFlow(emptySet<String>())

    fun applyIgnores(message: Message): Message? {
        return when (message) {
            is NoticeMessage, is PointRedemptionMessage -> message
            is PrivMessage                              -> message.applyIgnores()
            is UserNoticeMessage                        -> message.applyIgnores()
            is WhisperMessage                           -> message.applyIgnores()
            else                                        -> message
        }
    }

    fun isUserBlocked(userId: String?): Boolean {
        return userId in _userBlocks.value
    }

    suspend fun loadUserBlocks(id: String) = withContext(Dispatchers.Default) {
        if (!preferences.isLoggedIn) {
            return@withContext
        }

        runCatching {
            val blocks = apiManager.getUserBlocks(id) ?: return@withContext
            val userIds = blocks.data.map { it.id }
            _userBlocks.update { userIds.toSet() }
        }.getOrElse {
            Log.d(TAG, "Failed to load user blocks for $id", it)
        }
    }

    fun addUserBlock(targetUserId: String) = _userBlocks.update { it + targetUserId }
    fun removeUserBlock(targetUserId: String) = _userBlocks.update { it - targetUserId }
    fun clearIgnores() = _userBlocks.update { emptySet() }

    private fun UserNoticeMessage.applyIgnores(): UserNoticeMessage {
        return copy(
            childMessage = childMessage?.applyIgnores()
        )
    }

    private fun PrivMessage.applyIgnores(): PrivMessage? {
        if (isIgnoredUsername(name)) {
            return null
        }

        isIgnoredMessageWithReplacement(message) { replacement ->
            return replacement?.let { copy(message = it) }
        }

        return this
    }

    private fun WhisperMessage.applyIgnores(): WhisperMessage? {
        if (isIgnoredUsername(name)) {
            return null
        }

        isIgnoredMessageWithReplacement(message) { replacement ->
            return when (replacement) {
                null -> null
                else -> copy(message = replacement)
            }
        }

        return this
    }

    private fun isIgnoredUsername(name: String): Boolean {
        userIgnores.value.forEach {
            val hasMatch = when {
                it.isRegex -> it.regex?.let { regex -> name.matches(regex) } ?: false
                else       -> name == it.username

            }

            if (hasMatch) {
                return true
            }
        }

        return false
    }

    private inline fun isIgnoredMessageWithReplacement(message: String, replacement: (String?) -> Unit) {
        messageIgnores.value.forEach {
            val regex = it.regex ?: return@forEach

            if (message.contains(regex)) {
                if (it.replacement != null) {
                    val filteredMessage = message.replace(regex, it.replacement)
                    return replacement(filteredMessage)
                }

                return replacement(null)
            }
        }
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