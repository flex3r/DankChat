package com.flxrs.dankchat.chat

import com.flxrs.dankchat.data.twitch.message.Message

data class ChatItem(val message: Message, val tag: Int = 0, val isMentionTab: Boolean = false, val importance: ChatImportance = ChatImportance.REGULAR, val isInReplies: Boolean = false)

fun List<ChatItem>.toMentionTabItems(): List<ChatItem> = map { it.copy(isMentionTab = true) }
