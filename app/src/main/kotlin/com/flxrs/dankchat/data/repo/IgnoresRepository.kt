@file:Suppress("DEPRECATION")

package com.flxrs.dankchat.data.repo

import android.util.Log
import com.flxrs.dankchat.data.api.ApiManager
import com.flxrs.dankchat.data.database.dao.MessageIgnoreDao
import com.flxrs.dankchat.data.database.dao.UserIgnoreDao
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntityType
import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntity
import com.flxrs.dankchat.data.database.entity.MessageIgnoreEntityType
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

    private val validMessageIgnores = messageIgnores
        .map { ignores -> ignores.filter { it.enabled && (it.type != MessageIgnoreEntityType.Custom || it.pattern.isNotBlank()) } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    private val validUserIgnores = userIgnores
        .map { ignores -> ignores.filter { it.enabled && it.username.isNotBlank() } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    fun applyIgnores(message: Message): Message? {
        return when (message) {
            is PointRedemptionMessage -> message.applyIgnores()
            is PrivMessage            -> message.applyIgnores()
            is UserNoticeMessage      -> message.applyIgnores()
            is WhisperMessage         -> message.applyIgnores()
            else                      -> message
        }
    }

    fun runMigrationsIfNeeded() = coroutineScope.launch {
        runCatching {
            if (preferences.blackListEntries.isEmpty() && messageIgnoreDao.getMessageIgnores().isNotEmpty()) {
                return@launch
            }

            Log.d(TAG, "Running ignores migration...")
            messageIgnoreDao.addIgnores(DEFAULT_IGNORES)

            val existingBlacklistEntries = preferences.customBlacklist
            val messageIgnores = existingBlacklistEntries.mapToMessageIgnoreEntities()
            messageIgnoreDao.addIgnores(messageIgnores)

            val userIgnores = existingBlacklistEntries.mapToUserIgnoreEntities()
            userIgnoreDao.addIgnores(userIgnores)

            val totalIgnores = DEFAULT_IGNORES.size + messageIgnores.size + userIgnores.size
            Log.d(TAG, "Ignores migration completed, added $totalIgnores entries.")
        }.getOrElse {
            Log.e(TAG, "Failed to run ignores migration", it)
            runCatching {
                messageIgnoreDao.deleteAllIgnores()
                userIgnoreDao.deleteAllIgnores()
                return@launch
            }
        }

        preferences.clearBlacklist()
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
            if (blocks.data.isEmpty()) {
                _twitchBlocks.update { emptySet() }
                return@withContext
            }
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
            type = MessageIgnoreEntityType.Custom,
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

    private fun UserNoticeMessage.applyIgnores(): UserNoticeMessage? {
        val messageIgnores = validMessageIgnores.value

        if (isSub && messageIgnores.areSubsIgnored) {
            return null
        }

        if (isAnnouncement && messageIgnores.areAnnouncementsIgnored) {
            return null
        }

        return copy(
            childMessage = childMessage?.applyIgnores()
        )
    }

    private fun PrivMessage.applyIgnores(): PrivMessage? {
        val messageIgnores = validMessageIgnores.value

        if (isSub && messageIgnores.areSubsIgnored) {
            return null
        }

        if (isAnnouncement && messageIgnores.areAnnouncementsIgnored) {
            return null
        }

        if (isReward && messageIgnores.areRewardsIgnored) {
            return null
        }

        if (isElevatedMessage && messageIgnores.areElevatedMessagesIgnored) {
            return null
        }

        if (isFirstMessage && messageIgnores.areFirstMessagesIgnored) {
            return null
        }

        if (isIgnoredUsername(name)) {
            return null
        }

        messageIgnores
            .isIgnoredMessageWithReplacement(message) { replacement ->
                return replacement?.let { copy(message = it) }
            }

        return this
    }

    private fun PointRedemptionMessage.applyIgnores(): PointRedemptionMessage? {
        val redemptionsIgnored = validMessageIgnores.value
            .any { it.type == MessageIgnoreEntityType.ChannelPointRedemption }

        if (redemptionsIgnored) {
            return null
        }

        return this
    }

    private fun WhisperMessage.applyIgnores(): WhisperMessage? {
        if (isIgnoredUsername(name)) {
            return null
        }

        validMessageIgnores.value
            .isIgnoredMessageWithReplacement(message) { replacement ->
                return when (replacement) {
                    null -> null
                    else -> copy(message = replacement)
                }
            }

        return this
    }

    private val List<MessageIgnoreEntity>.areSubsIgnored: Boolean
        get() = isMessageIgnoreTypeEnabled(MessageIgnoreEntityType.Subscription)

    private val List<MessageIgnoreEntity>.areAnnouncementsIgnored: Boolean
        get() = isMessageIgnoreTypeEnabled(MessageIgnoreEntityType.Announcement)

    private val List<MessageIgnoreEntity>.areRewardsIgnored: Boolean
        get() = isMessageIgnoreTypeEnabled(MessageIgnoreEntityType.ChannelPointRedemption)

    private val List<MessageIgnoreEntity>.areFirstMessagesIgnored: Boolean
        get() = isMessageIgnoreTypeEnabled(MessageIgnoreEntityType.FirstMessage)

    private val List<MessageIgnoreEntity>.areElevatedMessagesIgnored: Boolean
        get() = isMessageIgnoreTypeEnabled(MessageIgnoreEntityType.ElevatedMessage)

    private fun List<MessageIgnoreEntity>.isMessageIgnoreTypeEnabled(type: MessageIgnoreEntityType): Boolean {
        return any { it.type == type }
    }

    private fun isIgnoredUsername(name: String): Boolean {
        validUserIgnores.value
            .forEach {
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

    private inline fun List<MessageIgnoreEntity>.isIgnoredMessageWithReplacement(message: String, replacement: (String?) -> Unit) {
        filter { it.type == MessageIgnoreEntityType.Custom }
            .forEach {
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
                    type = MessageIgnoreEntityType.Custom,
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
        private val DEFAULT_IGNORES = listOf(
            MessageIgnoreEntity(id = 1, enabled = false, type = MessageIgnoreEntityType.Subscription, pattern = ""),
            MessageIgnoreEntity(id = 2, enabled = false, type = MessageIgnoreEntityType.Announcement, pattern = ""),
            MessageIgnoreEntity(id = 3, enabled = false, type = MessageIgnoreEntityType.ChannelPointRedemption, pattern = ""),
            MessageIgnoreEntity(id = 4, enabled = false, type = MessageIgnoreEntityType.FirstMessage, pattern = ""),
            MessageIgnoreEntity(id = 5, enabled = false, type = MessageIgnoreEntityType.ElevatedMessage, pattern = ""),
        )
    }
}