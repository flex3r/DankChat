package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.twitch.message.AutomodMessage
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.ModerationMessage
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.developer.ChatSendProtocol
import com.flxrs.dankchat.utils.extensions.addAndLimit
import com.flxrs.dankchat.utils.extensions.addSystemMessage
import com.flxrs.dankchat.utils.extensions.replaceOrAddModerationMessage
import com.flxrs.dankchat.utils.extensions.replaceWithTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.plusAssign

@Single
class ChatMessageRepository(
    private val messageProcessor: MessageProcessor,
    private val chatNotificationRepository: ChatNotificationRepository,
    private val dispatchersProvider: DispatchersProvider,
    private val chatSettingsDataStore: ChatSettingsDataStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private val messages = ConcurrentHashMap<UserName, MutableStateFlow<List<ChatItem>>>()
    private val _chatLoadingFailures = MutableStateFlow(emptySet<ChatLoadingFailure>())
    private val _sessionMessageCount = AtomicInt(0)
    private val _ircSentCount = AtomicInt(0)
    private val _helixSentCount = AtomicInt(0)
    private val _sendFailureCount = AtomicInt(0)

    val sessionMessageCount: Int get() = _sessionMessageCount.load()
    val ircSentCount: Int get() = _ircSentCount.load()
    val helixSentCount: Int get() = _helixSentCount.load()
    val sendFailureCount: Int get() = _sendFailureCount.load()

    fun incrementSentMessageCount(protocol: ChatSendProtocol) {
        when (protocol) {
            ChatSendProtocol.IRC -> _ircSentCount += 1
            ChatSendProtocol.Helix -> _helixSentCount += 1
        }
    }

    fun incrementSendFailureCount() {
        _sendFailureCount += 1
    }

    private val scrollBackLengthFlow =
        chatSettingsDataStore.debouncedScrollBack
            .onEach { length ->
                messages.forEach { (_, flow) ->
                    if (flow.value.size > length) {
                        flow.update { it.takeLast(length) }
                    }
                }
            }.stateIn(scope, SharingStarted.Eagerly, 500)
    val scrollBackLength get() = scrollBackLengthFlow.value

    val chatLoadingFailures = _chatLoadingFailures.asStateFlow()

    fun getChat(channel: UserName): StateFlow<List<ChatItem>> = messages.getOrPut(channel) { MutableStateFlow(emptyList()) }.also {
        updateChannelKeys()
    }

    fun getMessagesFlow(channel: UserName): MutableStateFlow<List<ChatItem>>? = messages[channel]

    val channels: Flow<List<UserName>>
        get() = _channelKeys.map { it.toList() }

    private val _channelKeys = MutableStateFlow<Set<UserName>>(emptySet())

    private fun updateChannelKeys() {
        _channelKeys.value = messages.keys.toSet()
    }

    fun getAllChat(): Flow<List<ChatItem>> = _channelKeys.flatMapLatest { keys ->
        when {
            keys.isEmpty() -> flowOf(emptyList())

            else -> combine(keys.map { getChat(it) }) { arrays ->
                arrays
                    .flatMap { it }
                    // Global notices are broadcast to all channels with the same message id
                    .distinctBy { it.message.id }
                    .sortedBy { it.message.timestamp }
            }
        }
    }

    fun findMessage(
        messageId: String,
        channel: UserName?,
        whispers: StateFlow<List<ChatItem>>,
    ): Message? = (channel?.let { messages[it] } ?: whispers).value.find { it.message.id == messageId }?.message

    fun addMessages(
        channel: UserName,
        items: List<ChatItem>,
    ) {
        _sessionMessageCount += items.size
        messages[channel]?.update { current ->
            current.addAndLimit(items = items, scrollBackLength, messageProcessor::onMessageRemoved)
        }
    }

    fun applyModerationMessage(message: ModerationMessage) {
        messages[message.channel]?.update { current ->
            when (message.action) {
                ModerationMessage.Action.Delete,
                ModerationMessage.Action.SharedDelete,
                -> current.replaceWithTimeout(message, scrollBackLength, messageProcessor::onMessageRemoved)

                else -> current.replaceOrAddModerationMessage(message, scrollBackLength, messageProcessor::onMessageRemoved)
            }
        }
    }

    fun broadcastToAllChannels(item: ChatItem) {
        messages.keys.forEach { channel ->
            messages[channel]?.update { current ->
                current.addAndLimit(item, scrollBackLength, messageProcessor::onMessageRemoved)
            }
        }
    }

    fun broadcastWhisperIfEnabled(item: ChatItem) {
        if (!chatSettingsDataStore.current().showWhispersInline) return
        val whisper = item.message as? WhisperMessage ?: return
        val inlineWhisper =
            item.copy(
                message = messageProcessor.processInlineWhisper(whisper),
                isMentionTab = false,
            )
        broadcastToAllChannels(inlineWhisper)
    }

    fun clearMessages(channel: UserName) {
        messages[channel]?.value = emptyList()
    }

    fun updateAutomodMessageStatus(
        channel: UserName,
        heldMessageId: String,
        status: AutomodMessage.Status,
    ) {
        messages[channel]?.update { current ->
            current.map { item ->
                val msg = item.message
                when {
                    msg is AutomodMessage && msg.heldMessageId == heldMessageId -> {
                        item.copy(tag = item.tag + 1, message = msg.copy(status = status))
                    }

                    else -> {
                        item
                    }
                }
            }
        }
    }

    suspend fun reparseAllEmotesAndBadges() = withContext(dispatchersProvider.default) {
        messages.values
            .map { flow ->
                async {
                    flow.update { items ->
                        items.map { item ->
                            // Unchanged messages keep their tag so mapping caches and compose keys survive
                            when (val reparsed = messageProcessor.reparseEmotesAndBadges(item.message)) {
                                item.message -> item
                                else -> item.copy(tag = item.tag + 1, message = reparsed)
                            }
                        }
                    }
                }
            }.awaitAll()
        chatNotificationRepository.reparseAll()
    }

    fun addSystemMessage(
        channel: UserName,
        type: SystemMessageType,
    ) {
        messages[channel]?.update {
            it.addSystemMessage(type, scrollBackLength, messageProcessor::onMessageRemoved)
        }
    }

    fun addSystemMessageToChannels(
        type: SystemMessageType,
        channels: Set<UserName> = messages.keys,
    ): Set<UserName> {
        val reconnectedChannels = mutableSetOf<UserName>()
        channels.forEach { channel ->
            val flow = messages[channel] ?: return@forEach
            val current = flow.value
            flow.value =
                current.addSystemMessage(type, scrollBackLength, messageProcessor::onMessageRemoved) {
                    reconnectedChannels += channel
                }
        }
        return reconnectedChannels
    }

    fun addLoadingFailure(failure: ChatLoadingFailure) {
        _chatLoadingFailures.update { it + failure }
    }

    fun clearChatLoadingFailures() = _chatLoadingFailures.update { emptySet() }

    fun createMessageFlows(channel: UserName) {
        messages.putIfAbsent(channel, MutableStateFlow(emptyList()))
    }

    fun removeMessageFlows(channel: UserName) {
        messages.remove(channel)
    }
}
