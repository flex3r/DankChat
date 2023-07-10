package com.flxrs.dankchat.data.twitch.command

import android.util.Log
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.HelixApiException
import com.flxrs.dankchat.data.api.helix.HelixError
import com.flxrs.dankchat.data.api.helix.dto.AnnouncementColor
import com.flxrs.dankchat.data.api.helix.dto.AnnouncementRequestDto
import com.flxrs.dankchat.data.api.helix.dto.BanRequestDataDto
import com.flxrs.dankchat.data.api.helix.dto.BanRequestDto
import com.flxrs.dankchat.data.api.helix.dto.ChatSettingsRequestDto
import com.flxrs.dankchat.data.api.helix.dto.CommercialRequestDto
import com.flxrs.dankchat.data.api.helix.dto.MarkerRequestDto
import com.flxrs.dankchat.data.api.helix.dto.ShieldModeRequestDto
import com.flxrs.dankchat.data.api.helix.dto.WhisperRequestDto
import com.flxrs.dankchat.data.repo.chat.UserState
import com.flxrs.dankchat.data.repo.command.CommandResult
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.DateTimeUtils
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchCommandRepository @Inject constructor(
    private val helixApiClient: HelixApiClient,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
) {

    fun isIrcCommand(trigger: String): Boolean = trigger in ALLOWED_IRC_COMMAND_TRIGGERS

    fun getAvailableCommandTriggers(room: RoomState, userState: UserState): List<String> {
        val currentUserId = dankChatPreferenceStore.userIdString ?: return emptyList()
        return when {
            room.channelId == currentUserId              -> TwitchCommand.ALL_COMMANDS
            room.channel in userState.moderationChannels -> TwitchCommand.MODERATOR_COMMANDS
            else                                         -> TwitchCommand.USER_COMMANDS
        }.map(TwitchCommand::trigger)
            .plus(ALLOWED_IRC_COMMANDS)
            .map { "/$it" }
    }

    fun findTwitchCommand(trigger: String): TwitchCommand? {
        if (trigger.first() !in ALLOWED_FIRST_TRIGGER_CHARS) {
            return null
        }

        val withoutFirstChar = trigger.drop(1)
        return TwitchCommand.ALL_COMMANDS.find { it.trigger == withoutFirstChar }
    }

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
            TwitchCommand.Commercial     -> startCommercial(command, context)
            TwitchCommand.Delete         -> deleteMessage(command, currentUserId, context)
            TwitchCommand.EmoteOnly      -> enableEmoteMode(command, currentUserId, context)
            TwitchCommand.EmoteOnlyOff   -> disableEmoteMode(command, currentUserId, context)
            TwitchCommand.Followers      -> enableFollowersMode(command, currentUserId, context)
            TwitchCommand.FollowersOff   -> disableFollowersMode(command, currentUserId, context)
            TwitchCommand.Marker         -> createMarker(command, context)
            TwitchCommand.Mod            -> addModerator(command, context)
            TwitchCommand.Mods           -> getModerators(command, context)
            TwitchCommand.R9kBeta        -> enableUniqueChatMode(command, currentUserId, context)
            TwitchCommand.R9kBetaOff     -> disableUniqueChatMode(command, currentUserId, context)
            TwitchCommand.Raid           -> startRaid(command, context)
            TwitchCommand.Shield,
            TwitchCommand.ShieldOff      -> toggleShieldMode(command, currentUserId, context)

            TwitchCommand.Slow           -> enableSlowMode(command, currentUserId, context)
            TwitchCommand.SlowOff        -> disableSlowMode(command, currentUserId, context)
            TwitchCommand.Subscribers    -> enableSubscriberMode(command, currentUserId, context)
            TwitchCommand.SubscribersOff -> disableSubscriberMode(command, currentUserId, context)
            TwitchCommand.Timeout        -> timeoutUser(command, currentUserId, context)
            TwitchCommand.Unban          -> unbanUser(command, currentUserId, context)
            TwitchCommand.UniqueChat     -> enableUniqueChatMode(command, currentUserId, context)
            TwitchCommand.UniqueChatOff  -> disableUniqueChatMode(command, currentUserId, context)
            TwitchCommand.Unmod          -> removeModerator(command, context)
            TwitchCommand.Unraid         -> cancelRaid(command, context)
            TwitchCommand.Untimeout      -> unbanUser(command, currentUserId, context)
            TwitchCommand.Unvip          -> removeVip(command, context)
            TwitchCommand.Vip            -> addVip(command, context)
            TwitchCommand.Vips           -> getVips(command, context)
            TwitchCommand.Whisper        -> sendWhisper(command, currentUserId, context.trigger, context.args)
            TwitchCommand.Shoutout       -> sendShoutout(command, currentUserId, context)
        }
    }

    suspend fun sendWhisper(command: TwitchCommand, currentUserId: UserId, trigger: String, args: List<String>): CommandResult {
        if (args.size < 2 || args[0].isBlank() || args[1].isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Usage: $trigger <username> <message>.")
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
                val markerDescription = result.description?.let { ": \"$it\"" }.orEmpty()
                val response = "Successfully added a stream marker at ${DateTimeUtils.formatSeconds(result.positionSeconds)}$markerDescription."
                CommandResult.AcceptedTwitchCommand(command, response)
            },
            onFailure = {
                val response = "Failed to create stream marker - ${it.toErrorMessage(command)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun startCommercial(command: TwitchCommand, context: CommandContext): CommandResult {
        val args = context.args
        val usage = "Usage: /commercial <length> - Starts a commercial with the specified duration for the current channel. Valid length options are 30, 60, 90, 120, 150, and 180 seconds."
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = usage)
        }

        val length = args.first().toIntOrNull() ?: return CommandResult.AcceptedTwitchCommand(command, response = usage)
        val request = CommercialRequestDto(context.channelId, length)
        return helixApiClient.postCommercial(request).fold(
            onSuccess = { result ->
                val response = "Starting ${result.length} second long commercial break. " +
                        "Keep in mind you are still live and not all viewers will receive a commercial. " +
                        "You may run another commercial in ${result.retryAfter} seconds."
                CommandResult.AcceptedTwitchCommand(command, response)
            },
            onFailure = {
                val response = "Failed to start commercial - ${it.toErrorMessage(command)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun startRaid(command: TwitchCommand, context: CommandContext): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            val usage = "Usage: /raid <username> - Raid a user. Only the broadcaster can start a raid."
            return CommandResult.AcceptedTwitchCommand(command, response = usage)
        }

        val target = helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
            return CommandResult.AcceptedTwitchCommand(command, response = "Invalid username: ${args.first()}")
        }

        return helixApiClient.postRaid(context.channelId, target.id).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = "You started to raid ${target.displayName}.") },
            onFailure = {
                val response = "Failed to start a raid - ${it.toErrorMessage(command)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun cancelRaid(command: TwitchCommand, context: CommandContext): CommandResult {
        return helixApiClient.deleteRaid(context.channelId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = "You cancelled the raid.") },
            onFailure = {
                val response = "Failed to cancel the raid - ${it.toErrorMessage(command)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun enableFollowersMode(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        val args = context.args
        val usage = "Usage: /followers [duration] - Enables followers-only mode (only users who have followed for 'duration' may chat). " +
                "Duration is optional and must be specified in the format like \"30m\", \"1w\", \"5d 12h\". " +
                "Must be less than 3 months. The default is \"0\" (no restriction)."
        val durationArg = args.joinToString(separator = " ").ifBlank { null }
        val duration = durationArg?.let {
            val seconds = DateTimeUtils.durationToSeconds(it) ?: return CommandResult.AcceptedTwitchCommand(command, response = usage)
            seconds / 60
        }

        if (duration != null && duration == context.roomState.followerModeDuration) {
            return CommandResult.AcceptedTwitchCommand(command, response = "This room is already in ${DateTimeUtils.formatSeconds(duration * 60)} followers-only mode.")
        }

        val request = ChatSettingsRequestDto(followerMode = true, followerModeDuration = duration)
        return updateChatSettings(command, currentUserId, context, request) { range ->
            val start = if (range.first == 0) "0s" else DateTimeUtils.formatSeconds(durationInSeconds = range.first * 60)
            val end = if (range.last == 0) "0s" else DateTimeUtils.formatSeconds(durationInSeconds = range.last * 60)
            "$start through $end"
        }
    }

    private suspend fun disableFollowersMode(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        if (!context.roomState.isFollowMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = "This room is not in followers-only mode.")
        }

        val request = ChatSettingsRequestDto(followerMode = false)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun enableEmoteMode(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        if (context.roomState.isEmoteMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = "This room is already in emote-only mode.")
        }

        val request = ChatSettingsRequestDto(emoteMode = true)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun disableEmoteMode(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        if (!context.roomState.isEmoteMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = "This room is not in emote-only mode.")
        }

        val request = ChatSettingsRequestDto(emoteMode = false)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun enableSubscriberMode(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        if (context.roomState.isSubscriberMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = "This room is already in subscribers-only mode.")
        }

        val request = ChatSettingsRequestDto(subscriberMode = true)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun disableSubscriberMode(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        if (!context.roomState.isSubscriberMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = "This room is not in subscribers-only mode.")
        }

        val request = ChatSettingsRequestDto(subscriberMode = false)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun enableUniqueChatMode(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        if (context.roomState.isUniqueChatMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = "This room is already in unique-chat mode.")
        }

        val request = ChatSettingsRequestDto(uniqueChatMode = true)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun disableUniqueChatMode(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        if (!context.roomState.isUniqueChatMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = "This room is not in unique-chat mode.")
        }

        val request = ChatSettingsRequestDto(uniqueChatMode = false)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun enableSlowMode(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        val args = context.args
        val duration = args.firstOrNull()?.toIntOrNull()
        if (duration == null) {
            val usage = "Usage: /slow [duration] - Enables slow mode (limit how often users may send messages)." +
                    "Duration (optional, default=30) must be a positive number of seconds. Use /slowoff to disable."
            return CommandResult.AcceptedTwitchCommand(command, usage)
        }

        if (duration == context.roomState.slowModeWaitTime) {
            return CommandResult.AcceptedTwitchCommand(command, response = "This room is already in $duration-second slow mode.")
        }

        val request = ChatSettingsRequestDto(slowMode = true, slowModeWaitTime = duration)
        return updateChatSettings(command, currentUserId, context, request) { range ->
            "${range.first}s through ${range.last}s"
        }
    }

    private suspend fun disableSlowMode(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        if (!context.roomState.isSlowMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = "This room is not in slow mode.")
        }

        val request = ChatSettingsRequestDto(slowMode = false)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun updateChatSettings(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
        request: ChatSettingsRequestDto,
        formatRange: ((IntRange) -> String)? = null
    ): CommandResult {
        return helixApiClient.patchChatSettings(context.channelId, currentUserId, request).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command) },
            onFailure = {
                val response = "Failed to update - ${it.toErrorMessage(command, formatRange = formatRange)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun sendShoutout(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = "Usage: ${context.trigger} <username> - Sends a shoutout to the specified Twitch user.")
        }

        val target = helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
            return CommandResult.AcceptedTwitchCommand(command, response = "No user matching that username.")
        }

        return helixApiClient.postShoutout(context.channelId, target.id, currentUserId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = "Sent shoutout to ${target.displayName}") },
            onFailure = {
                val response = "Failed to send shoutout - ${it.toErrorMessage(command)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private suspend fun toggleShieldMode(command: TwitchCommand, currentUserId: UserId, context: CommandContext): CommandResult {
        val enable = command == TwitchCommand.Shield
        val request = ShieldModeRequestDto(isActive = enable)

        return helixApiClient.putShieldMode(context.channelId, currentUserId, request).fold(
            onSuccess = {
                val response = when {
                    it.isActive -> "Shield mode was activated."
                    else        -> "Shield mode was deactivated."
                }
                CommandResult.AcceptedTwitchCommand(command, response)
            },
            onFailure = {
                val response = "Failed to update shield mode - ${it.toErrorMessage(command)}"
                CommandResult.AcceptedTwitchCommand(command, response)
            }
        )
    }

    private fun Throwable.toErrorMessage(command: TwitchCommand, targetUser: UserName? = null, formatRange: ((IntRange) -> String)? = null): String {
        Log.v(TAG, "Command failed: $this")
        if (this !is HelixApiException) {
            return GENERIC_ERROR_MESSAGE
        }

        return when (error) {
            HelixError.UserNotAuthorized          -> "You don't have permission to perform that action."
            HelixError.Forwarded                  -> message ?: GENERIC_ERROR_MESSAGE
            HelixError.MissingScopes              -> "Missing required scope. Re-login with your account and try again."
            HelixError.NotLoggedIn                -> "Missing login credentials. Re-login with your account and try again."
            HelixError.WhisperSelf                -> "You cannot whisper yourself."
            HelixError.NoVerifiedPhone            -> "Due to Twitch restrictions, you are now required to have a verified phone number to send whispers. You can add a phone number in Twitch settings. https://www.twitch.tv/settings/security"
            HelixError.RecipientBlockedUser       -> "The recipient doesn't allow whispers from strangers or you directly."
            HelixError.RateLimited                -> "You are being rate-limited by Twitch. Try again in a few seconds."
            HelixError.WhisperRateLimited         -> "You may only whisper a maximum of 40 unique recipients per day. Within the per day limit, you may whisper a maximum of 3 whispers per second and a maximum of 100 whispers per minute."
            HelixError.BroadcasterTokenRequired   -> "Due to Twitch restrictions, this command can only be used by the broadcaster. Please use the Twitch website instead."
            HelixError.TargetAlreadyModded        -> "${targetUser ?: "The target user"} is already a moderator of this channel."
            HelixError.TargetIsVip                -> "${targetUser ?: "The target user"} is currently a VIP, /unvip them and retry this command."
            HelixError.TargetNotModded            -> "${targetUser ?: "The target user"} is not a moderator of this channel."
            HelixError.TargetNotBanned            -> "${targetUser ?: "The target user"} is not banned from this channel."
            HelixError.TargetAlreadyBanned        -> "${targetUser ?: "The target user"} is already banned in this channel."
            HelixError.TargetCannotBeBanned       -> "You cannot ${command.trigger} ${targetUser ?: "this user"}."
            HelixError.ConflictingBanOperation    -> "There was a conflicting ban operation on this user. Please try again."
            HelixError.InvalidColor               -> "Color must be one of Twitch's supported colors (${VALID_HELIX_COLORS.joinToString()}) or a hex code (#000000) if you have Turbo or Prime."
            is HelixError.MarkerError             -> error.message ?: GENERIC_ERROR_MESSAGE
            HelixError.CommercialNotStreaming     -> "You must be streaming live to run commercials."
            HelixError.CommercialRateLimited      -> "You must wait until your cool-down period expires before you can run another commercial."
            HelixError.MissingLengthParameter     -> "Command must include a desired commercial break length that is greater than zero."
            HelixError.NoRaidPending              -> "You don't have an active raid."
            HelixError.RaidSelf                   -> "A channel cannot raid itself."
            HelixError.ShoutoutSelf               -> "The broadcaster may not give themselves a Shoutout."
            HelixError.ShoutoutTargetNotStreaming -> "The broadcaster is not streaming live or does not have one or more viewers."
            is HelixError.NotInRange              -> {
                val range = error.validRange
                when (val formatted = range?.let { formatRange?.invoke(it) }) {
                    null -> message ?: GENERIC_ERROR_MESSAGE
                    else -> "The duration is out of the valid range: $formatted."
                }

            }

            HelixError.Unknown                    -> GENERIC_ERROR_MESSAGE
        }
    }

    companion object {
        private val ALLOWED_IRC_COMMANDS = listOf("me", "disconnect")
        private val ALLOWED_FIRST_TRIGGER_CHARS = listOf('/', '.')
        private val ALLOWED_IRC_COMMAND_TRIGGERS = ALLOWED_IRC_COMMANDS.flatMap { asCommandTriggers(it) }
        fun asCommandTriggers(command: String): List<String> = ALLOWED_FIRST_TRIGGER_CHARS.map { "$it$command" }
        val ALL_COMMAND_TRIGGERS = ALLOWED_IRC_COMMAND_TRIGGERS + TwitchCommand.ALL_COMMANDS.flatMap { asCommandTriggers(it.trigger) }

        private val TAG = TwitchCommandRepository::class.java.simpleName
        private const val GENERIC_ERROR_MESSAGE = "An unknown error has occurred."
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
