package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayEffectiveValue
import com.flxrs.dankchat.preferences.userdisplay.nameOr
import com.flxrs.dankchat.utils.DateTimeUtils
import java.util.*

data class ClearChatMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: List<Highlight> = emptyList(),
    val channel: String,
    val targetUser: String? = null,
    val duration: String = "",
    val stackCount: Int = 0,
    val userDisplay: UserDisplayEffectiveValue? = null,
) : Message() {
    val isBan = duration.isBlank()
    val isFullChatClear = targetUser == null

    companion object {
        fun parseClearChat(message: IrcMessage): ClearChatMessage = with(message) {
            val channel = params[0].substring(1)
            val target = params.getOrNull(1)
            val duration = tags["ban-duration"] ?: ""
            val ts = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis()
            val id = tags["id"] ?: UUID.randomUUID().toString()

            return ClearChatMessage(
                timestamp = ts,
                id = id,
                channel = channel,
                targetUser = target,
                duration = duration,
                stackCount = if (target != null && duration.isNotBlank()) 1 else 0
            )
        }
    }


}

// Extracted from ChatAdapter, TODO localize
val ClearChatMessage.fullText: String
    get() = when {
        isFullChatClear -> "Chat has been cleared by a moderator."
        // why is targetUser can be null?
        isBan           -> "${userDisplay.nameOr(targetUser ?: "A user")} has been permanently banned"
        else            -> {
            val countOrBlank = if (stackCount > 1) " ($stackCount times)" else ""
            "${userDisplay.nameOr(targetUser ?: "A user")} has been timed out for ${DateTimeUtils.formatSeconds(duration)}.$countOrBlank"
        }
    }

