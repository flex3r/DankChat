package com.flxrs.dankchat.data.twitch.message

data class Highlight(
    val type: HighlightType,
    val customColor: Int? = null
) {
    val isMention = type == HighlightType.Username || type == HighlightType.Custom
    val shouldNotify = type == HighlightType.Notification
}

fun Collection<Highlight>.hasMention(): Boolean = any(Highlight::isMention)
fun Collection<Highlight>.shouldNotify(): Boolean = any(Highlight::shouldNotify)
fun Collection<Highlight>.highestPriorityHighlight(): Highlight? = maxByOrNull { it.type.priority.value }

enum class HighlightType(val priority: HighlightPriority) {
    Subscription(HighlightPriority.HIGH),
    Announcement(HighlightPriority.HIGH),
    ChannelPointRedemption(HighlightPriority.HIGH),
    FirstMessage(HighlightPriority.MEDIUM),
    ElevatedMessage(HighlightPriority.MEDIUM),
    Username(HighlightPriority.LOW),
    Custom(HighlightPriority.LOW),
    Notification(HighlightPriority.LOW),
}
enum class HighlightPriority(val value: Int) {
    LOW(0),
    MEDIUM(1),
    HIGH(2),
}