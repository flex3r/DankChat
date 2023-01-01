package com.flxrs.dankchat.data.twitch.emote

import com.flxrs.dankchat.data.UserName


sealed class EmoteType : Comparable<EmoteType> {
    abstract val title: String

    data class ChannelTwitchEmote(val channel: UserName) : EmoteType() {
        override val title = channel.value
    }

    data class ChannelTwitchBitEmote(val channel: UserName) : EmoteType() {
        override val title = channel.value
    }

    data class ChannelTwitchFollowerEmote(val channel: UserName) : EmoteType() {
        override val title = channel.value
    }

    object ChannelFFZEmote : EmoteType() {
        override val title = "FrankerFaceZ"
    }

    object ChannelBTTVEmote : EmoteType() {
        override val title = "BetterTTV"
    }

    object ChannelSevenTVEmote : EmoteType() {
        override val title = "SevenTV"
    }

    object GlobalTwitchEmote : EmoteType() {
        override val title = "Twitch"
    }

    object GlobalFFZEmote : EmoteType() {
        override val title = "FrankerFaceZ"
    }

    object GlobalBTTVEmote : EmoteType() {
        override val title = "BetterTTV"
    }

    object GlobalSevenTVEmote : EmoteType() {
        override val title = "SevenTV"
    }

    object RecentUsageEmote : EmoteType() {
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