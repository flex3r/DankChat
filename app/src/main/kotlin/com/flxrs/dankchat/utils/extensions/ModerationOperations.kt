package com.flxrs.dankchat.utils.extensions

import com.flxrs.dankchat.data.chat.ChatImportance
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.twitch.message.ModerationMessage
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun MutableList<ChatItem>.replaceOrAddHistoryModerationMessage(moderationMessage: ModerationMessage) {
    if (!moderationMessage.canClearMessages) {
        return
    }

    if (deduplicateOrStack(moderationMessage)) {
        add(ChatItem(moderationMessage, importance = ChatImportance.SYSTEM))
    }
}

fun List<ChatItem>.replaceOrAddModerationMessage(
    moderationMessage: ModerationMessage,
    scrollBackLength: Int,
    onMessageRemoved: (ChatItem) -> Unit,
): List<ChatItem> = toMutableList().apply {
    if (!moderationMessage.canClearMessages) {
        addAndTrimInline(ChatItem(moderationMessage, importance = ChatImportance.SYSTEM), scrollBackLength, onMessageRemoved)
        return this
    }

    val addSystemMessage = deduplicateOrStack(moderationMessage)
    for (idx in indices) {
        val item = this[idx]
        when (moderationMessage.action) {
            ModerationMessage.Action.Clear -> {
                this[idx] =
                    when (item.message) {
                        is PrivMessage -> item.copy(tag = item.tag + 1, message = item.message.copy(timedOut = true), importance = ChatImportance.DELETED)
                        else -> item.copy(tag = item.tag + 1, importance = ChatImportance.DELETED)
                    }
            }

            is ModerationMessage.Action.Timeout,
            ModerationMessage.Action.Ban,
            is ModerationMessage.Action.SharedTimeout,
            ModerationMessage.Action.SharedBan,
            -> {
                item.message as? PrivMessage ?: continue
                if (moderationMessage.targetUser != item.message.name) {
                    continue
                }

                this[idx] = item.copy(tag = item.tag + 1, message = item.message.copy(timedOut = true), importance = ChatImportance.DELETED)
            }

            else -> {
                continue
            }
        }
    }

    if (addSystemMessage) {
        addAndTrimInline(ChatItem(moderationMessage, importance = ChatImportance.SYSTEM), scrollBackLength, onMessageRemoved)
    }
}

// Marks the affected messages without adding the moderation message, for lists that mix channels like the mentions
fun List<ChatItem>.markModeratedMessages(moderationMessage: ModerationMessage): List<ChatItem> {
    if (none { it.isAffectedBy(moderationMessage) }) {
        return this
    }

    return map { item ->
        val message = item.message
        when {
            message is PrivMessage && item.isAffectedBy(moderationMessage) -> item.copy(tag = item.tag + 1, message = message.copy(timedOut = true), importance = ChatImportance.DELETED)
            else -> item
        }
    }
}

private fun ChatItem.isAffectedBy(moderationMessage: ModerationMessage): Boolean {
    val message = message as? PrivMessage ?: return false
    if (message.timedOut || message.channel != moderationMessage.channel) {
        return false
    }

    return when (moderationMessage.action) {
        ModerationMessage.Action.Clear -> true

        is ModerationMessage.Action.Timeout,
        ModerationMessage.Action.Ban,
        is ModerationMessage.Action.SharedTimeout,
        ModerationMessage.Action.SharedBan,
        -> moderationMessage.targetUser == message.name

        ModerationMessage.Action.Delete,
        ModerationMessage.Action.SharedDelete,
        -> moderationMessage.targetMsgId == message.id

        else -> false
    }
}

fun List<ChatItem>.replaceWithTimeout(
    moderationMessage: ModerationMessage,
    scrollBackLength: Int,
    onMessageRemoved: (ChatItem) -> Unit,
): List<ChatItem> = toMutableList().apply {
    val targetMsgId = moderationMessage.targetMsgId ?: return@apply
    if (moderationMessage.fromEventSource) {
        val end = (lastIndex - 20).coerceAtLeast(0)
        for (idx in lastIndex downTo end) {
            val item = this[idx]
            val message = item.message as? ModerationMessage ?: continue
            if ((message.action is ModerationMessage.Action.Delete || message.action is ModerationMessage.Action.SharedDelete) && message.targetMsgId == targetMsgId && !message.fromEventSource) {
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

    addAndTrimInline(ChatItem(moderationMessage, importance = ChatImportance.SYSTEM), scrollBackLength, onMessageRemoved)
}

/**
 * Checks recent messages for an existing moderation message with the same target and action.
 * Handles three cases:
 * - **Event source dedup**: EventSub replaces IRC; IRC is ignored if EventSub exists
 * - **Stacking**: Repeated timeouts on the same user increment a stack counter
 * - **New message**: If no recent match, or the match is >5s old, the message should be added
 *
 * @return `true` if the message should be added as a new system message, `false` if it was merged/replaced
 */
private fun MutableList<ChatItem>.deduplicateOrStack(moderationMessage: ModerationMessage): Boolean {
    if (!moderationMessage.canStack) {
        return true
    }

    val end = (lastIndex - 20).coerceAtLeast(0)
    for (idx in lastIndex downTo end) {
        val item = this[idx]
        val existing = item.message as? ModerationMessage ?: continue
        if (existing.targetUser != moderationMessage.targetUser || !existing.action.isSameType(moderationMessage.action)) {
            continue
        }

        // Different moderation action on the same user, treat as new
        if ((moderationMessage.timestamp - existing.timestamp).milliseconds >= 5.seconds) {
            return true
        }

        // Same action within 5 seconds — deduplicate or stack
        when {
            // IRC arriving after EventSub → keep EventSub, discard IRC
            !moderationMessage.fromEventSource && existing.fromEventSource -> Unit

            // EventSub arriving after IRC → replace IRC with EventSub (has moderator info), preserve stack count
            moderationMessage.fromEventSource && !existing.fromEventSource -> {
                val merged = moderationMessage.copy(stackCount = maxOf(existing.stackCount, moderationMessage.stackCount))
                this[idx] = item.copy(tag = item.tag + 1, message = merged)
            }

            // Same source, stackable action → increment stack count
            moderationMessage.action is ModerationMessage.Action.Timeout || moderationMessage.action is ModerationMessage.Action.SharedTimeout -> {
                val stackedMessage = moderationMessage.copy(stackCount = existing.stackCount + 1)
                this[idx] = item.copy(tag = item.tag + 1, message = stackedMessage)
            }
        }
        return false
    }

    return true
}
