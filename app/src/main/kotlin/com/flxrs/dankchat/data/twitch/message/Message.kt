package com.flxrs.dankchat.data.twitch.message

import android.graphics.Color
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.irc.IrcMessage

sealed class Message {
    abstract val id: String
    abstract val timestamp: Long
    abstract val highlights: Set<Highlight>

    data class EmoteData(val message: String, val channel: UserName, val emoteTag: String)
    data class BadgeData(val userId: UserId?, val channel: UserName?, val badgeTag: String?, val badgeInfoTag: String?)

    open val emoteData: EmoteData? = null
    open val badgeData: BadgeData? = null

    companion object {
        private const val DEFAULT_COLOR_TAG = "#717171"
        val DEFAULT_COLOR = Color.parseColor(DEFAULT_COLOR_TAG)
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


