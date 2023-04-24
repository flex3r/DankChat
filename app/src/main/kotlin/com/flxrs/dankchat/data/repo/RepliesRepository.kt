package com.flxrs.dankchat.data.repo

import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.twitch.message.HighlightType
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.MessageThread
import com.flxrs.dankchat.data.twitch.message.MessageThreadHeader
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.replaceIf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepliesRepository @Inject constructor(private val dankChatPreferenceStore: DankChatPreferenceStore) {

    private val threads = ConcurrentHashMap<String, MutableStateFlow<MessageThread>>()

    fun getThreadItemsFlow(rootMessageId: String): Flow<List<ChatItem>> = threads[rootMessageId]?.map { thread ->
        val root = ChatItem(thread.rootMessage.clearHighlight(), isInReplies = true)
        val replies = thread.replies.map { ChatItem(it.clearHighlight(), isInReplies = true) }
        listOf(root) + replies
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

    fun calculateMessageThread(message: Message, findMessageById: (channel: UserName, id: String) -> Message?): Message {
        if (message !is PrivMessage) {
            return message
        }

        if (ROOT_MESSAGE_ID_TAG !in message.tags) {
            return message
        }

        val strippedMessage = message.stripLeadingReplyMention()
        val rootId = message.tags.getValue(ROOT_MESSAGE_ID_TAG)
        val thread = when (val existing = threads[rootId]?.value) {
            null -> {
                val rootMessage = findMessageById(strippedMessage.channel, rootId) as? PrivMessage ?: return message
                MessageThread(
                    rootMessageId = rootId,
                    rootMessage = rootMessage,
                    replies = listOf(strippedMessage),
                    participated = strippedMessage.isParticipating()
                )
            }

            else -> {
                // Message already exists in thread
                if (existing.replies.any { it.id == strippedMessage.id }) {
                    return strippedMessage
                }

                existing.copy(replies = existing.replies + strippedMessage, participated = existing.updateParticipated(strippedMessage))
            }
        }
        when {
            !threads.containsKey(rootId) -> threads[rootId] = MutableStateFlow(thread)
            else                         -> threads.getValue(rootId).update { thread }
        }

        return strippedMessage
            .copy(thread = MessageThreadHeader(thread.rootMessageId, thread.rootMessage.name, thread.rootMessage.originalMessage, thread.participated))
    }

    fun updateMessageInThread(message: Message): Message {
        if (message !is PrivMessage) {
            return message
        }

        when (ROOT_MESSAGE_ID_TAG) {
            in message.tags -> {
                val rootId = message.tags.getValue(ROOT_MESSAGE_ID_TAG)
                val flow = threads[rootId] ?: return message
                flow.update { thread ->
                    thread.copy(replies = thread.replies.replaceIf(message) { it.id == message.id })
                }
            }

            else            -> {
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

    private fun PrivMessage.isParticipating(): Boolean {
        return name == dankChatPreferenceStore.userName || (ROOT_MESSAGE_LOGIN_TAG in tags && tags[ROOT_MESSAGE_LOGIN_TAG] == dankChatPreferenceStore.userName?.value)
    }

    private fun PrivMessage.stripLeadingReplyMention(): PrivMessage {
        val displayName = tags[ROOT_MESSAGE_DISPLAY_TAG] ?: return this

        if (message.startsWith("@$displayName ")) {
            val stripped = message.substringAfter("@$displayName ")
            return copy(
                message = stripped,
                replyMentionOffset = displayName.length + 2,
                emoteData = emoteData.copy(message = stripped)
            )
        }

        return this
    }

    private fun PrivMessage.clearHighlight(): PrivMessage {
        return copy(highlights = highlights.filter { it.type != HighlightType.Reply }.toSet())
    }

    companion object {
        private val TAG = RepliesRepository::class.java.simpleName

        private const val ROOT_MESSAGE_ID_TAG = "reply-parent-msg-id"
        private const val ROOT_MESSAGE_LOGIN_TAG = "reply-parent-user-login"
        private const val ROOT_MESSAGE_DISPLAY_TAG = "reply-parent-display-name"
    }
}
