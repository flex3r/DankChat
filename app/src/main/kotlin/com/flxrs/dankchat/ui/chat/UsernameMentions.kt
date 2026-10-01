package com.flxrs.dankchat.ui.chat

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.toUserName

// A whole word, optionally prefixed with @ and followed by punctuation, e.g. "@forsen," or "forsen:"
private val USERNAME_WORD_REGEX = Regex("""(?<!\S)(@?)([A-Za-z0-9_]+)(?=[.,!?;:]*(?:\s|$))""")

internal data class UsernameMention(
    val start: Int,
    val end: Int,
    val userName: UserName,
)

/**
 * Finds @mentions, plus words without the @ prefix that [isChatter] recognizes as a username.
 */
internal fun findUsernameMentions(
    message: String,
    isChatter: (UserName) -> Boolean = { false },
): List<UsernameMention> = USERNAME_WORD_REGEX
    .findAll(message)
    .mapNotNull { match ->
        val hasPrefix = match.groupValues[1].isNotEmpty()
        val userName = match.groupValues[2].toUserName()
        when {
            hasPrefix || isChatter(userName.lowercase()) -> {
                UsernameMention(
                    start = match.range.first,
                    end = match.range.last + 1,
                    userName = userName,
                )
            }

            else -> null
        }
    }.toList()
