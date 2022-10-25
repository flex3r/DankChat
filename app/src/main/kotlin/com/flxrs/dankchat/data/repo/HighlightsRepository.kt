package com.flxrs.dankchat.data.repo

import android.util.Log
import com.flxrs.dankchat.data.database.dao.MessageHighlightDao
import com.flxrs.dankchat.data.database.dao.UserHighlightDao
import com.flxrs.dankchat.data.database.entity.MessageHighlightEntity
import com.flxrs.dankchat.data.database.entity.MessageHighlightType
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

    private val messageHighlights = messageHighlightDao.getMessageHighlightsFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    private val userHighlights = userHighlightDao.getUserHighlightsFlow().stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
    private val currentUserNameRegex = preferences.currentUserNameFlow
        .map { it?.let { """\b$it\b""".toRegex(RegexOption.IGNORE_CASE) } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    fun calculateHighlightState(message: Message): Message {
        return when (message) {
            is UserNoticeMessage      -> message.calculateHighlightState()
            is PointRedemptionMessage -> message.calculateHighlightState()
            is PrivMessage            -> message.calculateHighlightState()
            else                      -> message
        }
    }

    private fun UserNoticeMessage.calculateHighlightState(): UserNoticeMessage {
        val subscriptionEnabled = messageHighlights.value
            .any { it.enabled && it.type == MessageHighlightType.Subscription }

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
                .filter { it.type == MessageHighlightType.Custom }
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
        get() = isMessageHighlightTypeEnabled(MessageHighlightType.ChannelPointRedemption)

    private val List<MessageHighlightEntity>.areFirstMessagesEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightType.FirstMessage)

    private val List<MessageHighlightEntity>.areElevatedMessagesEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightType.ElevatedMessage)

    private val List<MessageHighlightEntity>.isOwnUserNameEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightType.Username)

    private fun List<MessageHighlightEntity>.isMessageHighlightTypeEnabled(type: MessageHighlightType): Boolean {
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
            MessageHighlightEntity(id = 4, enabled = true, type = MessageHighlightType.FirstMessage, pattern = ""),
            MessageHighlightEntity(id = 5, enabled = true, type = MessageHighlightType.ElevatedMessage, pattern = ""),
        )
    }
}