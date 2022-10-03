package com.flxrs.dankchat.data.twitch.message

data class HighlightState(
    val type: HighlightType,
    val customColor: Int? = null
) {
    // TODO Check if enough
    val isMention = type == HighlightType.Username || type == HighlightType.Custom
}

enum class HighlightType {
    Username,
    Subscription,
    ChannelPointRedemption,
    FirstMessage,
    Custom
}