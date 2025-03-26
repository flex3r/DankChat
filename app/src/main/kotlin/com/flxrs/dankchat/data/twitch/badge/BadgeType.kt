package com.flxrs.dankchat.data.twitch.badge

enum class BadgeType {
    Authority,
    Predictions,
    Channel,
    Subscriber,
    Vanity,
    DankChat,
    SharedChat;
    //FrankerFaceZ;

    companion object {
        fun parseFromBadgeId(id: String): BadgeType = when (id) {
            "staff", "admin", "global_admin"  -> Authority
            "predictions"                     -> Predictions
            "moderator", "vip", "broadcaster" -> Channel
            "subscriber", "founder"           -> Subscriber
            else                              -> Vanity
        }
    }
}
