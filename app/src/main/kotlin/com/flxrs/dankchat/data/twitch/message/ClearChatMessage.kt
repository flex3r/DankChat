package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.preferences.userdisplay.UserFinalizedDisplay
import java.util.*

data class ClearChatMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: List<Highlight> = emptyList(),
    val channel: String,
    val targetUser: String? = null,
    val duration: String = "",
    val stackCount: Int = 0,
    val userDisplay: UserFinalizedDisplay? = null,
) : Message() {
    val isBan = duration.isBlank()
    val isFullChatClear = targetUser == null

    // final (effective) name/color to be shown, according to override logic
    val finalName: String? get() = userDisplay?.username ?: targetUser
    val finalDisplayName: String get() = userDisplay?.displayName ?: ""

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