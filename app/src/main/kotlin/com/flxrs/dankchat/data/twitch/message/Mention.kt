package com.flxrs.dankchat.data.twitch.message

sealed class Mention {
    abstract val matchUser: Boolean

    data class Phrase(val entry: String, override val matchUser: Boolean) : Mention()
    data class RegexPhrase(val entry: Regex, override val matchUser: Boolean) : Mention()
    data class User(val name: String, override val matchUser: Boolean = false) : Mention() {
        val regex = """\b$name\b""".toRegex(RegexOption.IGNORE_CASE)
    }

    fun matches(message: String): Boolean {
        val split = message.split(" ")
        return when (this) {
            is User        -> message.contains(regex)
            is Phrase      -> split.any { it.equals(entry, true) }
            is RegexPhrase -> message.contains(entry)
        }
    }

    fun matchUser(user: String, displayName: String): Boolean {
        if (!matchUser) return false
        return when (this) {
            is Phrase      -> entry.equals(user, true) || entry.equals(displayName, true)
            is RegexPhrase -> user.contains(entry) || displayName.contains(entry)
            else           -> false
        }
    }
}

fun List<Mention>.matches(message: TwitchMessage): Boolean = any {
    it.matches(message.message) || it.matchUser(message.name, message.displayName) || message.emotes.any { e -> it.matches(e.code) }
}
