package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.chat.ChatImportance
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.utils.TextResource

sealed interface SystemMessageType {
    data object Connected : SystemMessageType

    data object Disconnected : SystemMessageType

    data object Reconnected : SystemMessageType

    data object LoginExpired : SystemMessageType

    data object MessageHistoryIncomplete : SystemMessageType

    data object MessageHistoryIgnored : SystemMessageType

    data class MessageHistoryUnavailable(
        val status: String?,
    ) : SystemMessageType

    data class ChannelNonExistent(
        val channel: UserName,
    ) : SystemMessageType

    data class ChannelFFZEmotesFailed(
        val status: String,
    ) : SystemMessageType

    data class ChannelBTTVEmotesFailed(
        val status: String,
    ) : SystemMessageType

    data class ChannelSevenTVEmotesFailed(
        val status: String,
    ) : SystemMessageType

    data object ChannelFFZEmotesCachedFallback : SystemMessageType

    data object ChannelBTTVEmotesCachedFallback : SystemMessageType

    data object ChannelSevenTVEmotesCachedFallback : SystemMessageType

    data class ChannelSevenTVEmoteSetChanged(
        val actorName: DisplayName,
        val newEmoteSetName: String,
    ) : SystemMessageType

    data class ChannelSevenTVEmoteAdded(
        val actorName: DisplayName,
        val emoteName: String,
    ) : SystemMessageType

    data class ChannelSevenTVEmoteRenamed(
        val actorName: DisplayName,
        val oldEmoteName: String,
        val emoteName: String,
    ) : SystemMessageType

    data class ChannelSevenTVEmoteRemoved(
        val actorName: DisplayName,
        val emoteName: String,
    ) : SystemMessageType

    data class ChannelBTTVEmoteAdded(
        val emoteName: String,
    ) : SystemMessageType

    data class ChannelBTTVEmoteRenamed(
        val oldEmoteName: String,
        val emoteName: String,
    ) : SystemMessageType

    data class ChannelBTTVEmoteRemoved(
        val emoteName: String,
    ) : SystemMessageType

    data class Custom(
        val message: TextResource,
    ) : SystemMessageType

    data object SendNotLoggedIn : SystemMessageType

    data class SendChannelNotResolved(
        val channel: UserName,
    ) : SystemMessageType

    data object SendNotDelivered : SystemMessageType

    data class SendDropped(
        val reason: String,
        val code: String,
    ) : SystemMessageType

    data object SendMissingScopes : SystemMessageType

    data object SendNotAuthorized : SystemMessageType

    data object SendMessageTooLarge : SystemMessageType

    data object SendRateLimited : SystemMessageType

    data class SendFailed(
        val message: String?,
    ) : SystemMessageType

    data class StreamLive(
        val channel: UserName,
        val title: String?,
    ) : SystemMessageType

    data class StreamOffline(
        val channel: UserName,
    ) : SystemMessageType

    data class Debug(
        val message: String,
    ) : SystemMessageType

    data class AutomodActionFailed(
        val statusCode: Int?,
        val allow: Boolean,
    ) : SystemMessageType

    data class PinnedMessageActionFailed(
        val statusCode: Int?,
        val pin: Boolean,
    ) : SystemMessageType
}

fun SystemMessageType.toChatItem() = ChatItem(SystemMessage(this), importance = ChatImportance.SYSTEM)

fun SystemMessageType.toDebugChatItem() = ChatItem(SystemMessage(this), importance = ChatImportance.DELETED)
