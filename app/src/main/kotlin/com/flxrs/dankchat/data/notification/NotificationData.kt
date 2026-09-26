package com.flxrs.dankchat.data.notification

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.data.twitch.message.shouldNotify

data class NotificationData(
    val id: String,
    val timestamp: Long,
    val channel: UserName,
    val userId: UserId?,
    val name: UserName,
    val displayName: DisplayName,
    val message: String,
    val isWhisper: Boolean = false,
    val isNotify: Boolean = false,
)

fun Message.toNotificationData(): NotificationData? {
    if (!highlights.shouldNotify()) {
        return null
    }

    return when (this) {
        is PrivMessage -> {
            NotificationData(id, timestamp, channel, userId, name, displayName, originalMessage)
        }

        is WhisperMessage -> {
            NotificationData(
                id = id,
                timestamp = timestamp,
                channel = UserName.EMPTY,
                userId = userId,
                name = name,
                displayName = displayName,
                message = originalMessage,
                isWhisper = true,
            )
        }

        else -> {
            null
        }
    }
}
