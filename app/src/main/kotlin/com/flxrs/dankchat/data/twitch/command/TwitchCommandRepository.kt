package com.flxrs.dankchat.data.twitch.command

import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
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
import com.flxrs.dankchat.data.api.helix.dto.ModifyChannelRequestDto
import com.flxrs.dankchat.data.api.helix.dto.ShieldModeRequestDto
import com.flxrs.dankchat.data.api.helix.dto.WhisperRequestDto
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.ShieldModeRepository
import com.flxrs.dankchat.data.repo.chat.UserState
import com.flxrs.dankchat.data.repo.command.CommandResult
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.utils.DateTimeUtils
import com.flxrs.dankchat.utils.TextResource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.collections.immutable.persistentListOf
import org.koin.core.annotation.Single
import java.util.UUID

private val logger = KotlinLogging.logger("TwitchCommandRepository")

@Single
class TwitchCommandRepository(
    private val helixApiClient: HelixApiClient,
    private val authDataStore: AuthDataStore,
    private val shieldModeRepository: ShieldModeRepository,
) {
    fun isIrcCommand(trigger: String): Boolean = trigger in ALLOWED_IRC_COMMAND_TRIGGERS

    fun getAvailableCommandTriggers(
        room: RoomState,
        userState: UserState,
    ): List<String> {
        val currentUserId = authDataStore.userIdString ?: return emptyList()
        return when {
            room.channelId == currentUserId -> TwitchCommand.ALL_COMMANDS
            room.channel in userState.moderationChannels -> TwitchCommand.MODERATOR_COMMANDS
            else -> TwitchCommand.USER_COMMANDS
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

    suspend fun handleTwitchCommand(
        command: TwitchCommand,
        context: CommandContext,
    ): CommandResult {
        val currentUserId =
            authDataStore.userIdString ?: return CommandResult.AcceptedTwitchCommand(
                command = command,
                response = TextResource.Res(R.string.cmd_error_not_logged_in, persistentListOf(context.trigger)),
            )

        return when (command) {
            TwitchCommand.Announce,
            TwitchCommand.AnnounceBlue,
            TwitchCommand.AnnounceGreen,
            TwitchCommand.AnnounceOrange,
            TwitchCommand.AnnouncePurple,
            -> sendAnnouncement(command, currentUserId, context)

            TwitchCommand.Ban -> banUser(command, currentUserId, context)

            TwitchCommand.Clear -> clearChat(command, currentUserId, context)

            TwitchCommand.Color -> updateColor(command, currentUserId, context)

            TwitchCommand.Commercial -> startCommercial(command, context)

            TwitchCommand.Delete -> deleteMessage(command, currentUserId, context)

            TwitchCommand.EmoteOnly -> enableEmoteMode(command, currentUserId, context)

            TwitchCommand.EmoteOnlyOff -> disableEmoteMode(command, currentUserId, context)

            TwitchCommand.Followers -> enableFollowersMode(command, currentUserId, context)

            TwitchCommand.FollowersOff -> disableFollowersMode(command, currentUserId, context)

            TwitchCommand.Marker -> createMarker(command, context)

            TwitchCommand.Mod -> addModerator(command, context)

            TwitchCommand.Mods -> getModerators(command, context)

            TwitchCommand.R9kBeta -> enableUniqueChatMode(command, currentUserId, context)

            TwitchCommand.R9kBetaOff -> disableUniqueChatMode(command, currentUserId, context)

            TwitchCommand.Raid -> startRaid(command, context)

            TwitchCommand.SetGame -> setGame(command, context)

            TwitchCommand.SetTitle -> setTitle(command, context)

            TwitchCommand.Shield,
            TwitchCommand.ShieldOff,
            -> toggleShieldMode(command, currentUserId, context)

            TwitchCommand.Slow -> enableSlowMode(command, currentUserId, context)

            TwitchCommand.SlowOff -> disableSlowMode(command, currentUserId, context)

            TwitchCommand.Subscribers -> enableSubscriberMode(command, currentUserId, context)

            TwitchCommand.SubscribersOff -> disableSubscriberMode(command, currentUserId, context)

            TwitchCommand.Timeout -> timeoutUser(command, currentUserId, context)

            TwitchCommand.Unban -> unbanUser(command, currentUserId, context)

            TwitchCommand.UniqueChat -> enableUniqueChatMode(command, currentUserId, context)

            TwitchCommand.UniqueChatOff -> disableUniqueChatMode(command, currentUserId, context)

            TwitchCommand.Unmod -> removeModerator(command, context)

            TwitchCommand.Unraid -> cancelRaid(command, context)

            TwitchCommand.Untimeout -> unbanUser(command, currentUserId, context)

            TwitchCommand.Unvip -> removeVip(command, context)

            TwitchCommand.Vip -> addVip(command, context)

            TwitchCommand.Vips -> getVips(command, context)

            TwitchCommand.Whisper -> sendWhisper(command, currentUserId, context.trigger, context.args)

            TwitchCommand.Shoutout -> sendShoutout(command, currentUserId, context)
        }
    }

    suspend fun sendWhisper(
        command: TwitchCommand,
        currentUserId: UserId,
        trigger: String,
        args: List<String>,
    ): CommandResult {
        if (args.size < 2 || args[0].isBlank() || args[1].isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_usage_whisper, persistentListOf(trigger)))
        }

        val targetName = args[0]
        val targetId =
            helixApiClient.getUserIdByName(targetName.toUserName()).getOrElse {
                return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_error_no_user_matching))
            }
        val request = WhisperRequestDto(args.drop(1).joinToString(separator = " "))
        val result = helixApiClient.postWhisper(currentUserId, targetId, request)
        return result.fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_whisper_sent)) },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_whisper, persistentListOf(it.toErrorMessage(command)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun setTitle(
        command: TwitchCommand,
        context: CommandContext,
    ): CommandResult {
        if (context.args.isEmpty() || context.args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, TextResource.Res(R.string.cmd_usage_set_title))
        }

        val title = context.args.joinToString(" ")
        return helixApiClient.patchChannel(context.channelId, ModifyChannelRequestDto(title = title)).fold(
            onSuccess = {
                CommandResult.AcceptedTwitchCommand(command, TextResource.Res(R.string.cmd_success_set_title, persistentListOf(title)))
            },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_set_title, persistentListOf(it.toErrorMessage(command)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun setGame(
        command: TwitchCommand,
        context: CommandContext,
    ): CommandResult {
        if (context.args.isEmpty() || context.args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, TextResource.Res(R.string.cmd_usage_set_game))
        }

        val query = context.args.joinToString(" ")
        val categories = helixApiClient.searchCategories(query).getOrElse {
            val response = TextResource.Res(R.string.cmd_fail_search_game, persistentListOf(it.toErrorMessage(command)))
            return CommandResult.AcceptedTwitchCommand(command, response)
        }
        val category = categories.firstOrNull { it.name.equals(query, ignoreCase = true) } ?: categories.firstOrNull()
        if (category == null) {
            return CommandResult.AcceptedTwitchCommand(command, TextResource.Res(R.string.cmd_game_not_found))
        }

        return helixApiClient.patchChannel(context.channelId, ModifyChannelRequestDto(gameId = category.id)).fold(
            onSuccess = {
                CommandResult.AcceptedTwitchCommand(command, TextResource.Res(R.string.cmd_success_set_game, persistentListOf(category.name)))
            },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_set_game, persistentListOf(it.toErrorMessage(command)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun sendAnnouncement(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_usage_announce, persistentListOf(context.trigger)))
        }

        val message = args.joinToString(" ")
        val color =
            when (command) {
                TwitchCommand.AnnounceBlue -> AnnouncementColor.Blue
                TwitchCommand.AnnounceGreen -> AnnouncementColor.Green
                TwitchCommand.AnnounceOrange -> AnnouncementColor.Orange
                TwitchCommand.AnnouncePurple -> AnnouncementColor.Purple
                else -> AnnouncementColor.Primary
            }
        val request = AnnouncementRequestDto(message, color)
        val result = helixApiClient.postAnnouncement(context.channelId, currentUserId, request)
        return result.fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command) },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_announce, persistentListOf(it.toErrorMessage(command)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun getModerators(
        command: TwitchCommand,
        context: CommandContext,
    ): CommandResult = helixApiClient.getModerators(context.channelId).fold(
        onSuccess = { result ->
            when {
                result.isEmpty() -> {
                    CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_no_moderators))
                }

                else -> {
                    val users = result.joinToString { it.userLogin.formatWithDisplayName(it.userName) }
                    CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_moderators_list, persistentListOf(users)))
                }
            }
        },
        onFailure = {
            val response = TextResource.Res(R.string.cmd_fail_list_moderators, persistentListOf(it.toErrorMessage(command)))
            CommandResult.AcceptedTwitchCommand(command, response)
        },
    )

    private suspend fun addModerator(
        command: TwitchCommand,
        context: CommandContext,
    ): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_usage_mod, persistentListOf(context.trigger)))
        }

        val target =
            helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
                return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_error_no_user_matching))
            }

        val targetId = target.id
        val targetUser = target.displayName
        return helixApiClient.postModerator(context.channelId, targetId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_success_mod_added, persistentListOf(targetUser.toString()))) },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_mod_add, persistentListOf(it.toErrorMessage(command, targetUser)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun removeModerator(
        command: TwitchCommand,
        context: CommandContext,
    ): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_usage_unmod, persistentListOf(context.trigger)))
        }

        val target =
            helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
                return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_error_no_user_matching))
            }

        val targetId = target.id
        val targetUser = target.displayName
        return helixApiClient.deleteModerator(context.channelId, targetId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_success_mod_removed, persistentListOf(targetUser.toString()))) },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_mod_remove, persistentListOf(it.toErrorMessage(command, targetUser)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun getVips(
        command: TwitchCommand,
        context: CommandContext,
    ): CommandResult = helixApiClient.getVips(context.channelId).fold(
        onSuccess = { result ->
            when {
                result.isEmpty() -> {
                    CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_no_vips))
                }

                else -> {
                    val users = result.joinToString { it.userLogin.formatWithDisplayName(it.userName) }
                    CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_vips_list, persistentListOf(users)))
                }
            }
        },
        onFailure = {
            val response = TextResource.Res(R.string.cmd_fail_list_vips, persistentListOf(it.toErrorMessage(command)))
            CommandResult.AcceptedTwitchCommand(command, response)
        },
    )

    private suspend fun addVip(
        command: TwitchCommand,
        context: CommandContext,
    ): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_usage_vip, persistentListOf(context.trigger)))
        }

        val target =
            helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
                return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_error_no_user_matching))
            }

        val targetId = target.id
        val targetUser = target.displayName
        return helixApiClient.postVip(context.channelId, targetId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_success_vip_added, persistentListOf(targetUser.toString()))) },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_vip_add, persistentListOf(it.toErrorMessage(command, targetUser)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun removeVip(
        command: TwitchCommand,
        context: CommandContext,
    ): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_usage_unvip, persistentListOf(context.trigger)))
        }

        val target =
            helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
                return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_error_no_user_matching))
            }

        val targetId = target.id
        val targetUser = target.displayName
        return helixApiClient.deleteVip(context.channelId, targetId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_success_vip_removed, persistentListOf(targetUser.toString()))) },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_vip_remove, persistentListOf(it.toErrorMessage(command, targetUser)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun banUser(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, TextResource.Res(R.string.cmd_usage_ban, persistentListOf(context.trigger)))
        }

        val target =
            helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
                return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_error_no_user_matching))
            }

        if (target.id == currentUserId) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_ban_cannot_self))
        } else if (target.id == context.channelId) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_ban_cannot_broadcaster))
        }

        val reason = args.drop(1).joinToString(separator = " ").ifBlank { null }

        val targetId = target.id
        val targetUser = target.displayName
        val request = BanRequestDto(BanRequestDataDto(targetId, duration = null, reason = reason))
        return helixApiClient.postBan(context.channelId, currentUserId, request).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command) },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_ban, persistentListOf(it.toErrorMessage(command, targetUser)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun unbanUser(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, TextResource.Res(R.string.cmd_usage_unban, persistentListOf(context.trigger)))
        }

        val target =
            helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
                return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_error_no_user_matching))
            }

        val targetId = target.id
        return helixApiClient.deleteBan(context.channelId, currentUserId, targetId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command) },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_unban, persistentListOf(it.toErrorMessage(command, target.displayName)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun timeoutUser(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        val args = context.args
        val usageResponse = TextResource.Res(R.string.cmd_usage_timeout, persistentListOf(context.trigger))
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, usageResponse)
        }

        val target =
            helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
                return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_error_no_user_matching))
            }

        if (target.id == currentUserId) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_timeout_cannot_self))
        } else if (target.id == context.channelId) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_timeout_cannot_broadcaster))
        }

        val durationInSeconds =
            when {
                args.size > 1 && args[1].isNotBlank() -> DateTimeUtils.durationToSeconds(args[1].trim()) ?: return CommandResult.AcceptedTwitchCommand(command, usageResponse)
                else -> 60 * 10
            }
        val reason = args.drop(2).joinToString(separator = " ").ifBlank { null }

        val targetId = target.id
        val request = BanRequestDto(BanRequestDataDto(targetId, duration = durationInSeconds, reason = reason))
        return helixApiClient.postBan(context.channelId, currentUserId, request).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command) },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_timeout, persistentListOf(it.toErrorMessage(command, target.displayName)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun clearChat(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult = helixApiClient.deleteMessages(context.channelId, currentUserId).fold(
        onSuccess = { CommandResult.AcceptedTwitchCommand(command) },
        onFailure = {
            val response = TextResource.Res(R.string.cmd_fail_clear, persistentListOf(it.toErrorMessage(command)))
            CommandResult.AcceptedTwitchCommand(command, response)
        },
    )

    private suspend fun deleteMessage(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_usage_delete))
        }

        val messageId = args.first()
        val parsedId = runCatching { UUID.fromString(messageId) }
        if (parsedId.isFailure) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_delete_invalid_id, persistentListOf(messageId)))
        }

        return helixApiClient.deleteMessages(context.channelId, currentUserId, messageId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command) },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_delete, persistentListOf(it.toErrorMessage(command)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun updateColor(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_usage_color, persistentListOf(VALID_HELIX_COLORS.joinToString())))
        }

        val colorArg = args.first().lowercase()
        val color = HELIX_COLOR_REPLACEMENTS[colorArg] ?: colorArg

        return helixApiClient.putUserChatColor(currentUserId, color).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_success_color, persistentListOf(color))) },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_color, persistentListOf(color, it.toErrorMessage(command)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun createMarker(
        command: TwitchCommand,
        context: CommandContext,
    ): CommandResult {
        val description =
            context.args
                .joinToString(separator = " ")
                .take(140)
                .ifBlank { null }
        val request = MarkerRequestDto(context.channelId, description)

        return helixApiClient.postMarker(request).fold(
            onSuccess = { result ->
                val markerDescription = result.description?.let { ": \"$it\"" }.orEmpty()
                val response = TextResource.Res(R.string.cmd_success_marker, persistentListOf(DateTimeUtils.formatSeconds(result.positionSeconds), markerDescription))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_marker, persistentListOf(it.toErrorMessage(command)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun startCommercial(
        command: TwitchCommand,
        context: CommandContext,
    ): CommandResult {
        val args = context.args
        val usage = TextResource.Res(R.string.cmd_usage_commercial)
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = usage)
        }

        val length = args.first().toIntOrNull() ?: return CommandResult.AcceptedTwitchCommand(command, response = usage)
        val request = CommercialRequestDto(context.channelId, length)
        return helixApiClient.postCommercial(request).fold(
            onSuccess = { result ->
                val response = TextResource.PluralRes(R.plurals.cmd_success_commercial, result.length, persistentListOf(result.length, result.retryAfter))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_commercial, persistentListOf(it.toErrorMessage(command)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun startRaid(
        command: TwitchCommand,
        context: CommandContext,
    ): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_usage_raid))
        }

        val target =
            helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
                return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_raid_invalid_username, persistentListOf(args.first())))
            }

        return helixApiClient.postRaid(context.channelId, target.id).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_success_raid, persistentListOf(target.displayName.toString()))) },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_raid, persistentListOf(it.toErrorMessage(command)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun cancelRaid(
        command: TwitchCommand,
        context: CommandContext,
    ): CommandResult = helixApiClient.deleteRaid(context.channelId).fold(
        onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_success_unraid)) },
        onFailure = {
            val response = TextResource.Res(R.string.cmd_fail_unraid, persistentListOf(it.toErrorMessage(command)))
            CommandResult.AcceptedTwitchCommand(command, response)
        },
    )

    private suspend fun enableFollowersMode(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        val args = context.args
        val usage = TextResource.Res(R.string.cmd_usage_followers, persistentListOf(context.trigger))
        val durationArg = args.joinToString(separator = " ").ifBlank { null }
        val duration =
            durationArg?.let {
                val seconds = DateTimeUtils.durationToSeconds(it) ?: return CommandResult.AcceptedTwitchCommand(command, response = usage)
                seconds / 60
            }

        if (duration != null && duration == context.roomState.followerModeDuration) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_room_already_followers, persistentListOf(DateTimeUtils.formatSeconds(duration * 60))))
        }

        val request = ChatSettingsRequestDto(followerMode = true, followerModeDuration = duration)
        return updateChatSettings(command, currentUserId, context, request) { range ->
            val start = if (range.first == 0) "0s" else DateTimeUtils.formatSeconds(durationInSeconds = range.first * 60)
            val end = if (range.last == 0) "0s" else DateTimeUtils.formatSeconds(durationInSeconds = range.last * 60)
            "$start through $end"
        }
    }

    private suspend fun disableFollowersMode(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        if (!context.roomState.isFollowMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_room_not_followers))
        }

        val request = ChatSettingsRequestDto(followerMode = false)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun enableEmoteMode(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        if (context.roomState.isEmoteMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_room_already_emote_only))
        }

        val request = ChatSettingsRequestDto(emoteMode = true)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun disableEmoteMode(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        if (!context.roomState.isEmoteMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_room_not_emote_only))
        }

        val request = ChatSettingsRequestDto(emoteMode = false)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun enableSubscriberMode(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        if (context.roomState.isSubscriberMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_room_already_subscribers))
        }

        val request = ChatSettingsRequestDto(subscriberMode = true)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun disableSubscriberMode(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        if (!context.roomState.isSubscriberMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_room_not_subscribers))
        }

        val request = ChatSettingsRequestDto(subscriberMode = false)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun enableUniqueChatMode(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        if (context.roomState.isUniqueChatMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_room_already_unique_chat))
        }

        val request = ChatSettingsRequestDto(uniqueChatMode = true)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun disableUniqueChatMode(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        if (!context.roomState.isUniqueChatMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_room_not_unique_chat))
        }

        val request = ChatSettingsRequestDto(uniqueChatMode = false)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun enableSlowMode(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        val args = context.args.firstOrNull() ?: "30"
        val duration = args.toIntOrNull()
        if (duration == null) {
            return CommandResult.AcceptedTwitchCommand(command, TextResource.Res(R.string.cmd_usage_slow, persistentListOf(context.trigger)))
        }

        if (duration == context.roomState.slowModeWaitTime) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_room_already_slow, persistentListOf(duration)))
        }

        val request = ChatSettingsRequestDto(slowMode = true, slowModeWaitTime = duration)
        return updateChatSettings(command, currentUserId, context, request) { range ->
            "${range.first}s through ${range.last}s"
        }
    }

    private suspend fun disableSlowMode(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        if (!context.roomState.isSlowMode) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_room_not_slow))
        }

        val request = ChatSettingsRequestDto(slowMode = false)
        return updateChatSettings(command, currentUserId, context, request)
    }

    private suspend fun updateChatSettings(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
        request: ChatSettingsRequestDto,
        formatRange: ((IntRange) -> String)? = null,
    ): CommandResult = helixApiClient.patchChatSettings(context.channelId, currentUserId, request).fold(
        onSuccess = { CommandResult.AcceptedTwitchCommand(command) },
        onFailure = {
            val response = TextResource.Res(R.string.cmd_fail_chat_settings, persistentListOf(it.toErrorMessage(command, formatRange = formatRange)))
            CommandResult.AcceptedTwitchCommand(command, response)
        },
    )

    private suspend fun sendShoutout(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        val args = context.args
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_usage_shoutout, persistentListOf(context.trigger)))
        }

        val target =
            helixApiClient.getUserByName(args.first().toUserName()).getOrElse {
                return CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_error_no_user_matching))
            }

        return helixApiClient.postShoutout(context.channelId, target.id, currentUserId).fold(
            onSuccess = { CommandResult.AcceptedTwitchCommand(command, response = TextResource.Res(R.string.cmd_success_shoutout, persistentListOf(target.displayName.toString()))) },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_shoutout, persistentListOf(it.toErrorMessage(command)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private suspend fun toggleShieldMode(
        command: TwitchCommand,
        currentUserId: UserId,
        context: CommandContext,
    ): CommandResult {
        val enable = command == TwitchCommand.Shield
        val request = ShieldModeRequestDto(isActive = enable)

        return helixApiClient.putShieldMode(context.channelId, currentUserId, request).fold(
            onSuccess = {
                shieldModeRepository.setState(context.channel, it.isActive)
                val response =
                    when {
                        it.isActive -> TextResource.Res(R.string.cmd_shield_activated)
                        else -> TextResource.Res(R.string.cmd_shield_deactivated)
                    }
                CommandResult.AcceptedTwitchCommand(command, response)
            },
            onFailure = {
                val response = TextResource.Res(R.string.cmd_fail_shield, persistentListOf(it.toErrorMessage(command)))
                CommandResult.AcceptedTwitchCommand(command, response)
            },
        )
    }

    private fun Throwable.toErrorMessage(
        command: TwitchCommand,
        targetUser: DisplayName? = null,
        formatRange: ((IntRange) -> String)? = null,
    ): TextResource {
        logger.trace { "Command failed: $this" }
        if (this !is HelixApiException) {
            return TextResource.Res(R.string.cmd_error_unknown)
        }

        val target = targetUser?.let { TextResource.Plain(it.toString()) } ?: TextResource.Res(R.string.cmd_error_target_default)
        return when (error) {
            HelixError.UserNotAuthorized -> {
                TextResource.Res(R.string.cmd_error_no_permission)
            }

            HelixError.Forwarded -> {
                message?.let { TextResource.Plain(it) } ?: TextResource.Res(R.string.cmd_error_unknown)
            }

            HelixError.MissingScopes -> {
                TextResource.Res(R.string.cmd_error_missing_scopes)
            }

            HelixError.NotLoggedIn -> {
                TextResource.Res(R.string.cmd_error_not_logged_in_credentials)
            }

            HelixError.WhisperSelf -> {
                TextResource.Res(R.string.cmd_error_whisper_self)
            }

            HelixError.NoVerifiedPhone -> {
                TextResource.Res(R.string.cmd_error_no_verified_phone)
            }

            HelixError.RecipientBlockedUser -> {
                TextResource.Res(R.string.cmd_error_recipient_blocked)
            }

            HelixError.RateLimited -> {
                TextResource.Res(R.string.cmd_error_rate_limited)
            }

            HelixError.WhisperRateLimited -> {
                TextResource.Res(R.string.cmd_error_whisper_rate_limited)
            }

            HelixError.BroadcasterTokenRequired -> {
                TextResource.Res(R.string.cmd_error_broadcaster_required)
            }

            HelixError.TargetAlreadyModded -> {
                TextResource.Res(R.string.cmd_error_already_modded, persistentListOf(target))
            }

            HelixError.TargetIsVip -> {
                TextResource.Res(R.string.cmd_error_target_is_vip, persistentListOf(target))
            }

            HelixError.TargetNotModded -> {
                TextResource.Res(R.string.cmd_error_not_modded, persistentListOf(target))
            }

            HelixError.TargetNotBanned -> {
                TextResource.Res(R.string.cmd_error_not_banned, persistentListOf(target))
            }

            HelixError.TargetAlreadyBanned -> {
                TextResource.Res(R.string.cmd_error_already_banned, persistentListOf(target))
            }

            HelixError.TargetCannotBeBanned -> {
                TextResource.Res(R.string.cmd_error_cannot_perform, persistentListOf(command.trigger, target))
            }

            HelixError.ConflictingBanOperation -> {
                TextResource.Res(R.string.cmd_error_conflicting_ban)
            }

            HelixError.InvalidColor -> {
                TextResource.Res(R.string.cmd_error_invalid_color, persistentListOf(VALID_HELIX_COLORS.joinToString()))
            }

            is HelixError.MarkerError -> {
                error.message?.let { TextResource.Plain(it) } ?: TextResource.Res(R.string.cmd_error_unknown)
            }

            HelixError.CommercialNotStreaming -> {
                TextResource.Res(R.string.cmd_error_commercial_not_streaming)
            }

            HelixError.CommercialRateLimited -> {
                TextResource.Res(R.string.cmd_error_commercial_rate_limited)
            }

            HelixError.MissingLengthParameter -> {
                TextResource.Res(R.string.cmd_error_missing_length)
            }

            HelixError.NoRaidPending -> {
                TextResource.Res(R.string.cmd_error_no_raid_pending)
            }

            HelixError.RaidSelf -> {
                TextResource.Res(R.string.cmd_error_raid_self)
            }

            HelixError.ShoutoutSelf -> {
                TextResource.Res(R.string.cmd_error_shoutout_self)
            }

            HelixError.ShoutoutTargetNotStreaming -> {
                TextResource.Res(R.string.cmd_error_shoutout_not_streaming)
            }

            is HelixError.NotInRange -> {
                val range = error.validRange
                val formatted = range?.let { formatRange?.invoke(it) }
                when {
                    formatted != null -> TextResource.Res(R.string.cmd_error_out_of_range, persistentListOf(formatted))
                    else -> message?.let { TextResource.Plain(it) } ?: TextResource.Res(R.string.cmd_error_unknown)
                }
            }

            HelixError.MessageAlreadyProcessed -> {
                TextResource.Res(R.string.cmd_error_message_already_processed)
            }

            HelixError.MessageNotFound -> {
                TextResource.Res(R.string.cmd_error_message_not_found)
            }

            HelixError.MessageTooLarge -> {
                TextResource.Res(R.string.cmd_error_message_too_large)
            }

            HelixError.ChatMessageRateLimited -> {
                TextResource.Res(R.string.cmd_error_chat_rate_limited)
            }

            HelixError.Unknown, HelixError.EmptyResponse -> {
                TextResource.Res(R.string.cmd_error_unknown)
            }
        }
    }

    companion object {
        private val ALLOWED_IRC_COMMANDS = listOf("me", "disconnect")
        private val ALLOWED_FIRST_TRIGGER_CHARS = listOf('/', '.')
        private val ALLOWED_IRC_COMMAND_TRIGGERS = ALLOWED_IRC_COMMANDS.flatMap { asCommandTriggers(it) }

        fun asCommandTriggers(command: String): List<String> = ALLOWED_FIRST_TRIGGER_CHARS.map { "$it$command" }

        // `/`- or `.`-prefixed input that Twitch would post verbatim as a chat message,
        // allowing "/me", dot-only messages and escaped variants like "/ text" or ". text"
        private val UNKNOWN_COMMAND_REGEX = """^(?:\.(?!\.|$)|/)(?!me(?:\s|$)|\s)""".toRegex(RegexOption.IGNORE_CASE)

        fun isUnknownCommand(message: String): Boolean = UNKNOWN_COMMAND_REGEX.containsMatchIn(message)

        val ALL_COMMAND_TRIGGERS = ALLOWED_IRC_COMMAND_TRIGGERS + TwitchCommand.ALL_COMMANDS.flatMap { asCommandTriggers(it.trigger) }

        private val VALID_HELIX_COLORS =
            listOf(
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

        private val HELIX_COLOR_REPLACEMENTS =
            mapOf(
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
