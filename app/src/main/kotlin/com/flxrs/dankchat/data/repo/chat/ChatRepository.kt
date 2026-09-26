package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.repo.PinnedMessageRepository
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.emote.EmoteRepository
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.utils.TextResource
import com.flxrs.dankchat.utils.extensions.parseColorOrNull
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

@Single
class ChatRepository(
    private val chatChannelProvider: ChatChannelProvider,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatConnector: ChatConnector,
    private val chatNotificationRepository: ChatNotificationRepository,
    private val chatEventProcessor: ChatEventProcessor,
    private val userStateRepository: UserStateRepository,
    private val usersRepository: UsersRepository,
    private val pinnedMessageRepository: PinnedMessageRepository,
    private val emoteRepository: EmoteRepository,
    private val channelRepository: ChannelRepository,
    private val messageProcessor: MessageProcessor,
    private val chatMessageSender: ChatMessageSender,
    private val authDataStore: AuthDataStore,
    private val chatSettingsDataStore: ChatSettingsDataStore,
    private val messageRateTracker: ChannelMessageRateTracker,
) {
    val activeChannel get() = chatChannelProvider.activeChannel
    val channels get() = chatChannelProvider.channels

    init {
        chatChannelProvider.channels.value?.forEach { createFlowsIfNecessary(it) }
    }

    fun joinChannel(channel: UserName): List<UserName> {
        val currentChannels = channels.value.orEmpty()
        if (channel in currentChannels) {
            return currentChannels
        }

        val updatedChannels = currentChannels + channel
        chatChannelProvider.setChannels(updatedChannels)

        createFlowsIfNecessary(channel)
        chatMessageRepository.clearMessages(channel)

        chatConnector.joinIrcChannel(channel)

        return updatedChannels
    }

    fun updateChannels(updatedChannels: List<UserName>): List<UserName> {
        val currentChannels = channels.value.orEmpty()
        val removedChannels = currentChannels - updatedChannels.toSet()

        removedChannels.forEach { partChannel(it) }

        chatChannelProvider.setChannels(updatedChannels)
        return removedChannels
    }

    fun createFlowsIfNecessary(channel: UserName) {
        chatMessageRepository.createMessageFlows(channel)
        chatConnector.createConnectionState(channel)
        chatNotificationRepository.createMentionFlows(channel)
        usersRepository.initChannel(channel)
        channelRepository.initRoomState(channel)
    }

    suspend fun sendMessage(
        input: String,
        replyId: String? = null,
        forceIrc: Boolean = false,
    ) {
        val channel = chatChannelProvider.activeChannel.value ?: return
        chatMessageSender.send(channel, input, replyId, forceIrc)
    }

    fun fakeWhisperIfNecessary(input: String) {
        val split = input.split(" ")
        if (split.size > 2 && (split[0] == "/w" || split[0] == ".w") && split[1].isNotBlank()) {
            val message = input.substring(4 + split[1].length)
            val emotes = emoteRepository.parse3rdPartyEmotes(message, WhisperMessage.WHISPER_CHANNEL, withTwitch = true)
            val userState = userStateRepository.userState.value
            val name = authDataStore.userName ?: return
            val displayName = userState.displayName ?: return
            val fakeMessage =
                WhisperMessage(
                    userId = userState.userId,
                    name = name,
                    displayName = displayName,
                    color = userState.color?.parseColorOrNull(),
                    recipientId = null,
                    recipientColor = usersRepository.getCachedUserColor(split[1].toUserName()),
                    recipientName = split[1].toUserName(),
                    recipientDisplayName = split[1].toDisplayName(),
                    message = message,
                    rawEmotes = "",
                    rawBadges = "",
                    emotes = emotes,
                )
            val fakeItem = ChatItem(fakeMessage, isMentionTab = true)
            chatNotificationRepository.addWhisper(fakeItem)
            chatMessageRepository.broadcastWhisperIfEnabled(fakeItem)
        }
    }

    fun getLastMessage(): String? = chatEventProcessor.getLastMessageForDisplay(chatChannelProvider.activeChannel.value)

    fun getRecentMessages(): ImmutableList<String> = chatEventProcessor.getRecentMessagesForDisplay(chatChannelProvider.activeChannel.value)

    internal val lastReceivedWhisperUser get() = chatEventProcessor.lastReceivedWhisperUser

    internal val lastMessagesFlow get() = chatEventProcessor.lastMessagesFlow

    fun appendLastMessage(
        channel: UserName,
        message: String,
    ) = chatEventProcessor.setLastMessage(channel, message)

    suspend fun loadRecentMessagesIfEnabled(channel: UserName) {
        if (chatSettingsDataStore.settings.first().loadMessageHistory) {
            chatEventProcessor.loadRecentMessages(channel)
        }
    }

    fun makeAndPostCustomSystemMessage(
        msg: TextResource,
        channel: UserName,
    ) {
        chatMessageRepository.addSystemMessage(channel, SystemMessageType.Custom(msg))
    }

    fun makeAndPostCustomSystemMessage(
        msg: String,
        channel: UserName,
    ) {
        makeAndPostCustomSystemMessage(TextResource.Plain(msg), channel)
    }

    private fun partChannel(channel: UserName) {
        chatMessageRepository.removeMessageFlows(channel)
        chatConnector.removeConnectionState(channel)
        chatConnector.partChannel(channel)
        chatNotificationRepository.removeMentionFlows(channel)
        chatEventProcessor.removeLastMessages(channel)
        usersRepository.removeChannel(channel)
        userStateRepository.removeChannel(channel)
        pinnedMessageRepository.removeChannel(channel)
        channelRepository.removeRoomState(channel)
        emoteRepository.removeChannel(channel)
        messageProcessor.cleanupMessageThreadsInChannel(channel)
        messageRateTracker.removeChannel(channel)
    }
}
