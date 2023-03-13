package com.flxrs.dankchat.data.twitch.message

import android.graphics.Color
import androidx.annotation.ColorInt
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.pubsub.dto.whisper.WhisperData
import com.flxrs.dankchat.utils.extensions.normalizeColor
import java.util.UUID

data class WhisperMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: Set<Highlight> = emptySet(),
    val userId: UserId?,
    val name: UserName,
    val displayName: DisplayName,
    val color: Int = DEFAULT_COLOR,
    val recipientId: UserId?,
    val recipientName: UserName,
    val recipientDisplayName: DisplayName,
    val recipientColor: Int = DEFAULT_COLOR,
    val message: String,
    val rawEmotes: String,
    val rawBadges: String?,
    val rawBadgeInfo: String? = null,
    val originalMessage: String = message,
    val emotes: List<ChatMessageEmote> = emptyList(),
    val badges: List<Badge> = emptyList(),
    val userDisplay: UserDisplay? = null,
    val recipientDisplay: UserDisplay? = null,
    override val emoteData: EmoteData = EmoteData(originalMessage, WHISPER_CHANNEL, parseEmoteTag(originalMessage, rawEmotes)),
    override val badgeData: BadgeData = BadgeData(userId, channel = null, badgeTag = rawBadges, badgeInfoTag = rawBadgeInfo),
) : Message() {

    companion object {
        val WHISPER_CHANNEL = "w".toUserName()
        fun parseFromIrc(ircMessage: IrcMessage, recipientName: DisplayName, recipientColorTag: String?): WhisperMessage = with(ircMessage) {
            val name = prefix.substringBefore('!')
            val displayName = tags["display-name"] ?: name
            val color = tags["color"]?.ifBlank { null }?.let(Color::parseColor) ?: DEFAULT_COLOR
            val recipientColor = recipientColorTag?.let(Color::parseColor) ?: DEFAULT_COLOR
            val emoteTag = tags["emotes"] ?: ""
            val message = params.getOrElse(1) { "" }

            return WhisperMessage(
                timestamp = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis(),
                id = tags["id"] ?: UUID.randomUUID().toString(),
                userId = tags["user-id"]?.toUserId(),
                name = name.toUserName(),
                displayName = displayName.toDisplayName(),
                color = color,
                recipientId = null,
                recipientName = recipientName.toUserName(),
                recipientDisplayName = recipientName,
                recipientColor = recipientColor,
                message = message,
                rawEmotes = emoteTag,
                rawBadges = tags["badges"],
                rawBadgeInfo = tags["badge-info"]
            )
        }

        fun fromPubSub(data: WhisperData): WhisperMessage = with(data) {
            val color = data.tags.color.ifBlank { null }?.let(Color::parseColor) ?: DEFAULT_COLOR
            val recipientColor = data.recipient.color.ifBlank { null }?.let(Color::parseColor) ?: DEFAULT_COLOR
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
                recipientColor = recipientColor,
                message = message,
                rawEmotes = emotesTag,
                rawBadges = badgeTag,
            )
        }
    }

}

val WhisperMessage.senderAliasOrFormattedName: String
    get() = userDisplay?.alias ?: name.formatWithDisplayName(displayName)

fun WhisperMessage.senderColorOnBackground(@ColorInt background: Int): Int = userDisplay.colorOrElse(color.normalizeColor(background))

val WhisperMessage.recipientAliasOrFormattedName: String
    get() = recipientDisplay?.alias ?: recipientName.formatWithDisplayName(recipientDisplayName)

fun WhisperMessage.recipientColorOnBackground(@ColorInt background: Int): Int = recipientDisplay.colorOrElse(recipientColor.normalizeColor(background))
