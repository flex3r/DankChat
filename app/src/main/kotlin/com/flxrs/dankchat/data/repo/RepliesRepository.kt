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
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

@Single
class RepliesRepository(
    private val authDataStore: AuthDataStore,
    private val usersRepository: UsersRepository,
) {
    private val threads = ConcurrentHashMap<String, MutableStateFlow<MessageThread>>()

    // Ids of messages that left the buffer while their thread was open in a replies sheet, by thread root id
    private val removedWhileObserved = ConcurrentHashMap<String, Set<String>>()

    fun getThreadItemsFlow(rootMessageId: String): Flow<List<ChatItem>> {
        val threadFlow = threads[rootMessageId] ?: return flowOf(emptyList())
        return threadFlow
            .map { thread ->
                val replies = thread.replies.map { ChatItem(it, isInReplies = true) }
                if (thread.rootMessage.message.isEmpty()) {
                    replies
                } else {
                    listOf(ChatItem(thread.rootMessage, isInReplies = true)) + replies
                }
            }.onCompletion { cleanupClosedThread(rootMessageId) }
    }

    fun hasMessageThread(rootMessageId: String) = threads.containsKey(rootMessageId)

    fun cleanupMessageThread(message: Message) {
        if (message !is PrivMessage) {
            return
        }

        val rootId = message.tags[THREAD_ROOT_MESSAGE_ID_TAG] ?: message.id
        threads.computeIfPresent(rootId) { _, thread ->
            when {
                // An open replies sheet keeps its content and still receives new replies, its cleanup runs once it closes
                thread.subscriptionCount.value > 0 -> {
                    removedWhileObserved.merge(rootId, setOf(message.id)) { ids, added -> ids + added }
                    thread
                }

                else -> {
                    thread.withoutRemovedMessages(setOf(message.id))
                }
            }
        }
    }

    fun cleanupMessageThreadsInChannel(channel: UserName) {
        threads.values
            .map { it.value }
            .filter { it.rootMessage.channel == channel }
            .forEach {
                threads.remove(it.rootMessageId)
                removedWhileObserved.remove(it.rootMessageId)
            }
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
        val existingFlow = threads[rootId]
        val existing = existingFlow?.value
        // Message already exists in thread
        if (existing != null && existing.replies.any { it.id == strippedMessage.id }) {
            val parentName = message.tags[PARENT_MESSAGE_LOGIN_TAG]?.toUserName() ?: existing.rootMessage.name
            val parentBody = message.tags[PARENT_MESSAGE_BODY_TAG] ?: existing.rootMessage.originalMessage
            return strippedMessage.copy(thread = MessageThreadHeader(rootId, parentName, parentBody, existing.participated))
        }

        // History loads and live messages write to the same thread concurrently, so every write builds on the current value
        val thread =
            when (existingFlow) {
                null -> {
                    val rootMessage =
                        findMessageById(strippedMessage.channel, rootId) as? PrivMessage
                            ?: createPlaceholderRootMessage(strippedMessage, rootId)
                            ?: return message
                    val created =
                        MessageThread(
                            rootMessageId = rootId,
                            rootMessage = rootMessage,
                            replies = listOf(strippedMessage),
                            participated = strippedMessage.isParticipating(),
                        )
                    threads.putIfAbsent(rootId, MutableStateFlow(created))?.addReply(strippedMessage) ?: created
                }

                else -> {
                    existingFlow.addReply(strippedMessage)
                }
            }

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

    private fun cleanupClosedThread(rootId: String) {
        threads.computeIfPresent(rootId) { _, thread ->
            when {
                thread.subscriptionCount.value > 0 -> {
                    thread
                }

                else -> {
                    when (val removedIds = removedWhileObserved.remove(rootId)) {
                        null -> thread
                        else -> thread.withoutRemovedMessages(removedIds)
                    }
                }
            }
        }
    }

    // A thread is dropped once its root or its last reply left the buffer
    private fun MutableStateFlow<MessageThread>.withoutRemovedMessages(removedIds: Set<String>): MutableStateFlow<MessageThread>? {
        if (value.rootMessageId in removedIds) {
            return null
        }

        val remaining = updateAndGet { thread -> thread.copy(replies = thread.replies.filterNot { it.id in removedIds }) }
        return takeIf { remaining.replies.isNotEmpty() }
    }

    private fun MutableStateFlow<MessageThread>.addReply(reply: PrivMessage): MessageThread = updateAndGet { thread ->
        when {
            thread.replies.any { it.id == reply.id } -> thread
            else -> thread.copy(replies = thread.replies + reply, participated = thread.updateParticipated(reply))
        }
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
