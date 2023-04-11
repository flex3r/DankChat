package com.flxrs.dankchat.chat.message

import com.flxrs.dankchat.data.UserName

sealed interface MessageSheetState {
    object Default : MessageSheetState
    object NotFound : MessageSheetState
    data class Found(
        val messageId: String,
        val name: UserName,
        val originalMessage: String,
        val canModerate: Boolean,
        val replyName: UserName,
        val replyMessageId: String,
        val hasReplyThread: Boolean,
        val canReply: Boolean
    ) : MessageSheetState
}
