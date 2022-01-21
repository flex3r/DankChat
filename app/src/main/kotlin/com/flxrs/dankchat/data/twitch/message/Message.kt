package com.flxrs.dankchat.data.twitch.message

import android.graphics.Color
import com.flxrs.dankchat.data.ChatRepository
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.badge.BadgeType
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.emote.EmoteManager
import com.flxrs.dankchat.utils.DateTimeUtils
import com.flxrs.dankchat.utils.extensions.appendSpacesBetweenEmojiGroup
import com.flxrs.dankchat.utils.extensions.removeDuplicateWhitespace
import java.util.*

sealed class Message {
    abstract val id: String
    abstract val timestamp: Long

    companion object {
        fun parse(message: IrcMessage, emoteManager: EmoteManager, currentUserName: String): List<Message> = with(message) {
            return when (command) {
                "PRIVMSG"    -> listOf(TwitchMessage.parsePrivMessage(message, emoteManager))
                "NOTICE"     -> listOf(TwitchMessage.parseNotice(message))
                "USERNOTICE" -> TwitchMessage.parseUserNotice(message, emoteManager)
                "CLEARCHAT"  -> listOf(ClearChatMessage.parseClearChat(message))
                "WHISPER"    -> listOf(TwitchMessage.parseWhisper(message, emoteManager, currentUserName))
                //"HOSTTARGET" -> listOf(parseHostTarget(message))
                else         -> listOf()
            }
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
            val ts = tags["tmi-sent-ts"]?.toLong() ?: System.currentTimeMillis()
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

data class TwitchMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    val channel: String,
    val userId: String? = null,
    val name: String = "",
    val displayName: String = "",
    val color: Int = Color.parseColor("#717171"),
    val message: String,
    val originalMessage: String = message,
    val emotes: List<ChatMessageEmote> = listOf(),
    val isAction: Boolean = false,
    val isNotify: Boolean = false,
    val badges: List<Badge> = emptyList(),
    val timedOut: Boolean = false,
    val isSystem: Boolean = false,
    val isMention: Boolean = false,
    val isReward: Boolean = false,
    val isWhisper: Boolean = false,
    val whisperRecipient: String = ""
) : Message() {

    fun checkForMention(username: String, mentions: List<Mention>): TwitchMessage {
        val mentionsWithUser = mentions + Mention.User(username, false)
        val isMention = !isMention && username.isNotBlank() && !name.equals(username, true)
                && !timedOut && !isSystem && mentionsWithUser.matches(message, name to displayName, emotes)
        return copy(isMention = isMention)
    }

    companion object {
        fun parsePrivMessage(ircMessage: IrcMessage, emoteManager: EmoteManager, isNotify: Boolean = false): TwitchMessage = with(ircMessage) {
            val name = when (ircMessage.command) {
                "USERNOTICE" -> tags.getValue("login")
                else         -> prefix.substringBefore('!')
            }

            val displayName = tags["display-name"] ?: name
            val colorTag = tags["color"]?.ifBlank { "#717171" } ?: "#717171"
            val color = Color.parseColor(colorTag)

            val ts = tags["tmi-sent-ts"]?.toLong() ?: System.currentTimeMillis()
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
            val badges = parseBadges(emoteManager, tags["badges"], channel, tags["user-id"])

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
                isReward = tags["msg-id"] == "highlighted-message"
            )
        }

        fun parseUserNotice(message: IrcMessage, emoteManager: EmoteManager, historic: Boolean = false): List<TwitchMessage> = with(message) {
            val messages = mutableListOf<TwitchMessage>()
            val msgId = tags["msg-id"]
            val id = tags["id"] ?: UUID.randomUUID().toString()
            val channel = params[0].substring(1)
            val systemMsg = if (historic) params[1] else tags["system-msg"] ?: ""
            val color = Color.parseColor("#717171")
            val ts = tags["tmi-sent-ts"]?.toLong() ?: System.currentTimeMillis()

            if (msgId != null && (msgId == "sub" || msgId == "resub")) {
                val subMsg = parsePrivMessage(message, emoteManager, isNotify = true)
                if (subMsg.message.isNotBlank()) messages += subMsg
            }
            val systemTwitchMessage = TwitchMessage(
                timestamp = ts,
                channel = channel,
                name = "",
                color = color,
                message = systemMsg,
                isNotify = true,
                id = id
            )
            messages += systemTwitchMessage
            return messages
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

            val ts = tags["rm-received-ts"]?.toLong() ?: System.currentTimeMillis()
            val id = tags["id"] ?: UUID.randomUUID().toString()

            return makeSystemMessage(notice, channel, ts, id)
        }

        fun parseWhisper(ircMessage: IrcMessage, emoteManager: EmoteManager, currentUserName: String): TwitchMessage = with(ircMessage) {
            val name = prefix.substringBefore('!')
            val displayName = tags["display-name"] ?: name
            val colorTag = tags["color"]?.ifBlank { "#717171" } ?: "#717171"
            val color = Color.parseColor(colorTag)
            val emoteTag = tags["emotes"] ?: ""
            val badges = parseBadges(emoteManager, tags["badges"], userId = tags["user-id"])
            val message = params.getOrElse(1) { "" }

            val withEmojiFix = message.replace(ChatRepository.ESCAPE_TAG_REGEX, ChatRepository.ZERO_WIDTH_JOINER)
            val (duplicateSpaceAdjustedMessage, removedSpaces) = withEmojiFix.removeDuplicateWhitespace()
            val (appendedSpaceAdjustedMessage, appendedSpaces) = duplicateSpaceAdjustedMessage.appendSpacesBetweenEmojiGroup()
            val (overlayEmotesAdjustedMessage, emotes) = emoteManager.parseEmotes(appendedSpaceAdjustedMessage, channel = "", emoteTag, appendedSpaces, removedSpaces)

            return TwitchMessage(
                timestamp = System.currentTimeMillis(),
                channel = "*",
                name = name,
                displayName = displayName,
                color = color,
                message = overlayEmotesAdjustedMessage,
                originalMessage = appendedSpaceAdjustedMessage,
                emotes = emotes,
                badges = badges,
                id = UUID.randomUUID().toString(),
                userId = tags["user-id"],
                isWhisper = true,
                whisperRecipient = currentUserName,
            )
        }

        private fun makeSystemMessage(message: String, channel: String, timestamp: Long = System.currentTimeMillis(), id: String = UUID.randomUUID().toString()): TwitchMessage {
            val color = Color.parseColor("#717171")
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

        private fun parseBadges(emoteManager: EmoteManager, badgeTags: String?, channel: String = "", userId: String? = null): List<Badge> {
            val badges = badgeTags?.split(',')?.mapNotNull { badgeTag ->
                val trimmed = badgeTag.trim()
                val badgeSet = trimmed.substringBefore('/')
                val badgeVersion = trimmed.substringAfter('/')
                val globalBadgeUrl = emoteManager.getGlobalBadgeUrl(badgeSet, badgeVersion)
                val channelBadgeUrl = emoteManager.getChannelBadgeUrl(channel, badgeSet, badgeVersion)
                val ffzModBadgeUrl = emoteManager.getFfzModBadgeUrl(channel)
                val ffzVipBadgeUrl = emoteManager.getFfzVipBadgeUrl(channel)
                val type = BadgeType.parseFromBadgeId(badgeSet)
                when {
                    badgeSet.startsWith("moderator") && ffzModBadgeUrl != null                                    -> Badge.FFZModBadge(ffzModBadgeUrl, type)
                    badgeSet.startsWith("vip") && ffzVipBadgeUrl != null                                          -> Badge.FFZVipBadge(ffzVipBadgeUrl, type)
                    (badgeSet.startsWith("subscriber") || badgeSet.startsWith("bits")) && channelBadgeUrl != null -> Badge.ChannelBadge(badgeSet, channelBadgeUrl, type)
                    else                                                                                          -> globalBadgeUrl?.let { Badge.GlobalBadge(badgeSet, it, type) }
                }
            }.orEmpty()

            userId ?: return badges
            return when (val badge = emoteManager.getDankChatBadgeUrl(userId)) {
                null -> badges
                else -> badges + Badge.GlobalBadge(badge.first, badge.second, BadgeType.DankChat)
            }
        }
    }
}
