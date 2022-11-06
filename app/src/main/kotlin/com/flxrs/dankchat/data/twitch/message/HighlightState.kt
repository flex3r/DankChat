package com.flxrs.dankchat.data.twitch.message

data class Highlight(
    val type: HighlightType,
    val customColor: Int? = null
) {
    val isMention = type == HighlightType.Username || type == HighlightType.Custom
}

fun List<Highlight>.hasMention(): Boolean = any(Highlight::isMention)

enum class HighlightType {
    Username,
    Subscription,
    Announcement,
    ChannelPointRedemption,
    FirstMessage,
    ElevatedMessage,
    Custom
}