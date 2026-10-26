package com.flxrs.dankchat.ui.chat.search

import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.ui.chat.suggestion.Suggestion

object SearchFilterSuggestions {
    private val KEYWORD_FILTERS =
        listOf(
            Suggestion.FilterSuggestion("from:", R.string.search_filter_by_username),
            Suggestion.FilterSuggestion("has:link", R.string.search_filter_has_link),
            Suggestion.FilterSuggestion("has:emote", R.string.search_filter_has_emote),
            Suggestion.FilterSuggestion("badge:", R.string.search_filter_by_badge),
        )

    private val HAS_VALUES =
        listOf(
            Suggestion.FilterSuggestion("has:link", R.string.search_filter_has_link, displayText = "link"),
            Suggestion.FilterSuggestion("has:emote", R.string.search_filter_has_emote, displayText = "emote"),
        )

    private const val MAX_VALUE_SUGGESTIONS = 10
    private const val MIN_KEYWORD_CHARS = 2

    fun filter(
        input: String,
        users: Set<DisplayName> = emptySet(),
        badgeNames: Set<String> = emptySet(),
    ): List<Suggestion> {
        val lastToken = input.lastFilterToken()
        val colonIndex = lastToken.indexOf(':')
        if (colonIndex < 0) {
            if (lastToken.length < MIN_KEYWORD_CHARS) {
                return emptyList()
            }
            return KEYWORD_FILTERS.filter { suggestion ->
                suggestion.keyword.startsWith(lastToken, ignoreCase = true) && !suggestion.keyword.equals(lastToken, ignoreCase = true)
            }
        }

        val prefix = lastToken.substring(0, colonIndex + 1)
        val partial = lastToken.substring(colonIndex + 1)

        return when (prefix.lowercase()) {
            "from:" -> {
                users
                    .filter { it.value.startsWith(partial, ignoreCase = true) && !it.value.equals(partial, ignoreCase = true) }
                    .take(MAX_VALUE_SUGGESTIONS)
                    .map { Suggestion.FilterSuggestion(keyword = "from:${it.value}", descriptionRes = R.string.search_filter_user, displayText = it.value) }
            }

            "has:" -> {
                HAS_VALUES.filter { suggestion ->
                    val value = suggestion.displayText.orEmpty()
                    value.startsWith(partial, ignoreCase = true) && !value.equals(partial, ignoreCase = true)
                }
            }

            "badge:" -> {
                badgeNames
                    .filter { it.startsWith(partial, ignoreCase = true) && !it.equals(partial, ignoreCase = true) }
                    .take(MAX_VALUE_SUGGESTIONS)
                    .map { Suggestion.FilterSuggestion(keyword = "badge:$it", descriptionRes = R.string.search_filter_badge, displayText = it) }
            }

            else -> {
                emptyList()
            }
        }
    }

    fun needsBadgeNames(input: String): Boolean = input.lastFilterToken().startsWith("badge:", ignoreCase = true)

    private fun String.lastFilterToken(): String = trimEnd()
        .substringAfterLast(' ')
        .removePrefix("-")
        .removePrefix("!")
}
