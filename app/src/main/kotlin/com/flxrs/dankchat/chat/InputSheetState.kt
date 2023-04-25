package com.flxrs.dankchat.chat

import com.flxrs.dankchat.data.UserName

sealed interface InputSheetState {
    object Closed : InputSheetState
    data class Emotes(val previousReply: Replying?) : InputSheetState
    data class Replying(val replyMessageId: String, val replyName: UserName) : InputSheetState

    val isOpen: Boolean get() = this != Closed
    val replyIdOrNull: String? get() = (this as? Replying)?.replyMessageId
}
