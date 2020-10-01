package com.flxrs.dankchat.service.twitch.emote

data class ChatMessageEmote(
    val positions: List<Pair<Int, Int>>,
    val url: String,
    val id: String,
    val code: String,
    val scale: Int,
    val isGif: Boolean,
    val isTwitch: Boolean = false
)