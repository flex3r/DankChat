package com.flxrs.dankchat.chat

sealed class ChatSheetState {


    object Closed : ChatSheetState()
    object Mention : ChatSheetState()
    object Whisper : ChatSheetState()
    data class Replies(val rootMessageId: String) : ChatSheetState()

    val isOpen: Boolean get() = this != Closed
    val isMentionTab: Boolean get() = this == Mention || this == Whisper
    val replyIdOrNull: String? get() = (this as? Replies)?.rootMessageId
}
