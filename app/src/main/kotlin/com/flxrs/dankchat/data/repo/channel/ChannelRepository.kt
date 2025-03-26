package com.flxrs.dankchat.data.repo.channel

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.repo.chat.UsersRepository
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.firstValue
import com.flxrs.dankchat.utils.extensions.firstValueOrNull
import kotlinx.coroutines.Dispatchers
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
    private val dankChatPreferenceStore: DankChatPreferenceStore,
) {

    private val channelCache = ConcurrentHashMap<UserName, Channel>()
    private val roomStates = ConcurrentHashMap<UserName, RoomState>()
    private val roomStateFlows = ConcurrentHashMap<UserName, MutableSharedFlow<RoomState>>()

    suspend fun getChannel(name: UserName): Channel? {
        val cached = channelCache[name]
        if (cached != null) {
            return channelCache[name]
        }

        val channel = when {
            dankChatPreferenceStore.isLoggedIn -> helixApiClient.getUserByName(name)
                .getOrNull()
                ?.let { Channel(id = it.id, name = it.name, displayName = it.displayName, avatarUrl = it.avatarUrl) }

            else                               -> null
        } ?: tryGetChannelFromIrc(name)

        if (channel != null) {
            channelCache[name] = channel
        }

        return channel
    }

    suspend fun getChannel(id: UserId): Channel? {
        val cached = getCachedChannelByIdOrNull(id)
        if (cached != null) {
            return cached
        }

        if (!dankChatPreferenceStore.isLoggedIn) {
            return null
        }

        val channel = helixApiClient.getUser(id)
            .getOrNull()
            ?.let { Channel(id = it.id, name = it.name, displayName = it.displayName, avatarUrl = it.avatarUrl) }

        if (channel != null) {
            channelCache[channel.name] = channel
        }

        return channel
    }

    fun getCachedChannelByIdOrNull(id: UserId): Channel? {
        return channelCache.values.find { it.id == id }
    }

    fun tryGetUserNameById(id: UserId): UserName? {
        return roomStates.values.find { it.channelId == id }?.channel
    }

    fun getRoomStateFlow(channel: UserName): SharedFlow<RoomState> = roomStateFlows.getOrPut(channel) {
        MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    fun getRoomState(channel: UserName): RoomState? = roomStateFlows[channel]?.firstValueOrNull

    fun handleRoomState(msg: IrcMessage) {
        val channel = msg.params.getOrNull(0)?.substring(1)?.toUserName() ?: return
        val channelId = msg.tags["room-id"]?.toUserId() ?: return
        val flow = roomStateFlows[channel] ?: return
        val state = if (flow.replayCache.isEmpty()) {
            RoomState(channel, channelId).copyFromIrcMessage(msg)
        } else {
            flow.firstValue.copyFromIrcMessage(msg)
        }
        roomStates[channel] = state
        flow.tryEmit(state)
    }

    suspend fun getChannels(names: Collection<UserName>): List<Channel> = withContext(Dispatchers.IO) {
        val cached = names.mapNotNull { channelCache[it] }
        val cachedNames = cached.mapTo(mutableSetOf(), Channel::name)
        val remaining = names - cachedNames
        if (remaining.isEmpty() || !dankChatPreferenceStore.isLoggedIn) {
            return@withContext cached
        }

        val channels = helixApiClient.getUsersByNames(remaining)
            .getOrNull()
            .orEmpty()
            .map { Channel(id = it.id, name = it.name, displayName = it.displayName, avatarUrl = it.avatarUrl) }

        channels.forEach { channelCache[it.name] = it }
        return@withContext cached + channels
    }

    fun cacheChannels(channels: List<Channel>) {
        channels.forEach { channelCache[it.name] = it }
    }

    fun initRoomState(channel: UserName) {
        roomStateFlows.putIfAbsent(channel, MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST))    }

    fun removeRoomState(channel: UserName) {
        roomStates.remove(channel)
        roomStateFlows.remove(channel)
    }

    private fun tryGetChannelFromIrc(name: UserName): Channel? {
        val id = roomStates[name]?.channelId
        val displayName = usersRepository.findDisplayName(name, name)
        return id?.let { Channel(id = it, name = name, displayName = displayName ?: name.toDisplayName(), avatarUrl = null) }
    }
}
