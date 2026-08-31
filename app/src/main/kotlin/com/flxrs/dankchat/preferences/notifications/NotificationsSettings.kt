package com.flxrs.dankchat.preferences.notifications

import com.flxrs.dankchat.data.UserName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationsSettings(
    val showNotifications: Boolean = true,
    val showWhisperNotifications: Boolean = true,
    val mentionFormat: MentionFormat = MentionFormat.Name,
    val mutedChannels: Set<UserName> = emptySet(),
) {
    fun areChannelNotificationsEnabled(channel: UserName): Boolean = channel.lowercase() !in mutedChannels

    fun withChannelNotificationsEnabled(
        channel: UserName,
        enabled: Boolean,
    ): NotificationsSettings {
        val normalizedChannel = channel.lowercase()
        return copy(
            mutedChannels =
                when {
                    enabled -> mutedChannels - normalizedChannel
                    else -> mutedChannels + normalizedChannel
                },
        )
    }
}

enum class MentionFormat(
    val template: String,
) {
    Name("name"),
    NameComma("name,"),
    AtName("@name"),
    AtNameComma("@name,"),
}
