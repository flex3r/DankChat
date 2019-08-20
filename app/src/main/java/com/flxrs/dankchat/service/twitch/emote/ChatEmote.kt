package com.flxrs.dankchat.service.twitch.emote

data class ChatEmote(
    val positions: List<String>,
    val url: String,
    val id: String,
    val code: String,
    val scale: Int,
    val isGif: Boolean,
    val isTwitch: Boolean = false
)