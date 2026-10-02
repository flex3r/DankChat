package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.recentmessages.RecentMessagesApiClient
import com.flxrs.dankchat.data.api.recentmessages.RecentMessagesApiException
import com.flxrs.dankchat.data.api.recentmessages.RecentMessagesError
import com.flxrs.dankchat.data.api.recentmessages.dto.RecentMessagesDto
import com.flxrs.dankchat.data.chat.ChatImportance
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.chat.toMentionTabItems
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.ModerationMessage
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.data.twitch.message.UserNoticeMessage
import com.flxrs.dankchat.data.twitch.message.hasMention
import com.flxrs.dankchat.data.twitch.message.toChatItem
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.extensions.addAndLimit
import com.flxrs.dankchat.utils.extensions.replaceOrAddHistoryModerationMessage
import com.flxrs.dankchat.utils.extensions.runCatchingCancellable
import com.flxrs.dankchat.utils.extensions.withoutShownModerationMessages
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentSet
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger("RecentMessagesHandler")

@Single
class RecentMessagesHandler(
    private val recentMessagesApiClient: RecentMessagesApiClient,
    private val messageProcessor: MessageProcessor,
    private val chatMessageRepository: ChatMessageRepository,
    private val usersRepository: UsersRepository,
    private val dispatchersProvider: DispatchersProvider,
) {
    private val loadedChannels = ConcurrentSet<UserName>()

    data class Result(
        val mentionItems: List<ChatItem>,
        val userSuggestions: List<Pair<UserName, DisplayName>>,
    )

    @Suppress("LoopWithTooManyJumpStatements")
    suspend fun load(
        channel: UserName,
        isReconnect: Boolean = false,
    ): Result = withContext(dispatchersProvider.io) {
        if (!isReconnect && channel in loadedChannels) {
            return@withContext Result(emptyList(), emptyList())
        }

        val limit = if (isReconnect) RECENT_MESSAGES_LIMIT_AFTER_RECONNECT else null
        val result =
            recentMessagesApiClient.getRecentMessages(channel, limit).getOrElse { throwable ->
                if (!isReconnect) {
                    handleFailure(throwable, channel)
                }
                return@withContext Result(emptyList(), emptyList())
            }

        loadedChannels += channel
        val recentMessages = result.messages.orEmpty()
        val items = mutableListOf<ChatItem>()
        val messageIndex = HashMap<String, Message>(recentMessages.size)
        val userSuggestions = mutableListOf<Pair<UserName, DisplayName>>()

        measureTimeMillis {
            for (recentMessage in recentMessages) {
                val parsedIrc = IrcMessage.parse(recentMessage)
                val isDeleted = parsedIrc.tags["rm-deleted"] == "1"
                if (messageProcessor.isUserBlocked(parsedIrc.tags["user-id"]?.toUserId())) {
                    continue
                }

                when (parsedIrc.command) {
                    "CLEARCHAT" -> {
                        val parsed =
                            runCatching {
                                ModerationMessage.parseClearChat(parsedIrc)
                            }.getOrNull() ?: continue

                        items.replaceOrAddHistoryModerationMessage(parsed)
                    }

                    "CLEARMSG" -> {
                        val parsed =
                            runCatching {
                                ModerationMessage.parseClearMessage(parsedIrc)
                            }.getOrNull() ?: continue

                        items += ChatItem(parsed, importance = ChatImportance.SYSTEM)
                    }

                    else -> {
                        val message =
                            runCatchingCancellable {
                                messageProcessor.processIrcMessage(parsedIrc) { _, id -> messageIndex[id] }
                            }.getOrNull() ?: continue

                        messageIndex[message.id] = message
                        if (message is PrivMessage) {
                            val userForSuggestion = message.name.valueOrDisplayName(message.displayName).toDisplayName()
                            userSuggestions += message.name.lowercase() to userForSuggestion
                            val color = message.color
                            if (color != null) {
                                usersRepository.cacheUserColor(message.name, color)
                            }
                        }

                        val importance =
                            when {
                                isDeleted -> ChatImportance.DELETED
                                isReconnect -> ChatImportance.SYSTEM
                                else -> ChatImportance.REGULAR
                            }
                        if (message is UserNoticeMessage && message.childMessage != null) {
                            items += ChatItem(message.childMessage, importance = importance)
                        }
                        items += ChatItem(message, importance = importance)
                    }
                }
            }
        }.let { logger.info { "Parsing message history for #$channel took $it ms" } }

        val messagesFlow = chatMessageRepository.getMessagesFlow(channel)
        messagesFlow?.update { current ->
            val withIncompleteWarning =
                when {
                    !isReconnect && recentMessages.isNotEmpty() && result.errorCode == RecentMessagesDto.ERROR_CHANNEL_NOT_JOINED -> {
                        current + SystemMessageType.MessageHistoryIncomplete.toChatItem()
                    }

                    else -> {
                        current
                    }
                }

            withIncompleteWarning.addAndLimit(
                items.withoutShownModerationMessages(current),
                chatMessageRepository.scrollBackLength,
                messageProcessor::onMessageRemoved,
                checkForDuplications = true,
            )
        }

        val mentionItems = items.filter { it.message.highlights.hasMention() }.toMentionTabItems()
        Result(mentionItems, userSuggestions)
    }

    private fun handleFailure(
        throwable: Throwable,
        channel: UserName,
    ) {
        val type =
            when (throwable) {
                !is RecentMessagesApiException -> {
                    chatMessageRepository.addLoadingFailure(ChatLoadingFailure(ChatLoadingStep.RecentMessages(channel), throwable))
                    SystemMessageType.MessageHistoryUnavailable(status = null)
                }

                else -> {
                    when (throwable.error) {
                        RecentMessagesError.ChannelNotJoined -> {
                            return
                        }

                        RecentMessagesError.ChannelIgnored -> {
                            SystemMessageType.MessageHistoryIgnored
                        }

                        else -> {
                            chatMessageRepository.addLoadingFailure(ChatLoadingFailure(ChatLoadingStep.RecentMessages(channel), throwable))
                            SystemMessageType.MessageHistoryUnavailable(status = throwable.status.value.toString())
                        }
                    }
                }
            }
        chatMessageRepository.addSystemMessageToChannels(type, setOf(channel))
    }

    companion object {
        private const val RECENT_MESSAGES_LIMIT_AFTER_RECONNECT = 100
    }
}
