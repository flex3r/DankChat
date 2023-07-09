package com.flxrs.dankchat.chat.suggestion

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.twitch.emote.GenericEmote

sealed interface Suggestion {
    data class EmoteSuggestion(val emote: GenericEmote) : Suggestion {
        override fun toString() = emote.toString()
    }

    data class UserSuggestion(val name: DisplayName, val withLeadingAt: Boolean = false) : Suggestion {
        override fun toString() = if (withLeadingAt) "@$name" else name.toString()
    }

    data class CommandSuggestion(val command: String) : Suggestion {
        override fun toString() = command
    }
}
