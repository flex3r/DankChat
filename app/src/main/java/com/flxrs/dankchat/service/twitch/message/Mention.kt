package com.flxrs.dankchat.service.twitch.message

import com.flxrs.dankchat.service.twitch.emote.ChatMessageEmote
import java.util.regex.Pattern

sealed class Mention {
    data class Phrase(val entry: String) : Mention()
    data class User(val name: String) : Mention() {
        val regex = "@?$name,?".toPattern(Pattern.CASE_INSENSITIVE).toRegex()
    }

    data class RegexPhrase(val entry: Regex) : Mention()

    fun matches(message: String): Boolean {
        val split = message.split(" ")
        return when (this) {
            is User -> split.any { it.contains(regex) }
            is Phrase -> split.any { it.equals(entry, true) }
            is RegexPhrase -> message.contains(entry)
        }
    }
}

fun List<Mention>.matches(message: String, emotes: List<ChatMessageEmote>): Boolean = any {
    it.matches(message) || emotes.any { e -> it.matches(e.code) }
}
