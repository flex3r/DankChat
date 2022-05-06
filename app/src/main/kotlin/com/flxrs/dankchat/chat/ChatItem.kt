package com.flxrs.dankchat.chat

import com.flxrs.dankchat.data.twitch.message.Message

data class ChatItem(val message: Message, val isMentionTab: Boolean = false)

fun List<ChatItem>.toMentionTabItems(): List<ChatItem> = map { it.copy(isMentionTab = true) }