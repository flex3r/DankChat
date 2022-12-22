package com.flxrs.dankchat.data.twitch.command

import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.HelixApiException
import com.flxrs.dankchat.data.api.helix.HelixError
import com.flxrs.dankchat.data.api.helix.dto.AnnouncementColor
import com.flxrs.dankchat.data.api.helix.dto.AnnouncementRequestDto
import com.flxrs.dankchat.data.repo.CommandResult
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchCommandRepository @Inject constructor(
    private val helixApiClient: HelixApiClient,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
) {

    suspend fun handleTwitchCommand(command: TwitchCommand, context: CommandContext): CommandResult {
        return when (command) {
            TwitchCommand.Announce,
            TwitchCommand.AnnounceBlue,
            TwitchCommand.AnnounceGreen,
            TwitchCommand.AnnounceOrange,
            TwitchCommand.AnnouncePurple -> handleAnnouncement(command, context)

            TwitchCommand.Ban            -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Clear          -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Color          -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Commercial     -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Delete         -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Disconnect     -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.EmoteOnly      -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.EmoteOnlyOff   -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Followers      -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.FollowersOff   -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Marker         -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Me             -> CommandResult.Message(context.originalMessage)
            TwitchCommand.Mod            -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Mods           -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.R9kBeta        -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.R9kBetaOff     -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Raid           -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Slow           -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.SlowOff        -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Subscribers    -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.SubscribersOff -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Timeout        -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Unban          -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.UniqueChat     -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.UniqueChatOff  -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Unmod          -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Unraid         -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Untimeout      -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Unvip          -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Vip            -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Vips           -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Whisper        -> CommandResult.Message(context.originalMessage) // TODO
        }
    }

    private suspend fun handleAnnouncement(command: TwitchCommand, context: CommandContext): CommandResult {
        val currentUserId = dankChatPreferenceStore.userIdString
            ?: return CommandResult.AcceptedWithResponse("You must be logged in to use the ${context.trigger} command")
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedWithResponse("Usage: ${context.trigger} <message> - Call attention to your message with a highlight.")
        }

        val message = args.joinToString(" ")
        val color = when (command) {
            TwitchCommand.AnnounceBlue   -> AnnouncementColor.Blue
            TwitchCommand.AnnounceGreen  -> AnnouncementColor.Green
            TwitchCommand.AnnounceOrange -> AnnouncementColor.Orange
            TwitchCommand.AnnouncePurple -> AnnouncementColor.Purple
            else                         -> AnnouncementColor.Primary
        }
        val request = AnnouncementRequestDto(message, color)
        val result = helixApiClient.postAnnouncement(context.channelId, currentUserId, request)
        return result.fold(
            onSuccess = { CommandResult.Accepted },
            onFailure = {
                val response = "Failed to send announcement - ${it.toErrorMessage()}"
                CommandResult.AcceptedWithResponse(response)
            }
        )
    }

    private fun Throwable.toErrorMessage(): String {
        if (this !is HelixApiException){
            return genericErrorMessage
        }

        return when(error) {
            HelixError.BadRequest,
            HelixError.Forbidden,
            HelixError.Unauthorized  -> message ?: genericErrorMessage
            HelixError.MissingScopes -> "Missing required scope. Re-login with your account and try again."
            HelixError.NotLoggedIn   -> "Missing login credentials. Re-login with your account and try again."
            HelixError.Unknown       -> genericErrorMessage
        }
    }

    companion object {
        private const val genericErrorMessage = "An unknown error has occurred."
    }
}

