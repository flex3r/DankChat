package com.flxrs.dankchat.data.twitch.message

import android.graphics.Color
import com.flxrs.dankchat.data.ChatRepository
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.badge.BadgeType
import com.flxrs.dankchat.data.twitch.connection.PointRedemptionData
import com.flxrs.dankchat.data.twitch.connection.WhisperData
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.emote.EmoteManager
import com.flxrs.dankchat.utils.DateTimeUtils
import com.flxrs.dankchat.utils.extensions.appendSpacesBetweenEmojiGroup
import com.flxrs.dankchat.utils.extensions.removeDuplicateWhitespace
import java.time.Instant
import java.time.ZoneId
import java.util.*

sealed class Message {
    abstract val id: String
    abstract val timestamp: Long

    companion object {
        const val DEFAULT_COLOR = "#717171"
        fun parse(message: IrcMessage, emoteManager: EmoteManager): List<Message> = with(message) {
            return when (command) {
                "PRIVMSG"    -> listOf(TwitchMessage.parsePrivMessage(message, emoteManager))
                "NOTICE"     -> listOf(TwitchMessage.parseNotice(message))
                "USERNOTICE" -> TwitchMessage.parseUserNotice(message, emoteManager)
                "CLEARCHAT"  -> listOf(ClearChatMessage.parseClearChat(message))
                else         -> emptyList()
            }
        }

        fun parseBadges(emoteManager: EmoteManager, badgeTags: String?, badgeInfoTag: String?, channel: String = "", userId: String? = null): List<Badge> {
            val badgeInfos = badgeInfoTag
                ?.parseTagList()
                ?.associate { it.key to it.value }
                .orEmpty()

            val badges = badgeTags
                ?.parseTagList()
                ?.mapNotNull { (badgeKey, badgeValue, tag) ->
                    val badgeInfo = badgeInfos[badgeKey]

                    val globalBadgeUrl = emoteManager.getGlobalBadgeUrl(badgeKey, badgeValue)
                    val channelBadgeUrl = emoteManager.getChannelBadgeUrl(channel, badgeKey, badgeValue)
                    val ffzModBadgeUrl = emoteManager.getFfzModBadgeUrl(channel)
                    val ffzVipBadgeUrl = emoteManager.getFfzVipBadgeUrl(channel)

                    val title = emoteManager.getBadgeTitle(channel, badgeKey, badgeValue)
                    val type = BadgeType.parseFromBadgeId(badgeKey)
                    when {
                        badgeKey.startsWith("moderator") && ffzModBadgeUrl != null -> Badge.FFZModBadge(
                            title = title,
                            badgeTag = tag,
                            badgeInfo = badgeInfo,
                            url = ffzModBadgeUrl,
                            type = type
                        )
                        badgeKey.startsWith("vip") && ffzVipBadgeUrl != null       -> Badge.FFZVipBadge(
                            title = title,
                            badgeTag = tag,
                            badgeInfo = badgeInfo,
                            url = ffzVipBadgeUrl,
                            type = type
                        )
                        (badgeKey.startsWith("subscriber") || badgeKey.startsWith("bits"))
                                && channelBadgeUrl != null                         -> Badge.ChannelBadge(
                            title = title,
                            badgeTag = tag,
                            badgeInfo = badgeInfo,
                            url = channelBadgeUrl,
                            type = type
                        )
                        else                                                       -> globalBadgeUrl?.let { Badge.GlobalBadge(title, tag, badgeInfo, it, type) }
                    }
                }.orEmpty()

            userId ?: return badges
            return when (val badge = emoteManager.getDankChatBadgeTitleAndUrl(userId)) {
                null -> badges
                else -> {
                    val (title, url) = badge
                    badges + Badge.DankChatBadge(title = title, badgeTag = null, badgeInfo = null, url = url, type = BadgeType.DankChat)
                }
            }
        }

        data class TagListEntry(val key: String, val value: String, val tag: String)

        private fun String.parseTagList(): List<TagListEntry> = split(',')
            .mapNotNull {
                if (!it.contains('/')) {
                    return@mapNotNull null
                }

                val key = it.substringBefore('/')
                val value = it.substringAfter('/')
                TagListEntry(key, value, it)
            }
    }
}


data class SystemMessage(
    val type: SystemMessageType,
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString()
) : Message()

