package com.flxrs.dankchat.data.notification

import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.data.twitch.message.shouldNotify

data class NotificationData(
    val channel: String,
    val name: String,
    val message: String,
    val isWhisper: Boolean = false,
    val isNotify: Boolean = false,
)

fun Message.toNotificationData(): NotificationData? {
    if (!highlights.shouldNotify()) {
        return null
    }

    return when (this) {
        is PrivMessage    -> NotificationData(channel, name, originalMessage)
        is WhisperMessage -> NotificationData(
            channel = "",
            name = name,
            message = originalMessage,
            isWhisper = true,
        )

        else              -> null
    }
}
