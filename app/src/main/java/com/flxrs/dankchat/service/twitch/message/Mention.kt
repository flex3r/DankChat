package com.flxrs.dankchat.service.twitch.message

import com.flxrs.dankchat.service.twitch.emote.ChatMessageEmote

sealed class Mention {
    data class Phrase(val entry: String) : Mention()
    data class RegexPhrase(val entry: Regex) : Mention()

    fun matches(message: String): Boolean {
        return when (this) {
            is Phrase -> message.split(" ").any { it.equals(entry, true) }
            is RegexPhrase -> entry.containsMatchIn(message)
        }
    }
}

fun List<Mention>.matches(message: String, emotes: List<ChatMessageEmote>): Boolean = any {
    it.matches(message) || emotes.any { e -> it.matches(e.code) }
}
