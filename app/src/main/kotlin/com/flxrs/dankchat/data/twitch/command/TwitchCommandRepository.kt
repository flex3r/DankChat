package com.flxrs.dankchat.data.twitch.command

import android.util.Log
import com.flxrs.dankchat.data.*
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
        val currentUserId = dankChatPreferenceStore.userIdString ?: return CommandResult.AcceptedTwitchCommand(
            command = command,
            response = "You must be logged in to use the ${context.trigger} command"
        )

        return when (command) {
            TwitchCommand.Announce,
            TwitchCommand.AnnounceBlue,
            TwitchCommand.AnnounceGreen,
            TwitchCommand.AnnounceOrange,
            TwitchCommand.AnnouncePurple -> sendAnnouncement(command, currentUserId, context)

            TwitchCommand.Ban            -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Clear          -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Color          -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Commercial     -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Delete         -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Disconnect     -> CommandResult.NotFound
            TwitchCommand.EmoteOnly      -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.EmoteOnlyOff   -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Followers      -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.FollowersOff   -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Marker         -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Me             -> CommandResult.NotFound
            TwitchCommand.Mod            -> addModerator(command, context)
            TwitchCommand.Mods           -> getModerators(command, context)
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
            TwitchCommand.Unmod          -> removeModerator(command, context)
            TwitchCommand.Unraid         -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Untimeout      -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Unvip          -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Vip            -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Vips           -> CommandResult.Message(context.originalMessage) // TODO
            TwitchCommand.Whisper        -> sendWhisper(command, currentUserId, context)
        }
    }

    private suspend fun sendAnnouncement(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
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
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun sendWhisper(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        val args = context.args
        if (args.size < 2 || args[0].isBlank() || args[1].isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Usage: ${context.trigger} <username> <message>")
        }

        val targetName = args[0]
        val targetId = helixApiClient.getUserIdByName(targetName.toUserName()).getOrElse {
            return CommandResult.AcceptedTwitchCommand(command, response = "No user matching that username.")
        }
        val request = WhisperRequestDto(args.drop(1).joinToString(separator = " "))
        val result = helixApiClient.postWhisper(currentUserId, targetId, request)
        return result.fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = "Whisper sent.") },
            onFailure = {
                val response = "Failed to send whisper - ${it.toErrorMessage()}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun getModerators(command: TwitchCommand, context: CommandContext): CommandResult {
        return helixApiClient.getModerators(context.channelId).fold(
            onSuccess = { result ->
                val users = result.joinToString { it.userLogin.formatWithDisplayName(it.userName) }
                CommandResult.AcceptedTwitchCommand(command, response = "The moderators of this channel are $users.")
            },
            onFailure = {
                val response = "Failed to get moderators - ${it.toErrorMessage()}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun addModerator(command: TwitchCommand, context: CommandContext): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Usage: ${context.trigger} <username> - Grant moderation status to a user.")
        }

        val target = helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
            return CommandResult.AcceptedTwitchCommand(command, response = "No user matching that username.")
        }

        val targetId = target.id
        val targetName = target.displayName
        return helixApiClient.postModerator(context.channelId, targetId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = "You have added $targetName as a moderator of this channel.") },
            onFailure = {
                val response = "Failed to add channel moderator - ${it.toErrorMessage(targetName)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun removeModerator(command: TwitchCommand, context: CommandContext): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Usage: ${context.trigger} <username> - Revoke moderation status from a user.")
        }

        val target = helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
            return CommandResult.AcceptedTwitchCommand(command, response = "No user matching that username.")
        }

        val targetId = target.id
        val targetName = target.displayName
        return helixApiClient.deleteModerator(context.channelId, targetId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = "You have removed $targetName as a moderator of this channel.") },
            onFailure = {
                val response = "Failed to remove channel moderator - ${it.toErrorMessage(targetName)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private fun Throwable.toErrorMessage(targetName: DisplayName? = null): String {
        Log.d(TAG, "Command failed: ", this)
        if (this !is HelixApiException) {
            return GENERIC_ERROR_MESSAGE
        }

        return when (error) {
            HelixError.UserNotAuthorized,
            HelixError.Forbidden                -> "You don't have permission to perform that action."

            HelixError.BadRequest               -> message ?: GENERIC_ERROR_MESSAGE
            HelixError.Unauthorized             -> message ?: GENERIC_ERROR_MESSAGE
            HelixError.MissingScopes            -> "Missing required scope. Re-login with your account and try again."
            HelixError.NotLoggedIn              -> "Missing login credentials. Re-login with your account and try again."
            HelixError.WhisperSelf              -> "You cannot whisper yourself."
            HelixError.NoVerifiedPhone          -> "Due to Twitch restrictions, you are now required to have a verified phone number to send whispers. You can add a phone number in Twitch settings. https://www.twitch.tv/settings/security"
            HelixError.RecipientBlockedUser     -> "The recipient doesn't allow whispers from strangers or you directly."
            HelixError.RateLimited              -> "You are being rate-limited by Twitch. Try again in a few seconds."
            HelixError.WhisperRateLimited       -> "You may only whisper a maximum of 40 unique recipients per day. Within the per day limit, you may whisper a maximum of 3 whispers per second and a maximum of 100 whispers per minute."
            HelixError.BroadcasterTokenRequired -> "Due to Twitch restrictions, this command can only be used by the broadcaster. Please use the Twitch website instead."
            HelixError.TargetAlreadyModded      -> "${targetName ?: "The target user"} is already a moderator of this channel."
            HelixError.TargetIsVip              -> "${targetName ?: "The target user"} is currently a VIP, /unvip them and retry this command."
            HelixError.TargetNotModded          -> "${targetName ?: "The target user"} is not a moderator of this channel."
            HelixError.Unknown                  -> GENERIC_ERROR_MESSAGE
        }
    }

    companion object {
        private val TAG = TwitchCommandRepository::class.java.simpleName
        private const val GENERIC_ERROR_MESSAGE = "An unknown error has occurred."
    }
}

