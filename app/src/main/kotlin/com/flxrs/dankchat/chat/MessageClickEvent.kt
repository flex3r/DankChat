package com.flxrs.dankchat.chat

import com.flxrs.dankchat.data.UserName

sealed interface MessageClickEvent {
    data class Copy(val message: String) : MessageClickEvent
    data class Reply(val rootMessageId: String, val channel: UserName?) : MessageClickEvent
}
