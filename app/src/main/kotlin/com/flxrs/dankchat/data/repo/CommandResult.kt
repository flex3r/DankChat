package com.flxrs.dankchat.data.repo

sealed class CommandResult {
    object Accepted : CommandResult()
    data class AcceptedWithResponse(val response: String) : CommandResult()
    data class Message(val message: String) : CommandResult()
    object NotFound : CommandResult()
}