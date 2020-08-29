package com.flxrs.dankchat.service.twitch.message

import android.graphics.Color
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.badge.Badge
import com.flxrs.dankchat.service.twitch.connection.SystemMessageType
import com.flxrs.dankchat.service.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.utils.extensions.appendSpacesBetweenEmojiGroup

sealed class Message {
    abstract val id: String
    abstract val timestamp: Long

    data class SystemMessage(
        val state: SystemMessageType,
        override val timestamp: Long = System.currentTimeMillis(),
        override val id: String = System.nanoTime().toString()
    ) : Message()

    data class TwitchMessage(
        override val timestamp: Long,
        val channel: String,
        val name: String = "",
        val displayName: String = "",
        val color: Int,
        val message: String,
        val emotes: List<ChatMessageEmote> = listOf(),
        val isAction: Boolean = false,
        val isNotify: Boolean = false,
        val badges: List<Badge> = emptyList(),
        override val id: String,
        var timedOut: Boolean = false,
        val isSystem: Boolean = false,
        var isMention: Boolean = false,
        var isReward: Boolean = false,
        val isWhisper: Boolean = false
    ) : Message() {

        fun checkForMention(username: String, mentions: List<Mention>) {
            val mentionsWithUser = mentions + Mention.User(username, false)
            isMention = !isMention && username.isNotBlank() && !name.equals(username, true)
                    && !timedOut && !isSystem && mentionsWithUser.matches(message, name to displayName, emotes)
        }

        companion object {
            fun parse(message: IrcMessage): List<TwitchMessage> = with(message) {
                return when (command) {
                    "PRIVMSG" -> listOf(parsePrivMessage(message))
                    "NOTICE" -> listOf(parseNotice(message))
                    "USERNOTICE" -> parseUserNotice(message)
                    "CLEARCHAT" -> listOf(parseClearChat(message))
                    "WHISPER" -> listOf(parseWhisper(message))
                    //"HOSTTARGET" -> listOf(parseHostTarget(message))
                    else -> listOf()
                }
            }

            private fun parsePrivMessage(ircMessage: IrcMessage, isNotify: Boolean = false): TwitchMessage = with(ircMessage) {
                val displayName = tags.getValue("display-name")
                val name = when (ircMessage.command) {
                    "USERNOTICE" -> tags.getValue("login")
                    else -> prefix.substringBefore('!')
                }
                val colorTag = tags["color"]?.ifBlank { "#717171" } ?: "#717171"
                val color = Color.parseColor(colorTag)

                val ts = tags["tmi-sent-ts"]?.toLong() ?: System.currentTimeMillis()
                var isAction = false
                val content = when {
                    params.size > 1 && params[1].startsWith("\u0001ACTION") && params[1].endsWith("\u0001") -> {
                        isAction = true
                        params[1].substring("\u0001ACTION ".length, params[1].length - "\u0001".length)
                    }
                    params.size > 1 -> params[1]
                    else -> ""
                }

                val (fixedContent, spaces) = content.appendSpacesBetweenEmojiGroup()
                val channel = params[0].substring(1)
                val emoteTag = tags["emotes"] ?: ""
                val emotes = EmoteManager.parseTwitchEmotes(emoteTag, fixedContent, spaces)
                val otherEmotes = EmoteManager.parse3rdPartyEmotes(fixedContent, channel)
                val id = tags["id"] ?: System.nanoTime().toString()
                val badges = parseBadges(tags["badges"], channel, tags["user-id"])

                return TwitchMessage(
                    timestamp = ts,
                    channel = channel,
                    name = name,
                    displayName = displayName,
                    color = color,
                    message = fixedContent,
                    emotes = (emotes + otherEmotes).distinctBy { it.code },
                    isAction = isAction,
                    isNotify = isNotify,
                    badges = badges,
                    id = id,
                    timedOut = tags["rm-deleted"] == "1",
                    isReward = tags["msg-id"] == "highlighted-message"
                )
            }

            private fun makeSystemMessage(message: String, channel: String, timestamp: Long = System.currentTimeMillis(), id: String = System.nanoTime().toString()): TwitchMessage {
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

            private fun parseUserNotice(message: IrcMessage, historic: Boolean = false): List<TwitchMessage> = with(message) {
                val messages = mutableListOf<TwitchMessage>()
                val msgId = tags["msg-id"]
                val id = tags["id"] ?: System.nanoTime().toString()
                val channel = params[0].substring(1)
                val systemMsg = if (historic) params[1] else tags["system-msg"] ?: ""
                val color = Color.parseColor("#717171")
                val ts = tags["tmi-sent-ts"]?.toLong() ?: System.currentTimeMillis()

                if (msgId != null && (msgId == "sub" || msgId == "resub")) {
                    val subMsg = parsePrivMessage(message, true)
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

            private fun parseNotice(message: IrcMessage): TwitchMessage = with(message) {
                val channel = params[0].substring(1)
                val notice = params[1]
                val ts = tags["rm-received-ts"]?.toLong() ?: System.currentTimeMillis()
                val id = tags["id"] ?: System.nanoTime().toString()

                return makeSystemMessage(notice, channel, ts, id)
            }

            private fun parseHostTarget(message: IrcMessage): TwitchMessage = with(message) {
                val target = params[1].substringBefore("-")
                val channel = params[0].substring(1)
                val ts = tags["rm-received-ts"]?.toLong() ?: System.currentTimeMillis()
                val id = tags["id"] ?: System.nanoTime().toString()

                return makeSystemMessage("Now hosting $target", channel, ts, id)
            }

            private fun parseClearChat(message: IrcMessage): TwitchMessage = with(message) {
                val channel = params[0].substring(1)
                val target = if (params.size > 1) params[1] else ""
                val duration = tags["ban-duration"] ?: ""
                val systemMessage = when {
                    target.isBlank() -> "Chat has been cleared by a moderator."
                    duration.isBlank() -> "$target has been permanently banned"
                    else -> "$target has been timed out for ${duration}s."
                }
                val ts = tags["tmi-sent-ts"]?.toLong() ?: System.currentTimeMillis()
                val id = tags["id"] ?: System.nanoTime().toString()

                return makeSystemMessage(systemMessage, channel, ts, id)
            }

            private fun parseWhisper(message: IrcMessage): TwitchMessage = with(message) {
                val displayName = tags.getValue("display-name")
                val name = prefix.substringBefore('!')
                val colorTag = tags["color"]?.ifBlank { "#717171" } ?: "#717171"
                val color = Color.parseColor(colorTag)
                val (fixedContent, spaces) = params[1].appendSpacesBetweenEmojiGroup()
                val badges = parseBadges(tags["badges"], userId = tags["user-id"])
                val emotes = EmoteManager
                    .parseTwitchEmotes(tags["emotes"] ?: "", fixedContent, spaces) + EmoteManager.parse3rdPartyEmotes(fixedContent)

                return TwitchMessage(
                    timestamp = System.currentTimeMillis(),
                    channel = "*",
                    name = name,
                    displayName = displayName,
                    color = color,
                    message = fixedContent,
                    emotes = emotes,
                    badges = badges,
                    id = System.nanoTime().toString(),
                    isWhisper = true
                )
            }

            private fun parseBadges(badgeTags: String?, channel: String = "", userId: String? = null): List<Badge> {
                val badges = badgeTags?.split(',')?.mapNotNull { badgeTag ->
                    val trimmed = badgeTag.trim()
                    val badgeSet = trimmed.substringBefore('/')
                    val badgeVersion = trimmed.substringAfter('/')
                    val globalBadgeUrl = EmoteManager.getGlobalBadgeUrl(badgeSet, badgeVersion)
                    val channelBadgeUrl = EmoteManager.getChannelBadgeUrl(channel, badgeSet, badgeVersion)
                    val ffzModBadgeUrl = EmoteManager.getFFzModBadgeUrl(channel)
                    when {
                        (badgeSet.startsWith("subscriber") || badgeSet.startsWith("bits")) && channelBadgeUrl != null -> Badge.ChannelBadge(badgeSet, channelBadgeUrl)
                        badgeSet.startsWith("moderator") && ffzModBadgeUrl != null -> Badge.FFZModBadge(ffzModBadgeUrl)
                        else -> globalBadgeUrl?.let { Badge.GlobalBadge(badgeSet, it) }
                    }
                } ?: emptyList()

                userId ?: return badges
                return when (val badge = EmoteManager.getDankChatBadgeUrl(userId)) {
                    null -> badges
                    else -> badges + Badge.GlobalBadge(badge.first, badge.second)
                }
            }
        }

    }
}
