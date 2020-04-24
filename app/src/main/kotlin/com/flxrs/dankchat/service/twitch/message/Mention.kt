package com.flxrs.dankchat.service.twitch.message

import com.flxrs.dankchat.service.twitch.emote.ChatMessageEmote
import java.util.regex.Pattern

sealed class Mention {
    abstract val matchUser: Boolean
    data class Phrase(val entry: String, override val matchUser: Boolean) : Mention()
    data class RegexPhrase(val entry: Regex, override val matchUser: Boolean) : Mention()
    data class User(val name: String, override val matchUser: Boolean = false) : Mention() {
        val regex = """\b$name\b""".toPattern(Pattern.CASE_INSENSITIVE).toRegex()
    }

    fun matches(message: String): Boolean {
        val split = message.split(" ")
        return when (this) {
            is User -> message.contains(regex)
            is Phrase -> split.any { it.equals(entry, true) }
            is RegexPhrase -> message.contains(entry)
        }
    }

    fun matchUser(user: Pair<String, String>): Boolean {
        if (!matchUser) return false
        return when(this) {
            is Phrase -> entry.equals(user.first, true) || entry.equals(user.second, true)
            is RegexPhrase -> user.first.contains(entry) || user.second.contains(entry)
            else -> false
        }
    }
}

fun List<Mention>.matches(message: String, user: Pair<String, String>, emotes: List<ChatMessageEmote>): Boolean = any {
    it.matches(message) || it.matchUser(user) || emotes.any { e -> it.matches(e.code) }
}
