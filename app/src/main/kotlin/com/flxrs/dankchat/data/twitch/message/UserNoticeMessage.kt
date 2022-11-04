package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.irc.IrcMessage
import java.util.*

data class UserNoticeMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: List<Highlight> = emptyList(),
    val channel: String,
    val message: String,
    val childMessage: PrivMessage?
) : Message() {

    override val emoteData: EmoteData? = childMessage?.emoteData
    override val badgeData: BadgeData? = childMessage?.badgeData

    companion object {
        fun parseUserNotice(message: IrcMessage, historic: Boolean = false): UserNoticeMessage = with(message) {
            val msgId = tags["msg-id"]
            val id = tags["id"] ?: UUID.randomUUID().toString()
            val channel = params[0].substring(1)
            val systemMsg = when {
                msgId == "announcement" -> "Announcement "
                historic                -> params[1]
                else                    -> tags["system-msg"] ?: ""
            }
            val ts = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis()

            val childMessage = when (msgId) {
                "sub", "resub", "announcement" -> PrivMessage.parsePrivMessage(message)
                else                           -> null
            }

            return UserNoticeMessage(
                timestamp = ts,
                id = id,
                channel = channel,
                message = systemMsg,
                childMessage = childMessage?.takeIf { it.message.isNotBlank() }
            )
        }
    }
}