package com.flxrs.dankchat.data.repo.channel

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.firstValueOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val chatRepository: ChatRepository,
    private val dataRepository: DataRepository,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
) {

    private val cache = ConcurrentHashMap<UserName, Channel>()

    suspend fun getChannel(name: UserName): Channel? {
        val cached = cache[name]
        if (cached != null) {
            return cache[name]
        }

        val channel = when {
            dankChatPreferenceStore.isLoggedIn -> dataRepository.getUserByName(name)?.let { Channel(it.id, it.name, it.displayName) }
            else                               -> null
        } ?: tryGetChannelFromIrc(name)

        if (channel != null) {
            cache[name] = channel
        }

        return channel
    }

    suspend fun getChannels(names: List<UserName>): List<Channel> = withContext(Dispatchers.IO) {
        val cached = names.mapNotNull { cache[it] }
        val cachedNames = cached.map(Channel::name).toSet()
        val remaining = names - cachedNames
        if (remaining.isEmpty()) {
            return@withContext cached
        }

        if (dankChatPreferenceStore.isLoggedIn) {
            val channels = dataRepository.getUsersByNames(remaining).map { Channel(it.id, it.name, it.displayName) }
            channels.forEach { cache[it.name] = it }
            return@withContext cached + channels
        }

        val (roomStatePairs, remainingForRoomState) = remaining.fold(Pair(emptyList<RoomState>(), emptyList<UserName>())) { (states, remaining), user ->
            when (val state = chatRepository.getRoomState(user).firstValueOrNull) {
                null -> states to remaining + user
                else -> states + state to remaining
            }
        }

        val remainingPairs = remainingForRoomState.map { user ->
            async {
                withTimeoutOrNull(getRoomStateDelay(remainingForRoomState)) {
                    chatRepository
                        .getRoomState(user)
                        .firstOrNull()
                        ?.let { Channel(it.channelId, it.channel, it.channel.toDisplayName()) }
                }
            }
        }.awaitAll().filterNotNull()

        val roomStateChannels = roomStatePairs.map { Channel(it.channelId, it.channel, it.channel.toDisplayName()) } + remainingPairs
        roomStateChannels.forEach { cache[it.name] = it }
        cached + roomStateChannels
    }

    private fun tryGetChannelFromIrc(name: UserName): Channel? {
        val id = chatRepository.getRoomState(name).firstValueOrNull?.channelId
        val displayName = chatRepository.findDisplayName(name, name)
        return id?.let { Channel(it, name, displayName ?: name.toDisplayName()) }
    }

    companion object {
        private const val IRC_TIMEOUT_DELAY = 5_000L
        private const val IRC_TIMEOUT_CHANNEL_DELAY = 600L
        private fun getRoomStateDelay(channels: List<UserName>): Long = IRC_TIMEOUT_DELAY + channels.size * IRC_TIMEOUT_CHANNEL_DELAY
    }
}
