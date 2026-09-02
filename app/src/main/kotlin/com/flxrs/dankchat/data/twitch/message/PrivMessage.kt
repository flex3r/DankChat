package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.message.Message.Companion.parseEmoteTag
import com.flxrs.dankchat.utils.extensions.parseColorOrNull
import java.util.Locale
import java.util.UUID

data class PrivMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: Set<Highlight> = emptySet(),
    val channel: UserName,
    val sourceChannel: UserName?,
    val userId: UserId? = null,
    val name: UserName,
    val displayName: DisplayName,
    val color: Int? = null,
    val message: String,
    val originalMessage: String = message,
    val tags: Map<String, String>,
    val emotes: List<ChatMessageEmote> = emptyList(),
    val gifs: List<TwitchGif> = emptyList(),
    val gifData: TwitchGifData =
        TwitchGifData(
            message = originalMessage,
            gifs = parseTwitchGifTag(originalMessage, tags["gifs"].orEmpty()),
        ),
    val isAction: Boolean = false,
    val badges: List<Badge> = emptyList(),
    val timedOut: Boolean = false,
    val userDisplay: UserDisplay? = null,
    val thread: MessageThreadHeader? = null,
    val replyMentionOffset: Int = 0,
    val rewardCost: Int? = null,
    val rewardTitle: String? = null,
    val rewardImageUrl: String? = null,
    override val emoteData: Message.EmoteData =
        Message.EmoteData(
            message = originalMessage,
            channel = sourceChannel ?: channel,
            emotesWithPositions = parseEmoteTag(originalMessage, tags["emotes"].orEmpty()),
        ),
    override val badgeData: Message.BadgeData = Message.BadgeData(userId, channel, badgeTag = tags["badges"], badgeInfoTag = tags["badge-info"]),
) : Message {
    companion object {
        fun parsePrivMessage(
            ircMessage: IrcMessage,
            findChannel: (UserId) -> UserName?,
        ): PrivMessage = with(ircMessage) {
            val (name, id) =
                when (ircMessage.command) {
                    "USERNOTICE" -> tags["login"].orEmpty() to (tags["id"]?.let { "$it-msg" } ?: UUID.randomUUID().toString())
                    else -> prefix.substringBefore('!') to (tags["id"] ?: UUID.randomUUID().toString())
                }

            val displayName = tags["display-name"] ?: name
            val color = tags["color"]?.parseColorOrNull()

            val ts = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis()
            var isAction = false
            val messageParam = params.getOrElse(1) { "" }
            val message =
                when {
                    params.size > 1 && messageParam.startsWith("\u0001ACTION") && messageParam.endsWith("\u0001") -> {
                        isAction = true
                        messageParam.substring("\u0001ACTION ".length, messageParam.length - "\u0001".length)
                    }

                    else -> {
                        messageParam
                    }
                }

            val channel = params[0].substring(1).toUserName()
            val sourceChannel =
                tags["source-room-id"]
                    ?.takeIf { it.isNotEmpty() && it != tags["room-id"] }
                    ?.toUserId()
                    ?.let(findChannel)

            val gifs = parseTwitchGifTag(message, tags["gifs"].orEmpty())

            return PrivMessage(
                timestamp = ts,
                channel = channel,
                sourceChannel = sourceChannel,
                name = name.toUserName(),
                displayName = displayName.toDisplayName(),
                color = color,
                message = message,
                gifs = gifs,
                gifData = TwitchGifData(message, gifs),
                isAction = isAction,
                id = id,
                userId = tags["user-id"]?.toUserId(),
                timedOut = tags["rm-deleted"] == "1",
                tags = tags,
            )
        }
    }
}

private val SUB_MSG_IDS = UserNoticeMessage.USER_NOTICE_MSG_IDS_WITH_MESSAGE - "announcement" - UserNoticeMessage.MILESTONE_MSG_IDS

val PrivMessage.isSub: Boolean
    get() = tags["msg-id"] in SUB_MSG_IDS

val PrivMessage.isAnnouncement: Boolean
    get() = tags["msg-id"] == "announcement"

val PrivMessage.announcementColor: AnnouncementColor
    get() = AnnouncementColor.parse(tags["msg-param-color"])

val PrivMessage.isMilestone: Boolean
    get() = tags["msg-id"] in UserNoticeMessage.MILESTONE_MSG_IDS

val PrivMessage.isReward: Boolean
    get() = tags["msg-id"] in REWARD_MSG_IDS || !tags["custom-reward-id"].isNullOrEmpty()

val PrivMessage.isGigantifiedEmote: Boolean
    get() = tags["msg-id"] == "gigantified-emote-message"

val PrivMessage.isAnimatedMessage: Boolean
    get() = tags["msg-id"] == "animated-message"

private val REWARD_MSG_IDS = setOf(
    "highlighted-message",
    "gigantified-emote-message",
    "animated-message",
)

val PrivMessage.isFirstMessage: Boolean
    get() = tags["first-msg"] == "1"

val PrivMessage.isElevatedMessage: Boolean
    get() = tags["pinned-chat-paid-amount"] != null

val PrivMessage.hypeChatInfo: String?
    get() {
        val amount = tags["pinned-chat-paid-amount"]?.toLongOrNull() ?: return null
        val exponent = tags["pinned-chat-paid-exponent"]?.toIntOrNull() ?: 0
        val currency = tags["pinned-chat-paid-currency"] ?: return null
        val level = tags["pinned-chat-paid-level"]?.let { HYPE_CHAT_LEVELS[it] } ?: return null
        val divisor = Math.pow(10.0, exponent.toDouble())
        val formatted = "%.2f".format(Locale.getDefault(), amount / divisor)
        return "Hype Chat Level $level — $formatted $currency"
    }

private val HYPE_CHAT_LEVELS = mapOf(
    "ONE" to 1,
    "TWO" to 2,
    "THREE" to 3,
    "FOUR" to 4,
    "FIVE" to 5,
    "SIX" to 6,
    "SEVEN" to 7,
    "EIGHT" to 8,
    "NINE" to 9,
    "TEN" to 10,
)

/** format name for display in chat */
val PrivMessage.aliasOrFormattedName: String
    get() = userDisplay?.alias ?: name.formatWithDisplayName(displayName)
