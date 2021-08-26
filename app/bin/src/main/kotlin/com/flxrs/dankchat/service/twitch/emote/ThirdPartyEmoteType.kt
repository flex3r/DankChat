package com.flxrs.dankchat.service.twitch.emote

enum class ThirdPartyEmoteType {
    FrankerFaceZ,
    BetterTTV,
    SevenTV;

    companion object {
        fun mapFromPreferenceSet(preferenceSet: Set<String>): Set<ThirdPartyEmoteType> = preferenceSet.mapNotNull {
            values().find { emoteType -> emoteType.name.lowercase() == it }
        }.toSet()
    }
}