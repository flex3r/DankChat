package com.flxrs.dankchat.data.twitch.message

enum class RoomStateTag {
    EMOTE,
    FOLLOW,
    R9K,
    SLOW,
    SUBS;

    val ircTag: String
        get() = when (this) {
            EMOTE  -> "emote-only"
            FOLLOW -> "followers-only"
            R9K    -> "r9k"
            SLOW   -> "slow"
            SUBS   -> "subs-only"
        }
}
