package com.flxrs.dankchat.chat

import com.flxrs.dankchat.service.twitch.message.Message

data class ChatItem(val message: Message, val historic: Boolean = false, val isMentionTab: Boolean = false)

fun List<ChatItem>.toMentionTabItems(): List<ChatItem> = map { it.copy(isMentionTab = true) }