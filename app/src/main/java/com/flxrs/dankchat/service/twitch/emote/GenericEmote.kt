package com.flxrs.dankchat.service.twitch.emote

data class GenericEmote(
    val keyword: String,
    val url: String,
    val lowResUrl: String,
    val isGif: Boolean,
    val id: String,
    val scale: Int,
    val emoteType: EmoteType
) :
    Comparable<GenericEmote> {
    override fun toString(): String {
        return keyword
    }

    override fun compareTo(other: GenericEmote): Int {
        return keyword.compareTo(other.keyword)
    }
}