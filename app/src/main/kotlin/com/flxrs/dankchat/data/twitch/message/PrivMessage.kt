package com.flxrs.dankchat.data.twitch.message

import android.graphics.Color
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayDto
import java.util.*

data class PrivMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: List<Highlight> = emptyList(),
    val channel: String,
    val userId: String? = null,
    val name: String = "",
    val displayName: String = "",
    val color: Int = Color.parseColor(DEFAULT_COLOR),
    val message: String,
    val originalMessage: String = message,
    val emotes: List<ChatMessageEmote> = emptyList(),
    val isAction: Boolean = false,
    val badges: List<Badge> = emptyList(),
    val timedOut: Boolean = false,
    val tags: Map<String, String>,
    val userDisplay: UserDisplayDto? = null,
) : Message() {

    override val emoteData: EmoteData = EmoteData(message, channel, emoteTag = tags["emotes"].orEmpty())
    override val badgeData: BadgeData = BadgeData(userId, channel, badgeTag = tags["badges"], badgeInfoTag = tags["badge-info"])

    companion object {
        fun parsePrivMessage(ircMessage: IrcMessage): PrivMessage = with(ircMessage) {
            val (name, id) = when (ircMessage.command) {
                "USERNOTICE" -> tags.getValue("login") to UUID.randomUUID().toString()
                else         -> prefix.substringBefore('!') to (tags["id"] ?: UUID.randomUUID().toString())
            }

            val displayName = tags["display-name"] ?: name
            val colorTag = tags["color"]?.ifBlank { DEFAULT_COLOR } ?: DEFAULT_COLOR
            val color = Color.parseColor(colorTag)

            val ts = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis()
            var isAction = false
            val messageParam = params.getOrElse(1) { "" }
            val message = when {
                params.size > 1 && messageParam.startsWith("\u0001ACTION") && messageParam.endsWith("\u0001") -> {
                    isAction = true
                    messageParam.substring("\u0001ACTION ".length, messageParam.length - "\u0001".length)
                }

                else                                                                                          -> messageParam
            }
            val channel = params[0].substring(1)

            return PrivMessage(
                timestamp = ts,
                channel = channel,
                name = name,
                displayName = displayName,
                color = color,
                message = message,
                isAction = isAction,
                id = id,
                userId = tags["user-id"],
                timedOut = tags["rm-deleted"] == "1",
                tags = tags,
            )
        }
    }
}

val PrivMessage.isSub: Boolean
    get() = tags["msg-id"] == "sub" || tags["msg-id"] == "resub"

val PrivMessage.isAnnouncement: Boolean
    get() = tags["msg-id"] == "announcement"

val PrivMessage.isReward: Boolean
    get() = tags["msg-id"] == "highlighted-message" || tags["custom-reward-id"] != null

val PrivMessage.isFirstMessage: Boolean
    get() = tags["first-msg"] == "1"

val PrivMessage.isElevatedMessage: Boolean
    get() = tags["pinned-chat-paid-amount"] != null