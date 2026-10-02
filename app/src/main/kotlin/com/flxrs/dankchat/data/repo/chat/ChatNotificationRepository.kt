package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.twitch.message.ModerationMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.utils.extensions.addAndLimit
import com.flxrs.dankchat.utils.extensions.assign
import com.flxrs.dankchat.utils.extensions.clear
import com.flxrs.dankchat.utils.extensions.firstValue
import com.flxrs.dankchat.utils.extensions.increment
import com.flxrs.dankchat.utils.extensions.markModeratedMessages
import com.flxrs.dankchat.utils.extensions.mutableSharedFlowOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single

@Single
class ChatNotificationRepository(
    private val messageProcessor: MessageProcessor,
    chatSettingsDataStore: ChatSettingsDataStore,
    private val chatChannelProvider: ChatChannelProvider,
    dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)

    private val _mentions = MutableStateFlow<ImmutableList<ChatItem>>(persistentListOf())
    private val _whispers = MutableStateFlow<ImmutableList<ChatItem>>(persistentListOf())
    private val _messageUpdates = MutableSharedFlow<List<ChatItem>>(replay = 0, extraBufferCapacity = 10)
    private val _channelMentionCount = mutableSharedFlowOf(mutableMapOf<UserName, Int>())
    private val _unreadMessagesMap = mutableSharedFlowOf(mutableMapOf<UserName, Boolean>())
    private val _notificationClearRequests = MutableSharedFlow<NotificationClearScope>(extraBufferCapacity = 16)

    private val scrollBackLengthFlow =
        chatSettingsDataStore.debouncedScrollBack
            .stateIn(scope, SharingStarted.Eagerly, 500)
    private val scrollBackLength get() = scrollBackLengthFlow.value

    val messageUpdates: SharedFlow<List<ChatItem>> = _messageUpdates.asSharedFlow()
    val channelMentionCount: SharedFlow<Map<UserName, Int>> = _channelMentionCount.asSharedFlow()
    val unreadMessagesMap: SharedFlow<Map<UserName, Boolean>> = _unreadMessagesMap.asSharedFlow()
    val notificationClearRequests: SharedFlow<NotificationClearScope> = _notificationClearRequests.asSharedFlow()
    val mentions: StateFlow<ImmutableList<ChatItem>> = _mentions
    val whispers: StateFlow<ImmutableList<ChatItem>> = _whispers

    suspend fun reparseAll() {
        _mentions.update { items ->
            items
                .map {
                    it.copy(
                        tag = it.tag + 1,
                        message = messageProcessor.reparseEmotesAndBadges(it.message),
                    )
                }.toImmutableList()
        }
        _whispers.update { items ->
            items
                .map {
                    it.copy(
                        tag = it.tag + 1,
                        message = messageProcessor.reparseEmotesAndBadges(it.message),
                    )
                }.toImmutableList()
        }
    }

    fun addMentions(items: List<ChatItem>) {
        if (items.isEmpty()) return
        _mentions.update { current ->
            current.addAndLimit(items, scrollBackLength, messageProcessor::onMessageRemoved).toImmutableList()
        }
    }

    fun addMentionsDeduped(items: List<ChatItem>) {
        if (items.isEmpty()) return
        _mentions.update { current ->
            (current + items)
                .distinctBy { it.message.id }
                .sortedBy { it.message.timestamp }
                .toImmutableList()
        }
    }

    fun applyModerationMessage(message: ModerationMessage) {
        _mentions.update { current -> current.markModeratedMessages(message).toImmutableList() }
    }

    fun addWhisper(item: ChatItem) {
        _whispers.update { current ->
            current.addAndLimit(item, scrollBackLength, messageProcessor::onMessageRemoved).toImmutableList()
        }
    }

    fun emitMessages(items: List<ChatItem>) {
        _messageUpdates.tryEmit(items)
    }

    fun setUnreadIfInactive(channel: UserName) {
        if (channel != chatChannelProvider.activeChannel.value) {
            val isUnread = _unreadMessagesMap.firstValue[channel] == true
            if (!isUnread) {
                _unreadMessagesMap.assign(channel, true)
            }
        }
    }

    fun incrementMentionCount(
        channel: UserName,
        count: Int,
    ) {
        _channelMentionCount.increment(channel, count)
    }

    fun clearMentionCount(channel: UserName) = with(_channelMentionCount) {
        tryEmit(firstValue.apply { set(channel, 0) })
    }

    fun clearMentionCounts() = with(_channelMentionCount) {
        tryEmit(firstValue.apply { keys.forEach { if (it != WhisperMessage.WHISPER_CHANNEL) set(it, 0) } })
    }

    fun clearUnreadMessage(channel: UserName) {
        _unreadMessagesMap.assign(channel, false)
    }

    fun createMentionFlows(channel: UserName) {
        with(_channelMentionCount) {
            if (!firstValue.contains(WhisperMessage.WHISPER_CHANNEL)) tryEmit(firstValue.apply { set(channel, 0) })
            if (!firstValue.contains(channel)) tryEmit(firstValue.apply { set(channel, 0) })
        }
    }

    fun removeMentionFlows(channel: UserName) {
        _channelMentionCount.clear(channel)
    }

    fun markMentionsRead() {
        _notificationClearRequests.tryEmit(NotificationClearScope.Mentions)
    }

    fun markWhispersRead() {
        _notificationClearRequests.tryEmit(NotificationClearScope.Whispers)
    }

    fun markChannelRead(channel: UserName) {
        _notificationClearRequests.tryEmit(NotificationClearScope.Channel(channel))
    }

    fun removeChannelNotifications(channel: UserName) {
        _notificationClearRequests.tryEmit(NotificationClearScope.ChannelRemoved(channel))
    }
}

sealed interface NotificationClearScope {
    data object Mentions : NotificationClearScope

    data object Whispers : NotificationClearScope

    data class Channel(
        val channel: UserName,
    ) : NotificationClearScope

    data class ChannelRemoved(
        val channel: UserName,
    ) : NotificationClearScope
}
