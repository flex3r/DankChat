package com.flxrs.dankchat.data.repo.command

import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.supibot.SupibotApiClient
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.data.repo.chat.UserState
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.command.CommandContext
import com.flxrs.dankchat.data.twitch.command.TwitchCommand
import com.flxrs.dankchat.data.twitch.command.TwitchCommandRepository
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.domain.BackgroundDataLoader
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.chat.CustomCommand
import com.flxrs.dankchat.preferences.chat.SuggestionType
import com.flxrs.dankchat.preferences.developer.ChatSendProtocol
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
import com.flxrs.dankchat.utils.DateTimeUtils.calculateUptime
import com.flxrs.dankchat.utils.TextResource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger("CommandRepository")
private const val SUPIBOT_LOAD_TAG = "supibot-commands"

@Single
class CommandRepository(
    private val ignoresRepository: IgnoresRepository,
    private val twitchCommandRepository: TwitchCommandRepository,
    private val helixApiClient: HelixApiClient,
    private val supibotApiClient: SupibotApiClient,
    private val chatSettingsDataStore: ChatSettingsDataStore,
    private val developerSettingsDataStore: DeveloperSettingsDataStore,
    private val authDataStore: AuthDataStore,
    private val backgroundDataLoader: BackgroundDataLoader,
    private val dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private val customCommands = chatSettingsDataStore.commands.stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val supibotCommands = mutableMapOf<UserName, MutableStateFlow<List<String>>>()

    private val defaultCommands = Command.entries
    private val defaultCommandTriggers = defaultCommands.map { it.trigger }
    private val commandShortcutTriggers = CommandShortcut.entries.map { it.trigger }

    private val commandTriggers =
        chatSettingsDataStore.commands.map { customCommands ->
            defaultCommandTriggers + commandShortcutTriggers + TwitchCommandRepository.ALL_COMMAND_TRIGGERS + customCommands.map(CustomCommand::trigger)
        }

    init {
        scope.launch {
            chatSettingsDataStore.settings
                .map { SuggestionType.SupibotCommands in it.suggestionTypes }
                .distinctUntilChanged()
                .collect { enabled ->
                    when {
                        enabled -> loadSupibotCommands()
                        else -> clearSupibotCommands()
                    }
                }
        }
    }

    fun getReservedTriggers(): Set<String> {
        val builtIn = defaultCommandTriggers
        val shortcuts = commandShortcutTriggers
        val twitch = TwitchCommandRepository.ALL_COMMAND_TRIGGERS
        val supibot = supibotCommands.values.flatMap { it.value }
        return (builtIn + shortcuts + twitch + supibot).toSet()
    }

    fun getCommandTriggers(channel: UserName): Flow<List<String>> = when (channel) {
        WhisperMessage.WHISPER_CHANNEL -> flowOf(TwitchCommandRepository.asCommandTriggers(TwitchCommand.Whisper.trigger))
        else -> commandTriggers
    }

    fun getCustomCommandTriggers(): Flow<List<String>> = chatSettingsDataStore.commands.map { commands ->
        commands.map(CustomCommand::trigger)
    }

    fun getSupibotCommands(channel: UserName): StateFlow<List<String>> = supibotCommands.getOrPut(channel) { MutableStateFlow(emptyList()) }

    @Suppress("ReturnCount")
    suspend fun checkForCommands(
        message: String,
        channel: UserName,
        roomState: RoomState,
        userState: UserState,
        skipSuspendingCommands: Boolean = false,
    ): CommandResult {
        if (!authDataStore.isLoggedIn) {
            return CommandResult.NotFound
        }

        val (trigger, args) = triggerAndArgsOrNull(message) ?: return CommandResult.NotFound

        if (twitchCommandRepository.isIrcCommand(trigger)) {
            return CommandResult.IrcCommand
        }

        val twitchCommand = twitchCommandRepository.findTwitchCommand(trigger)
        if (twitchCommand != null) {
            if (developerSettingsDataStore.settings.first().bypassCommandHandling) {
                return CommandResult.IrcCommand
            } else if (skipSuspendingCommands) {
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
                Command.Block -> blockUserCommand(args)

                Command.Unblock -> unblockUserCommand(args)

                // Command.Chatters -> chattersCommand(channel)
                Command.Uptime -> uptimeCommand(channel)

                Command.Help -> helpCommand(roomState, userState)
            }
        }

        val userCommand = checkUserCommands(trigger)
        if (userCommand != CommandResult.NotFound) {
            return userCommand
        }

        if (!TwitchCommandRepository.isUnknownCommand(message)) {
            return CommandResult.NotFound
        }

        val developerSettings = developerSettingsDataStore.settings.first()
        return when {
            developerSettings.bypassCommandHandling -> CommandResult.IrcCommand

            // Twitch rejects unknown commands itself when sending via IRC
            developerSettings.chatSendProtocol == ChatSendProtocol.IRC -> CommandResult.NotFound

            else -> CommandResult.UnknownCommand(trigger)
        }
    }

    suspend fun checkForWhisperCommand(
        message: String,
        skipSuspendingCommands: Boolean,
    ): CommandResult {
        if (skipSuspendingCommands) {
            return CommandResult.Blocked
        }

        val (trigger, args) = triggerAndArgsOrNull(message) ?: return CommandResult.NotFound
        return when (val twitchCommand = twitchCommandRepository.findTwitchCommand(trigger)) {
            TwitchCommand.Whisper -> {
                val currentUserId =
                    authDataStore.userIdString
                        ?.takeIf { authDataStore.isLoggedIn }
                        ?: return CommandResult.AcceptedTwitchCommand(
                            command = twitchCommand,
                            response = TextResource.Res(R.string.cmd_error_not_logged_in, persistentListOf(trigger)),
                        )
                twitchCommandRepository.sendWhisper(twitchCommand, currentUserId, trigger, args)
            }

            else -> {
                CommandResult.NotFound
            }
        }
    }

    fun loadSupibotCommands() {
        backgroundDataLoader.load(SUPIBOT_LOAD_TAG) {
            fetchSupibotCommands()
        }
    }

    private suspend fun fetchSupibotCommands() = withContext(dispatchersProvider.default) {
        if (!authDataStore.isLoggedIn || SuggestionType.SupibotCommands !in chatSettingsDataStore.settings.first().suggestionTypes) {
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
        }.let { logger.info { "Loaded Supibot commands in $it ms" } }
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

    private suspend fun getSupibotChannels(): List<UserName> = supibotApiClient
        .getSupibotChannels()
        .getOrNull()
        ?.let { (data) ->
            data.filter { it.isActive }.map { it.name }
        }.orEmpty()

    private suspend fun getSupibotCommands(): List<String> = supibotApiClient
        .getSupibotCommands()
        .getOrNull()
        ?.let { (data) ->
            data.flatMap { command ->
                listOf("$${command.name}") + command.aliases.map { "$$it" }
            }
        }.orEmpty()

    private suspend fun getSupibotUserAliases(): List<String> {
        val user = authDataStore.userName ?: return emptyList()
        return supibotApiClient
            .getSupibotUserAliases(user)
            .getOrNull()
            ?.let { (data) ->
                data.map { alias -> "$$${alias.name}" }
            }.orEmpty()
    }

    private fun clearSupibotCommands() = supibotCommands
        .forEach { it.value.value = emptyList() }
        .also { supibotCommands.clear() }

    private suspend fun blockUserCommand(args: List<String>): CommandResult.AcceptedWithResponse {
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedWithResponse(TextResource.Res(R.string.cmd_block_usage))
        }

        val target = args.first().toUserName()
        val targetId =
            helixApiClient
                .getUserIdByName(target)
                .getOrNull() ?: return CommandResult.AcceptedWithResponse(TextResource.Res(R.string.cmd_block_not_found, persistentListOf(target.toString())))

        val result = helixApiClient.blockUser(targetId)
        return when {
            result.isSuccess -> {
                ignoresRepository.addUserBlock(targetId, target)
                CommandResult.AcceptedWithResponse(TextResource.Res(R.string.cmd_block_success, persistentListOf(target.toString())))
            }

            else -> {
                CommandResult.AcceptedWithResponse(TextResource.Res(R.string.cmd_block_error, persistentListOf(target.toString())))
            }
        }
    }

    private suspend fun unblockUserCommand(args: List<String>): CommandResult.AcceptedWithResponse {
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.AcceptedWithResponse(TextResource.Res(R.string.cmd_unblock_usage))
        }

        val target = args.first().toUserName()
        val targetId =
            helixApiClient
                .getUserIdByName(target)
                .getOrNull() ?: return CommandResult.AcceptedWithResponse(TextResource.Res(R.string.cmd_unblock_not_found, persistentListOf(target.toString())))

        val result =
            runCatching {
                ignoresRepository.removeUserBlock(targetId, target)
                CommandResult.AcceptedWithResponse(TextResource.Res(R.string.cmd_unblock_success, persistentListOf(target.toString())))
            }

        return result.getOrElse {
            CommandResult.AcceptedWithResponse(TextResource.Res(R.string.cmd_unblock_error, persistentListOf(target.toString())))
        }
    }

    private suspend fun uptimeCommand(channel: UserName): CommandResult.AcceptedWithResponse {
        val result =
            helixApiClient
                .getStreams(listOf(channel))
                .getOrNull()
                ?.getOrNull(0) ?: return CommandResult.AcceptedWithResponse(TextResource.Res(R.string.cmd_uptime_not_live))

        val uptime = calculateUptime(result.startedAt)
        return CommandResult.AcceptedWithResponse(TextResource.Res(R.string.cmd_uptime_response, persistentListOf(uptime)))
    }

    private fun helpCommand(
        roomState: RoomState,
        userState: UserState,
    ): CommandResult.AcceptedWithResponse {
        val commands =
            twitchCommandRepository
                .getAvailableCommandTriggers(roomState, userState)
                .plus(defaultCommandTriggers)
                .plus(commandShortcutTriggers)
                .joinToString(separator = " ")

        return CommandResult.AcceptedWithResponse(TextResource.Res(R.string.cmd_help_response, persistentListOf(commands)))
    }

    private fun checkUserCommands(trigger: String): CommandResult {
        val commands = customCommands.value
        val foundCommand = commands.find { it.trigger == trigger } ?: return CommandResult.NotFound

        return CommandResult.Message(foundCommand.command)
    }
}
