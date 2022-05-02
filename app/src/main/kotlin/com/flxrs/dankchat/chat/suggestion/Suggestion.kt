package com.flxrs.dankchat.chat.suggestion

import com.flxrs.dankchat.data.twitch.emote.GenericEmote

sealed class Suggestion {
    data class EmoteSuggestion(val emote: GenericEmote) : Suggestion() {
        override fun toString() = emote.toString()
    }

    data class UserSuggestion(val name: String, val withLeadingAt: Boolean = false) : Suggestion() {
        override fun toString() = if (withLeadingAt) "@$name" else name
    }

    data class CommandSuggestion(val command: String) : Suggestion() {
        override fun toString() = command
    }
}