package com.flxrs.dankchat.ui.chat.suggestion

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.chat.UsersRepository
import com.flxrs.dankchat.data.repo.command.CommandRepository
import com.flxrs.dankchat.data.repo.emote.EmojiData
import com.flxrs.dankchat.data.repo.emote.EmojiRepository
import com.flxrs.dankchat.data.repo.emote.EmoteRepository
import com.flxrs.dankchat.data.repo.emote.EmoteUsageRepository
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.preferences.chat.SuggestionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class SuggestionProvider(
    private val emoteRepository: EmoteRepository,
    private val usersRepository: UsersRepository,
    private val commandRepository: CommandRepository,
    private val emoteUsageRepository: EmoteUsageRepository,
    private val emojiRepository: EmojiRepository,
) {
    fun getSuggestions(
        inputText: String,
        cursorPosition: Int,
        channel: UserName?,
        enabledTypes: List<SuggestionType>,
        prefixOnly: Boolean = false,
    ): Flow<List<Suggestion>> {
        if (inputText.isBlank() || channel == null || enabledTypes.isEmpty()) {
            return flowOf(emptyList())
        }

        val currentWord = extractCurrentWord(inputText, cursorPosition)
        if (currentWord.isBlank()) {
            return flowOf(emptyList())
        }

        val emotesEnabled = SuggestionType.Emotes in enabledTypes
        val usersEnabled = SuggestionType.Users in enabledTypes
        val commandsEnabled = SuggestionType.Commands in enabledTypes
        val supibotEnabled = SuggestionType.SupibotCommands in enabledTypes

        // ':' trigger: emote + emoji mode with reduced min chars
        val isEmoteTrigger = currentWord.startsWith(':')
        val emoteQuery =
            when {
                isEmoteTrigger -> currentWord.removePrefix(":")
                else -> currentWord
            }

        if ((isEmoteTrigger && emoteQuery.isEmpty()) || (!isEmoteTrigger && currentWord.length < MIN_SUGGESTION_CHARS)) {
            return flowOf(emptyList())
        }

        if (isEmoteTrigger && emotesEnabled) {
            val emojiResults = filterEmojis(emojiRepository.emojis.value, emoteQuery)
            return getScoredEmoteSuggestions(channel, emoteQuery).map { emoteResults ->
                mergeSorted(emoteResults, emojiResults)
            }
        }

        // '@' trigger: users only
        if (currentWord.startsWith('@') && usersEnabled) {
            return getUserSuggestions(channel, currentWord).map { users ->
                users.take(MAX_SUGGESTIONS)
            }
        }

        // Built-in command prefixes (/, $): twitch commands + supibot + custom
        val isCommandTrigger = currentWord.startsWith('/') || currentWord.startsWith('$')
        if (isCommandTrigger && (commandsEnabled || supibotEnabled)) {
            return getCommandSuggestions(channel, currentWord, commandsEnabled, supibotEnabled).map { commands ->
                commands.take(MAX_SUGGESTIONS)
            }
        }

        // Custom commands with arbitrary triggers — always checked since prefixes are dynamic
        val customCommandFlow = getCustomCommandSuggestions(currentWord)

        // In prefix-only mode, only custom commands can match (no free-type emotes/users)
        if (prefixOnly) {
            return customCommandFlow.map { it.take(MAX_SUGGESTIONS) }
        }

        // General (free type): score emotes + users together, plus custom commands
        val emoteFlow = when {
            emotesEnabled -> getScoredEmoteSuggestions(channel, currentWord)
            else -> flowOf(emptyList())
        }
        val userFlow = when {
            usersEnabled -> getScoredUserSuggestions(channel, currentWord)
            else -> flowOf(emptyList())
        }
        return combine(emoteFlow, userFlow, customCommandFlow) { emotes, users, customCommands ->
            val merged = mergeSorted(emotes, users)
            when {
                customCommands.isEmpty() -> merged
                else -> merged + customCommands
            }
        }
    }

    private fun getScoredEmoteSuggestions(
        channel: UserName,
        constraint: String,
    ): Flow<List<ScoredSuggestion>> = emoteRepository.getEmotes(channel).map { emotes ->
        val recentIds = emoteUsageRepository.recentEmoteIds.value
        filterEmotesScored(emotes.suggestions, constraint, recentIds)
    }

    private fun getScoredUserSuggestions(
        channel: UserName,
        constraint: String,
    ): Flow<List<ScoredSuggestion>> = usersRepository.getUsersFlow(channel).map { displayNameSet ->
        filterUsersScored(displayNameSet, constraint)
    }

    private fun getUserSuggestions(
        channel: UserName,
        constraint: String,
    ): Flow<List<Suggestion.UserSuggestion>> = usersRepository.getUsersFlow(channel).map { displayNameSet ->
        filterUsers(displayNameSet, constraint)
    }

    private fun getCustomCommandSuggestions(constraint: String): Flow<List<Suggestion.CommandSuggestion>> = commandRepository.getCustomCommandTriggers().map { triggers ->
        filterCommands(triggers, constraint)
    }

    private fun getCommandSuggestions(
        channel: UserName,
        constraint: String,
        commandsEnabled: Boolean,
        supibotEnabled: Boolean,
    ): Flow<List<Suggestion.CommandSuggestion>> = combine(
        commandRepository.getCommandTriggers(channel),
        commandRepository.getSupibotCommands(channel),
    ) { triggers, supibotCommands ->
        val combined = buildList {
            if (commandsEnabled) addAll(triggers)
            if (supibotEnabled) addAll(supibotCommands)
        }
        filterCommands(combined, constraint)
    }

    // Merge two pre-sorted lists in O(n+m) without intermediate allocations
    private fun mergeSorted(
        a: List<ScoredSuggestion>,
        b: List<ScoredSuggestion>,
    ): List<Suggestion> {
        val result = mutableListOf<Suggestion>()
        var i = 0
        var j = 0
        while (result.size < MAX_SUGGESTIONS && (i < a.size || j < b.size)) {
            val pick =
                when {
                    i >= a.size -> b[j++]
                    j >= b.size -> a[i++]
                    a[i].score <= b[j].score -> a[i++]
                    else -> b[j++]
                }
            result.add(pick.suggestion)
        }
        return result
    }

    internal fun extractCurrentWord(
        text: String,
        cursorPosition: Int,
    ): String {
        val cursorPos = cursorPosition.coerceIn(0, text.length)
        val separator = ' '

        var start = cursorPos
        while (start > 0 && text[start - 1] != separator) start--

        return text.substring(start, cursorPos)
    }

    // Scoring based on Chatterino2's SmartEmoteStrategy by Mm2PL
    // https://github.com/Chatterino/chatterino2/pull/4987
    internal fun scoreEmote(
        code: String,
        query: String,
        isRecentlyUsed: Boolean,
    ): Int {
        val matchIndex = code.indexOf(query, ignoreCase = true)
        if (matchIndex < 0) return NO_MATCH

        var caseDiffs = 0
        for (i in query.indices) {
            if (code[matchIndex + i] != query[i]) caseDiffs++
        }

        val extraChars = code.length - query.length
        val caseCost = if (caseDiffs == 0) -10 else caseDiffs
        val usageBoost = if (isRecentlyUsed) -50 else 0
        return caseCost + extraChars * 100 + usageBoost
    }

    internal fun filterEmotesScored(
        emotes: List<GenericEmote>,
        constraint: String,
        recentEmoteIds: Set<String>,
    ): List<ScoredSuggestion> = emotes
        .mapNotNull { emote ->
            val score = scoreEmote(emote.code, constraint, emote.id in recentEmoteIds)
            if (score == NO_MATCH) null else ScoredSuggestion(Suggestion.EmoteSuggestion(emote), score)
        }.sortedBy { it.score }

    // Score raw EmojiData, only wrap matches
    internal fun filterEmojis(
        emojis: List<EmojiData>,
        constraint: String,
    ): List<ScoredSuggestion> = emojis
        .mapNotNull { emoji ->
            val score = scoreEmote(emoji.code, constraint, isRecentlyUsed = false)
            if (score == NO_MATCH) null else ScoredSuggestion(Suggestion.EmojiSuggestion(emoji), score)
        }.sortedBy { it.score }

    // Filter raw DisplayName set, only wrap matches — used for @-prefix suggestions
    internal fun filterUsers(
        users: Set<DisplayName>,
        constraint: String,
    ): List<Suggestion.UserSuggestion> {
        val withAt = constraint.startsWith('@')
        return users
            .mapNotNull { name ->
                val suggestion = Suggestion.UserSuggestion(name, withLeadingAt = withAt)
                suggestion.takeIf { it.toString().startsWith(constraint, ignoreCase = true) }
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.value })
    }

    internal fun filterUsersScored(
        users: Set<DisplayName>,
        constraint: String,
    ): List<ScoredSuggestion> = users
        .mapNotNull { name ->
            val score = scoreEmote(name.value, constraint, isRecentlyUsed = false)
            if (score == NO_MATCH) null else ScoredSuggestion(Suggestion.UserSuggestion(name), score + USER_SCORE_PENALTY)
        }.sortedBy { it.score }

    // Filter raw command strings, only wrap matches
    internal fun filterCommands(
        commands: List<String>,
        constraint: String,
    ): List<Suggestion.CommandSuggestion> = commands
        .filter { it.startsWith(constraint, ignoreCase = true) }
        // Twitch and supibot commands can share a name, lazy list keys require unique entries
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .map { Suggestion.CommandSuggestion(it) }

    companion object {
        internal const val NO_MATCH = Int.MIN_VALUE
        internal const val USER_SCORE_PENALTY = 25
        private const val MAX_SUGGESTIONS = 50
        private const val MIN_SUGGESTION_CHARS = 2
    }
}

internal class ScoredSuggestion(
    val suggestion: Suggestion,
    val score: Int,
)
