package com.flxrs.dankchat.utils.extensions

import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.twitch.message.SystemMessage
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.data.twitch.message.toChatItem

fun List<ChatItem>.addSystemMessage(
    type: SystemMessageType,
    scrollBackLength: Int,
    onMessageRemoved: (ChatItem) -> Unit,
    onReconnect: () -> Unit = {},
): List<ChatItem> = when {
    type != SystemMessageType.Connected -> addAndLimit(type.toChatItem(), scrollBackLength, onMessageRemoved)
    else -> replaceLastSystemMessageIfNecessary(scrollBackLength, onMessageRemoved, onReconnect)
}

private fun List<ChatItem>.replaceLastSystemMessageIfNecessary(
    scrollBackLength: Int,
    onMessageRemoved: (ChatItem) -> Unit,
    onReconnect: () -> Unit,
): List<ChatItem> {
    // Scan backwards for a Disconnected message that may be separated from Connected by debug messages
    val disconnectedIdx = indexOfLast { (it.message as? SystemMessage)?.type == SystemMessageType.Disconnected }
    if (disconnectedIdx >= 0) {
        onReconnect()
        return toMutableList().apply {
            this[disconnectedIdx] = this[disconnectedIdx].copy(message = SystemMessage(SystemMessageType.Reconnected))
        }
    }

    val lastType = (lastOrNull()?.message as? SystemMessage)?.type
    if (lastType is SystemMessageType.ChannelNonExistent || lastType is SystemMessageType.ChannelJoinFailed) {
        return dropLast(1) + SystemMessageType.Connected.toChatItem()
    }

    return addAndLimit(SystemMessageType.Connected.toChatItem(), scrollBackLength, onMessageRemoved)
}
