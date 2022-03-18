package com.flxrs.dankchat.data.twitch.emote

enum class ThirdPartyEmoteType {
    FrankerFaceZ,
    BetterTTV,
    SevenTV,
    UnlistedSevenTV;

    companion object {
        fun mapFromPreferenceSet(preferenceSet: Set<String>): Set<ThirdPartyEmoteType> = preferenceSet.mapNotNull {
            values().find { emoteType -> emoteType.name.lowercase() == it }
        }.toSet()
    }
}