package com.flxrs.dankchat.data.twitch.command

data class CommandContext(
    val trigger: String,
    val channel: String,
    val channelId: String,
    val originalMessage: String,
    val args: List<String>
)
