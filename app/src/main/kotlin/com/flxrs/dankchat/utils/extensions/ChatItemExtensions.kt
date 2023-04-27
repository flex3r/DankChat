package com.flxrs.dankchat.utils.extensions

import com.flxrs.dankchat.chat.ChatImportance
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.data.twitch.message.ModerationMessage
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.SystemMessage
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.data.twitch.message.toChatItem
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun MutableList<ChatItem>.replaceOrAddHistoryModerationMessage(moderationMessage: ModerationMessage) {
    if (!moderationMessage.canClearMessages) {
        return
    }

    if (checkForStackedTimeouts(moderationMessage)) {
        add(ChatItem(moderationMessage, importance = ChatImportance.SYSTEM))
    }
}

fun List<ChatItem>.replaceOrAddModerationMessage(moderationMessage: ModerationMessage, scrollBackLength: Int, onMessageRemoved: (ChatItem) -> Unit): List<ChatItem> = toMutableList().apply {
    if (!moderationMessage.canClearMessages) {
        return addAndLimit(ChatItem(moderationMessage, importance = ChatImportance.SYSTEM), scrollBackLength, onMessageRemoved)
    }

    val addSystemMessage = checkForStackedTimeouts(moderationMessage)
    for (idx in indices) {
        val item = this[idx]
        when (moderationMessage.action) {
            ModerationMessage.Action.Clear -> {
                this[idx] = when (item.message) {
                    is PrivMessage -> item.copy(tag = item.tag + 1, message = item.message.copy(timedOut = true), importance = ChatImportance.DELETED)
                    else           -> item.copy(tag = item.tag + 1, importance = ChatImportance.DELETED)
                }
            }

            ModerationMessage.Action.Timeout,
            ModerationMessage.Action.Ban   -> {
                item.message as? PrivMessage ?: continue
                if (moderationMessage.targetUser != item.message.name) {
                    continue
                }

                this[idx] = item.copy(tag = item.tag + 1, message = item.message.copy(timedOut = true), importance = ChatImportance.DELETED)
            }

            else                           -> continue
        }
    }

    return when {
        addSystemMessage -> addAndLimit(ChatItem(moderationMessage, importance = ChatImportance.SYSTEM), scrollBackLength, onMessageRemoved)
        else             -> this
    }
}

fun List<ChatItem>.replaceWithTimeout(moderationMessage: ModerationMessage, scrollBackLength: Int, onMessageRemoved: (ChatItem) -> Unit): List<ChatItem> = toMutableList().apply {
    val targetMsgId = moderationMessage.targetMsgId ?: return@apply
    if (moderationMessage.fromPubsub) {
        val end = (lastIndex - 20).coerceAtLeast(0)
        for (idx in lastIndex downTo end) {
            val item = this[idx]
            val message = item.message as? ModerationMessage ?: continue
            if (message.action == ModerationMessage.Action.Delete && message.targetMsgId == targetMsgId && !message.fromPubsub) {
                this[idx] = item.copy(tag = item.tag + 1, message = moderationMessage)
                return@apply
            }
        }
    }

    for (idx in indices) {
        val item = this[idx]
        if (item.message is PrivMessage && item.message.id == targetMsgId) {
            this[idx] = item.copy(tag = item.tag + 1, message = item.message.copy(timedOut = true), importance = ChatImportance.DELETED)
            break
        }
    }
    return addAndLimit(ChatItem(moderationMessage, importance = ChatImportance.SYSTEM), scrollBackLength, onMessageRemoved)
}

fun List<ChatItem>.addAndLimit(item: ChatItem, scrollBackLength: Int, onMessageRemoved: (ChatItem) -> Unit): List<ChatItem> = toMutableList().apply {
    add(item)
    while (size > scrollBackLength) {
        onMessageRemoved(removeAt(index = 0))
    }
}

fun List<ChatItem>.addAndLimit(
    items: Collection<ChatItem>,
    scrollBackLength: Int,
    onMessageRemoved: (ChatItem) -> Unit,
    checkForDuplications: Boolean = false
): List<ChatItem> = when {
    checkForDuplications -> plus(items)
        .distinctBy { it.message.id }
        .sortedBy { it.message.timestamp }
        .also {
            it
                .take((it.size - scrollBackLength).coerceAtLeast(minimumValue = 0))
                .forEach(onMessageRemoved)
        }
        .takeLast(scrollBackLength)

    else                 -> toMutableList().apply {
        addAll(items)
        while (size > scrollBackLength) {
            onMessageRemoved(removeAt(index = 0))
        }
    }
}

fun List<ChatItem>.addSystemMessage(type: SystemMessageType, scrollBackLength: Int, onMessageRemoved: (ChatItem) -> Unit, onReconnect: () -> Unit = {}): List<ChatItem> {
    return when {
        type != SystemMessageType.Connected -> addAndLimit(type.toChatItem(), scrollBackLength, onMessageRemoved)
        else                                -> replaceLastSystemMessageIfNecessary(scrollBackLength, onMessageRemoved, onReconnect)
    }
}

fun List<ChatItem>.replaceLastSystemMessageIfNecessary(scrollBackLength: Int, onMessageRemoved: (ChatItem) -> Unit, onReconnect: () -> Unit): List<ChatItem> {
    val item = lastOrNull()
    val message = item?.message
    return when ((message as? SystemMessage)?.type) {
        SystemMessageType.Disconnected          -> {
            onReconnect()
            dropLast(1) + item.copy(message = SystemMessage(SystemMessageType.Reconnected))
        }

        is SystemMessageType.ChannelNonExistent -> dropLast(1) + SystemMessageType.Connected.toChatItem()
        else                                    -> addAndLimit(SystemMessageType.Connected.toChatItem(), scrollBackLength, onMessageRemoved)
    }
}

private fun MutableList<ChatItem>.checkForStackedTimeouts(moderationMessage: ModerationMessage): Boolean {
    if (moderationMessage.action == ModerationMessage.Action.Timeout || moderationMessage.action == ModerationMessage.Action.Ban) {
        val end = (lastIndex - 20).coerceAtLeast(0)
        for (idx in lastIndex downTo end) {
            val item = this[idx]
            val message = item.message as? ModerationMessage ?: continue
            if (message.targetUser != moderationMessage.targetUser || message.action != moderationMessage.action) {
                continue
            }

            if ((moderationMessage.timestamp - message.timestamp).milliseconds >= 5.seconds) {
                return true
            }

            when {
                !moderationMessage.fromPubsub && message.fromPubsub          -> Unit
                moderationMessage.fromPubsub && !message.fromPubsub          -> this[idx] = item.copy(tag = item.tag + 1, message = moderationMessage)
                moderationMessage.action == ModerationMessage.Action.Timeout -> {
                    val stackedMessage = moderationMessage.copy(stackCount = message.stackCount + 1)
                    this[idx] = item.copy(tag = item.tag + 1, message = stackedMessage)
                }
            }
            return false
        }
    }

    return true
}
