package com.flxrs.dankchat.data.twitch.message

import android.graphics.Color
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.irc.IrcMessage

sealed class Message {
    abstract val id: String
    abstract val timestamp: Long
    abstract val highlights: Set<Highlight>

    data class EmoteData(val message: String, val channel: UserName, val emotesWithPositions: List<EmoteWithPositions>)
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

        fun parseEmoteTag(message: String, tag: String): List<EmoteWithPositions> {
            return tag.split('/').mapNotNull { emote ->
                val split = emote.split(':')
                // bad emote data :)
                if (split.size != 2) return@mapNotNull null

                val (id, positions) = split
                val pairs = positions.split(',')
                // bad emote data :)
                if (pairs.isEmpty()) return@mapNotNull null

                // skip over invalid parsed data
                val parsedPositions = pairs.mapNotNull positions@ { pos ->
                    val pair = pos.split('-')
                    if (pair.size != 2) return@positions null

                    val start = pair[0].toIntOrNull() ?: return@positions null
                    val end = pair[1].toIntOrNull() ?: return@positions null

                    // be extra safe in case twitch sends invalid emote ranges :)
                    start.coerceAtLeast(minimumValue = 0)..end.coerceAtMost(message.length)
                }

                EmoteWithPositions(id, parsedPositions)
            }
        }
    }
}


