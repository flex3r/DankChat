package com.flxrs.dankchat.service.twitch.message

import android.graphics.Color
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.badge.Badge
import com.flxrs.dankchat.service.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.utils.TimeUtils
import com.flxrs.dankchat.utils.extensions.isEmoji
import java.util.regex.Pattern

data class TwitchMessage(
    val time: String,
    val channel: String,
    val name: String = "",
    val displayName: String = "",
    val color: Int,
    val message: String,
    val emotes: List<ChatMessageEmote> = listOf(),
    val isAction: Boolean = false,
    val isNotify: Boolean = false,
    val badges: List<Badge> = emptyList(),
    val id: String,
    var timedOut: Boolean = false,
    val isSystem: Boolean = false,
    var isMention: Boolean = false
) {

    fun checkForMention(username: String, patterns: List<Regex>) {
        val regex = """\b$username\b""".toPattern(Pattern.CASE_INSENSITIVE).toRegex()
        val regexps = patterns.plus(regex)
        if (!isMention && username.isNotBlank() && !name.equals(username, true)
            && !timedOut && !isSystem && regexps.any {
                it.containsMatchIn(message) || emotes.any { e -> it.containsMatchIn(e.code) }
            }
        ) {
            isMention = true
        }
    }

    companion object {

        fun makeSystemMessage(
            message: String,
            channel: String,
            timestamp: String = TimeUtils.localTime(),
            id: String = System.nanoTime().toString()
        ): TwitchMessage {
            val color = Color.parseColor("#717171")
            return TwitchMessage(
                timestamp,
                channel = channel,
                name = "",
                color = color,
                message = message,
                id = id,
                isSystem = true
            )
        }

        fun parse(message: IrcMessage): List<TwitchMessage> =
            with(message) {
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

        private fun parsePrivMessage(
            ircMessage: IrcMessage,
            isNotify: Boolean = false
        ): TwitchMessage = with(ircMessage) {
            val displayName = tags.getValue("display-name")
            val name =
                if (ircMessage.command == "USERNOTICE") tags.getValue("login") else prefix.substringBefore(
                    '!'
                )
            val colorTag = tags["color"]?.ifBlank { "#717171" } ?: "#717171"
            val color = Color.parseColor(colorTag)

            val ts = tags["tmi-sent-ts"]?.toLong() ?: System.currentTimeMillis()
            val time = TimeUtils.timestampToLocalTime(ts)
            var isAction = false
            val content =
                if (params.size > 1 && params[1].startsWith("\u0001ACTION") && params[1].endsWith("\u0001")) {
                    isAction = true
                    params[1].substring("\u0001ACTION ".length, params[1].length - "\u0001".length)
                } else if (params.size > 1) params[1] else ""

            //@badge-info=;badges=broadcaster/1,bits-charity/1;color=#00BCD4;display-name=flex3rs;emotes=521050:9-15,25-31;flags=;id=08649ff3-8fee-4200-8e06-c46bcdfb06e8;mod=0;room-id=73697410;subscriber=0;tmi-sent-ts=1575196101040;turbo=0;user-id=73697410;user-type= :flex3rs!flex3rs@flex3rs.tmi.twitch.tv PRIVMSG #flex3rs :🍓🍑🍊🍋🍍NaM forsenE 🍐🍏🐬🐳NaM forsenE
            // Adds extra space after every emoji group to support 3rd part emotes directly after emojis
            val fixedContentBuilder = StringBuilder()
            var previousEmoji = false
            val spaces = mutableListOf<Int>()
            content.forEachIndexed { i, c ->
                if (c.isEmoji()) {
                    previousEmoji = true
                } else if (previousEmoji) {
                    previousEmoji = false
                    if (!c.isWhitespace()) {
                        fixedContentBuilder.append(" ")
                        spaces.add(i)
                    }
                }
                fixedContentBuilder.append(c)
            }
            val fixedContent = fixedContentBuilder.toString()

            val channel = params[0].substring(1)
            val emoteTag = tags["emotes"] ?: ""
            val emotes = EmoteManager.parseTwitchEmotes(emoteTag, fixedContent, spaces)
            val otherEmotes = EmoteManager.parse3rdPartyEmotes(fixedContent, channel)
            val id = tags["id"] ?: System.nanoTime().toString()

            val badges = parseBadges(tags["badges"], channel)

            return TwitchMessage(
                time,
                channel,
                name,
                displayName,
                color,
                fixedContent,
                emotes.plus(otherEmotes).distinctBy { it.code },
                isAction,
                isNotify || tags["msg-id"] == "highlighted-message",
                badges,
                id,
                tags["rm-deleted"] == "1"
            )
        }

        private fun parseUserNotice(
            message: IrcMessage,
            historic: Boolean = false
        ): List<TwitchMessage> =
            with(message) {
                val messages = mutableListOf<TwitchMessage>()
                val msgId = tags["msg-id"]
                val id = tags["id"] ?: System.nanoTime().toString()
                val channel = params[0].substring(1)
                val systemMsg = if (historic) params[1] else tags["system-msg"] ?: ""
                val color = Color.parseColor("#717171")

                val ts = tags["tmi-sent-ts"]?.toLong() ?: System.currentTimeMillis()
                val time = TimeUtils.timestampToLocalTime(ts)

                if (msgId != null && (msgId == "sub" || msgId == "resub")) {
                    val subMsg = parsePrivMessage(message, true)
                    if (subMsg.message.isNotBlank()) messages.add(subMsg)
                }
                val systemTwitchMessage = TwitchMessage(
                    time,
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
            val time = TimeUtils.timestampToLocalTime(ts)
            val id = tags["id"] ?: System.nanoTime().toString()

            return makeSystemMessage(notice, channel, time, id)
        }

        private fun parseHostTarget(message: IrcMessage): TwitchMessage = with(message) {
            val target = params[1].substringBefore("-")
            val channel = params[0].substring(1)
            val ts = tags["rm-received-ts"]?.toLong() ?: System.currentTimeMillis()
            val time = TimeUtils.timestampToLocalTime(ts)
            val id = tags["id"] ?: System.nanoTime().toString()

            return makeSystemMessage("Now hosting $target", channel, time, id)
        }

        private fun parseClearChat(message: IrcMessage): TwitchMessage = with(message) {
            val channel = params[0].substring(1)
            val target = if (params.size > 1) params[1] else ""
            val duration = tags["ban-duration"] ?: ""
            val systemMessage = if (target.isBlank()) "Chat has been cleared by a moderator." else {
                if (duration.isBlank()) "$target has been permanently banned" else "$target has been timed out for ${duration}s."
            }
            val ts = tags["tmi-sent-ts"]?.toLong() ?: System.currentTimeMillis()
            val time = TimeUtils.timestampToLocalTime(ts)
            val id = tags["id"] ?: System.nanoTime().toString()

            return makeSystemMessage(systemMessage, channel, time, id)
        }

        private fun parseWhisper(message: IrcMessage): TwitchMessage = with(message) {
            val displayName = tags.getValue("display-name")
            val name = prefix.substringBefore('!')
            val colorTag = tags["color"]?.ifBlank { "#717171" } ?: "#717171"
            val color = Color.parseColor(colorTag)
            val content = params[1]

            val fixedContentBuilder = StringBuilder()
            var previousEmoji = false
            val spaces = mutableListOf<Int>()
            content.forEachIndexed { i, c ->
                if (c.isEmoji()) {
                    previousEmoji = true
                } else if (previousEmoji) {
                    previousEmoji = false
                    if (!c.isWhitespace()) {
                        fixedContentBuilder.append(" ")
                        spaces.add(i)
                    }
                }
                fixedContentBuilder.append(c)
            }
            val fixedContent = fixedContentBuilder.toString()

            val time = "${TimeUtils.timestampToLocalTime(System.currentTimeMillis())} (Whisper)"
            val badges = parseBadges(tags["badges"])
            val emotes = EmoteManager.parseTwitchEmotes(tags["emotes"] ?: "", fixedContent, spaces)
                .plus(EmoteManager.parse3rdPartyEmotes(fixedContent))

            return TwitchMessage(
                time,
                "*",
                name,
                displayName,
                color,
                fixedContent,
                emotes,
                badges = badges,
                id = System.nanoTime().toString()
            )
        }

        private fun parseBadges(badgeTags: String?, channel: String = ""): List<Badge> {
            val result = mutableListOf<Badge>()
            badgeTags?.split(',')?.forEach { badgeTag ->
                val trimmed = badgeTag.trim()
                val badgeSet = trimmed.substringBefore('/')
                val badgeVersion = trimmed.substringAfter('/')
                val badge = when {
                    badgeSet.startsWith("subscriber")
                            || badgeSet.startsWith("bits") -> {
                        EmoteManager.getSubBadgeUrl(channel, badgeSet, badgeVersion)
                            ?: EmoteManager.getGlobalBadgeUrl(badgeSet, badgeVersion)
                    }
                    else -> EmoteManager.getGlobalBadgeUrl(badgeSet, badgeVersion)
                }
                badge?.let { result += Badge(badgeSet, it) }
            }
            return result
        }
    }

}
