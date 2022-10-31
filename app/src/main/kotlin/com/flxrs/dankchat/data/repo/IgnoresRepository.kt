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
import kotlinx.coroutines.flow.*
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

    data class TwitchBlock(val id: String, val name: String)

    private val _twitchBlocks = MutableStateFlow(emptySet<TwitchBlock>())

    val messageIgnores = messageIgnoreDao.getMessageIgnoresFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    val userIgnores = userIgnoreDao.getUserIgnoresFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    val twitchBlocks = _twitchBlocks.asStateFlow()

    fun applyIgnores(message: Message): Message? {
        return when (message) {
            is NoticeMessage, is PointRedemptionMessage -> message
            is PrivMessage                              -> message.applyIgnores()
            is UserNoticeMessage                        -> message.applyIgnores()
            is WhisperMessage                           -> message.applyIgnores()
            else                                        -> message
        }
    }

    fun runMigrationsIfNeeded() = coroutineScope.launch {
        runCatching {
            if (preferences.blackListEntries.isEmpty()) {
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

        preferences.customBlacklist = emptyList()
    }

    fun isUserBlocked(userId: String?): Boolean {
        return _twitchBlocks.value.any { it.id == userId }
    }

    suspend fun loadUserBlocks(id: String) = withContext(Dispatchers.Default) {
        if (!preferences.isLoggedIn) {
            return@withContext
        }

        runCatching {
            val blocks = apiManager.getUserBlocks(id) ?: return@withContext
            val userIds = blocks.data.map { it.id }
            val users = apiManager.getUsersByIds(userIds) ?: return@withContext
            val twitchBlocks = users.map { user ->
                TwitchBlock(
                    id = user.id,
                    name = user.name,
                )
            }.toSet()

            _twitchBlocks.update { twitchBlocks }
        }.getOrElse {
            Log.d(TAG, "Failed to load user blocks for $id", it)
        }
    }

    suspend fun addUserBlock(targetUserId: String, targetUsername: String) {
        val result = apiManager.blockUser(targetUserId)
        if (result) {
            _twitchBlocks.update {
                it + TwitchBlock(
                    id = targetUserId,
                    name = targetUsername,
                )
            }
        }
    }

    suspend fun removeUserBlock(targetUserId: String, targetUsername: String) {
        val result = apiManager.unblockUser(targetUserId)
        if (result) {
            _twitchBlocks.update {
                it - TwitchBlock(
                    id = targetUserId,
                    name = targetUsername,
                )
            }
        }
    }

    fun clearIgnores() = _twitchBlocks.update { emptySet() }

    suspend fun addMessageIgnore(): MessageIgnoreEntity {
        val entity = MessageIgnoreEntity(
            id = 0,
            enabled = true,
            pattern = "",
            isBlockMessage = false,
            replacement = "***",
        )
        val id = messageIgnoreDao.addIgnore(entity)
        return messageIgnoreDao.getMessageIgnore(id)
    }

    suspend fun updateMessageIgnore(entity: MessageIgnoreEntity) {
        messageIgnoreDao.addIgnore(entity)
    }

    suspend fun removeMessageIgnore(entity: MessageIgnoreEntity) {
        messageIgnoreDao.deleteIgnore(entity)
    }

    suspend fun updateMessageIgnores(entities: List<MessageIgnoreEntity>) {
        messageIgnoreDao.addIgnores(entities)
    }

    suspend fun addUserIgnore(): UserIgnoreEntity {
        val entity = UserIgnoreEntity(
            id = 0,
            enabled = true,
            username = "",
        )
        val id = userIgnoreDao.addIgnore(entity)
        return userIgnoreDao.getUserIgnore(id)
    }

    suspend fun updateUserIgnore(entity: UserIgnoreEntity) {
        userIgnoreDao.addIgnore(entity)
    }

    suspend fun removeUserIgnore(entity: UserIgnoreEntity) {
        userIgnoreDao.deleteIgnore(entity)
    }

    suspend fun updateUserIgnores(entities: List<UserIgnoreEntity>) {
        userIgnoreDao.addIgnores(entities)
    }

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
                else       -> name.equals(it.username, ignoreCase = !it.isCaseSensitive)
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

    private fun List<MultiEntryDto>.mapToMessageIgnoreEntities(): List<MessageIgnoreEntity> {
        return filterNot { it.matchUser }
            .map {
                MessageIgnoreEntity(
                    id = 0,
                    enabled = true,
                    pattern = it.entry,
                    isRegex = it.isRegex,
                    isCaseSensitive = false,
                    isBlockMessage = true,
                    replacement = null
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
                    isRegex = it.isRegex,
                    isCaseSensitive = false
                )
            }
    }

    companion object {
        private val TAG = IgnoresRepository::class.java.simpleName
    }
}