package com.flxrs.dankchat.data.twitch.emote

data class ChatMessageEmote(
    var position: IntRange,
    val url: String,
    val id: String,
    val code: String,
    val scale: Int,
    val isTwitch: Boolean = false,
    val isOverlayEmote: Boolean = false
)