package com.flxrs.dankchat.data.twitch.emote

enum class ThirdPartyEmoteType {
    FrankerFaceZ,
    BetterTTV,
    SevenTV;

    companion object {
        fun mapFromPreferenceSet(preferenceSet: Set<String>): Set<ThirdPartyEmoteType> = preferenceSet.mapNotNull {
            entries.find { emoteType -> emoteType.name.lowercase() == it }
        }.toSet()
    }
}
