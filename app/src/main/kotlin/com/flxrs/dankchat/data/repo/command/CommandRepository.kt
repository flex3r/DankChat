package com.flxrs.dankchat.data.repo.command

import android.util.Log
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.chatters.ChattersApiClient
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.supibot.SupibotApiClient
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.data.repo.chat.UserState
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.command.CommandContext
import com.flxrs.dankchat.data.twitch.command.TwitchCommand
import com.flxrs.dankchat.data.twitch.command.TwitchCommandRepository
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.di.ApplicationScope
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.command.CommandItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.seconds

@Singleton
class CommandRepository @Inject constructor(
    private val ignoresRepository: IgnoresRepository,
    private val twitchCommandRepository: TwitchCommandRepository,
    private val helixApiClient: HelixApiClient,
    private val supibotApiClient: SupibotApiClient,
    private val chattersApiClient: ChattersApiClient,
    private val preferenceStore: DankChatPreferenceStore,
    @ApplicationScope scope: CoroutineScope,
) {
    private val customCommands = preferenceStore.commandsAsFlow.stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val supibotCommands = mutableMapOf<UserName, MutableStateFlow<List<String>>>()

    private val defaultCommands = Command.values()
    private val defaultCommandTriggers = defaultCommands.map { it.trigger }

    private val commandTriggers = customCommands.map { customCommands ->
        defaultCommandTriggers + TwitchCommandRepository.ALL_COMMAND_TRIGGERS + customCommands.map(CommandItem.Entry::trigger)
    }

    fun getCommandTriggers(channel: UserName): Flow<List<String>> = when (channel) {
        WhisperMessage.WHISPER_CHANNEL -> flowOf(TwitchCommandRepository.asCommandTriggers(TwitchCommand.Whisper.trigger))
        else                           -> commandTriggers
    }

    fun getSupibotCommands(channel: UserName): StateFlow<List<String>> = supibotCommands.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun clearSupibotCommands() = supibotCommands
        .forEach { it.value.value = emptyList() }
        .also { supibotCommands.clear() }

    suspend fun checkForCommands(message: String, channel: UserName, roomState: RoomState, userState: UserState, skipSuspendingCommands: Boolean = false): CommandResult {
        if (!preferenceStore.isLoggedIn) {
            return CommandResult.NotFound
        }

        val (trigger, args) = triggerAndArgsOrNull(message) ?: return CommandResult.NotFound

        if (twitchCommandRepository.isIrcCommand(trigger)) {
            return CommandResult.IrcCommand
        }

        val twitchCommand = twitchCommandRepository.findTwitchCommand(trigger)
        if (twitchCommand != null) {
            if (skipSuspendingCommands) {
                return CommandResult.Blocked
            }

            val context = CommandContext(trigger, channel, roomState.channelId, roomState, message, args)
            return twitchCommandRepository.handleTwitchCommand(twitchCommand, context)
        }

        val defaultCommand = defaultCommands.find { it.trigger == trigger }
        if (defaultCommand != null) {
            if (skipSuspendingCommands && defaultCommand != Command.Help) {
                return CommandResult.Blocked
            }

            return when (defaultCommand) {
                Command.Block    -> blockUserCommand(args)
                Command.Unblock  -> unblockUserCommand(args)
                //Command.Chatters -> chattersCommand(channel)
                Command.Uptime   -> uptimeCommand(channel)
                Command.Help     -> helpCommand(roomState, userState)
            }
        }

        return checkUserCommands(trigger)
    }

    suspend fun checkForWhisperCommand(message: String, skipSuspendingCommands: Boolean): CommandResult {
        if (skipSuspendingCommands) {
            return CommandResult.Blocked
        }

        val (trigger, args) = triggerAndArgsOrNull(message) ?: return CommandResult.NotFound
        return when (val twitchCommand = twitchCommandRepository.findTwitchCommand(trigger)) {
            TwitchCommand.Whisper -> {
                val currentUserId = preferenceStore.userIdString
                    ?.takeIf { preferenceStore.isLoggedIn }
                    ?: return CommandResult.AcceptedTwitchCommand(
                        command = twitchCommand,
                        response = "You must be logged in to use the $trigger command"
                    )
                twitchCommandRepository.sendWhisper(twitchCommand, currentUserId, trigger, args)
            }

            else                  -> CommandResult.NotFound
        }
    }

    suspend fun loadSupibotCommands() = withContext(Dispatchers.Default) {
        if (!preferenceStore.isLoggedIn || !preferenceStore.shouldLoadSupibot) {
            return@withContext
        }

        measureTimeMillis {
            val channelsDeferred = async { getSupibotChannels() }
            val commandsDeferred = async { getSupibotCommands() }
            val aliasesDeferred = async { getSupibotUserAliases() }

            val channels = channelsDeferred.await()
            val commands = commandsDeferred.await()
            val aliases = aliasesDeferred.await()

            channels.forEach {
                supibotCommands
                    .getOrPut(it) { MutableStateFlow(emptyList()) }
                    .update { commands + aliases }
            }
        }.let { Log.i(TAG, "Loaded Supibot commands in $it ms") }
    }

    private fun triggerAndArgsOrNull(message: String): Pair<String, List<String>>? {
        val words = message.split(" ")
        if (words.isEmpty()) {
            return null
        }

        val trigger = words.first()
        if (trigger.isEmpty()) {
            return null
        }

        return trigger to words.drop(1)
    }

    private suspend fun getSupibotChannels(): List<UserName> {
        return supibotApiClient.getSupibotChannels()
            .getOrNull()
            ?.let { (data) ->
                data.filter { it.isActive }.map { it.name }
            }.orEmpty()
    }

    private suspend fun getSupibotCommands(): List<String> {
        return supibotApiClient.getSupibotCommands()
            .getOrNull()
            ?.let { (data) ->
                data.flatMap { command ->
                    listOf("$${command.name}") + command.aliases.map { "$$it" }
                }
            }.orEmpty()
    }

    private suspend fun getSupibotUserAliases(): List<String> {
        val user = preferenceStore.userName ?: return emptyList()
        return supibotApiClient.getSupibotUserAliases(user)
            .getOrNull()
            ?.let { (data) ->
                data.map { alias -> "$$${alias.name}" }
            }.orEmpty()
    }

    private suspend fun blockUserCommand(args: List<String>): CommandResult.AcceptedWithResponse {
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedWithResponse("Usage: /block <user>")
        }

        val target = args.first().toUserName()
        val targetId = helixApiClient.getUserIdByName(target)
            .getOrNull() ?: return CommandResult.AcceptedWithResponse("User $target couldn't be blocked, no user with that name found!")

        val result = helixApiClient.blockUser(targetId)
        return when {
            result.isSuccess -> {
                ignoresRepository.addUserBlock(targetId, target)
                CommandResult.AcceptedWithResponse("You successfully blocked user $target")
            }

            else             -> CommandResult.AcceptedWithResponse("User $target couldn't be blocked, an unknown error occurred!")
        }
    }

    private suspend fun unblockUserCommand(args: List<String>): CommandResult.AcceptedWithResponse {
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedWithResponse("Usage: /unblock <user>")
        }

        val target = args.first().toUserName()
        val targetId = helixApiClient.getUserIdByName(target)
            .getOrNull() ?: return CommandResult.AcceptedWithResponse("User $target couldn't be unblocked, no user with that name found!")

        val result = runCatching {
            ignoresRepository.removeUserBlock(targetId, target)
            CommandResult.AcceptedWithResponse("You successfully unblocked user $target")
        }

        return result.getOrElse {
            CommandResult.AcceptedWithResponse("User $target couldn't be unblocked, an unknown error occurred!")
        }
    }

    private suspend fun chattersCommand(channel: UserName): CommandResult.AcceptedWithResponse {
        val result = chattersApiClient.getChatterCount(channel)
            .getOrNull() ?: return CommandResult.AcceptedWithResponse("An unknown error occurred!")

        return CommandResult.AcceptedWithResponse("Chatter count: $result")
    }

    private suspend fun uptimeCommand(channel: UserName): CommandResult.AcceptedWithResponse {
        val result = helixApiClient.getStreams(listOf(channel))
            .getOrNull()
            ?.getOrNull(0) ?: return CommandResult.AcceptedWithResponse("Channel is not live.")

        val startedAt = Instant.parse(result.startedAt).atZone(ZoneId.systemDefault()).toEpochSecond()
        val now = ZonedDateTime.now().toEpochSecond()

        val duration = now.seconds - startedAt.seconds
        val uptime = duration.toComponents { days, hours, minutes, _, _ ->
            buildString {
                if (days > 0) append("${days}d ")
                if (hours > 0) append("${hours}h ")
                append("${minutes}m")
            }
        }

        return CommandResult.AcceptedWithResponse("Uptime: $uptime")
    }

    private fun helpCommand(roomState: RoomState, userState: UserState): CommandResult.AcceptedWithResponse {
        val commands = twitchCommandRepository
            .getAvailableCommandTriggers(roomState, userState)
            .plus(defaultCommandTriggers)
            .joinToString(separator = " ")

        val response = "Commands available to you in this room: $commands"
        return CommandResult.AcceptedWithResponse(response)
    }

    private fun checkUserCommands(trigger: String): CommandResult {
        val commands = customCommands.value
        val foundCommand = commands.find { it.trigger == trigger } ?: return CommandResult.NotFound

        return CommandResult.Message(foundCommand.command)
    }

    companion object {
        private val TAG = CommandRepository::class.java.simpleName
    }
}
