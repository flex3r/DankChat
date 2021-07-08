package com.flxrs.dankchat.service.twitch.emote

sealed class EmoteType : Comparable<EmoteType> {
    abstract val title: String

    data class ChannelTwitchEmote(val channel: String) : EmoteType() {
        override val title = channel
    }

    data class ChannelTwitchBitEmote(val channel: String) : EmoteType() {
        override val title = channel
    }

    object ChannelFFZEmote : EmoteType() {
        override val title = "FrankerFaceZ"
    }

    object ChannelBTTVEmote : EmoteType() {
        override val title = "BetterTTV"
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

    override fun compareTo(other: EmoteType): Int = when {
        this is ChannelTwitchBitEmote  -> {
            when (other) {
                is ChannelTwitchBitEmote -> 0
                else                     -> 1
            }
        }
        other is ChannelTwitchBitEmote -> -1
        else                           -> 0
    }
}