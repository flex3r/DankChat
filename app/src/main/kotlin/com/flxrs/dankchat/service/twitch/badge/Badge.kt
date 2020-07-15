package com.flxrs.dankchat.service.twitch.badge

sealed class Badge {
    abstract val url: String

    data class ChannelBadge(val name: String, override val url: String) : Badge()
    data class GlobalBadge(val name: String, override val url: String) : Badge()
    data class FFZModBadge(override val url: String) : Badge()
}