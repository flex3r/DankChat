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
        messageHighlights.value
            .firstOrNull { it.enabled && it.type == MessageHighlightType.Subscription }
            ?: return this

        val highlightState = HighlightState(HighlightType.Subscription)
        return copy(
            highlightState = highlightState,
            childMessage = childMessage?.copy(highlightState = highlightState)
        )
    }

    private fun PointRedemptionMessage.calculateHighlightState(): PointRedemptionMessage {
        messageHighlights.value
            .firstOrNull { it.enabled && it.type == MessageHighlightType.ChannelPointRedemption }
            ?: return this

        val highlightState = HighlightState(HighlightType.ChannelPointRedemption)
        return copy(highlightState = highlightState)
    }

    private fun PrivMessage.calculateHighlightState(): PrivMessage {
        val enabledUserHighlights = userHighlights.value.filter { it.enabled }
        val enabledMessageHighlights = messageHighlights.value.filter { it.enabled }

        if (enabledMessageHighlights.areRewardsEnabled && isReward) {
            return copy(highlightState = HighlightState(HighlightType.ChannelPointRedemption))
        }

        if (enabledMessageHighlights.areFirstMessagesEnabled && isFirstMessage) {
            return copy(highlightState = HighlightState(HighlightType.FirstMessage))
        }

        // Username
        // TODO also match DisplayName?
        if (enabledMessageHighlights.isOwnUserNameEnabled && containsCurrentUserName) {
            return copy(highlightState = HighlightState(HighlightType.Username))
        }

        // User highlights
        enabledUserHighlights.forEach {
            if (it.username.equals(name, ignoreCase = true) || it.username.equals(displayName, ignoreCase = true)) {
                return copy(highlightState = HighlightState(HighlightType.Custom))
            }
        }

        // custom message highlights
        enabledMessageHighlights
            .filter { it.type == MessageHighlightType.Custom }
            .forEach {
                val hasMatch = when {
                    it.isRegex -> it.regex?.let { regex -> originalMessage.matches(regex) } ?: false
                    else -> originalMessage.split(" ").any { word -> word.contains(it.pattern, ignoreCase = it.isCaseSensitive) }
                }

                if (hasMatch) {
                    return copy(highlightState = HighlightState(HighlightType.Custom))
                }
            }

        return this
    }

    private val List<MessageHighlightEntity>.areRewardsEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightType.ChannelPointRedemption)

    private val List<MessageHighlightEntity>.areFirstMessagesEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightType.FirstMessage)

    private val List<MessageHighlightEntity>.isOwnUserNameEnabled: Boolean
        get() = isMessageHighlightTypeEnabled(MessageHighlightType.Username)

    private fun List<MessageHighlightEntity>.isMessageHighlightTypeEnabled(type: MessageHighlightType): Boolean {
        return any { it.type == type }
    }

    private val PrivMessage.isReward: Boolean
        get() = tags["msg-id"] == "highlighted-message" || tags["custom-reward-id"] != null

    private val PrivMessage.isFirstMessage: Boolean
        get() = tags["first-msg"] == "1"

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
            MessageHighlightEntity(id = 4, enabled = true, type = MessageHighlightType.FirstMessage, pattern = "")
        )
    }
}