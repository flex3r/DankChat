package com.flxrs.dankchat.data.twitch.message

import androidx.core.graphics.toColorInt
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.irc.IrcMessage

sealed interface Message {
    val id: String
    val timestamp: Long
    val highlights: Set<Highlight>

    data class EmoteData(
        val message: String,
        val channel: UserName,
        val emotesWithPositions: List<EmoteWithPositions>,
        val gifsWithPositions: List<TwitchGifWithPosition> = emptyList(),
    )

    data class BadgeData(
        val userId: UserId?,
        val channel: UserName?,
        val badgeTag: String?,
        val badgeInfoTag: String?,
    )

    val emoteData: EmoteData? get() = null
    val badgeData: BadgeData? get() = null

    companion object {
        private const val DEFAULT_COLOR_TAG = "#717171"
        val DEFAULT_COLOR = DEFAULT_COLOR_TAG.toColorInt()

        fun parse(
            message: IrcMessage,
            findChannel: (UserId) -> UserName?,
        ): Message? = with(message) {
            return when (command) {
                "PRIVMSG" -> PrivMessage.parsePrivMessage(message, findChannel)
                "NOTICE" -> NoticeMessage.parseNotice(message)
                "USERNOTICE" -> UserNoticeMessage.parseUserNotice(message, findChannel)
                else -> null
            }
        }

        fun parseEmoteTag(
            message: String,
            tag: String,
        ): List<EmoteWithPositions> {
            if (tag.isEmpty()) return emptyList()

            return buildList {
                var emoteStart = 0
                while (emoteStart < tag.length) {
                    val slashIdx = tag.indexOf('/', emoteStart)
                    val emoteEnd = if (slashIdx == -1) tag.length else slashIdx

                    val colonIdx = tag.indexOf(':', emoteStart)
                    // bad emote data :)
                    if (colonIdx == -1 || colonIdx >= emoteEnd) {
                        emoteStart = emoteEnd + 1
                        continue
                    }

                    val id = tag.substring(emoteStart, colonIdx)
                    val positions = mutableListOf<IntRange>()
                    var posStart = colonIdx + 1
                    while (posStart < emoteEnd) {
                        val commaIdx = tag.indexOf(',', posStart)
                        val posEnd = if (commaIdx == -1 || commaIdx > emoteEnd) emoteEnd else commaIdx

                        val dashIdx = tag.indexOf('-', posStart)
                        if (dashIdx == -1 || dashIdx >= posEnd) {
                            posStart = posEnd + 1
                            continue
                        }

                        val start = tag.substring(posStart, dashIdx).toIntOrNull()
                        val end = tag.substring(dashIdx + 1, posEnd).toIntOrNull()
                        if (start != null && end != null) {
                            positions += start.coerceAtLeast(minimumValue = 0)..end.coerceAtMost(message.length)
                        }

                        posStart = posEnd + 1
                    }

                    if (positions.isNotEmpty()) {
                        this += EmoteWithPositions(id, positions)
                    }

                    emoteStart = emoteEnd + 1
                }
            }
        }
    }
}
