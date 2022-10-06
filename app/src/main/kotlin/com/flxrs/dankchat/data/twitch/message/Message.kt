package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.repo.EmoteRepository
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.badge.BadgeType

sealed class Message {
    abstract val id: String
    abstract val timestamp: Long
    abstract val highlights: List<Highlight>

    companion object {
        const val DEFAULT_COLOR = "#717171"
        fun parse(message: IrcMessage): Message? = with(message) {
            return when (command) {
                "PRIVMSG"    -> PrivMessage.parsePrivMessage(message)
                "NOTICE"     -> NoticeMessage.parseNotice(message)
                "USERNOTICE" -> UserNoticeMessage.parseUserNotice(message)
                else         -> null
            }
        }
    }
}


