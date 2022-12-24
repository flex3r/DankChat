package com.flxrs.dankchat.data.repo

import com.flxrs.dankchat.data.twitch.command.TwitchCommand

sealed class CommandResult {
    object Accepted : CommandResult()
    data class AcceptedTwitchCommand(val command: TwitchCommand, val response: String? = null) : CommandResult()
    data class AcceptedWithResponse(val response: String) : CommandResult()
    data class Message(val message: String) : CommandResult()
    object NotFound : CommandResult()
    object IrcCommand : CommandResult()
    object Blocked : CommandResult()
}