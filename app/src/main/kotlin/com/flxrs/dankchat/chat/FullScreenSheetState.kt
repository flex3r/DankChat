package com.flxrs.dankchat.chat

sealed interface FullScreenSheetState {
    object Closed : FullScreenSheetState
    object Mention : FullScreenSheetState
    object Whisper : FullScreenSheetState
    data class Replies(val replyMessageId: String) : FullScreenSheetState

    val isOpen: Boolean get() = this != Closed
    val isMentionSheet: Boolean get() = this == Mention || this == Whisper
    val replyIdOrNull: String? get() = (this as? Replies)?.replyMessageId
}
