package com.flxrs.dankchat.service.twitch.emote

sealed class EmoteType {
    abstract val title: String

    data class ChannelTwitchEmote(val channel: String) : EmoteType() {
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
}