package com.flxrs.dankchat.ui.chat.message

import com.flxrs.dankchat.data.UserName

data class MessageOptionsParams(
    val messageId: String,
    val channel: UserName?,
    val fullMessage: String,
    val canModerate: Boolean,
    val canCopy: Boolean = true,
    val canJump: Boolean = false,
    val startWithBan: Boolean = false,
    val replyAction: MessageReplyAction? = null,
)

sealed interface MessageReplyAction {
    data object Channel : MessageReplyAction

    data class Whisper(
        val target: UserName,
    ) : MessageReplyAction
}
