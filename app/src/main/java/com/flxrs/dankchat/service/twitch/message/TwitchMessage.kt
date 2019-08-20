package com.flxrs.dankchat.service.twitch.message

import android.graphics.Color
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.badge.Badge
import com.flxrs.dankchat.service.twitch.emote.ChatEmote
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.utils.TimeUtils

data class TwitchMessage(
    val time: String,
    val channel: String,
    val name: String,
    val color: Int,
    val message: String,
    val emotes: List<ChatEmote> = listOf(),
    val isAction: Boolean = false,
    val isNotify: Boolean = false,
    val badges: List<Badge> = emptyList(),
    val id: String,
    var timedOut: Boolean = false,
    val isSystem: Boolean = false
) {

    fun isMention(username: String): Boolean {
        return username.isNotBlank() && !name.equals(username, true)
                && !timedOut && !isSystem && message.contains(username, true)
    }

    companion object {
        fun parseFromIrc(
            ircMessage: IrcMessage,
            isNotify: Boolean = false,
            isHistoric: Boolean = false
        ): TwitchMessage = with(ircMessage) {
            var notify = isNotify
            val user = tags["display-name"] ?: "NaM"
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

            val channel = params[0].substring(1)
            val emoteTag = tags["emotes"] ?: ""
            val emotes = EmoteManager.parseTwitchEmotes(emoteTag, content)
            val otherEmotes = EmoteManager.parse3rdPartyEmotes(content, channel)
            val id = tags["id"] ?: System.nanoTime().toString()

            val badges = arrayListOf<Badge>()
            tags["badges"]?.split(',')?.forEach { badge ->
                val trimmed = badge.trim()
                val badgeSet = trimmed.substringBefore('/')
                val badgeVersion = trimmed.substringAfter('/')
                when {
                    badgeSet.startsWith("twitchbot") -> if (isHistoric) notify = true
                    badgeSet.startsWith("subscriber") -> EmoteManager.getSubBadgeUrl(
                        channel,
                        badgeSet,
                        badgeVersion
                    )?.let { badges.add(Badge(badgeSet, it)) }
                    badgeSet.startsWith("bits") -> EmoteManager.getSubBadgeUrl(
                        channel,
                        badgeSet,
                        badgeVersion
                    )
                        ?: EmoteManager.getGlobalBadgeUrl(badgeSet, badgeVersion)?.let {
                            badges.add(Badge(badgeSet, it))
                        }
                    else -> EmoteManager.getGlobalBadgeUrl(badgeSet, badgeVersion)?.let {
                        badges.add(Badge(badgeSet, it))
                    }
                }
            }

            return TwitchMessage(
                time,
                channel,
                user,
                color,
                content,
                emotes.plus(otherEmotes),
                isAction,
                notify,
                badges,
                id
            )
        }

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

        fun parseUserNotice(message: IrcMessage, historic: Boolean = false): List<TwitchMessage> =
            with(message) {
                val messages = mutableListOf<TwitchMessage>()
                val msgId = tags["msg-id"]
                val id = tags["id"] ?: System.nanoTime().toString()
                val channel = params[0].substring(1)
                val systemMsg = if (historic) {
                    params[1]
                } else {
                    tags["system-msg"] ?: ""
                }
                val color = Color.parseColor("#717171")

                val ts = tags["tmi-sent-ts"]?.toLong() ?: System.currentTimeMillis()
                val time = TimeUtils.timestampToLocalTime(ts)

                if (msgId != null && (msgId == "sub" || msgId == "resub")) {
                    val subMsg = parseFromIrc(message, true)
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
                messages.add(systemTwitchMessage)
                return messages
            }

        fun parseNotice(message: IrcMessage): TwitchMessage = with(message) {
            val channel = params[0].substring(1)
            val notice = params[1]
            val ts = tags["tmi-sent-ts"]?.toLong() ?: System.currentTimeMillis()
            val time = TimeUtils.timestampToLocalTime(ts)
            val id = tags["id"] ?: System.nanoTime().toString()

            return makeSystemMessage(notice, channel, time, id)
        }

        fun parse(message: IrcMessage): List<TwitchMessage> = with(message) {
            return when (command) {
                "PRIVMSG" -> listOf(parseFromIrc(message))
                "NOTICE" -> listOf(parseNotice(message))
                "USERNOTICE" -> parseUserNotice(message)
                "CLEARCHAT" -> listOf(parseClearChat(message))
                "CLEARMSG" -> listOf() //TODO
                "HOSTTARGET" -> listOf(parseHostTarget(message))
                else -> listOf()
            }
        }

        private fun parseHostTarget(message: IrcMessage): TwitchMessage = with(message) {
            val target = params[1].substringBefore("-")
            val channel = params[0].substring(1)
            val ts = tags["tmi-sent-ts"]?.toLong() ?: System.currentTimeMillis()
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
    }

    data class Roomstate(
        val channel: String,
        val flags: MutableMap<String, Int> = mutableMapOf(
            "emote" to 0,
            "follow" to -1,
            "r9k" to 0,
            "slow" to 0,
            "subs" to 0
        )
    ) {

        override fun toString(): String {
            return flags.filter { (it.key == "follow" && it.value >= 0) || it.value > 0 }.map {
                when (it.key) {
                    "follow" -> if (it.value == 0) "follow" else "follow(${it.value})"
                    "slow" -> "slow(${it.value})"
                    else -> it.key
                }
            }.joinToString()
        }

        fun updateState(msg: IrcMessage) {
            msg.tags.entries.forEach {
                when (it.key) {
                    "emote-only" -> flags["emote"] = it.value.toInt()
                    "followers-only" -> flags["follow"] = it.value.toInt()
                    "r9k" -> flags["r9k"] = it.value.toInt()
                    "slow" -> flags["slow"] = it.value.toInt()
                    "subs-only" -> flags["subs"] = it.value.toInt()
                }
            }
        }
    }
}
