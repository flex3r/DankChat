package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.data.UserName

sealed class SystemMessageType {
    object Connected : SystemMessageType()
    object Disconnected : SystemMessageType()
    object Reconnected : SystemMessageType()
    object NoHistoryLoaded : SystemMessageType()
    object LoginExpired : SystemMessageType()
    object MessageHistoryIncomplete : SystemMessageType()
    object MessageHistoryIgnored : SystemMessageType()
    data class MessageHistoryUnavailable(val status: String?) : SystemMessageType()
    data class ChannelNonExistent(val channel: UserName) : SystemMessageType()
    data class ChannelFFZEmotesFailed(val status: String) : SystemMessageType()
    data class ChannelBTTVEmotesFailed(val status: String) : SystemMessageType()
    data class ChannelSevenTVEmotesFailed(val status: String) : SystemMessageType()
    data class Custom(val message: String) : SystemMessageType()
}

fun SystemMessageType.toChatItem() = ChatItem(SystemMessage(this))