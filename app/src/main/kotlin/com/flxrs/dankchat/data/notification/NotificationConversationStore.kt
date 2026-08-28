package com.flxrs.dankchat.data.notification

import com.flxrs.dankchat.data.UserName

internal class NotificationConversationStore(
    private val historyLimit: Int,
) {
    private val lock = Any()
    private val channelNotifications = mutableMapOf<UserName, LinkedHashMap<String, NotificationData>>()
    private val whisperNotifications = mutableMapOf<UserName, WhisperNotificationState>()

    fun addChannelMessage(
        channel: UserName,
        data: NotificationData,
    ) {
        synchronized(lock) {
            val messages = channelNotifications.getOrPut(channel) { linkedMapOf() }
            messages[data.id] = data
            while (messages.size > historyLimit) {
                messages.remove(messages.keys.first())
            }
        }
    }

    fun channelMessages(channel: UserName): List<NotificationData> = synchronized(lock) {
        channelNotifications[channel]
            ?.values
            ?.toList()
            .orEmpty()
    }

    fun clearChannel(channel: UserName) {
        synchronized(lock) {
            channelNotifications.remove(channel)
        }
    }

    fun channelKeys(): List<UserName> = synchronized(lock) { channelNotifications.keys.toList() }

    fun addWhisperMessage(
        key: UserName,
        target: NotificationData,
        message: ConversationMessage,
    ) {
        synchronized(lock) {
            val state = whisperNotifications.getOrPut(key) { WhisperNotificationState(target) }
            state.target = target
            state.messages.add(message)
            state.messages.trimHistory()
        }
    }

    fun whisper(key: UserName): WhisperNotificationSnapshot? = synchronized(lock) {
        whisperNotifications[key]?.let { WhisperNotificationSnapshot(it.target, it.messages.toList()) }
    }

    fun clearWhisper(key: UserName) {
        synchronized(lock) {
            whisperNotifications.remove(key)
        }
    }

    fun whisperKeys(): List<UserName> = synchronized(lock) { whisperNotifications.keys.toList() }

    private fun MutableList<ConversationMessage>.trimHistory() {
        while (size > historyLimit) {
            removeAt(0)
        }
    }

    private data class WhisperNotificationState(
        var target: NotificationData,
        val messages: MutableList<ConversationMessage> = mutableListOf(),
    )
}

internal data class WhisperNotificationSnapshot(
    val target: NotificationData,
    val messages: List<ConversationMessage>,
)

internal data class ConversationMessage(
    val text: String,
    val timestamp: Long,
    val sender: ConversationSender,
)

internal data class ConversationSender(
    val name: String,
    val key: String,
)
