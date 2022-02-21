package com.flxrs.dankchat.utils.extensions

import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.data.twitch.message.ClearChatMessage
import com.flxrs.dankchat.data.twitch.message.TwitchMessage
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun List<ChatItem>.replaceWithTimeOuts(clearChatMessage: ClearChatMessage, scrollBackLength: Int): MutableList<ChatItem> = toMutableList().apply {
    var addClearChat = true
    val end = (lastIndex - 20).coerceAtLeast(0)
    for (idx in lastIndex downTo end) {
        val item = this[idx]
        val message = item.message
        if (message !is ClearChatMessage || message.targetUser != clearChatMessage.targetUser) {
            continue
        }

        if ((clearChatMessage.timestamp - message.timestamp).milliseconds < 5.seconds) {
            val stackedMessage = message.copy(count = message.count + 1, timestamp = clearChatMessage.timestamp)
            this[idx] = item.copy(message = stackedMessage)
            addClearChat = false
            break
        }
    }

    for (idx in indices) {
        val item = this[idx]
        if (item.message is TwitchMessage && !item.message.isNotify
            && (clearChatMessage.isFullChatClear || clearChatMessage.targetUser.equals(item.message.name, true))
        ) {
            this[idx] = item.copy(message = item.message.copy(timedOut = true))
        }
    }

    return when {
        addClearChat -> addAndLimit(ChatItem(clearChatMessage), scrollBackLength)
        else         -> this
    }
}

fun List<ChatItem>.replaceWithTimeOut(id: String): MutableList<ChatItem> = toMutableList().apply {
    for (idx in indices) {
        val item = this[idx]
        if (item.message is TwitchMessage && item.message.id == id) {
            this[idx] = item.copy(message = item.message.copy(timedOut = true))
            break
        }
    }
}

fun List<ChatItem>.addAndLimit(item: ChatItem, scrollBackLength: Int): MutableList<ChatItem> = toMutableList().apply {
    add(item)
    while (size > scrollBackLength) {
        removeAt(0)
    }
}

fun List<ChatItem>.addAndLimit(
    collection: Collection<ChatItem>,
    scrollBackLength: Int,
    checkForDuplications: Boolean = false
): MutableList<ChatItem> = takeLast(scrollBackLength).toMutableList().apply {
    for (item in collection) {
        if (!checkForDuplications || !any { it.message.id == item.message.id }) {
            add(item)
        }

        if (size > scrollBackLength) {
            removeAt(0)
        }
    }
}
