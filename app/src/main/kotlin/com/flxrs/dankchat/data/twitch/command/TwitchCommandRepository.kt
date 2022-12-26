package com.flxrs.dankchat.data.twitch.command

import android.util.Log
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.HelixApiException
import com.flxrs.dankchat.data.api.helix.HelixError
import com.flxrs.dankchat.data.api.helix.dto.*
import com.flxrs.dankchat.data.repo.CommandResult
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.DateTimeUtils
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchCommandRepository @Inject constructor(
    private val helixApiClient: HelixApiClient,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
) {

    fun isIrcCommand(trigger: String): Boolean = trigger in ALLOWED_IRC_COMMAND_TRIGGERS

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

            TwitchCommand.Ban            -> banUser(command, currentUserId, context)
            TwitchCommand.Clear          -> clearChat(command, currentUserId, context)
            TwitchCommand.Color          -> updateColor(command, currentUserId, context)
            TwitchCommand.Commercial     -> CommandResult.IrcCommand // TODO
            TwitchCommand.Delete         -> deleteMessage(command, currentUserId, context)
            TwitchCommand.EmoteOnly      -> CommandResult.IrcCommand // TODO
            TwitchCommand.EmoteOnlyOff   -> CommandResult.IrcCommand // TODO
            TwitchCommand.Followers      -> CommandResult.IrcCommand // TODO
            TwitchCommand.FollowersOff   -> CommandResult.IrcCommand // TODO
            TwitchCommand.Marker         -> createMarker(command, context)
            TwitchCommand.Mod            -> addModerator(command, context)
            TwitchCommand.Mods           -> getModerators(command, context)
            TwitchCommand.R9kBeta        -> CommandResult.IrcCommand // TODO
            TwitchCommand.R9kBetaOff     -> CommandResult.IrcCommand // TODO
            TwitchCommand.Raid           -> CommandResult.IrcCommand // TODO
            TwitchCommand.Slow           -> CommandResult.IrcCommand // TODO
            TwitchCommand.SlowOff        -> CommandResult.IrcCommand // TODO
            TwitchCommand.Subscribers    -> CommandResult.IrcCommand // TODO
            TwitchCommand.SubscribersOff -> CommandResult.IrcCommand // TODO
            TwitchCommand.Timeout        -> timeoutUser(command, currentUserId, context)
            TwitchCommand.Unban          -> unbanUser(command, currentUserId, context)
            TwitchCommand.UniqueChat     -> CommandResult.IrcCommand // TODO
            TwitchCommand.UniqueChatOff  -> CommandResult.IrcCommand // TODO
            TwitchCommand.Unmod          -> removeModerator(command, context)
            TwitchCommand.Unraid         -> CommandResult.IrcCommand // TODO
            TwitchCommand.Untimeout      -> unbanUser(command, currentUserId, context)
            TwitchCommand.Unvip          -> removeVip(command, context)
            TwitchCommand.Vip            -> addVip(command, context)
            TwitchCommand.Vips           -> getVips(command, context)
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
                val response = "Failed to send announcement - ${it.toErrorMessage(command)}"
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
                val response = "Failed to send whisper - ${it.toErrorMessage(command)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun getModerators(command: TwitchCommand, context: CommandContext): CommandResult {
        return helixApiClient.getModerators(context.channelId).fold(
            onSuccess = { result ->
                when {
                    result.isEmpty() -> CommandResult.AcceptedTwitchCommand(command, response = "This channel does not have any moderators.")
                    else             -> {
                        val users = result.joinToString { it.userLogin.formatWithDisplayName(it.userName) }
                        CommandResult.AcceptedTwitchCommand(command, response = "The moderators of this channel are $users.")
                    }
                }
                val users = result.joinToString { it.userLogin.formatWithDisplayName(it.userName) }
                CommandResult.AcceptedTwitchCommand(command, response = "The moderators of this channel are $users.")
            },
            onFailure = {
                val response = "Failed to list moderators - ${it.toErrorMessage(command)}"
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
        val targetUser = target.name
        return helixApiClient.postModerator(context.channelId, targetId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = "You have added $targetUser as a moderator of this channel.") },
            onFailure = {
                val response = "Failed to add channel moderator - ${it.toErrorMessage(command, targetUser)}"
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
        val targetUser = target.name
        return helixApiClient.deleteModerator(context.channelId, targetId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = "You have removed $targetUser as a moderator of this channel.") },
            onFailure = {
                val response = "Failed to remove channel moderator - ${it.toErrorMessage(command, targetUser)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun getVips(command: TwitchCommand, context: CommandContext): CommandResult {
        return helixApiClient.getVips(context.channelId).fold(
            onSuccess = { result ->
                when {
                    result.isEmpty() -> CommandResult.AcceptedTwitchCommand(command, response = "This channel does not have any VIPs.")
                    else             -> {
                        val users = result.joinToString { it.userLogin.formatWithDisplayName(it.userName) }
                        CommandResult.AcceptedTwitchCommand(command, response = "The vips of this channel are $users.")
                    }
                }
            },
            onFailure = {
                val response = "Failed to list VIPs - ${it.toErrorMessage(command)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun addVip(command: TwitchCommand, context: CommandContext): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Usage: ${context.trigger} <username> - Grant VIP status to a user.")
        }

        val target = helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
            return CommandResult.AcceptedTwitchCommand(command, response = "No user matching that username.")
        }

        val targetId = target.id
        val targetUser = target.name
        return helixApiClient.postVip(context.channelId, targetId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = "You have added $targetUser as a VIP of this channel.") },
            onFailure = {
                val response = "Failed to add VIP - ${it.toErrorMessage(command, targetUser)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun removeVip(command: TwitchCommand, context: CommandContext): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Usage: ${context.trigger} <username> - Revoke VIP status from a user.")
        }

        val target = helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
            return CommandResult.AcceptedTwitchCommand(command, response = "No user matching that username.")
        }

        val targetId = target.id
        val targetUser = target.name
        return helixApiClient.deleteVip(context.channelId, targetId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = "You have removed $targetUser as a VIP of this channel.") },
            onFailure = {
                val response = "Failed to remove VIP - ${it.toErrorMessage(command, targetUser)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun banUser(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            val usageResponse = "Usage: ${context.trigger} <username> [reason] - Permanently prevent a user from chatting. " +
                    "Reason is optional and will be shown to the target user and other moderators. Use /unban to remove a ban."
            return CommandResult.AcceptedTwitchCommand(command, usageResponse)
        }

        val target = helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
            return CommandResult.AcceptedTwitchCommand(command, response = "No user matching that username.")
        }

        if (target.id == currentUserId) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Failed to ban user - You cannot ban yourself.")
        } else if (target.id == context.channelId) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Failed to ban user - You cannot ban the broadcaster.")
        }

        val reason = args.drop(1).joinToString(separator = " ").ifBlank { null }

        val targetId = target.id
        val targetUser = target.name
        val request = BanRequestDto(BanRequestDataDto(targetId, duration = null, reason = reason))
        return helixApiClient.postBan(context.channelId, currentUserId, request).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command) },
            onFailure = {
                val response = "Failed to ban user - ${it.toErrorMessage(command, targetUser)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun unbanUser(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            val usageResponse = "Usage: ${context.trigger} <username> - Removes a ban on a user."
            return CommandResult.AcceptedTwitchCommand(command, usageResponse)
        }

        val target = helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
            return CommandResult.AcceptedTwitchCommand(command, response = "No user matching that username.")
        }


        val targetId = target.id
        return helixApiClient.deleteBan(context.channelId, currentUserId, targetId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command) },
            onFailure = {
                val response = "Failed to unban user - ${it.toErrorMessage(command, target.name)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun timeoutUser(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        val args = context.args
        val usageResponse = "Usage: ${context.trigger} <username> [duration][time unit] [reason] - " +
                "Temporarily prevent a user from chatting. Duration (optional, " +
                "default=10 minutes) must be a positive integer; time unit " +
                "(optional, default=s) must be one of s, m, h, d, w; maximum " +
                "duration is 2 weeks. Combinations like 1d2h are also allowed. " +
                "Reason is optional and will be shown to the target user and other " +
                "moderators. Use /untimeout to remove a timeout."
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, usageResponse)
        }

        val target = helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
            return CommandResult.AcceptedTwitchCommand(command, response = "No user matching that username.")
        }

        if (target.id == currentUserId) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Failed to ban user - You cannot timeout yourself.")
        } else if (target.id == context.channelId) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Failed to ban user - You cannot timeout the broadcaster.")
        }

        val durationInSeconds = when {
            args.size > 1 && args[1].isNotBlank() -> DateTimeUtils.durationToSeconds(args[1].trim()) ?: return CommandResult.AcceptedTwitchCommand(command, usageResponse)
            else                                  -> 60 * 10
        }
        val reason = args.drop(2).joinToString(separator = " ").ifBlank { null }

        val targetId = target.id
        val request = BanRequestDto(BanRequestDataDto(targetId, duration = durationInSeconds, reason = reason))
        return helixApiClient.postBan(context.channelId, currentUserId, request).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command) },
            onFailure = {
                val response = "Failed to timeout user - ${it.toErrorMessage(command, target.name)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun clearChat(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        return helixApiClient.deleteMessages(context.channelId, currentUserId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command) },
            onFailure = {
                val response = "Failed to delete chat messages - ${it.toErrorMessage(command)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun deleteMessage(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Usage: /delete <msg-id> - Deletes the specified message.")
        }

        val messageId = args.first()
        val parsedId = runCatching { UUID.fromString(messageId) }
        if (parsedId.isFailure) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Invalid msg-id: \"$messageId\".")
        }

        return helixApiClient.deleteMessages(context.channelId, currentUserId, messageId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command) },
            onFailure = {
                val response = "Failed to delete chat messages - ${it.toErrorMessage(command)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun updateColor(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            val usage = "Usage: /color <color> - Color must be one of Twitch's supported colors (${VALID_HELIX_COLORS.joinToString()}) or a hex code (#000000) if you have Turbo or Prime."
            return CommandResult.AcceptedTwitchCommand(command, response = usage)
        }

        val colorArg = args.first().lowercase()
        val color = HELIX_COLOR_REPLACEMENTS[colorArg] ?: colorArg

        return helixApiClient.putUserChatColor(currentUserId, color).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = "Your color has been changed to $color") },
            onFailure = {
                val response = "Failed to change color to $color - ${it.toErrorMessage(command)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun createMarker(command: TwitchCommand, context: CommandContext): CommandResult {
        val description = context.args.joinToString(separator = " ").take(140).ifBlank { null }
        val request = MarkerRequestDto(context.channelId, description)

        return helixApiClient.postMarker(request).fold(
            onSuccess = { result ->
                val marker = result.data.firstOrNull()
                val time = marker?.positionSeconds?.let { " at ${DateTimeUtils.formatSeconds(it)}" }.orEmpty()
                val markerDescription = marker?.description?.let { ": \"$it\"" }.orEmpty()
                val response = "Successfully added a stream marker$time$markerDescription."
                CommandResult.AcceptedTwitchCommand(command, response)
            },
            onFailure = {
                val response = "Failed to create stream marker - ${it.toErrorMessage(command)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private fun Throwable.toErrorMessage(command: TwitchCommand, targetUser: UserName? = null): String {
        Log.v(TAG, "Command failed: $this")
        if (this !is HelixApiException) {
            return GENERIC_ERROR_MESSAGE
        }

        return when (error) {
            HelixError.UserNotAuthorized        -> "You don't have permission to perform that action."
            HelixError.Forwarded                -> message ?: GENERIC_ERROR_MESSAGE
            HelixError.MissingScopes            -> "Missing required scope. Re-login with your account and try again."
            HelixError.NotLoggedIn              -> "Missing login credentials. Re-login with your account and try again."
            HelixError.WhisperSelf              -> "You cannot whisper yourself."
            HelixError.NoVerifiedPhone          -> "Due to Twitch restrictions, you are now required to have a verified phone number to send whispers. You can add a phone number in Twitch settings. https://www.twitch.tv/settings/security"
            HelixError.RecipientBlockedUser     -> "The recipient doesn't allow whispers from strangers or you directly."
            HelixError.RateLimited              -> "You are being rate-limited by Twitch. Try again in a few seconds."
            HelixError.WhisperRateLimited       -> "You may only whisper a maximum of 40 unique recipients per day. Within the per day limit, you may whisper a maximum of 3 whispers per second and a maximum of 100 whispers per minute."
            HelixError.BroadcasterTokenRequired -> "Due to Twitch restrictions, this command can only be used by the broadcaster. Please use the Twitch website instead."
            HelixError.TargetAlreadyModded      -> "${targetUser ?: "The target user"} is already a moderator of this channel."
            HelixError.TargetIsVip              -> "${targetUser ?: "The target user"} is currently a VIP, /unvip them and retry this command."
            HelixError.TargetNotModded          -> "${targetUser ?: "The target user"} is not a moderator of this channel."
            HelixError.TargetNotBanned          -> "${targetUser ?: "The target user"} is not banned from this channel."
            HelixError.TargetAlreadyBanned      -> "${targetUser ?: "The target user"} is already banned in this channel."
            HelixError.TargetCannotBeBanned     -> "You cannot ${command.trigger} ${targetUser ?: "this user"}."
            HelixError.ConflictingBanOperation  -> "There was a conflicting ban operation on this user. Please try again."
            HelixError.InvalidColor             -> "Color must be one of Twitch's supported colors (${VALID_HELIX_COLORS.joinToString()}) or a hex code (#000000) if you have Turbo or Prime."
            is HelixError.MarkerError           -> error.message ?: GENERIC_ERROR_MESSAGE
            HelixError.Unknown                  -> GENERIC_ERROR_MESSAGE
        }
    }

    companion object {
        private val TAG = TwitchCommandRepository::class.java.simpleName
        private const val GENERIC_ERROR_MESSAGE = "An unknown error has occurred."
        private val ALLOWED_IRC_COMMAND_TRIGGERS = listOf("me", "disconnect")
        private val VALID_HELIX_COLORS = listOf(
            "blue",
            "blue_violet",
            "cadet_blue",
            "chocolate",
            "coral",
            "dodger_blue",
            "firebrick",
            "golden_rod",
            "green",
            "hot_pink",
            "orange_red",
            "red",
            "sea_green",
            "spring_green",
            "yellow_green",
        )

        private val HELIX_COLOR_REPLACEMENTS = mapOf(
            "blueviolet" to "blue_violet",
            "cadetblue" to "cadet_blue",
            "dodgerblue" to "dodger_blue",
            "goldenrod" to "golden_rod",
            "hotpink" to "hot_pink",
            "orangered" to "orange_red",
            "seagreen" to "sea_green",
            "springgreen" to "spring_green",
            "yellowgreen" to "yellow_green",
        )
    }
}

