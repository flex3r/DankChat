package com.flxrs.dankchat.ui.chat

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.toUserName

private val USERNAME_MENTION_REGEX = Regex("""(?<!\S)@(\w+)(?=[.,!?;:]*(?:\s|$))""")

internal data class UsernameMention(
    val start: Int,
    val end: Int,
    val userName: UserName,
)

internal fun findUsernameMentions(message: String): List<UsernameMention> = USERNAME_MENTION_REGEX
    .findAll(message)
    .map { match ->
        UsernameMention(
            start = match.range.first,
            end = match.range.last + 1,
            userName = match.groupValues[1].toUserName(),
        )
    }.toList()
