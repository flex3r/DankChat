package com.flxrs.dankchat.preferences.notifications

import kotlinx.serialization.Serializable

@Serializable
data class NotificationsSettings(
    val showNotifications: Boolean = true,
    val showWhisperNotifications: Boolean = true,
    val mentionFormat: MentionFormat = MentionFormat.Name,
)

enum class MentionFormat(val template: String) {
    Name("name"),
    NameComma("name,"),
    AtName("@name"),
    AtNameComma("@name,");
}
