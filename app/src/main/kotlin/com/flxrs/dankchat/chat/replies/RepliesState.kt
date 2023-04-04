package com.flxrs.dankchat.chat.replies

import com.flxrs.dankchat.chat.ChatItem

sealed interface RepliesState {
    object NotFound : RepliesState
    data class Found(val items: List<ChatItem>) : RepliesState
}
