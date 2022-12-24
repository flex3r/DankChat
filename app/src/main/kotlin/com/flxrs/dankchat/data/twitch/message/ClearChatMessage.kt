package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.toUserName
import java.util.*

data class ClearChatMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: Set<Highlight> = emptySet(),
    val channel: UserName,
    val targetUser: UserName? = null,
    val duration: String = "",
    val stackCount: Int = 0,
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
                channel = channel.toUserName(),
                targetUser = target?.toUserName(),
                duration = duration,
                stackCount = if (target != null && duration.isNotBlank()) 1 else 0
            )
        }
    }
}