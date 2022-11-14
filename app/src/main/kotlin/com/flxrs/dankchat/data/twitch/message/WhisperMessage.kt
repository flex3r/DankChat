package com.flxrs.dankchat.data.twitch.message

import android.graphics.Color
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.connection.WhisperData
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.preferences.userdisplay.UserDisplayDto
import java.util.*

data class WhisperMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: List<Highlight> = emptyList(),
    val userId: String?,
    val name: String,
    val displayName: String,
    val color: Int = Color.parseColor(DEFAULT_COLOR),
    val recipientId: String?,
    val recipientName: String,
    val recipientDisplayName: String,
    val recipientColor: Int = Color.parseColor(DEFAULT_COLOR),
    val message: String,
    val rawEmotes: String,
    val rawBadges: String?,
    val rawBadgeInfo: String? = null,
    val originalMessage: String = message,
    val emotes: List<ChatMessageEmote> = emptyList(),
    val badges: List<Badge> = emptyList(),
    val userDisplay: UserDisplayDto? = null,
    val recipientDisplay: UserDisplayDto? = null
) : Message() {

    override val emoteData: EmoteData = EmoteData(message, channel = "", emoteTag = rawEmotes)
    override val badgeData: BadgeData = BadgeData(userId, channel = "", badgeTag = rawBadges, badgeInfoTag = rawBadgeInfo)

    companion object {
        fun parseFromIrc(ircMessage: IrcMessage, recipientName: String, recipientColor: String?): WhisperMessage = with(ircMessage) {
            val name = prefix.substringBefore('!')
            val displayName = tags["display-name"] ?: name
            val colorTag = tags["color"]?.ifBlank { DEFAULT_COLOR } ?: DEFAULT_COLOR
            val color = Color.parseColor(colorTag)
            val recipientColorTag = recipientColor ?: DEFAULT_COLOR
            val emoteTag = tags["emotes"] ?: ""
            val message = params.getOrElse(1) { "" }

            return WhisperMessage(
                timestamp = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis(),
                id = tags["id"] ?: UUID.randomUUID().toString(),
                userId = tags["user-id"],
                name = name,
                displayName = displayName,
                color = color,
                recipientId = null,
                recipientName = recipientName,
                recipientDisplayName = recipientName,
                recipientColor = Color.parseColor(recipientColorTag),
                message = message,
                rawEmotes = emoteTag,
                rawBadges = tags["badges"],
                rawBadgeInfo = tags["badge-info"]
            )
        }

        fun fromPubSub(data: WhisperData): WhisperMessage = with(data) {
            val colorTag = data.tags.color.ifBlank { DEFAULT_COLOR }
            val color = Color.parseColor(colorTag)
            val recipientColorTag = data.recipient.color.ifBlank { DEFAULT_COLOR }
            val badgeTag = data.tags.badges.joinToString(",") { "${it.id}/${it.version}" }
            val emotesTag = data.tags.emotes
                .groupBy { it.id }
                .entries
                .joinToString("/") { entry ->
                    "${entry.key}:" + entry.value.joinToString(",") { "${it.start}-${it.end}" }
                }

            return WhisperMessage(
                timestamp = data.timestamp * 1_000L, // PubSub uses seconds instead of millis, nice
                id = data.messageId,
                userId = data.userId,
                name = data.tags.name,
                displayName = data.tags.displayName,
                color = color,
                recipientId = data.recipient.id,
                recipientName = data.recipient.name,
                recipientDisplayName = data.recipient.displayName,
                recipientColor = Color.parseColor(recipientColorTag),
                message = message,
                rawEmotes = emotesTag,
                rawBadges = badgeTag,
            )
        }
    }
}