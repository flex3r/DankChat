package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.chat.ChatImportance
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName

sealed interface SystemMessageType {
    data object Connected : SystemMessageType
    data object Disconnected : SystemMessageType
    data object Reconnected : SystemMessageType
    data object NoHistoryLoaded : SystemMessageType
    data object LoginExpired : SystemMessageType
    data object MessageHistoryIncomplete : SystemMessageType
    data object MessageHistoryIgnored : SystemMessageType
    data class MessageHistoryUnavailable(val status: String?) : SystemMessageType
    data class ChannelNonExistent(val channel: UserName) : SystemMessageType
    data class ChannelFFZEmotesFailed(val status: String) : SystemMessageType
    data class ChannelBTTVEmotesFailed(val status: String) : SystemMessageType
    data class ChannelSevenTVEmotesFailed(val status: String) : SystemMessageType
    data class ChannelSevenTVEmoteSetChanged(val actorName: DisplayName, val newEmoteSetName: String) : SystemMessageType
    data class ChannelSevenTVEmoteAdded(val actorName: DisplayName, val emoteName: String) : SystemMessageType
    data class ChannelSevenTVEmoteRenamed(val actorName: DisplayName, val oldEmoteName: String, val emoteName: String) : SystemMessageType
    data class ChannelSevenTVEmoteRemoved(val actorName: DisplayName, val emoteName: String) : SystemMessageType
    data class Custom(val message: String) : SystemMessageType
}

fun SystemMessageType.toChatItem() = ChatItem(SystemMessage(this), importance = ChatImportance.SYSTEM)
