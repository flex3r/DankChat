package com.flxrs.dankchat.data.repo.channel

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.dto.UserDto
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.repo.chat.UsersRepository
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.extensions.firstValue
import com.flxrs.dankchat.utils.extensions.firstValueOrNull
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

@Single
class ChannelRepository(
    private val usersRepository: UsersRepository,
    private val helixApiClient: HelixApiClient,
    private val authDataStore: AuthDataStore,
    private val dispatchersProvider: DispatchersProvider,
) {
    private val channelCache = ConcurrentHashMap<UserName, Channel>()
    private val userDtoCache = ConcurrentHashMap<UserId, UserDto>()
    private val roomStates = ConcurrentHashMap<UserName, RoomState>()
    private val roomStateFlows = ConcurrentHashMap<UserName, MutableSharedFlow<RoomState>>()

    suspend fun getChannel(name: UserName): Channel? {
        val cached = channelCache[name]
        if (cached != null) {
            return cached
        }

        val userDto = when {
            authDataStore.isLoggedIn -> helixApiClient.getUserByName(name).getOrNull()
            else -> null
        }

        val channel = userDto?.toChannel() ?: tryGetChannelFromIrc(name)
        if (channel != null) {
            channelCache[name] = channel
        }
        if (userDto != null) {
            userDtoCache[userDto.id] = userDto
        }

        return channel
    }

    suspend fun isChannelMissing(name: UserName): Boolean {
        if (channelCache[name] != null) {
            return false
        }

        // A failed lookup proves nothing, only an empty result means the user does not exist
        return helixApiClient.getUsersByNames(listOf(name)).getOrNull()?.isEmpty() == true
    }

    suspend fun getChannel(id: UserId): Channel? {
        val cached = getCachedChannelByIdOrNull(id)
        if (cached != null) {
            return cached
        }

        if (!authDataStore.isLoggedIn) {
            return null
        }

        val userDto = helixApiClient.getUser(id).getOrNull()
        val channel = userDto?.toChannel()

        if (channel != null) {
            channelCache[channel.name] = channel
        }
        if (userDto != null) {
            userDtoCache[userDto.id] = userDto
        }

        return channel
    }

    suspend fun getUserDto(id: UserId): UserDto? {
        userDtoCache[id]?.let { return it }

        if (!authDataStore.isLoggedIn) {
            return null
        }

        val userDto = helixApiClient.getUser(id).getOrNull() ?: return null
        userDtoCache[userDto.id] = userDto
        channelCache[userDto.name] = userDto.toChannel()
        return userDto
    }

    suspend fun getUserDtoByName(name: UserName): UserDto? {
        userDtoCache.values.firstOrNull { it.name == name }?.let { return it }

        if (!authDataStore.isLoggedIn) {
            return null
        }

        val userDto = helixApiClient.getUserByName(name).getOrNull() ?: return null
        userDtoCache[userDto.id] = userDto
        channelCache[userDto.name] = userDto.toChannel()
        return userDto
    }

    fun getCachedUserDto(id: UserId): UserDto? = userDtoCache[id]

    fun getCachedChannelByIdOrNull(id: UserId): Channel? = channelCache.values.find { it.id == id }

    fun tryGetUserNameById(id: UserId): UserName? = roomStates.values.find { it.channelId == id }?.channel

    fun getRoomStateFlow(channel: UserName): SharedFlow<RoomState> = roomStateFlows.getOrPut(channel) {
        MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    fun getRoomState(channel: UserName): RoomState? = roomStateFlows[channel]?.firstValueOrNull

    fun handleRoomState(msg: IrcMessage) {
        val channel =
            msg.params
                .getOrNull(0)
                ?.substring(1)
                ?.toUserName() ?: return
        val channelId = msg.tags["room-id"]?.toUserId() ?: return
        val flow = roomStateFlows[channel] ?: return
        val state =
            if (flow.replayCache.isEmpty()) {
                RoomState(channel, channelId).copyFromIrcMessage(msg)
            } else {
                flow.firstValue.copyFromIrcMessage(msg)
            }
        roomStates[channel] = state
        flow.tryEmit(state)
    }

    suspend fun getChannelsByIds(ids: Collection<UserId>): List<Channel> = withContext(dispatchersProvider.io) {
        val cached = ids.mapNotNull { getCachedChannelByIdOrNull(it) }
        val cachedIds = cached.mapTo(mutableSetOf(), Channel::id)
        val remaining = ids.filterNot { it in cachedIds }
        if (remaining.isEmpty() || !authDataStore.isLoggedIn) {
            return@withContext cached
        }

        val users =
            helixApiClient
                .getUsersByIds(remaining)
                .getOrNull()
                .orEmpty()

        val channels = users.map { it.toChannel() }
        channels.forEach { channelCache[it.name] = it }
        users.forEach { userDtoCache[it.id] = it }
        return@withContext cached + channels
    }

    suspend fun getChannels(names: Collection<UserName>): List<Channel> = withContext(dispatchersProvider.io) {
        val cached = names.mapNotNull { channelCache[it] }
        val cachedNames = cached.mapTo(mutableSetOf(), Channel::name)
        val remaining = names - cachedNames
        if (remaining.isEmpty() || !authDataStore.isLoggedIn) {
            return@withContext cached
        }

        val users =
            helixApiClient
                .getUsersByNames(remaining)
                .getOrNull()
                .orEmpty()

        val channels = users.map { it.toChannel() }
        channels.forEach { channelCache[it.name] = it }
        users.forEach { userDtoCache[it.id] = it }
        return@withContext cached + channels
    }

    fun cacheChannels(channels: List<Channel>) {
        channels.forEach { channelCache[it.name] = it }
    }

    fun initRoomState(channel: UserName) {
        roomStateFlows.putIfAbsent(channel, MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST))
    }

    fun removeRoomState(channel: UserName) {
        roomStates.remove(channel)
        roomStateFlows.remove(channel)
    }

    private fun tryGetChannelFromIrc(name: UserName): Channel? {
        val id = roomStates[name]?.channelId
        val displayName = usersRepository.findDisplayName(name, name)
        return id?.let { Channel(id = it, name = name, displayName = displayName ?: name.toDisplayName(), avatarUrl = null) }
    }

    private fun UserDto.toChannel() = Channel(id = id, name = name, displayName = displayName, avatarUrl = avatarUrl)
}
