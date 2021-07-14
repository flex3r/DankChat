package com.flxrs.dankchat.service.twitch.emote

data class GenericEmote(
    val code: String,
    val url: String,
    val lowResUrl: String,
    val id: String,
    val scale: Int,
    val emoteType: EmoteType
) : Comparable<GenericEmote> {
    override fun toString(): String {
        return code
    }

    override fun compareTo(other: GenericEmote): Int {
        return code.compareTo(other.code)
    }
}