package com.flxrs.dankchat.data.repo

import android.util.Log
import com.flxrs.dankchat.data.api.chatters.ChattersApiClient
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.supibot.SupibotApiClient
import com.flxrs.dankchat.data.twitch.command.CommandContext
import com.flxrs.dankchat.data.twitch.command.TwitchCommand
import com.flxrs.dankchat.data.twitch.command.TwitchCommandRepository
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.command.CommandItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
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
    private val preferenceStore: DankChatPreferenceStore
) {
    private val customCommands: Flow<List<CommandItem.Entry>> = preferenceStore.commandsAsFlow
    private val supibotCommands = mutableMapOf<String, MutableStateFlow<List<String>>>()

    private val defaultCommands = Command.values()
    private val defaultCommandTriggers = defaultCommands.map { it.trigger }
    private val twitchCommands = TwitchCommand.values()
    private val twitchCommandTriggers = twitchCommands.map { listOf(".${it.trigger}", "/${it.trigger}") }.flatten()

    val commandTriggers: Flow<List<String>> = customCommands.map { customCommands ->
        defaultCommandTriggers + twitchCommandTriggers + customCommands.map { it.trigger }
    }

    fun getSupibotCommands(channel: String): StateFlow<List<String>> = supibotCommands.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun clearSupibotCommands() = supibotCommands.forEach { it.value.value = emptyList() }.also { supibotCommands.clear() }

    suspend fun checkForCommands(message: String, channel: String, channelId: String, skipSuspendingCommands: Boolean = false): CommandResult {
        if (!preferenceStore.isLoggedIn) {
            return CommandResult.NotFound
        }

        val words = message.split(" ")
        if (words.isEmpty()) {
            return CommandResult.NotFound
        }


        val trigger = words.first()
        if (trigger.isEmpty()) {
            return CommandResult.NotFound
        }

        val triggerWithoutFirstChar = trigger.drop(1)
        if (triggerWithoutFirstChar == TwitchCommand.Me.trigger) {
            return CommandResult.NotFound
        }

        if (skipSuspendingCommands) {
            return checkUserCommands(trigger)
        }

        val twitchCommand = twitchCommands.find { it.trigger == triggerWithoutFirstChar }
        if (twitchCommand != null) {
            val context = CommandContext(trigger, channel, channelId, message, words.drop(1))
            return twitchCommandRepository.handleTwitchCommand(twitchCommand, context)
        }

        return when (defaultCommands.find { it.trigger == trigger }) {
            Command.BLOCK    -> blockUserCommand(words.drop(1))
            Command.UNBLOCK  -> unblockUserCommand(words.drop(1))
            Command.CHATTERS -> chattersCommand(channel)
            Command.UPTIME   -> uptimeCommand(channel)
            else             -> checkUserCommands(trigger)
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

    private suspend fun getSupibotChannels(): List<String> {
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
        val user = preferenceStore.userName?.ifBlank { null } ?: return emptyList()
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

        val target = args.first()
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

        val target = args.first()
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

    private suspend fun chattersCommand(channel: String): CommandResult.AcceptedWithResponse {
        val result = chattersApiClient.getChatterCount(channel)
            .getOrNull() ?: return CommandResult.AcceptedWithResponse("An unknown error occurred!")

        return CommandResult.AcceptedWithResponse("Chatter count: $result")
    }

    private suspend fun uptimeCommand(channel: String): CommandResult.AcceptedWithResponse {
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

    private fun checkUserCommands(trigger: String): CommandResult {
        val commands = preferenceStore.getCommands()
        val foundCommand = commands.find { it.trigger == trigger } ?: return CommandResult.NotFound

        return CommandResult.Message(foundCommand.command)
    }

    enum class Command(val trigger: String) {
        BLOCK(trigger = "/block"),
        UNBLOCK(trigger = "/unblock"),
        CHATTERS(trigger = "/chatters"),
        UPTIME(trigger = "/uptime");
    }

    companion object {
        private val TAG = CommandRepository::class.java.simpleName
    }
}