@file:Suppress("DEPRECATION")

package com.flxrs.dankchat.data.repo

import android.util.Log
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.database.dao.BlacklistedUserDao
import com.flxrs.dankchat.data.database.dao.MessageHighlightDao
import com.flxrs.dankchat.data.database.dao.UserHighlightDao
import com.flxrs.dankchat.data.database.entity.BlacklistedUserEntity
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntity
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntityType
import com.flxrs.dankchat.data.database.entity.UserHighlightEntity
import com.flxrs.dankchat.data.twitch.message.*
import com.flxrs.dankchat.di.ApplicationScope
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.multientry.MultiEntryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HighlightsRepository @Inject constructor(
    private val messageHighlightDao: MessageHighlightDao,
    private val userHighlightDao: UserHighlightDao,
    private val blacklistedUserDao: BlacklistedUserDao,
    private val preferences: DankChatPreferenceStore,
    @ApplicationScope private val coroutineScope: CoroutineScope
) {

    private val currentUserAndDisplay = preferences.currentUserAndDisplayFlow.stateIn(coroutineScope, SharingStarted.Eagerly, null)
    private val currentUserRegex = currentUserAndDisplay
        .map(::createUserAndDisplayRegex)
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    val messageHighlights = messageHighlightDao.getMessageHighlightsFlow()
        .map { it.addDefaultsIfNecessary() }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    val userHighlights = userHighlightDao.getUserHighlightsFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    val blacklistedUsers = blacklistedUserDao.getBlacklistedUserFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    private val validMessageHighlights = messageHighlights
        .map { highlights -> highlights.filter { it.enabled && (it.type != MessageHighlightEntityType.Custom || it.pattern.isNotBlank()) } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    private val validUserHighlights = userHighlights
        .map { highlights -> highlights.filter { it.enabled && it.username.isNotBlank() } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    private val validBlacklistedUsers = blacklistedUsers
        .map { highlights -> highlights.filter { it.enabled && it.username.isNotBlank() } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    fun calculateHighlightState(message: Message): Message {
        return when (message) {
            is UserNoticeMessage      -> message.calculateHighlightState()
            is PointRedemptionMessage -> message.calculateHighlightState()
            is PrivMessage            -> message.calculateHighlightState()
            is WhisperMessage         -> message.calculateHighlightState()
            else                      -> message
        }
    }

    fun runMigrationsIfNeeded() = coroutineScope.launch {
        runCatching {
            if (preferences.mentionEntries.isEmpty() && messageHighlightDao.getMessageHighlights().isNotEmpty()) {
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

        preferences.clearCustomMentions()
    }

    suspend fun addMessageHighlight(): MessageHighlightEntity {
        val entity = MessageHighlightEntity(
            id = 0,
            enabled = true,
            type = MessageHighlightEntityType.Custom,
            pattern = ""
        )
        val id = messageHighlightDao.addHighlight(entity)
        return messageHighlightDao.getMessageHighlight(id)
    }

    suspend fun updateMessageHighlight(entity: MessageHighlightEntity) {
        messageHighlightDao.addHighlight(entity)
    }

    suspend fun removeMessageHighlight(entity: MessageHighlightEntity) {
        messageHighlightDao.deleteHighlight(entity)
    }

    suspend fun updateMessageHighlights(entities: List<MessageHighlightEntity>) {
        messageHighlightDao.addHighlights(entities)
    }

    suspend fun addUserHighlight(): UserHighlightEntity {
        val entity = UserHighlightEntity(
            id = 0,
            enabled = true,
            username = ""
        )
        val id = userHighlightDao.addHighlight(entity)
        return userHighlightDao.getUserHighlight(id)
    }

    suspend fun updateUserHighlight(entity: UserHighlightEntity) {
        userHighlightDao.addHighlight(entity)
    }

    suspend fun removeUserHighlight(entity: UserHighlightEntity) {
        userHighlightDao.deleteHighlight(entity)
    }

    suspend fun updateUserHighlights(entities: List<UserHighlightEntity>) {
        userHighlightDao.addHighlights(entities)
    }

    suspend fun addBlacklistedUser(): BlacklistedUserEntity {
        val entity = BlacklistedUserEntity(
            id = 0,
            enabled = true,
            username = ""
        )
        val id = blacklistedUserDao.addBlacklistedUser(entity)
        return blacklistedUserDao.getBlacklistedUser(id)
    }

    suspend fun updateBlacklistedUser(entity: BlacklistedUserEntity) {
        blacklistedUserDao.addBlacklistedUser(entity)
    }

    suspend fun removeBlacklistedUser(entity: BlacklistedUserEntity) {
        blacklistedUserDao.deleteBlacklistedUser(entity)
    }

    suspend fun updateBlacklistedUser(entities: List<BlacklistedUserEntity>) {
        blacklistedUserDao.addBlacklistedUsers(entities)
    }

    private fun UserNoticeMessage.calculateHighlightState(): UserNoticeMessage {
        val messageHighlights = validMessageHighlights.value

        val highlights = buildSet {
            if (isSub && messageHighlights.areSubsEnabled) {
                add(Highlight(HighlightType.Subscription))
            }

            if (isAnnouncement && messageHighlights.areAnnouncementsEnabled) {
                add(Highlight(HighlightType.Announcement))
            }
        }

        return copy(
            highlights = highlights,
            childMessage = childMessage?.calculateHighlightState()
        )
    }

    private fun PointRedemptionMessage.calculateHighlightState(): PointRedemptionMessage {
        val redemptionsEnabled = validMessageHighlights.value
            .any { it.type == MessageHighlightEntityType.ChannelPointRedemption }

        val highlights = when {
            redemptionsEnabled -> setOf(Highlight(HighlightType.ChannelPointRedemption))
            else               -> emptySet()
        }

        return copy(highlights = highlights)
    }

    private fun PrivMessage.calculateHighlightState(): PrivMessage {
        if (isUserBlacklisted(name)) {
            return this
        }

        val userHighlights = validUserHighlights.value
        val messageHighlights = validMessageHighlights.value
        val highlights = buildSet {
            if (isSub && messageHighlights.areSubsEnabled) {
                add(Highlight(HighlightType.Subscription))
            }

            if (isAnnouncement && messageHighlights.areAnnouncementsEnabled) {
                add(Highlight(HighlightType.Announcement))
            }

            if (isReward && messageHighlights.areRewardsEnabled) {
                add(Highlight(HighlightType.ChannelPointRedemption))
            }

            if (isFirstMessage && messageHighlights.areFirstMessagesEnabled) {
                add(Highlight(HighlightType.FirstMessage))
            }

            if (isElevatedMessage && messageHighlights.areElevatedMessagesEnabled) {
                add(Highlight(HighlightType.ElevatedMessage))
            }

            if (containsCurrentUserName) {
                val highlight = messageHighlights.userNameHighlight
                if (highlight?.enabled == true) {
                    add(Highlight(HighlightType.Username))
                    addNotificationHighlightIfEnabled(highlight)
                }
            }

            if (containsParticipatedReply) {
                val highlight = messageHighlights.repliesHighlight
                if (highlight?.enabled == true) {
                    add(Highlight(HighlightType.Reply))
                    addNotificationHighlightIfEnabled(highlight)
                }
            }

            messageHighlights
                .filter { it.type == MessageHighlightEntityType.Custom }
                .forEach {
                    val regex = it.regex ?: return@forEach

                    if (message.contains(regex)) {
                        add(Highlight(HighlightType.Custom))
                        addNotificationHighlightIfEnabled(it)
                    }
                }

            userHighlights.forEach {
                if (name.matches(it.username)) {
                    add(Highlight(HighlightType.Custom))
                    addNotificationHighlightIfEnabled(it)
                }
            }
        }

        return copy(highlights = highlights)
    }

    private fun WhisperMessage.calculateHighlightState(): WhisperMessage = when {
        preferences.createWhisperNotifications -> copy(highlights = setOf(Highlight(HighlightType.Notification)))
        else                                   -> this
    }

    private val List<MessageHighlightEntity>.areSubsEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightEntityType.Subscription)

    private val List<MessageHighlightEntity>.areAnnouncementsEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightEntityType.Announcement)

    private val List<MessageHighlightEntity>.areRewardsEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightEntityType.ChannelPointRedemption)

    private val List<MessageHighlightEntity>.areFirstMessagesEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightEntityType.FirstMessage)

    private val List<MessageHighlightEntity>.areElevatedMessagesEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightEntityType.ElevatedMessage)

    private val List<MessageHighlightEntity>.repliesHighlight: MessageHighlightEntity?
        get() = find { it.type == MessageHighlightEntityType.Reply }

    private val List<MessageHighlightEntity>.userNameHighlight: MessageHighlightEntity?
        get() = find { it.type == MessageHighlightEntityType.Username }

    private fun List<MessageHighlightEntity>.isMessageHighlightTypeEnabled(type: MessageHighlightEntityType): Boolean {
        return any { it.type == type }
    }

    private fun MutableCollection<Highlight>.addNotificationHighlightIfEnabled(highlightEntity: MessageHighlightEntity) {
        if (highlightEntity.createNotification) {
            add(Highlight(HighlightType.Notification))
        }
    }

    private fun MutableCollection<Highlight>.addNotificationHighlightIfEnabled(highlightEntity: UserHighlightEntity) {
        if (highlightEntity.createNotification) {
            add(Highlight(HighlightType.Notification))
        }
    }

    private val PrivMessage.containsCurrentUserName: Boolean
        get() {
            val currentUser = currentUserAndDisplay.value?.first ?: return false
            if (name.matches(currentUser)) {
                return false
            }

            val regex = currentUserRegex.value ?: return false
            return message.contains(regex)
        }

    private val PrivMessage.containsParticipatedReply: Boolean
        get() = thread?.participated == true && name != currentUserAndDisplay.value?.first

    private fun createUserAndDisplayRegex(values: Pair<UserName?, DisplayName?>?): Regex? {
        val (user, display) = values ?: return null
        user ?: return null
        val displayRegex = display
            ?.takeIf { !user.matches(it) }
            ?.let { "|$it" }.orEmpty()
        return """\b$user$displayRegex\b""".toRegex(RegexOption.IGNORE_CASE)
    }

    private fun isUserBlacklisted(name: UserName): Boolean {
        validBlacklistedUsers.value
            .forEach {
                val hasMatch = when {
                    it.isRegex -> it.regex?.let { regex -> name.matches(regex) } ?: false
                    else       -> name.matches(it.username)
                }

                if (hasMatch) {
                    return true
                }
            }

        return false
    }

    private fun List<MultiEntryDto>.mapToMessageHighlightEntities(): List<MessageHighlightEntity> {
        return filterNot { it.matchUser }
            .map {
                MessageHighlightEntity(
                    id = 0,
                    enabled = true,
                    type = MessageHighlightEntityType.Custom,
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

    private fun List<MessageHighlightEntity>.addDefaultsIfNecessary(): List<MessageHighlightEntity> {
        return (this + DEFAULT_HIGHLIGHTS).distinctBy {
            when (it.type) {
                MessageHighlightEntityType.Custom -> it.id
                else                              -> it.type
            }
        }.sortedBy { it.type.ordinal }
    }

    companion object {
        private val TAG = HighlightsRepository::class.java.simpleName
        private val DEFAULT_HIGHLIGHTS = listOf(
            MessageHighlightEntity(id = 0, enabled = true, type = MessageHighlightEntityType.Username, pattern = ""),
            MessageHighlightEntity(id = 0, enabled = true, type = MessageHighlightEntityType.Subscription, pattern = "", createNotification = false),
            MessageHighlightEntity(id = 0, enabled = true, type = MessageHighlightEntityType.Announcement, pattern = "", createNotification = false),
            MessageHighlightEntity(id = 0, enabled = true, type = MessageHighlightEntityType.ChannelPointRedemption, pattern = "", createNotification = false),
            MessageHighlightEntity(id = 0, enabled = true, type = MessageHighlightEntityType.FirstMessage, pattern = "", createNotification = false),
            MessageHighlightEntity(id = 0, enabled = true, type = MessageHighlightEntityType.ElevatedMessage, pattern = "", createNotification = false),
            MessageHighlightEntity(id = 0, enabled = true, type = MessageHighlightEntityType.Reply, pattern = ""),
        )
    }
}
