package com.flxrs.dankchat.chat

sealed interface InputSheetState {
    object Closed : InputSheetState
    object Emotes : InputSheetState
    data class Replying(val replyMessageId: String) : InputSheetState
    val isOpen: Boolean get() = this != Closed
}
