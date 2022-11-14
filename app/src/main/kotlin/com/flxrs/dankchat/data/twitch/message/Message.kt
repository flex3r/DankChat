package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.irc.IrcMessage

sealed class Message {
    abstract val id: String
    abstract val timestamp: Long
    abstract val highlights: List<Highlight>

    data class EmoteData(val message: String, val channel: String, val emoteTag: String)
    data class BadgeData(val userId: String?, val channel: String, val badgeTag: String?, val badgeInfoTag: String?)

    open val emoteData: EmoteData? = null
    open val badgeData: BadgeData? = null

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


