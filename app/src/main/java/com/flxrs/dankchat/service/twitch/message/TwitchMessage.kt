package com.flxrs.dankchat.service.twitch.message

import android.graphics.Color
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.badge.Badge
import com.flxrs.dankchat.service.twitch.emote.ChatEmote
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.utils.TimeUtils

data class TwitchMessage(val time: String, val channel: String, val name: String, val color: Int, val message: String, val emotes: List<ChatEmote> = listOf(),
						 val isAction: Boolean = false, val isSystem: Boolean = false, val badges: List<Badge> = emptyList(), val id: String, var timedOut: Boolean = false) {

	companion object {
		fun parseFromIrc(ircMessage: IrcMessage, isSystem: Boolean = false, isHistoric: Boolean = false): TwitchMessage = with(ircMessage) {
			var system = isSystem
			val user = tags["display-name"] ?: "NaM"
			val colorTag = tags["color"]?.ifBlank { "#717171" } ?: "#717171"
			val color = Color.parseColor(colorTag)

			val ts = tags["tmi-sent-ts"]?.toLong() ?: System.currentTimeMillis()
			val time = TimeUtils.timestampToLocalTime(ts)

			var isAction = false
			val content = if (params.size > 1 && params[1].startsWith("\u0001ACTION") && params[1].endsWith("\u0001")) {
				isAction = true
				params[1].substring("\u0001ACTION ".length, params[1].length - "\u0001".length)
			} else if (params.size > 1) params[1] else ""

			val channel = params[0].substring(1)
			val emoteTag = tags["emotes"] ?: ""
			val emotes = EmoteManager.parseTwitchEmotes(emoteTag)
			val otherEmotes = EmoteManager.parse3rdPartyEmotes(content, channel)
			val id = tags["id"] ?: System.nanoTime().toString()

			val badges = arrayListOf<Badge>()
			tags["badges"]?.split(',')?.forEach { badge ->
				val trimmed = badge.trim()
				val badgeSet = trimmed.substringBefore('/')
				val badgeVersion = trimmed.substringAfter('/')
				when {
					badgeSet.startsWith("twitchbot")  -> if (isHistoric) system = true
					badgeSet.startsWith("subscriber") -> EmoteManager.getSubBadgeUrl(channel, badgeSet, badgeVersion)?.let { badges.add(Badge(badgeSet, it)) }
					badgeSet.startsWith("bits")       -> EmoteManager.getSubBadgeUrl(channel, badgeSet, badgeVersion)
							?: EmoteManager.getGlobalBadgeUrl(badgeSet, badgeVersion)?.let { badges.add(Badge(badgeSet, it)) }
					else                              -> EmoteManager.getGlobalBadgeUrl(badgeSet, badgeVersion)?.let { badges.add(Badge(badgeSet, it)) }
				}
			}

			return TwitchMessage(time, channel, user, color, content, emotes.plus(otherEmotes), isAction, system, badges, id)
		}

		fun makeSystemMessage(message: String, channel: String): TwitchMessage {
			val time = TimeUtils.localTime()
			val color = Color.parseColor("#717171")
			val id = System.nanoTime().toString()
			return TwitchMessage(time, channel = channel, name = "", color = color, message = message, id = id)
		}

		fun parseUserNotice(message: IrcMessage, historic: Boolean = false): List<TwitchMessage> = with(message) {
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
			val systemTwitchMessage = TwitchMessage(time, channel = channel, name = "", color = color, message = systemMsg, isSystem = true, id = id)
			messages.add(systemTwitchMessage)
			return messages
		}

		private fun parseHostTarget(message: IrcMessage): TwitchMessage = with(message) {
			val target = params[1].substringBefore("-")
			val channel = params[0].substring(1)
			return makeSystemMessage("Now hosting $target", channel)
		}

		private fun parseClearChat(message: IrcMessage): TwitchMessage = with(message) {
			val channel = params[0].substring(1)
			val target = if (params.size > 1) params[1] else ""
			val duration = tags["ban-duration"] ?: ""
			val systemMessage = if (target.isBlank()) "Chat has been cleared by a moderator." else {
				if (duration.isBlank()) "$target has been permanently banned" else "$target has been timed out for ${duration}s."
			}
			return makeSystemMessage(systemMessage, channel)
		}

		private fun parseNotice(message: IrcMessage): TwitchMessage = with(message) {
			val channel = params[0].substring(1)
			val notice = params[1]
			return makeSystemMessage(notice, channel)
		}

		fun parse(message: IrcMessage): List<TwitchMessage> = with(message) {
			return when (command) {
				"PRIVMSG"    -> listOf(parseFromIrc(message))
				"NOTICE"     -> listOf(parseNotice(message))
				"USERNOTICE" -> parseUserNotice(message)
				"CLEARCHAT"  -> listOf(parseClearChat(message))
				"CLEARMSG"   -> listOf() //TODO
				"HOSTTARGET" -> listOf(parseHostTarget(message))
				else         -> listOf()
			}
		}
	}
}
