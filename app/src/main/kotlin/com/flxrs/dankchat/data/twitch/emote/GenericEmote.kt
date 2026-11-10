package com.flxrs.dankchat.data.twitch.emote

data class GenericEmote(
    val code: String,
    val url: String,
    val lowResUrl: String,
    val id: String,
    val scale: Int,
    val emoteType: EmoteType,
    val isOverlayEmote: Boolean = false,
    // First frame of an animated emote, a small file the emote menu shows while the animation loads
    val stillUrl: String? = null,
) : Comparable<GenericEmote> {
    override fun toString(): String = code

    override fun compareTo(other: GenericEmote): Int = code.compareTo(other.code)
}
