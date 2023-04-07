package com.flxrs.dankchat.data.twitch.emote

import android.os.Parcelable
import com.flxrs.dankchat.data.DisplayName
import kotlinx.parcelize.Parcelize

sealed interface ChatMessageEmoteType : Parcelable {

    @Parcelize
    object TwitchEmote : ChatMessageEmoteType

    @Parcelize
    data class ChannelFFZEmote(val creator: DisplayName?) : ChatMessageEmoteType

    @Parcelize
    data class GlobalFFZEmote(val creator: DisplayName?) : ChatMessageEmoteType

    @Parcelize
    data class ChannelBTTVEmote(val creator: DisplayName?, val isShared: Boolean) : ChatMessageEmoteType

    @Parcelize
    object GlobalBTTVEmote : ChatMessageEmoteType

    @Parcelize
    data class ChannelSevenTVEmote(val creator: DisplayName?, val baseName: String?) : ChatMessageEmoteType

    @Parcelize
    data class GlobalSevenTVEmote(val creator: DisplayName?, val baseName: String?) : ChatMessageEmoteType
}

fun EmoteType.toChatMessageEmoteType(): ChatMessageEmoteType? = when (this) {
    is EmoteType.ChannelBTTVEmote    -> ChatMessageEmoteType.ChannelBTTVEmote(creator, isShared)
    is EmoteType.ChannelFFZEmote     -> ChatMessageEmoteType.ChannelFFZEmote(creator)
    is EmoteType.ChannelSevenTVEmote -> ChatMessageEmoteType.ChannelSevenTVEmote(creator, baseName)
    EmoteType.GlobalBTTVEmote        -> ChatMessageEmoteType.GlobalBTTVEmote
    is EmoteType.GlobalFFZEmote      -> ChatMessageEmoteType.GlobalFFZEmote(creator)
    is EmoteType.GlobalSevenTVEmote  -> ChatMessageEmoteType.GlobalSevenTVEmote(creator, baseName)
    else                             -> null
}
