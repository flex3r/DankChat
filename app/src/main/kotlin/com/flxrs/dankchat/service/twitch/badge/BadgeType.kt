package com.flxrs.dankchat.service.twitch.badge

enum class BadgeType {
    Authority,
    Predictions,
    Channel,
    Subscriber,
    Vanity,
    DankChat;
    //FrankerFaceZ;

    companion object {
        fun parseFromBadgeId(id: String): BadgeType = when (id) {
            "staff", "admin", "global_admin" -> Authority
            "predictions" -> Predictions
            "moderator", "vip", "broadcaster" -> Channel
            "subscriber", "founder" -> Subscriber
            else -> Vanity
        }

        fun mapFromPreferenceSet(preferenceSet: Set<String>): Set<BadgeType> = preferenceSet.mapNotNull {
            values().find { badgeType -> badgeType.name.lowercase() == it }
        }.toSet()
    }
}