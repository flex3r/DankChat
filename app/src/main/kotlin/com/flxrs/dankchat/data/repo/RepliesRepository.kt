package com.flxrs.dankchat.data.repo

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.repo.chat.UsersRepository
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.MessageThread
import com.flxrs.dankchat.data.twitch.message.MessageThreadHeader
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.utils.extensions.replaceIf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

@Single
class RepliesRepository(
    private val authDataStore: AuthDataStore,
    private val usersRepository: UsersRepository,
) {
    private val threads = ConcurrentHashMap<String, MutableStateFlow<MessageThread>>()

    fun getThreadItemsFlow(rootMessageId: String): Flow<List<ChatItem>> = threads[rootMessageId]?.map { thread ->
        val replies = thread.replies.map { ChatItem(it, isInReplies = true) }
        if (thread.rootMessage.message.isEmpty()) {
            replies
        } else {
            listOf(ChatItem(thread.rootMessage, isInReplies = true)) + replies
        }
    } ?: flowOf(emptyList())

    fun hasMessageThread(rootMessageId: String) = threads.containsKey(rootMessageId)

    fun cleanupMessageThread(message: Message) {
        if (message !is PrivMessage) {
            return
        }

        threads.remove(message.id)
    }

    fun cleanupMessageThreadsInChannel(channel: UserName) {
        threads.values
            .map { it.value }
            .filter { it.rootMessage.channel == channel }
            .forEach { threads.remove(it.rootMessageId) }
    }

    fun calculateMessageThread(
        message: Message,
        findMessageById: (channel: UserName, id: String) -> Message?,
    ): Message {
        if (message !is PrivMessage) {
            return message
        }

        if (THREAD_ROOT_MESSAGE_ID_TAG !in message.tags) {
            return message
        }

        val strippedMessage = message.stripLeadingReplyMention()
        val rootId = message.tags.getValue(THREAD_ROOT_MESSAGE_ID_TAG)
        val thread =
            when (val existing = threads[rootId]?.value) {
                null -> {
                    val rootMessage =
                        findMessageById(strippedMessage.channel, rootId) as? PrivMessage
                            ?: createPlaceholderRootMessage(strippedMessage, rootId)
                            ?: return message
                    MessageThread(
                        rootMessageId = rootId,
                        rootMessage = rootMessage,
                        replies = listOf(strippedMessage),
                        participated = strippedMessage.isParticipating(),
                    )
                }

                else -> {
                    // Message already exists in thread
                    if (existing.replies.any { it.id == strippedMessage.id }) {
                        val parentName = message.tags[PARENT_MESSAGE_LOGIN_TAG]?.toUserName() ?: existing.rootMessage.name
                        val parentBody = message.tags[PARENT_MESSAGE_BODY_TAG] ?: existing.rootMessage.originalMessage
                        return strippedMessage.copy(thread = MessageThreadHeader(rootId, parentName, parentBody, existing.participated))
                    }

                    existing.copy(replies = existing.replies + strippedMessage, participated = existing.updateParticipated(strippedMessage))
                }
            }
        val existing = threads.putIfAbsent(rootId, MutableStateFlow(thread))
        existing?.update { thread }

        val parentMessageId = message.tags[PARENT_MESSAGE_ID_TAG]
        val parentInThread =
            parentMessageId?.let { id ->
                if (id == thread.rootMessageId) {
                    thread.rootMessage
                } else {
                    thread.replies.find { it.id == id }
                }
            }

        val parentName: UserName
        val parentBody: String
        if (parentInThread != null) {
            parentName = parentInThread.name
            parentBody = parentInThread.originalMessage
        } else {
            parentName = message.tags[PARENT_MESSAGE_LOGIN_TAG]?.toUserName() ?: thread.rootMessage.name
            parentBody = message.tags[PARENT_MESSAGE_BODY_TAG] ?: thread.rootMessage.originalMessage
        }

        return strippedMessage
            .copy(thread = MessageThreadHeader(thread.rootMessageId, parentName, parentBody, thread.participated))
    }

    fun updateMessageInThread(message: Message): Message {
        if (message !is PrivMessage) {
            return message
        }

        when (THREAD_ROOT_MESSAGE_ID_TAG) {
            in message.tags -> {
                val rootId = message.tags.getValue(THREAD_ROOT_MESSAGE_ID_TAG)
                val flow = threads[rootId] ?: return message
                flow.update { thread ->
                    thread.copy(replies = thread.replies.replaceIf(message) { it.id == message.id })
                }
            }

            else -> {
                val flow = threads[message.id] ?: return message
                flow.update { thread -> thread.copy(rootMessage = message) }
            }
        }

        return message
    }

    private fun MessageThread.updateParticipated(message: PrivMessage): Boolean {
        if (participated) {
            return true
        }

        return message.isParticipating()
    }

    private fun PrivMessage.isParticipating(): Boolean = name == authDataStore.userName || (PARENT_MESSAGE_LOGIN_TAG in tags && tags[PARENT_MESSAGE_LOGIN_TAG] == authDataStore.userName?.value)

    private fun PrivMessage.stripLeadingReplyMention(): PrivMessage {
        val displayName = tags[PARENT_MESSAGE_DISPLAY_TAG] ?: return this

        if (message.startsWith("@$displayName ")) {
            val stripped = message.substringAfter("@$displayName ")
            return copy(
                message = stripped,
                originalMessage = stripped,
                replyMentionOffset = displayName.length + 2,
                emoteData = emoteData.copy(message = stripped),
            )
        }

        return this
    }

    private fun createPlaceholderRootMessage(
        reply: PrivMessage,
        rootId: String,
    ): PrivMessage? {
        val login = reply.tags[THREAD_ROOT_USER_LOGIN_TAG] ?: return null
        val name = login.toUserName()
        val displayName = (reply.tags[THREAD_ROOT_DISPLAY_TAG] ?: login).toDisplayName()
        val parentId = reply.tags[PARENT_MESSAGE_ID_TAG]
        val body = if (parentId == rootId) reply.tags[PARENT_MESSAGE_BODY_TAG].orEmpty() else ""

        return PrivMessage(
            timestamp = 0L,
            id = rootId,
            channel = reply.channel,
            sourceChannel = null,
            name = name,
            displayName = displayName,
            color = usersRepository.getCachedUserColor(name),
            message = body,
            originalMessage = body,
            tags = emptyMap(),
        )
    }

    companion object {
        private const val PARENT_MESSAGE_ID_TAG = "reply-parent-msg-id"
        private const val PARENT_MESSAGE_LOGIN_TAG = "reply-parent-user-login"
        private const val PARENT_MESSAGE_DISPLAY_TAG = "reply-parent-display-name"
        private const val PARENT_MESSAGE_BODY_TAG = "reply-parent-msg-body"

        private const val THREAD_ROOT_MESSAGE_ID_TAG = "reply-thread-parent-msg-id"
        private const val THREAD_ROOT_USER_LOGIN_TAG = "reply-thread-parent-user-login"
        private const val THREAD_ROOT_DISPLAY_TAG = "reply-thread-parent-display-name"
    }
}
