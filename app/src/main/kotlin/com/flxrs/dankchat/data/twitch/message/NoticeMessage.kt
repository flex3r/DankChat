package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.utils.DateTimeUtils
import java.util.*

data class NoticeMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: Set<Highlight> = emptySet(),
    val channel: UserName,
    val message: String
) : Message() {
    companion object {
        fun parseNotice(message: IrcMessage): NoticeMessage = with(message) {
            val channel = params[0].substring(1)
            val notice = when {
                tags["msg-id"] == "msg_timedout" -> params[1]
                    .split(" ")
                    .getOrNull(index = 5)
                    ?.toIntOrNull()
                    ?.let {
                        "You are timed out for ${DateTimeUtils.formatSeconds(it)}."
                    } ?: params[1]

                else                             -> params[1]
            }

            val ts = tags["rm-received-ts"]?.toLongOrNull() ?: System.currentTimeMillis()
            val id = tags["id"] ?: UUID.randomUUID().toString()

            return NoticeMessage(
                timestamp = ts,
                id = id,
                channel = channel.toUserName(),
                message = notice,
            )
        }
    }
}