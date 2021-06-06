package com.flxrs.dankchat.service.twitch.badge

sealed class Badge {
    abstract val url: String
    abstract val type: BadgeType

    data class ChannelBadge(val name: String, override val url: String, override val type: BadgeType) : Badge()
    data class GlobalBadge(val name: String, override val url: String, override val type: BadgeType) : Badge()
    data class FFZModBadge(override val url: String, override val type: BadgeType) : Badge()
    data class FFZVipBadge(override val url: String, override val type: BadgeType) : Badge()
}