data class ClearChatMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    val channel: String,
    val targetUser: String? = null,
    val duration: String = "",
    val count: Int = 0,
) : Message() {
    val isBan = duration.isBlank()
    val isFullChatClear = targetUser == null

    companion object {
        fun parseClearChat(message: IrcMessage): ClearChatMessage = with(message) {
            val channel = params[0].substring(1)
            val target = params.getOrNull(1)
            val duration = tags["ban-duration"] ?: ""
            val ts = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis()
            val id = tags["id"] ?: UUID.randomUUID().toString()

            return ClearChatMessage(
                timestamp = ts,
                id = id,
                channel = channel,
                targetUser = target,
                duration = duration,
                count = if (target != null && duration.isNotBlank()) 1 else 0
            )
        }
    }
}

data class PointRedemptionMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    val name: String,
    val displayName: String,
    val title: String,
    val rewardImageUrl: String,
    val cost: Int,
    val requiresUserInput: Boolean,
) : Message() {

    companion object {
        fun parsePointReward(timestamp: Instant, data: PointRedemptionData): PointRedemptionMessage {
            return PointRedemptionMessage(
                timestamp = timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                id = data.id,
                name = data.user.name,
                displayName = data.user.displayName,
                title = data.reward.title,
                rewardImageUrl = data.reward.images?.imageLarge ?: data.reward.defaultImages.imageLarge,
                cost = data.reward.cost,
                requiresUserInput = data.reward.requiresUserInput,
            )
        }
    }
}

data class WhisperMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    val userId: String?,
    val name: String,
    val displayName: String,
    val color: Int = Color.parseColor(DEFAULT_COLOR),
    val recipientId: String?,
    val recipientName: String,
    val recipientDisplayName: String,
    val recipientColor: Int = Color.parseColor(DEFAULT_COLOR),
    val message: String,
    val originalMessage: String,
    val emotes: List<ChatMessageEmote> = emptyList(),
    val badges: List<Badge> = emptyList(),
) : Message() {
    companion object {
        fun parseFromIrc(ircMessage: IrcMessage, emoteManager: EmoteManager, recipientName: String, recipientColor: String?): WhisperMessage = with(ircMessage) {
            val name = prefix.substringBefore('!')
            val displayName = tags["display-name"] ?: name
            val colorTag = tags["color"]?.ifBlank { DEFAULT_COLOR } ?: DEFAULT_COLOR
            val color = Color.parseColor(colorTag)
            val recipientColorTag = recipientColor ?: DEFAULT_COLOR
            val emoteTag = tags["emotes"] ?: ""
            val badges = parseBadges(emoteManager, tags["badges"], badgeInfoTag = tags["badge-info"], userId = tags["user-id"])
            val message = params.getOrElse(1) { "" }

            val withEmojiFix = message.replace(ChatRepository.ESCAPE_TAG_REGEX, ChatRepository.ZERO_WIDTH_JOINER)
            val (duplicateSpaceAdjustedMessage, removedSpaces) = withEmojiFix.removeDuplicateWhitespace()
            val (appendedSpaceAdjustedMessage, appendedSpaces) = duplicateSpaceAdjustedMessage.appendSpacesBetweenEmojiGroup()
            val (overlayEmotesAdjustedMessage, emotes) = emoteManager.parseEmotes(appendedSpaceAdjustedMessage, channel = "", emoteTag, appendedSpaces, removedSpaces)

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
                message = overlayEmotesAdjustedMessage,
                originalMessage = appendedSpaceAdjustedMessage,
                emotes = emotes,
                badges = badges,
            )
        }

        fun fromPubsub(data: WhisperData, emoteManager: EmoteManager): WhisperMessage = with(data) {
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


            val badges = parseBadges(emoteManager, badgeTag, badgeInfoTag = null, userId = data.userId)
            val withEmojiFix = message.replace(ChatRepository.ESCAPE_TAG_REGEX, ChatRepository.ZERO_WIDTH_JOINER)
            val (duplicateSpaceAdjustedMessage, removedSpaces) = withEmojiFix.removeDuplicateWhitespace()
            val (appendedSpaceAdjustedMessage, appendedSpaces) = duplicateSpaceAdjustedMessage.appendSpacesBetweenEmojiGroup()
            val (overlayEmotesAdjustedMessage, emotes) = emoteManager.parseEmotes(appendedSpaceAdjustedMessage, channel = "", emotesTag, appendedSpaces, removedSpaces)

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
                message = appendedSpaceAdjustedMessage,
                originalMessage = overlayEmotesAdjustedMessage,
                emotes = emotes,
                badges = badges,
            )
        }
    }
}

