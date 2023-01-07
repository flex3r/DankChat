package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.toUserName
import java.util.UUID

data class UserNoticeMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: Set<Highlight> = emptySet(),
    val channel: UserName,
    val message: String,
    val childMessage: PrivMessage?,
    val tags: Map<String, String>,
) : Message() {

    override val emoteData: EmoteData? = childMessage?.emoteData
    override val badgeData: BadgeData? = childMessage?.badgeData

    companion object {
        val USER_NOTICE_MSG_IDS_WITH_MESSAGE = listOf(
            "sub",
            "subgift",
            "resub",
            "bitsbadgetier",
            "ritual",
            "announcement"
        )

        fun parseUserNotice(message: IrcMessage, historic: Boolean = false): UserNoticeMessage = with(message) {
            val msgId = tags["msg-id"]
            val id = tags["id"] ?: UUID.randomUUID().toString()
            val channel = params[0].substring(1)
            val defaultMessage = tags["system-msg"] ?: ""
            val systemMsg = when {
                msgId == "announcement"  -> "Announcement"
                msgId == "bitsbadgetier" -> {
                    val displayName = tags["display-name"]
                    val bitAmount = tags["msg-param-threshold"]
                    when {
                        displayName != null && bitAmount != null -> "$displayName just earned a new ${bitAmount.toInt() / 1000}K Bits badge!"
                        else                                     -> defaultMessage
                    }
                }

                historic                 -> params[1]
                else                     -> defaultMessage
            }
            val ts = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis()

            val childMessage = when (msgId) {
                in USER_NOTICE_MSG_IDS_WITH_MESSAGE -> PrivMessage.parsePrivMessage(message)
                else                                -> null
            }

            return UserNoticeMessage(
                timestamp = ts,
                id = id,
                channel = channel.toUserName(),
                message = systemMsg,
                childMessage = childMessage?.takeIf { it.message.isNotBlank() },
                tags = tags,
            )
        }
    }
}

// TODO split into different user notice message types
val UserNoticeMessage.isSub: Boolean
    get() = tags["msg-id"] != "announcement"

val UserNoticeMessage.isAnnouncement: Boolean
    get() = tags["msg-id"] == "announcement"
