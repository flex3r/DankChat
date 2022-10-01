package com.flxrs.dankchat.data.repo

import android.util.Log
import com.flxrs.dankchat.data.api.ApiManager
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
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.seconds

class CommandRepository @Inject constructor(
    private val chatRepository: ChatRepository,
    private val apiManager: ApiManager,
    private val preferenceStore: DankChatPreferenceStore
) {
    private val customCommands: Flow<List<CommandItem.Entry>> = preferenceStore.commandsAsFlow
    private val supibotCommands = mutableMapOf<String, MutableStateFlow<List<String>>>()

    private val defaultCommands = Command.values().map { it.trigger }
    private val twitchCommands = TWITCH_COMMANDS.map { listOf(".$it", "/$it") }.flatten()

    val commands: Flow<List<String>> = customCommands.map { customCommands ->
        defaultCommands + twitchCommands + customCommands.map { it.trigger }
    }

    fun getSupibotCommands(channel: String): StateFlow<List<String>> = supibotCommands.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun clearSupibotCommands() = supibotCommands.forEach { it.value.value = emptyList() }.also { supibotCommands.clear() }

    suspend fun checkForCommands(message: String, channel: String, skipSuspendingCommands: Boolean = false): CommandResult {
        if (!preferenceStore.isLoggedIn) {
            return CommandResult.NotFound
        }

        val words = message.split(" ")
        if (words.isEmpty()) {
            return CommandResult.NotFound
        }


        val trigger = words.first()
        if (skipSuspendingCommands) {
            return checkUserCommands(trigger)
        }

        return when (Command.values().find { it.trigger == trigger }) {
            Command.BLOCK    -> blockUserCommand(words.drop(1))
            Command.UNBLOCK  -> unblockUserCommand(words.drop(1))
            Command.CHATTERS -> chattersCommand(channel)
            Command.UPTIME   -> uptimeCommand(channel)
            else             -> checkUserCommands(trigger)
        }
    }

    suspend fun loadSupibotCommands() = withContext(Dispatchers.Default) {
        if (!preferenceStore.isLoggedIn) {
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
        return apiManager.getSupibotChannels()?.let { (data) ->
            data.filter { it.isActive }
                .map { it.name }
        }.orEmpty()
    }

    private suspend fun getSupibotCommands(): List<String> {
        return apiManager.getSupibotCommands()?.let { (data) ->
            data.map { command ->
                listOf("$${command.name}") + command.aliases.map { "$$it" }
            }.flatten()
        }.orEmpty()
    }

    private suspend fun getSupibotUserAliases(): List<String> {
        val user = preferenceStore.userName?.ifBlank { null } ?: return emptyList()
        return apiManager.getSupibotUserAliases(user)?.let { (data) ->
            data.map { alias ->
                "$$${alias.name}"
            }
        }.orEmpty()
    }

    private suspend fun blockUserCommand(args: List<String>): CommandResult.Accepted {
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.Accepted("Usage: /block <user>")
        }

        val target = args.first()
        val targetId = runCatching {
            apiManager.getUserIdByName(target)
        }.getOrNull() ?: return CommandResult.Accepted("User $target couldn't be blocked, no user with that name found!")

        val result = runCatching { apiManager.blockUser(targetId) }
        return when {
            result.isSuccess -> {
                chatRepository.addUserBlock(targetId)
                CommandResult.Accepted("You successfully blocked user $target")
            }
            else             -> CommandResult.Accepted("User $target couldn't be blocked, an unknown error occurred!")
        }
    }

    private suspend fun unblockUserCommand(args: List<String>): CommandResult.Accepted {
        if (args.isEmpty() || args.first().isBlank()) {
            return CommandResult.Accepted("Usage: /unblock <user>")
        }

        val target = args.first()
        val targetId = runCatching {
            apiManager.getUserIdByName(target)
        }.getOrNull() ?: return CommandResult.Accepted("User $target couldn't be unblocked, no user with that name found!")

        val result = runCatching { apiManager.unblockUser(targetId) }
        return when {
            result.isSuccess -> {
                chatRepository.removeUserBlock(targetId)
                CommandResult.Accepted("You successfully unblocked user $target")
            }
            else             -> CommandResult.Accepted("User $target couldn't be unblocked, an unknown error occurred!")
        }
    }

    private suspend fun chattersCommand(channel: String): CommandResult.Accepted {
        val result = runCatching {
            apiManager.getChatterCount(channel)
        }.getOrNull() ?: return CommandResult.Accepted("An unknown error occurred!")

        return CommandResult.Accepted("Chatter count: $result")
    }

    private suspend fun uptimeCommand(channel: String): CommandResult.Accepted {
        val result = runCatching {
            apiManager.getStreams(listOf(channel))
        }.getOrNull()
            ?.data
            ?.getOrNull(0) ?: return CommandResult.Accepted("Channel is not live.")

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

        return CommandResult.Accepted("Uptime: $uptime")
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

    sealed class CommandResult {
        data class Accepted(val response: String) : CommandResult()
        data class Message(val message: String) : CommandResult()
        object NotFound : CommandResult()
    }

    companion object {
        private val TAG = CommandRepository::class.java.simpleName
        private val TWITCH_COMMANDS = listOf(
            "help",
            "w",
            "me",
            "disconnect",
            "mods",
            "vips",
            "color",
            "commercial",
            "mod",
            "unmod",
            "vip",
            "unvip",
            "ban",
            "unban",
            "timeout",
            "untimeout",
            "slow",
            "slowoff",
            "r9kbeta",
            "r9kbetaoff",
            "emoteonly",
            "emoteonlyoff",
            "clear",
            "subscribers",
            "subscribersoff",
            "followers",
            "followersoff",
            "host",
            "unhost",
            "raid",
            "unraid",
            "delete",
            "announce",
        )
    }
}