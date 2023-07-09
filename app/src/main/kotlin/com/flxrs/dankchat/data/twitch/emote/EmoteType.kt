package com.flxrs.dankchat.data.twitch.emote

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName

sealed interface EmoteType : Comparable<EmoteType> {
    val title: String

    data class ChannelTwitchEmote(val channel: UserName) : EmoteType {
        override val title = channel.value
    }

    data class ChannelTwitchBitEmote(val channel: UserName) : EmoteType {
        override val title = channel.value
    }

    data class ChannelTwitchFollowerEmote(val channel: UserName) : EmoteType {
        override val title = channel.value
    }

    data class ChannelFFZEmote(val creator: DisplayName?) : EmoteType {
        override val title = "FrankerFaceZ"
    }

    data class ChannelBTTVEmote(val creator: DisplayName, val isShared: Boolean) : EmoteType {
        override val title = "BetterTTV"
    }

    data class ChannelSevenTVEmote(val creator: DisplayName?, val baseName: String?) : EmoteType {
        override val title = "SevenTV"
    }

    data object GlobalTwitchEmote : EmoteType {
        override val title = "Twitch"
    }

    data class GlobalFFZEmote(val creator: DisplayName?) : EmoteType {
        override val title = "FrankerFaceZ"
    }

    data object GlobalBTTVEmote : EmoteType {
        override val title = "BetterTTV"
    }

    data class GlobalSevenTVEmote(val creator: DisplayName?, val baseName: String?) : EmoteType {
        override val title = "SevenTV"
    }

    data object RecentUsageEmote : EmoteType {
        override val title = ""
    }

    override fun compareTo(other: EmoteType): Int = when {
        this is ChannelTwitchBitEmote || this is ChannelTwitchFollowerEmote   -> {
            when (other) {
                is ChannelTwitchBitEmote,
                is ChannelTwitchFollowerEmote -> 0

                else                          -> 1
            }
        }

        other is ChannelTwitchBitEmote || other is ChannelTwitchFollowerEmote -> -1
        else                                                                  -> 0
    }
}
