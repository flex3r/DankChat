package com.flxrs.dankchat.data.repo.command

import com.flxrs.dankchat.data.twitch.command.TwitchCommand

sealed interface CommandResult {
    data object Accepted : CommandResult
    data class AcceptedTwitchCommand(val command: TwitchCommand, val response: String? = null) : CommandResult
    data class AcceptedWithResponse(val response: String) : CommandResult
    data class Message(val message: String) : CommandResult
    data object NotFound : CommandResult
    data object IrcCommand : CommandResult
    data object Blocked : CommandResult
}
