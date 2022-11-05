package com.flxrs.dankchat.data.repo

import android.util.Log
import com.flxrs.dankchat.data.database.dao.MessageHighlightDao
import com.flxrs.dankchat.data.database.dao.UserHighlightDao
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
    private val preferences: DankChatPreferenceStore,
    @ApplicationScope private val coroutineScope: CoroutineScope
) {

    private val currentUserNameRegex = preferences.currentUserNameFlow
        .map { it?.let { """\b$it\b""".toRegex(RegexOption.IGNORE_CASE) } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    val messageHighlights = messageHighlightDao.getMessageHighlightsFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    val userHighlights = userHighlightDao.getUserHighlightsFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    fun calculateHighlightState(message: Message): Message {
        return when (message) {
            is UserNoticeMessage      -> message.calculateHighlightState()
            is PointRedemptionMessage -> message.calculateHighlightState()
            is PrivMessage            -> message.calculateHighlightState()
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

    private fun UserNoticeMessage.calculateHighlightState(): UserNoticeMessage {
        val subscriptionEnabled = messageHighlights.value
            .any { it.enabled && it.type == MessageHighlightEntityType.Subscription }

        val highlights = when {
            subscriptionEnabled -> listOf(Highlight(HighlightType.Subscription))
            else                -> emptyList()
        }

        return copy(
            highlights = highlights,
            childMessage = childMessage?.calculateHighlightState()
        )
    }

    private fun PointRedemptionMessage.calculateHighlightState(): PointRedemptionMessage {
        val enabledMessageHighlights = messageHighlights.value.filter { it.enabled }
        val highlights = buildList {
            if (enabledMessageHighlights.areRewardsEnabled) {
                add(Highlight(HighlightType.ChannelPointRedemption))
            }
        }

        return copy(highlights = highlights)
    }

    private fun PrivMessage.calculateHighlightState(): PrivMessage {
        val enabledUserHighlights = userHighlights.value.filter { it.enabled }
        val enabledMessageHighlights = messageHighlights.value.filter { it.enabled }
        val highlights = buildList {
            if (enabledMessageHighlights.areRewardsEnabled && isReward) {
                add(Highlight(HighlightType.ChannelPointRedemption))
            }

            if (enabledMessageHighlights.areFirstMessagesEnabled && isFirstMessage) {
                add(Highlight(HighlightType.FirstMessage))
            }

            if (enabledMessageHighlights.areElevatedMessagesEnabled && isElevatedMessage) {
                add(Highlight(HighlightType.ElevatedMessage))
            }

            // Username
            if (enabledMessageHighlights.isOwnUserNameEnabled && containsCurrentUserName) {
                add(Highlight(HighlightType.Username))
            }

            // User highlights
            enabledUserHighlights.forEach {
                if (it.username.equals(name, ignoreCase = true)) {
                    add(Highlight(HighlightType.Custom))
                }
            }

            // custom message highlights
            enabledMessageHighlights
                .filter { it.type == MessageHighlightEntityType.Custom }
                .forEach {
                    val regex = it.regex ?: return@forEach

                    if (message.contains(regex)) {
                        add(Highlight(HighlightType.Custom))
                    }
                }

        }

        return copy(highlights = highlights)
    }

    private val List<MessageHighlightEntity>.areRewardsEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightEntityType.ChannelPointRedemption)

    private val List<MessageHighlightEntity>.areFirstMessagesEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightEntityType.FirstMessage)

    private val List<MessageHighlightEntity>.areElevatedMessagesEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightEntityType.ElevatedMessage)

    private val List<MessageHighlightEntity>.isOwnUserNameEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightEntityType.Username)

    private fun List<MessageHighlightEntity>.isMessageHighlightTypeEnabled(type: MessageHighlightEntityType): Boolean {
        return any { it.type == type }
    }

    private val PrivMessage.isReward: Boolean
        get() = tags["msg-id"] == "highlighted-message" || tags["custom-reward-id"] != null

    private val PrivMessage.isFirstMessage: Boolean
        get() = tags["first-msg"] == "1"

    private val PrivMessage.isElevatedMessage: Boolean
        get() = tags["pinned-chat-paid-amount"] != null

    private val PrivMessage.containsCurrentUserName: Boolean
        get() {
            val regex = currentUserNameRegex.value ?: return false
            return message.contains(regex)
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

    companion object {
        private val TAG = HighlightsRepository::class.java.simpleName
        private val DEFAULT_HIGHLIGHTS = listOf(
            MessageHighlightEntity(id = 1, enabled = true, type = MessageHighlightEntityType.Username, pattern = ""),
            MessageHighlightEntity(id = 2, enabled = true, type = MessageHighlightEntityType.Subscription, pattern = ""),
            MessageHighlightEntity(id = 3, enabled = true, type = MessageHighlightEntityType.ChannelPointRedemption, pattern = ""),
            MessageHighlightEntity(id = 4, enabled = true, type = MessageHighlightEntityType.FirstMessage, pattern = ""),
            MessageHighlightEntity(id = 5, enabled = true, type = MessageHighlightEntityType.ElevatedMessage, pattern = ""),
        )
    }
}