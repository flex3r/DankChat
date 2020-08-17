package com.flxrs.dankchat.chat.suggestion

import com.flxrs.dankchat.service.twitch.emote.GenericEmote

sealed class Suggestion {
    data class EmoteSuggestion(val emote: GenericEmote) : Suggestion(), Comparable<Suggestion> {
        override fun toString() = emote.toString()
        override fun compareTo(other: Suggestion): Int {
            return when (other) {
                is UserSuggestion, is CommandSuggestion -> -1
                is EmoteSuggestion -> emote.code.compareTo(other.emote.code)
            }
        }
    }

    data class UserSuggestion(val name: String) : Suggestion(), Comparable<Suggestion> {
        override fun toString() = name
        override fun compareTo(other: Suggestion): Int {
            return when (other) {
                is UserSuggestion -> name.compareTo(other.name)
                is CommandSuggestion -> name.compareTo(other.command)
                is EmoteSuggestion -> 1
            }
        }
    }

    data class CommandSuggestion(val command: String) : Suggestion(), Comparable<Suggestion> {
        override fun toString() = command
        override fun compareTo(other: Suggestion): Int {
            return when (other) {
                is CommandSuggestion -> command.compareTo(other.command)
                is UserSuggestion -> command.compareTo(other.name)
                is EmoteSuggestion -> 1
            }
        }
    }
}