data class TwitchMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    val channel: String,
    val userId: String? = null,
    val name: String = "",
    val displayName: String = "",
    val color: Int = Color.parseColor(DEFAULT_COLOR),
    val message: String,
    val originalMessage: String = message,
    val emotes: List<ChatMessageEmote> = emptyList(),
    val isAction: Boolean = false,
    val isNotify: Boolean = false,
    val badges: List<Badge> = emptyList(),
    val timedOut: Boolean = false,
    val isSystem: Boolean = false,
    val isMention: Boolean = false,
    val isReward: Boolean = false,
) : Message() {

    fun checkForMention(username: String, mentions: List<Mention>): TwitchMessage {
        val mentionsWithUser = mentions + Mention.User(username, false)
        if (isMention || isSystem || username.isBlank() || name.equals(username, ignoreCase = true)) {
            return this
        }

        val isMention = mentionsWithUser.matches(this)
        return copy(isMention = isMention)
    }

    companion object {
        fun parsePrivMessage(ircMessage: IrcMessage, emoteManager: EmoteManager, isNotify: Boolean = false): TwitchMessage = with(ircMessage) {
            val name = when (ircMessage.command) {
                "USERNOTICE" -> tags.getValue("login")
                else         -> prefix.substringBefore('!')
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
            val emoteTag = tags["emotes"] ?: ""
            val id = tags["id"] ?: UUID.randomUUID().toString()
            val badges = parseBadges(emoteManager, tags["badges"], tags["badge-info"], channel, tags["user-id"])

            val withEmojiFix = message.replace(ChatRepository.ESCAPE_TAG_REGEX, ChatRepository.ZERO_WIDTH_JOINER)
            val (duplicateSpaceAdjustedMessage, removedSpaces) = withEmojiFix.removeDuplicateWhitespace()
            val (appendedSpaceAdjustedMessage, appendedSpaces) = duplicateSpaceAdjustedMessage.appendSpacesBetweenEmojiGroup()
            val (overlayEmotesAdjustedMessage, emotes) = emoteManager.parseEmotes(appendedSpaceAdjustedMessage, channel, emoteTag, appendedSpaces, removedSpaces)

            return TwitchMessage(
                timestamp = ts,
                channel = channel,
                name = name,
                displayName = displayName,
                color = color,
                message = overlayEmotesAdjustedMessage,
                originalMessage = appendedSpaceAdjustedMessage,
                emotes = emotes,
                isAction = isAction,
                isNotify = isNotify,
                badges = badges,
                id = id,
                userId = tags["user-id"],
                timedOut = tags["rm-deleted"] == "1",
                isReward = tags["msg-id"] == "highlighted-message" || tags["custom-reward-id"] != null
            )
        }

        fun parseUserNotice(message: IrcMessage, emoteManager: EmoteManager, historic: Boolean = false): List<TwitchMessage> = with(message) {
            val msgId = tags["msg-id"]
            val id = tags["id"] ?: UUID.randomUUID().toString()
            val channel = params[0].substring(1)
            val systemMsg = when {
                msgId == "announcement" -> "Announcement "
                historic                -> params[1]
                else                    -> tags["system-msg"] ?: ""
            }
            val color = Color.parseColor(DEFAULT_COLOR)
            val ts = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis()
            val systemTwitchMessage = TwitchMessage(
                timestamp = ts,
                channel = channel,
                name = "",
                color = color,
                message = systemMsg,
                isNotify = true,
                id = id
            )

            val subMsg = when (msgId) {
                "sub", "resub", "announcement" -> parsePrivMessage(message, emoteManager, isNotify = true).takeIf { it.message.isNotBlank() }
                else                           -> null
            }

            return when (msgId) {
                "sub", "resub", "announcement" -> listOfNotNull(subMsg, systemTwitchMessage)
                else                           -> listOf(systemTwitchMessage)
            }
        }

        fun parseNotice(message: IrcMessage): TwitchMessage = with(message) {
            val channel = params[0].substring(1)

            val notice = when {
                tags["msg-id"] == "msg_timedout" -> {
                    message.params[1].split(" ").getOrNull(5)?.let {
                        "You are timed out for ${DateTimeUtils.formatSeconds(it)}."
                    } ?: params[1]
                }
                else                             -> params[1]
            }

            val ts = tags["rm-received-ts"]?.toLongOrNull() ?: System.currentTimeMillis()
            val id = tags["id"] ?: UUID.randomUUID().toString()

            return makeSystemMessage(notice, channel, ts, id)
        }

        private fun makeSystemMessage(message: String, channel: String, timestamp: Long = System.currentTimeMillis(), id: String = UUID.randomUUID().toString()): TwitchMessage {
            val color = Color.parseColor(DEFAULT_COLOR)
            return TwitchMessage(
                timestamp = timestamp,
                channel = channel,
                name = "",
                color = color,
                message = message,
                id = id,
                isSystem = true
            )
        }
    }
}
