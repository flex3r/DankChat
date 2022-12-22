package com.flxrs.dankchat.data.twitch.command

import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.HelixApiException
import com.flxrs.dankchat.data.api.helix.HelixError
import com.flxrs.dankchat.data.api.helix.dto.AnnouncementColor
import com.flxrs.dankchat.data.api.helix.dto.AnnouncementRequestDto
import com.flxrs.dankchat.data.api.helix.dto.WhisperRequestDto
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
            TwitchCommand.AnnouncePurple -> sendAnnouncement(command, context)

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
            TwitchCommand.Me             -> CommandResult.NotFound
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
            TwitchCommand.Whisper        -> sendWhisper(command, context)
        }
    }

    private suspend fun sendAnnouncement(command: TwitchCommand, context: CommandContext): CommandResult {
        val currentUserId = dankChatPreferenceStore.userIdString
            ?: return CommandResult.AcceptedTwitchCommand(command, response = "You must be logged in to use the ${context.trigger} command")
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Usage: ${context.trigger} <message> - Call attention to your message with a highlight.")
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
            onSuccess = { CommandResult.AcceptedTwitchCommand(command) },
            onFailure = {
                val response = "Failed to send announcement - ${it.toErrorMessage()}"
                CommandResult.AcceptedTwitchCommand(command,response)
            }
        )
    }

    private suspend fun sendWhisper(command: TwitchCommand, context: CommandContext): CommandResult {
        val currentUserId = dankChatPreferenceStore.userIdString
            ?: return CommandResult.AcceptedTwitchCommand(command, response = "You must be logged in to use the ${context.trigger} command")
        val args = context.args
        if (args.size < 2 || args[0].isBlank() || args[1].isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Usage: ${context.trigger} <username> <message>")
        }

        val targetName = args[0]
        val targetId = helixApiClient.getUserIdByName(targetName).getOrElse {
            return CommandResult.AcceptedTwitchCommand(command, response = "No user matching that username.")
        }
        val request = WhisperRequestDto(args.drop(1).joinToString(separator = " "))
        val result = helixApiClient.postWhisper(currentUserId, targetId, request)
        return result.fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = "Whisper sent.") },
            onFailure = {
                val response = "Failed to send whisper - ${it.toErrorMessage()}"
                CommandResult.AcceptedTwitchCommand(command,response)
            }
        )
    }

    private fun Throwable.toErrorMessage(): String {
        if (this !is HelixApiException) {
            return GENERIC_ERROR_MESSAGE
        }

        return when (error) {
            HelixError.BadRequest,
            HelixError.Forbidden            -> "You don't have permission to perform that action."

            HelixError.Unauthorized         -> message ?: GENERIC_ERROR_MESSAGE
            HelixError.MissingScopes        -> "Missing required scope. Re-login with your account and try again."
            HelixError.NotLoggedIn          -> "Missing login credentials. Re-login with your account and try again."
            HelixError.WhisperSelf          -> "You cannot whisper yourself."
            HelixError.NoVerifiedPhone      -> "Due to Twitch restrictions, you are now required to have a verified phone number to send whispers. You can add a phone number in Twitch settings. https://www.twitch.tv/settings/security"
            HelixError.RecipientBlockedUser -> "The recipient doesn't allow whispers from strangers or you directly."
            HelixError.RateLimited          -> "Too many requests. Please try again later."
            HelixError.WhisperRateLimited   -> "You may only whisper a maximum of 40 unique recipients per day. Within the per day limit, you may whisper a maximum of 3 whispers per second and a maximum of 100 whispers per minute."
            HelixError.Unknown              -> GENERIC_ERROR_MESSAGE
        }
    }

    companion object {
        private const val GENERIC_ERROR_MESSAGE = "An unknown error has occurred."
    }
}

