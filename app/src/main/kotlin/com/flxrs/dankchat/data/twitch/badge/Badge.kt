package com.flxrs.dankchat.data.twitch.badge

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class Badge : Parcelable {
    abstract val url: String
    abstract val type: BadgeType
    abstract val badgeTag: String?
    abstract val badgeInfo: String?
    abstract val title: String?

    data class ChannelBadge(override val title: String?, override val badgeTag: String?, override val badgeInfo: String?, override val url: String, override val type: BadgeType) : Badge()
    data class GlobalBadge(override val title: String?, override val badgeTag: String?, override val badgeInfo: String?, override val url: String, override val type: BadgeType) : Badge()
    data class FFZModBadge(override val title: String?, override val badgeTag: String?, override val badgeInfo: String?, override val url: String, override val type: BadgeType) : Badge()
    data class FFZVipBadge(override val title: String?, override val badgeTag: String?, override val badgeInfo: String?, override val url: String, override val type: BadgeType) : Badge()
    data class DankChatBadge(override val title: String?, override val badgeTag: String?, override val badgeInfo: String?, override val url: String, override val type: BadgeType) : Badge()
    data class SharedChatBadge(
        override val url: String,
        override val title: String?,
        override val badgeTag: String? = null,
        override val badgeInfo: String? = null,
        override val type: BadgeType = BadgeType.SharedChat,
    ) : Badge()
}
