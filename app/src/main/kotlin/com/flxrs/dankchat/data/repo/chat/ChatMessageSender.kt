package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.HelixApiException
import com.flxrs.dankchat.data.api.helix.HelixError
import com.flxrs.dankchat.data.api.helix.dto.SendChatMessageRequestDto
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.preferences.developer.ChatSendProtocol
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
import com.flxrs.dankchat.utils.extensions.INVISIBLE_CHAR
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.annotation.Single

private val logger = KotlinLogging.logger("ChatMessageSender")

@Single
class ChatMessageSender(
    private val chatConnector: ChatConnector,
    private val helixApiClient: HelixApiClient,
    private val channelRepository: ChannelRepository,
    private val authDataStore: AuthDataStore,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatEventProcessor: ChatEventProcessor,
    private val developerSettingsDataStore: DeveloperSettingsDataStore,
) {
    suspend fun send(
        channel: UserName,
        message: String,
        replyId: String? = null,
        forceIrc: Boolean = false,
    ) {
        if (message.isBlank()) {
            return
        }

        val protocol = developerSettingsDataStore.current().chatSendProtocol
        when {
            forceIrc || protocol == ChatSendProtocol.IRC -> sendViaIrc(channel, message, replyId)
            else -> sendViaHelix(channel, message, replyId)
        }
    }

    private suspend fun sendViaIrc(
        channel: UserName,
        message: String,
        replyId: String?,
    ) {
        val trimmedMessage = message.trimEnd()
        val replyIdOrBlank = replyId?.let { "@reply-parent-msg-id=$it " }.orEmpty()
        val messageWithSuffix = bypassDuplicateIfNeeded(channel, trimmedMessage)

        chatEventProcessor.setLastMessage(channel, sent = messageWithSuffix, typed = trimmedMessage)
        chatConnector.sendRaw("${replyIdOrBlank}PRIVMSG #$channel :$messageWithSuffix")
        chatMessageRepository.incrementSentMessageCount(ChatSendProtocol.IRC)
    }

    private suspend fun sendViaHelix(
        channel: UserName,
        message: String,
        replyId: String?,
    ) {
        val trimmedMessage = message.trimEnd()
        val senderId =
            authDataStore.userIdString ?: run {
                postError(channel, SystemMessageType.SendNotLoggedIn)
                return
            }
        val broadcasterId =
            channelRepository.getChannel(channel)?.id ?: run {
                postError(channel, SystemMessageType.SendChannelNotResolved(channel))
                return
            }

        val messageWithSuffix = bypassDuplicateIfNeeded(channel, trimmedMessage)
        val request =
            SendChatMessageRequestDto(
                broadcasterId = broadcasterId,
                senderId = senderId,
                message = messageWithSuffix,
                replyParentMessageId = replyId,
            )

        helixApiClient.postChatMessage(request).fold(
            onSuccess = { response ->
                when {
                    response.isSent -> {
                        chatEventProcessor.setLastMessage(channel, sent = messageWithSuffix, typed = trimmedMessage)
                        chatMessageRepository.incrementSentMessageCount(ChatSendProtocol.Helix)
                    }

                    else -> {
                        val type =
                            when (val reason = response.dropReason) {
                                null -> SystemMessageType.SendNotDelivered
                                else -> SystemMessageType.SendDropped(reason.message, reason.code)
                            }
                        postError(channel, type)
                    }
                }
            },
            onFailure = { throwable ->
                logger.error(throwable) { "Helix send failed" }
                postError(channel, throwable.toSendErrorType())
            },
        )
    }

    private fun bypassDuplicateIfNeeded(
        channel: UserName,
        trimmedMessage: String,
    ): String {
        val previousSentMessage = chatEventProcessor.getLastMessage(channel)
        return when (previousSentMessage) {
            trimmedMessage -> applyAntiDuplicate(trimmedMessage)
            else -> trimmedMessage
        }
    }

    private fun postError(
        channel: UserName,
        type: SystemMessageType,
    ) {
        chatMessageRepository.addSystemMessage(channel, type)
        chatMessageRepository.incrementSendFailureCount()
    }

    private fun applyAntiDuplicate(message: String): String {
        val startIndex =
            when {
                message.startsWith('/') || message.startsWith('.') -> message.indexOf(' ').let { if (it == -1) 0 else it + 1 }
                else -> 0
            }
        val spaceIndex = message.indexOf(' ', startIndex)

        return when {
            spaceIndex != -1 -> message.replaceRange(spaceIndex, spaceIndex + 1, "  ")
            else -> "$message $INVISIBLE_CHAR"
        }
    }

    private fun Throwable.toSendErrorType(): SystemMessageType = when (this) {
        is HelixApiException -> {
            when (error) {
                HelixError.NotLoggedIn -> SystemMessageType.SendNotLoggedIn
                HelixError.MissingScopes -> SystemMessageType.SendMissingScopes
                HelixError.UserNotAuthorized -> SystemMessageType.SendNotAuthorized
                HelixError.MessageTooLarge -> SystemMessageType.SendMessageTooLarge
                HelixError.ChatMessageRateLimited -> SystemMessageType.SendRateLimited
                else -> SystemMessageType.SendFailed(message)
            }
        }

        else -> {
            SystemMessageType.SendFailed(message)
        }
    }
}
