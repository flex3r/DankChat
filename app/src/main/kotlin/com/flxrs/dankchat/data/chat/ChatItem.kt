package com.flxrs.dankchat.data.chat

import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.PrivMessage

data class ChatItem(
    val message: Message,
    val tag: Int = 0,
    val isMentionTab: Boolean = false,
    val importance: ChatImportance = ChatImportance.REGULAR,
    val isInReplies: Boolean = false,
) {
    val mappingCacheKey: String = "${message.id}-$tag"
}

fun List<ChatItem>.toMentionTabItems(): List<ChatItem> = map { it.copy(isMentionTab = true) }

// A message can be replied to while it is still in the channel buffer and was neither deleted nor timed out
fun List<ChatItem>.canReplyTo(messageId: String): Boolean = any { item ->
    val message = item.message as? PrivMessage
    message?.id == messageId && !message.timedOut
}
